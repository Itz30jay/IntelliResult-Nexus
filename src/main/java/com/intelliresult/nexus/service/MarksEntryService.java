package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.TeacherSubjectDAO;
import com.intelliresult.nexus.dao.TeacherSubjectDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.TeacherSubject;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.exception.AuthorizationException;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.MarksEntryGroup;
import com.intelliresult.nexus.service.dto.MarksEntryRow;
import com.intelliresult.nexus.service.dto.StudentMarksInput;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Owns Sec. 29's Marks Entry. Never touches
 * {@code Result.totalMarks}/{@code percentage}/{@code grade}/
 * {@code gradePoint}/{@code pass} - confirmed against the entity's own
 * Javadoc (Phase 3) before writing a line of this class: those five fields
 * are set exclusively by {@code applyCalculatedScore()}, "called
 * exclusively by ResultCalculationService (Phase 7)." Sec. 29's "auto total
 * calculation" is satisfied client-side instead - a live JavaScript running
 * total in the grid, never persisted here.
 * <p>
 * Also never writes to {@code result_history}: that table's
 * {@code change_reason} column is {@code NOT NULL} (schema.sql), and an
 * initial DRAFT entry - or a correction made before the teacher has ever
 * submitted - has no "reason" in the sense Sec. 13 means. History becomes
 * relevant once a value that was already SUBMITTED or later needs
 * correcting, which is Phase 9's authorized-correction process, not
 * ordinary draft editing.
 */
public class MarksEntryService {

    private final TeacherSubjectDAO teacherSubjectDAO = new TeacherSubjectDAOImpl();
    private final ExamDAO examDAO = new ExamDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();

    /** The actual data-level authorization Sec. 8 requires ("Teachers can only enter/manage marks for subjects assigned to them") - beyond AuthorizationFilter's role-level /teacher gate, which has no idea which subjects or sections belong to which teacher. */
    public TeacherSubject requireAssignment(Long teacherId, Long subjectId, Long sectionId) {
        return teacherSubjectDAO.findActiveByTeacher(teacherId).stream()
                .filter(a -> a.getSubject().getId().equals(subjectId) && a.getSection().getId().equals(sectionId))
                .findFirst()
                .orElseThrow(() -> new AuthorizationException("You are not assigned to this subject and section."));
    }

    public Exam requireOpenExam(Long examId) {
        Exam exam = examDAO.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Exam not found."));
        if (!exam.getStatus().isOpenForMarksEntry()) {
            throw new BusinessRuleException("This exam is " + exam.getStatus().name().toLowerCase()
                    + " and is not currently open for marks entry.");
        }
        return exam;
    }

    /** The read path's gate - deliberately more permissive than {@link #requireOpenExam(Long)}, which guards writing. Used by the grid's GET handler so Submitted Results (Sec. 69) can link into a genuine history view even once an exam has moved past ACTIVE/SUBMISSION into APPROVAL or beyond; requireOpenExam alone would reject that. */
    public Exam requireViewableExam(Long examId) {
        Exam exam = examDAO.findById(examId).orElseThrow(() -> new ResourceNotFoundException("Exam not found."));
        if (!exam.getStatus().hasStartedMarksEntry()) {
            throw new BusinessRuleException("This exam is " + exam.getStatus().name().toLowerCase()
                    + " and marks entry has not started for it yet.");
        }
        return exam;
    }

    /** Built from the section roster outward, not from whichever Results already exist - see MarksEntryRow's own Javadoc for why that direction matters. */
    public List<MarksEntryRow> loadGrid(Long examId, Long subjectId, Long sectionId) {
        List<Student> students = studentDAO.findBySection(sectionId);
        Map<Long, Result> byStudentId = resultDAO.findByExamAndSubject(examId, subjectId).stream()
                .collect(Collectors.toMap(r -> r.getStudent().getId(), r -> r));
        return students.stream()
                .sorted(Comparator.comparing(Student::getRollNo))
                .map(s -> new MarksEntryRow(s, byStudentId.get(s.getId())))
                .toList();
    }

    public int saveDraft(Long examId, Long subjectId, Long sectionId, Long teacherId, User actingUser, List<StudentMarksInput> inputs) {
        return persistGrid(examId, subjectId, sectionId, teacherId, actingUser, inputs, false);
    }

    /** Saves whatever was just entered, then transitions every currently-DRAFT result among this section's students - not just the ones touched in this call - to SUBMITTED, in the same transaction. Deliberately one combined action rather than requiring "Save Draft" first: a separate "you must save before you can submit" step invites exactly the mistake of clicking Submit having forgotten to save first. */
    public int submitGrid(Long examId, Long subjectId, Long sectionId, Long teacherId, User actingUser, List<StudentMarksInput> inputs) {
        return persistGrid(examId, subjectId, sectionId, teacherId, actingUser, inputs, true);
    }

