package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.SemesterDAO;
import com.intelliresult.nexus.dao.SemesterDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Semester;
import com.intelliresult.nexus.entity.enums.ExamStatus;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.ExamRequest;
import com.intelliresult.nexus.util.ValidationUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns Sec. 9's Exam Management: creation and the CREATED to LOCKED
 * lifecycle. Exam deliberately takes only a semesterId on the way in, never
 * a separate academicYearId, even though exams.academic_year_id is a real
 * stored column (see schema.sql's note on why: query convenience, not a
 * second source of truth) - academicYear is always set here as
 * semester.getAcademicYear(), the same "derive it, don't ask the admin to
 * pick two things that must always agree" reasoning AcademicSetupService
 * applies when creating a Section from a Semester alone.
 * Identity fields (semester, examType) are fixed at creation, matching the
 * precedent PHASE5C-ACADEMIC-SETUP.md sets for Semester's course/year/number
 * - too much (results, once Phase 7 exists) will eventually anchor to a
 * specific exam+subject combination for "which exam" to be silently
 * changeable later. That precedent extends one step further here:
 * updateExam() takes its own narrow parameter list (name/dates/
 * defaultMaxMarks only), the exact same "give update a smaller signature
 * rather than reuse the create DTO and ignore half of it" choice
 * AcademicSetupService.updateSemester() already made, so a caller can never
 * accidentally believe semester/examType are still in play on an edit.
 */
public class ExamService {

    private final ExamDAO examDAO = new ExamDAOImpl();
    private final SemesterDAO semesterDAO = new SemesterDAOImpl();

    public Exam createExam(ExamRequest req, Long adminId) {
        Semester semester = semesterDAO.findById(req.semesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found."));

        Map<String, String> errors = validateCommonFields(req.name(), req.startTime(), req.endTime(),
                req.attemptLimit(), req.defaultMaxMarks());
        if (req.examType() == null) {
            errors.put("examType", "Select an exam type.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        return inTransaction(() -> {
            Exam exam = new Exam(req.name().trim(), req.examType(), semester.getAcademicYear(), semester,
                    req.startTime(), req.endTime(), req.attemptLimit());
            exam.setDefaultMaxMarks(req.defaultMaxMarks());
            examDAO.save(exam);
            return exam;
        });
    }

    public Exam updateExam(Long id, String name, LocalDateTime startTime, LocalDateTime endTime,
                            Integer attemptLimit, BigDecimal defaultMaxMarks) {
        Exam exam = examDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found."));
        requireEditable(exam);

        Map<String, String> errors = validateCommonFields(name, startTime, endTime, attemptLimit, defaultMaxMarks);
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        return inTransaction(() -> {
            exam.setName(name.trim());
            exam.setStartTime(startTime);
            exam.setEndTime(endTime);
            exam.setAttemptLimit(attemptLimit);
            exam.setDefaultMaxMarks(defaultMaxMarks);
            examDAO.update(exam);
            return exam;
        });
    }

    /** Sec. 9's forward-only lifecycle, one stage per call - deliberately not a jump-to-any-stage operation, so the UI action is always "advance to the next stage," never a free-form status picker that could skip a stage no controller/validation logic downstream has accounted for. */
    public Exam advanceStatus(Long id, Long adminId) {
        Exam exam = examDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found."));
        ExamStatus next = exam.getStatus().next();
        if (next == null) {
            throw new BusinessRuleException("This exam is already locked and cannot be advanced further.");
        }

        return inTransaction(() -> {
            exam.setStatus(next);
            examDAO.update(exam);
            return exam;
        });
    }

    /** Deletion is only offered while nothing downstream could plausibly reference this exam yet - the same reasoning as Sec. 47's "prevent unauthorized modifications," applied to the one destructive action this phase has (marks entry/results don't exist until Phase 6/7, so CREATED is the only status where "this exam never really started" is still true). */
    public void softDelete(Long id, Long adminId) {
        Exam exam = examDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found."));
        if (exam.getStatus() != ExamStatus.CREATED) {
            throw new BusinessRuleException(
                    "Only an exam still in the Created stage can be deleted. This one has already moved to "
                            + exam.getStatus().name().toLowerCase() + " - advance it through to Locked instead of removing it.");
        }
        runInTransaction(() -> exam.markDeleted(adminId));
    }

    public void restore(Long id) {
        Exam exam = examDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Exam not found."));
        runInTransaction(exam::restore);
    }

    /** PUBLISHED/LOCKED exams are read-only: by that stage results have been finalized (and, once published, shown to students) against this exam's own identity and dates - see the class Javadoc's cross-reference to the Semester precedent for the same "stop letting foundational facts move once enough depends on them" reasoning. */
    private void requireEditable(Exam exam) {
        if (exam.getStatus() == ExamStatus.PUBLISHED || exam.getStatus() == ExamStatus.LOCKED) {
            throw new BusinessRuleException(
                    "This exam is " + exam.getStatus().name().toLowerCase() + " and its details can no longer be edited.");
        }
    }

    private Map<String, String> validateCommonFields(String name, LocalDateTime startTime, LocalDateTime endTime,
                                                       Integer attemptLimit, BigDecimal defaultMaxMarks) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (ValidationUtil.isBlank(name)) {
            errors.put("name", "Exam name is required.");
        }
        if (startTime == null) {
            errors.put("startTime", "Start time is required.");
        }
        if (endTime == null) {
            errors.put("endTime", "End time is required.");
        }
        if (startTime != null && endTime != null && endTime.isBefore(startTime)) {
            errors.put("endTime", "End time cannot be before the start time.");
        }
        if (attemptLimit == null || attemptLimit < 1) {
            errors.put("attemptLimit", "Attempt limit must be at least 1.");
        }
        if (defaultMaxMarks != null && defaultMaxMarks.compareTo(BigDecimal.ZERO) <= 0) {
            errors.put("defaultMaxMarks", "Enter a reference maximum marks value greater than 0, or leave it blank.");
        }

        return errors;
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
