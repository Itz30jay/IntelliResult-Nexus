package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.GradingRuleDAO;
import com.intelliresult.nexus.dao.GradingRuleDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.ResultSummaryDAO;
import com.intelliresult.nexus.dao.ResultSummaryDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.GradingRule;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.enums.ExamType;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ResultProcessingException;
import com.intelliresult.nexus.util.GradeUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sec. 11's "centralized ResultCalculationService (never duplicate
 * calculation logic)" - the single place that turns raw marks into a scored,
 * ranked, comparable result. Two levels, matching how the schema is shaped:
 * <ul>
 * <li>{@link #calculateSubjectResult} - one {@link Result} row (one subject,
 * one student, one exam): total/percentage/grade/gradePoint/pass, written
 * through {@link Result#applyCalculatedScore}.</li>
 * <li>{@link #generateResultSummary}/{@link #generateAllSummariesAndRanks} -
 * one {@link ResultSummary} row (one student's whole exam): SGPA, overall
 * percentage/grade, CGPA (FINAL_EXAMINATION only), pass/fail, improvement vs.
 * the previous exam, and (bulk only) class/overall rank.</li>
 * </ul>
 * Deliberately not wired into MarksEntryService's submit transition
 * (Phase 6) - see docs/architecture/PHASE7-RESULT-ENGINE.md's "Decisions"
 * section for the full reasoning, in short: (a) the seeded Unit Test 1 data
 * already establishes that a SUBMITTED-but-not-yet-approved Result has null
 * calculated fields under this project's current architecture, and (b)
 * hard-coupling "can a teacher submit marks" to "is this academic year's
 * grading table fully configured" would let an unrelated admin
 * configuration gap block a teacher's own workflow for reasons outside
 * their control. The natural trigger is Phase 8's approval action - "an
 * administrator reviews and approves" (Sec. 10) is the point a computed
 * score is actually needed, the same "build the capability, wire the
 * trigger in the phase that needs it" discipline already used for
 * GradeUtil/this class themselves (skipped as premature scaffolding in
 * Phase 1, per that phase's own notes).
 */
public class ResultCalculationService {

    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultSummaryDAO resultSummaryDAO = new ResultSummaryDAOImpl();
    private final GradingRuleDAO gradingRuleDAO = new GradingRuleDAOImpl();
    private final SubjectDAO subjectDAO = new SubjectDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ExamDAO examDAO = new ExamDAOImpl();

    // ============================================================== subject-level

    /**
     * Computes and persists total/percentage/grade/gradePoint/pass for one
     * Result, from whichever of theory/practical/internal marks are
     * currently non-null on it - not from {@code subject.isHasTheory()}
     * etc. This distinction is not a simplification made here; it is a
     * verified fact about how this project's own seed data already behaves
     * (CS301's Unit Test 1 rows carry only theoryMarks, and their
     * percentage is computed against theoryMaxMarks (70) alone, not
     * Subject.totalMaxMarks (100), even though CS301.hasPractical is true -
     * confirmed by re-deriving both denominators against the live seeded
     * values before this method was written; see PHASE7-RESULT-ENGINE.md).
     * A subject's hasTheory/hasPractical/hasInternal flags describe what
     * that subject *can* be examined on across its whole curriculum, not
     * what any one specific exam actually tested.
     *
     * @throws ResourceNotFoundException  if no Result exists with this id.
     * @throws ResultProcessingException  if no component has marks entered,
     *         if an entered component's marks exceed that component's own
     *         configured maximum (defense in depth - MarksEntryService
     *         already checks this at entry time, but a later correction path
     *         must not blindly trust that), or if no active GradingRule
     *         covers the computed percentage for this exam's academic year.
     */
    public Result calculateSubjectResult(Long resultId) {
        return inTransaction(() -> {
            Result result = resultDAO.findById(resultId)
                    .orElseThrow(() -> new ResourceNotFoundException("Result not found."));
            return doCalculateSubjectResult(result);
        });
    }

    /**
     * Package-private, not private: this is the actual calculation logic,
     * deliberately separated from {@link #calculateSubjectResult}'s
     * transaction wrapper above so a same-package caller already inside its
     * own transaction can call straight through to it without triggering a
     * second, nested {@code session.beginTransaction()} (Hibernate does not
     * support that on one session). Phase 8's {@code ResultService} does
     * exactly this - "calculate, then mark approved" is one atomic write
     * (Sec. 66), not two separate transactions that could leave a result
     * scored but not approved, or approved on stale marks, if the process
     * died between them.
     */
    Result doCalculateSubjectResult(Result result) {
        Subject subject = result.getSubject();
        List<ComponentScore> components = collectPresentComponents(result, subject);
        if (components.isEmpty()) {
            throw new ResultProcessingException("Cannot calculate a result for "
                    + result.getStudent().getRollNo() + " in " + subject.getSubjectCode()
                    + ": no theory, practical, or internal marks have been entered yet.");
        }

        BigDecimal obtained = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        for (ComponentScore c : components) {
            if (c.marks().compareTo(c.maxMarks()) > 0) {
                throw new ResultProcessingException("Cannot calculate a result for "
                        + result.getStudent().getRollNo() + " in " + subject.getSubjectCode() + ": "
                        + c.label() + " marks (" + c.marks() + ") exceed the configured maximum (" + c.maxMarks() + ").");
            }
            obtained = obtained.add(c.marks());
            max = max.add(c.maxMarks());
        }

        BigDecimal percentage = GradeUtil.calculatePercentage(obtained, max);
        GradingRule matched = resolveGradeOrThrow(percentage, result.getExam());
        boolean pass = components.stream().allMatch(c -> GradeUtil.meetsPassingThreshold(c.marks(), c.passingMarks()));

        result.applyCalculatedScore(obtained, percentage, matched.getGrade(), matched.getGradePoint(), pass);
        resultDAO.update(result);
        return result;
    }

    /** One entered (non-null) mark component paired with its subject-configured maximum/passing thresholds - built fresh per call from whichever of theory/practical/internal are actually present, per this method's own class-level Javadoc note. */
    private List<ComponentScore> collectPresentComponents(Result result, Subject subject) {
        List<ComponentScore> components = new ArrayList<>(3);
        if (result.getTheoryMarks() != null) {
            requireSubjectHasComponent(subject.isHasTheory(), "theory", result, subject);
            components.add(new ComponentScore("Theory", result.getTheoryMarks(), subject.getTheoryMaxMarks(), subject.getTheoryPassingMarks()));
        }
        if (result.getPracticalMarks() != null) {
            requireSubjectHasComponent(subject.isHasPractical(), "practical", result, subject);
            components.add(new ComponentScore("Practical", result.getPracticalMarks(), subject.getPracticalMaxMarks(), subject.getPracticalPassingMarks()));
        }
        if (result.getInternalMarks() != null) {
            requireSubjectHasComponent(subject.isHasInternal(), "internal", result, subject);
            components.add(new ComponentScore("Internal", result.getInternalMarks(), subject.getInternalMaxMarks(), subject.getInternalPassingMarks()));
        }
        return components;
    }

    private void requireSubjectHasComponent(boolean subjectHasIt, String label, Result result, Subject subject) {
        if (!subjectHasIt) {
            // Should be unreachable in normal operation - MarksEntryService.validateComponent already
            // rejects entering marks for a component the subject doesn't have. Guarded here anyway
            // because calculation must never trust upstream validation blindly (Sec. 47); a future
            // correction path (Phase 9/10) writing marks through a different route is exactly the
            // scenario this exists for.
            throw new ResultProcessingException("Cannot calculate a result for " + result.getStudent().getRollNo()
                    + " in " + subject.getSubjectCode() + ": " + label + " marks are recorded but this subject has no "
                    + label + " component configured.");
        }
    }

    private GradingRule resolveGradeOrThrow(BigDecimal percentage, Exam exam) {
        List<GradingRule> activeRules = gradingRuleDAO.findActiveByAcademicYear(exam.getAcademicYear().getId());
        return GradeUtil.resolveGrade(activeRules, percentage)
                .orElseThrow(() -> new ResultProcessingException("No active grading rule covers a percentage of "
                        + percentage + "% for academic year " + exam.getAcademicYear().getLabel()
                        + ". An administrator must configure a grading rule for this range before results in this "
                        + "academic year can be calculated."));
    }

    /** {@code label} exists purely so validation/error messages can name the failing component ("Theory marks (95) exceed the configured maximum (70)") instead of a generic "a component" - Sec. 45's "clear feedback... never a generic message without context" applies just as much to calculation failures as to form validation. */
    private record ComponentScore(String label, BigDecimal marks, BigDecimal maxMarks, BigDecimal passingMarks) {
    }

    // ============================================================== exam-level (summary/SGPA/CGPA/improvement)

    /**
     * (Re)computes one student's aggregate for one exam from whichever of
     * their subject Results already have a calculated score - SGPA, overall
     * percentage/grade, pass/fail, CGPA (FINAL_EXAMINATION only), and
     * improvement vs. the previous exam. Does not set class/overall rank -
     * see {@link #recalculateRanksForExam}, since ranking one student
     * requires knowing every other student's percentage in the same
     * cohort, not just this one row. Upserts: if this student already has a
     * summary for this exam (a re-evaluation changed a mark, Phase 10),
     * it is recomputed in place rather than duplicated - {@code
     * uq_result_summaries_student_exam} would reject a duplicate insert
     * either way.
     *
     * @throws ResourceNotFoundException  if the student or exam does not exist.
     * @throws ResultProcessingException  if this student has no Result for
     *         this exam with a calculated score yet (nothing to aggregate -
     *         call {@link #calculateSubjectResult} for at least one subject
     *         first), or if no active GradingRule covers the computed
     *         overall percentage.
     */
    public ResultSummary generateResultSummary(Long studentId, Long examId) {
        return inTransaction(() -> {
            Student student = studentDAO.findById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
            Exam exam = examDAO.findById(examId)
                    .orElseThrow(() -> new ResourceNotFoundException("Exam not found."));
            return doGenerateResultSummary(student, exam);
        });
    }

    /**
     * Package-private for the same reason as {@link #doCalculateSubjectResult}
     * - Phase 9's {@link #refreshSummaryIfPublished} composes this into its
     * own already-open transaction rather than through
     * {@link #generateResultSummary}'s separately-transactional wrapper.
     */
    ResultSummary doGenerateResultSummary(Student student, Exam exam) {
        List<Result> calculated = resultDAO.findByStudentAndExam(student.getId(), exam.getId()).stream()
                .filter(r -> r.getTotalMarks() != null)
                .toList();
        if (calculated.isEmpty()) {
            throw new ResultProcessingException("Cannot generate a result summary for " + student.getRollNo()
                    + " in " + exam.getName() + ": no subject result has a calculated score yet.");
        }

        int subjectsExpected = subjectDAO.findBySemester(exam.getSemester().getId()).size();

        BigDecimal totalObtained = BigDecimal.ZERO;
        BigDecimal totalMax = BigDecimal.ZERO;
        BigDecimal totalCredits = BigDecimal.ZERO;
        boolean allSubjectsPass = true;
        List<GradeUtil.WeightedValue> subjectGradePoints = new ArrayList<>();

        for (Result r : calculated) {
            totalObtained = totalObtained.add(r.getTotalMarks());
            totalMax = totalMax.add(r.getSubject().getTotalMaxMarks());
            if (Boolean.FALSE.equals(r.getPass())) {
                allSubjectsPass = false;
            }
            // A subject with no credits configured (Subject.credits is nullable - Sec. 7 allows it
            // for, e.g., a non-credit-bearing audit subject) contributes to the percentage/pass-fail
            // above but is excluded here: giving it zero weight is correct, but *including* a
            // zero/null-credit term would either divide by nothing meaningfully or silently pull the
            // average toward zero, neither of which is what "this subject doesn't count toward SGPA"
            // should mean.
            BigDecimal credits = r.getSubject().getCredits();
            if (credits != null && credits.compareTo(BigDecimal.ZERO) > 0) {
                totalCredits = totalCredits.add(credits);
                subjectGradePoints.add(new GradeUtil.WeightedValue(r.getGradePoint(), credits));
            }
        }

        BigDecimal overallPercentage = GradeUtil.calculatePercentage(totalObtained, totalMax);
        GradingRule matched = resolveGradeOrThrow(overallPercentage, exam);
        BigDecimal sgpa = GradeUtil.weightedAverage(subjectGradePoints).orElse(null);

        ResultSummary summary = resultSummaryDAO.findByStudentAndExam(student.getId(), exam.getId())
                .orElseGet(() -> new ResultSummary(student, exam));
        summary.applyComputedResult(calculated.size(), subjectsExpected, totalObtained, totalMax, totalCredits,
                overallPercentage, matched.getGrade(), matched.getGradePoint(), sgpa, allSubjectsPass);

        applyImprovement(summary, student, exam, overallPercentage);
        applyCgpaIfFinalExamination(summary, student, exam);

        if (summary.getId() == null) {
            resultSummaryDAO.save(summary);
        } else {
            resultSummaryDAO.update(summary);
        }
        return summary;
    }

    /** Sec. 15/55's "current exam vs previous exam": looks up the most recent summary this student has for an exam that started before this one, and records the comparison directly on this summary (previousExam/previousPercentage/percentageChange) rather than leaving it to be recomputed on every dashboard read. Leaves all three null - Sec. 56's empty state, not a zero - when no earlier exam has a summary yet. */
    private void applyImprovement(ResultSummary summary, Student student, Exam exam, BigDecimal overallPercentage) {
        Optional<ResultSummary> previous = resultSummaryDAO.findMostRecentBefore(student.getId(), exam.getStartTime());
        if (previous.isEmpty()) {
            summary.applyImprovement(null, null, null);
            return;
        }
        ResultSummary prev = previous.get();
        BigDecimal change = GradeUtil.percentageChange(overallPercentage, prev.getOverallPercentage());
        summary.applyImprovement(prev.getExam(), prev.getOverallPercentage(), change);
    }

    /**
     * CGPA only on a FINAL_EXAMINATION summary - see
     * {@link ResultSummary#getCgpa()}'s own Javadoc for why. Credit-weighted
     * across one summary per completed semester: this exam's own
     * just-computed (SGPA, totalCredits) plus the most recent prior
     * FINAL_EXAMINATION summary for every *other* semester this student has
     * one for (guarding against a retake producing two FINAL_EXAMINATION
     * rows in the same semester and double-counting it - the DAO method's
     * own Javadoc flags this as a real possibility it deliberately leaves
     * for calculation logic to resolve, not a query filter).
     */
    private void applyCgpaIfFinalExamination(ResultSummary summary, Student student, Exam exam) {
        if (exam.getExamType() != ExamType.FINAL_EXAMINATION || summary.getSgpa() == null) {
            return;
        }
        // Ascending by exam.startTime (the DAO's documented contract) - a later entry for the same
        // semester simply overwrites an earlier one in this map, so what remains is the most recent
        // FINAL_EXAMINATION per semester.
        Map<Long, ResultSummary> mostRecentPerSemester = new LinkedHashMap<>();
        for (ResultSummary priorSummary : resultSummaryDAO.findFinalExamSummariesForStudent(student.getId())) {
            if (!priorSummary.getExam().getId().equals(exam.getId())) {
                mostRecentPerSemester.put(priorSummary.getExam().getSemester().getId(), priorSummary);
            }
        }

        List<GradeUtil.WeightedValue> semesterSgpas = new ArrayList<>();
        for (ResultSummary priorSummary : mostRecentPerSemester.values()) {
            if (priorSummary.getSgpa() != null) {
                semesterSgpas.add(new GradeUtil.WeightedValue(priorSummary.getSgpa(), priorSummary.getTotalCredits()));
            }
        }
        semesterSgpas.add(new GradeUtil.WeightedValue(summary.getSgpa(), summary.getTotalCredits()));

        GradeUtil.weightedAverage(semesterSgpas).ifPresent(summary::applyCgpa);
    }

    // ============================================================== rank + bulk orchestration

    /**
     * Bulk-populates class/overall rank for every ResultSummary already
     * generated for this exam - see {@link ResultSummaryDAO#recalculateRanks}
     * for the native-query mechanics. A standalone entry point (its own
     * transaction) as well as a step inside
     * {@link #generateAllSummariesAndRanks}, for the case an admin corrects
     * one student's marks later and wants ranks recomputed without
     * regenerating every other student's summary too.
     */
    public void recalculateRanksForExam(Long examId) {
        runInTransaction(() -> resultSummaryDAO.recalculateRanks(examId));
    }

    /**
     * The bulk entry point Sec. 11's "automatically calculates... class
     * rank, overall rank" and Sec. 22's Bulk Operations both describe: every
     * student with at least one calculated Result in this exam gets a
     * (re)generated summary, then ranks are computed across all of them in
     * one pass. One transaction end to end (Sec. 66: a bulk operation either
     * fully succeeds or fully rolls back - a partial run would leave some
     * students' summaries current and others stale mid-exam, which is worse
     * than not having run it at all). The returned list is re-fetched after
     * {@link ResultSummaryDAO#recalculateRanks}'s native update rather than
     * built up from the entities touched during the generation loop above,
     * specifically because that native update clears the persistence
     * context (see that method's own Javadoc) - anything read from it
     * beforehand would either miss the new rank values or, worse, throw
     * trying to lazily load an association through a now-detached entity.
     */
    public List<ResultSummary> generateAllSummariesAndRanks(Long examId) {
        return inTransaction(() -> doGenerateAllSummariesAndRanks(examId));
    }

    /**
     * Package-private, not private - same reason as
     * {@link #doCalculateSubjectResult}: Phase 8's {@code ResultService.
     * publishExam} needs "mark every approved result published, then
     * generate every affected student's summary and rank" to be one
     * transaction, not two, and this is the half of that work owned by the
     * calculation engine rather than by the workflow-status transition
     * itself.
     */
    List<ResultSummary> doGenerateAllSummariesAndRanks(Long examId) {
        Exam exam = examDAO.findById(examId)
                .orElseThrow(() -> new ResourceNotFoundException("Exam not found."));
        for (Long studentId : resultDAO.findDistinctStudentIdsByExam(examId)) {
            Student student = studentDAO.findById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
            doGenerateResultSummary(student, exam);
        }
        resultSummaryDAO.recalculateRanks(examId);
        return resultSummaryDAO.findByExam(examId);
    }

    /**
     * Phase 9's {@code ResultService.correctResult} calls this after
     * recalculating one corrected subject Result, so an authorized
     * post-lock correction (Sec. 47) can never leave an already-visible
     * {@link ResultSummary} silently wrong. Deliberately narrower than
     * {@link #doGenerateAllSummariesAndRanks}: it touches only *this*
     * student's summary, not the whole exam cohort, and - critically - it
     * is a no-op, not an error, when this student has no summary yet.
     * {@link #doGenerateAllSummariesAndRanks} would throw for any *other*
     * student in the exam who happens to have zero calculated results at
     * correction time (an entirely unrelated, in-progress exam is not this
     * one correction's problem to fail over), and re-generating a summary
     * that was never published in the first place has nothing to refresh -
     * the raw {@code Result} correction already recalculated above is
     * enough until this exam is actually published.
     */
    void refreshSummaryIfPublished(Student student, Exam exam) {
        if (resultSummaryDAO.findByStudentAndExam(student.getId(), exam.getId()).isEmpty()) {
            return;
        }
        doGenerateResultSummary(student, exam);
        resultSummaryDAO.recalculateRanks(exam.getId());
    }

    // ---------------------------------------------------------------- transaction helpers

    private interface TransactionalWork<T> {
        T run();
    }

    private <T> T inTransaction(TransactionalWork<T> work) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            T result = work.run();
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    private void runInTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
