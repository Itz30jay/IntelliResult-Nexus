package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.dao.dto.GradeDistributionDTO;
import com.intelliresult.nexus.dao.dto.StudentPerformanceDTO;
import com.intelliresult.nexus.dao.dto.SubjectAverageDTO;
import com.intelliresult.nexus.dao.dto.SubjectPerformanceDTO;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.enums.ResultStatus;

import java.util.List;
import java.util.Optional;

public interface ResultDAO extends SoftDeletableDAO<Result, Long> {
    /** All subject rows for one student in one exam - the marksheet/result-detail view (Sec. 18/19). */
    List<Result> findByStudentAndExam(Long studentId, Long examId);

    /** The one row uniqueness (uq_results_student_exam_subject) guarantees exists at most once - used both to load an existing draft for editing and to check-before-insert. */
    Optional<Result> findByStudentExamSubject(Long studentId, Long examId, Long subjectId);

    /** Every student's result for one subject in one exam - the teacher's marks-entry grid (Sec. 29) and Subject Analysis (Sec. 33). */
    List<Result> findByExamAndSubject(Long examId, Long subjectId);

    /** Only PUBLISHED results are ever visible to a student (Sec. 18) - deliberately not just findByStudent(id), so "what can this student see" can never accidentally leak a DRAFT/SUBMITTED/APPROVED row by forgetting a status filter at the call site. */
    List<Result> findPublishedByStudent(Long studentId);

    /**
     * Every result a student can see at all - PUBLISHED or LOCKED, unlike
     * {@link #findPublishedByStudent}'s PUBLISHED-only scope. Sec. 14's
     * "select eligible result" for a re-evaluation request needs this
     * broader set deliberately: Sec. 10 frames "authorized correction/
     * re-evaluation" as precisely the channel a *locked* result's
     * modification must go through, so a locked result has to remain
     * selectable here or that channel would have no way to ever be reached
     * for it. A separate method rather than widening findPublishedByStudent
     * itself - that method's own narrower "published only" contract may
     * still matter to whatever eventually calls it (Phase 11, most
     * plausibly), and broadening it silently out from under a caller that
     * hasn't been written yet isn't this phase's call to make.
     */
    List<Result> findVisibleToStudent(Long studentId);

    /** The admin approval queue and similar workflow-stage dashboards (Sec. 5/10). */
    List<Result> findByExamAndStatus(Long examId, ResultStatus status);

    /** One teacher's own results at a given status - the Teacher Dashboard's "submitted, awaiting approval" count (Sec. 28) and Phase 6b's Submitted Results list. Deliberately not the section-scoping a "pending marks" tally would need: submittedBy is a direct field on Result, so this is unambiguous by construction the moment status is SUBMITTED or later - unlike DRAFT rows, which have no submittedBy yet and can only be correctly attributed to "my" section by cross-referencing student.currentSection, something Phase 6b's marks-entry screen does naturally by already being scoped to one section and doesn't belong duplicated here. */
    List<Result> findBySubmittedByAndStatus(Long userId, ResultStatus status);

    /** System-wide count by workflow status - dashboard's "Pending Approvals" (SUBMITTED) and "Published Results" (PUBLISHED) cards. */
    long countByStatus(ResultStatus status);

    /** Pass/fail breakdown across every PUBLISHED result system-wide (not scoped to one exam, so the number stays meaningful as more exams accumulate). */
    long countPublishedByPass(boolean isPass);

    /** Average percentage per subject across every published result - dashboard's subject-performance chart and Sec. 33's Subject Analysis. */
    List<SubjectPerformanceDTO> subjectPerformance();

    /** Count of published results per grade - dashboard's grade-distribution chart. */
    List<GradeDistributionDTO> gradeDistribution();

    /** Top N students by average percentage across their published results. */
    List<StudentPerformanceDTO> topPerformers(int limit);

    /** Every (non-deleted) result for one exam, any status, any subject - Phase 9's {@code /admin/results} browser. Deliberately status-agnostic, unlike {@link #findByExamAndStatus}: an audit/versioning view has to be able to show a result regardless of where it sits in the workflow, not just the ones currently actionable at one stage. */
    List<Result> findByExam(Long examId);

    /**
     * Every distinct student who has at least one (non-deleted) Result row
     * in this exam, regardless of subject or status. The iteration list for
     * ResultCalculationService.generateAllSummariesAndRanks (Phase 7): a
     * plain {@code SELECT DISTINCT student_id}, not a full findByExam-style
     * entity load, since the caller only ever needs the ids to loop over -
     * loading every Result row's full student/exam/subject association
     * graph just to throw it away except for one id would be exactly the
     * "loading thousands of records into memory" Sec. 49 warns against.
     */
    List<Long> findDistinctStudentIdsByExam(Long examId);

    /**
     * One row per subject a section sat in this exam, with that subject's
     * class average - Sec. 15's Subject Comparison, scoped by section
     * rather than system-wide the way {@link #subjectPerformance} is.
     * Returns only the average, not any one student's own score
     * (StudentAnalyticsService joins this against
     * {@link #findByStudentAndExam}'s result for the querying student
     * specifically, in Java - see {@code SubjectAverageDTO}'s own Javadoc
     * for why not a correlated subquery here instead). PUBLISHED or LOCKED
     * only, matching {@link #findVisibleToStudent}'s exact "visible to a
     * student" scope - an average a student is being shown their own score
     * against must be built from the same set of results they themselves
     * can see.
     */
    List<SubjectAverageDTO> subjectAveragesForExamSection(Long examId, Long sectionId);

    /**
     * Every calculated (percentage is not null) result a student has,
     * across every exam and subject, ordered by subject then by the exam's
     * own start date ascending - Sec. 16's Subject Strength &amp; Weakness
     * Analysis needs each subject's *own* chronological history to compute
     * a trend, not just a system-wide or per-exam average, so this returns
     * full entities for {@code StudentAnalyticsService} to group and walk
     * in Java rather than a pre-aggregated projection - the grouping-and-
     * trend computation itself is exactly the kind of calculation logic
     * Sec. 37 keeps out of the DAO layer.
     */
    List<Result> findCalculatedByStudent(Long studentId);
}