    private int persistGrid(Long examId, Long subjectId, Long sectionId, Long teacherId, User actingUser,
                             List<StudentMarksInput> inputs, boolean thenSubmit) {
        TeacherSubject assignment = requireAssignment(teacherId, subjectId, sectionId);
        Exam exam = requireOpenExam(examId);
        Subject subject = assignment.getSubject();

        Map<Long, Student> sectionStudents = studentDAO.findBySection(sectionId).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));

        // Validate the entire batch before writing anything - an all-or-nothing
        // save (Sec. 66) rather than silently skipping the rows with problems,
        // which would leave the teacher unsure what actually got saved.
        Map<String, String> errors = new LinkedHashMap<>();
        List<StudentMarksInput> validInputs = new ArrayList<>();
        for (StudentMarksInput input : inputs) {
            Student student = sectionStudents.get(input.studentId());
            if (student == null || input.isBlank()) {
                // Not a student in this section (an arbitrary/tampered id is
                // simply ignored, never trusted) or nothing entered - both
                // skip silently, neither is an error.
                continue;
            }
            validateComponent(student, subject.isHasTheory(), subject.getTheoryMaxMarks(), input.theoryMarks(), "Theory", errors);
            validateComponent(student, subject.isHasPractical(), subject.getPracticalMaxMarks(), input.practicalMarks(), "Practical", errors);
            validateComponent(student, subject.isHasInternal(), subject.getInternalMaxMarks(), input.internalMarks(), "Internal", errors);
            validInputs.add(input);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the marks below before saving.", errors);
        }

        return inTransaction(() -> {
            int changed = 0;
            for (StudentMarksInput input : validInputs) {
                Result result = resultDAO.findByStudentExamSubject(input.studentId(), examId, subjectId).orElse(null);
                if (result != null && result.getStatus() != ResultStatus.DRAFT) {
                    continue; // SUBMITTED or later - not editable here (Sec. 10)
                }
                if (result == null) {
                    result = new Result(sectionStudents.get(input.studentId()), exam, subject);
                    resultDAO.save(result);
                }
                result.setTheoryMarks(input.theoryMarks());
                result.setPracticalMarks(input.practicalMarks());
                result.setInternalMarks(input.internalMarks());
                resultDAO.update(result);
                changed++;
            }

            int submitted = 0;
            if (thenSubmit) {
                for (Long studentId : sectionStudents.keySet()) {
                    Result result = resultDAO.findByStudentExamSubject(studentId, examId, subjectId).orElse(null);
                    if (result != null && result.getStatus() == ResultStatus.DRAFT) {
                        result.markSubmitted(actingUser);
                        resultDAO.update(result);
                        submitted++;
                    }
                }
            }
            return thenSubmit ? submitted : changed;
        });
    }

    private void validateComponent(Student student, boolean enabled, BigDecimal maxMarks, BigDecimal value, String label, Map<String, String> errors) {
        if (value == null) {
            return;
        }
        String who = student.getUser().getFullName() + " (" + student.getRollNo() + ") - " + label;
        if (!enabled) {
            errors.put(who, "This subject has no " + label.toLowerCase() + " component.");
        } else if (value.compareTo(BigDecimal.ZERO) < 0) {
            errors.put(who, "Cannot be negative.");
        } else if (maxMarks != null && value.compareTo(maxMarks) > 0) {
            errors.put(who, "Entered " + value + ", maximum is " + maxMarks + ".");
        }
    }

    /**
     * Every (exam, subject+section assignment) combination for one teacher,
     * including ones with zero Results yet - deliberately not filtered
     * here, since the three screens that consume this need different
     * filters (see this record's own Javadoc). Cross-references
     * {@code student.currentSection} against each assignment's own section,
     * not just exam+subject, so a subject taught to two different sections
     * by two different teachers never has one teacher's count include the
     * other's students.
     */
    public List<MarksEntryGroup> listGroupsForTeacher(Long teacherId) {
        List<TeacherSubject> assignments = teacherSubjectDAO.findActiveByTeacher(teacherId);
        List<MarksEntryGroup> groups = new ArrayList<>();

        for (TeacherSubject assignment : assignments) {
            int totalStudents = studentDAO.findBySection(assignment.getSection().getId()).size();
            List<Exam> exams = examDAO.findBySemester(assignment.getSubject().getSemester().getId());

            for (Exam exam : exams) {
                List<Result> scoped = resultDAO.findByExamAndSubject(exam.getId(), assignment.getSubject().getId()).stream()
                        .filter(r -> r.getStudent().getCurrentSection() != null
                                && r.getStudent().getCurrentSection().getId().equals(assignment.getSection().getId()))
                        .toList();
                int draftCount = (int) scoped.stream().filter(r -> r.getStatus() == ResultStatus.DRAFT).count();
                int submittedOrLaterCount = scoped.size() - draftCount;
                groups.add(new MarksEntryGroup(exam, assignment, totalStudents, draftCount, submittedOrLaterCount));
            }
        }
        return groups;
    }

    // ---------------------------------------------------------------- transaction helper

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
}
