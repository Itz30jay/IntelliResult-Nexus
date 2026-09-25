package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.RevaluationDAO;
import com.intelliresult.nexus.dao.RevaluationDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.RevaluationRequest;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.entity.enums.RevaluationStatus;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.math.BigDecimal;

/**
 * Sec. 37 names this service. Sec. 14's three parts: a student creates a
 * {@link RevaluationRequest} against one of their own eligible results
 * ({@link #submitRequest}); an admin hands it to any teacher
 * ({@link #assignToTeacher}); that teacher evaluates and resolves it
 * ({@link #resolveRequest}), optionally with corrected marks. "If approved
 * and marks change -&gt; automatically create result-history entry" (Sec.
 * 14, verbatim) is read literally here: *change* is the trigger, not
 * *approval* alone - a teacher can approve a request while confirming the
 * original marks were correct all along, and nothing gets touched on the
 * {@link Result} or in {@code result_history} when that happens. When
 * marks do change, this class does not reimplement "apply new marks, write
 * history, refresh summary" itself - it calls straight through to
 * {@link ResultService#doCorrectResult}, the exact mechanism Phase 9 built
 * and documented as expecting exactly this caller.
 * <p>
 * Upgrade: the workflow used to skip straight from student submission to
 * admin resolution - there was no delegation step, and no
 * {@code TeacherRevaluationServlet} existed at all. assignToTeacher/
 * resolveRequest's new teacherId-must-match-assignedTeacher check are what
 * make "marks are updated only after teacher confirmation" (Sec. 14) true:
 * the admin's own role now ends at assignment, and only the specific
 * teacher a request was assigned to may ever resolve it.
 */
public class RevaluationService {

    private final RevaluationDAO revaluationDAO = new RevaluationDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultService resultService = new ResultService();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final UserDAO userDAO = new UserDAOImpl();
    private final NotificationService notificationService = new NotificationService();

    /**
     * @throws BusinessRuleException      if {@code reason} is blank, if the
     *         result is not currently PUBLISHED or LOCKED (Sec. 18: those
     *         are the only statuses a student can see at all, so nothing
     *         else is "eligible" to begin with), or if this student already
     *         has a PENDING request against this same result.
     * @throws ResourceNotFoundException  if the result does not exist, or -
     *         deliberately indistinguishable from "does not exist," per
     *         {@link ResourceNotFoundException}'s own established reasoning
     *         for a soft-deleted row - if it exists but belongs to a
     *         different student. A crafted resultId from outside this
     *         student's own eligible list must never confirm to the caller
     *         that a *someone else's* result exists.
     */
    public RevaluationRequest submitRequest(Long studentId, Long resultId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A reason is required to request re-evaluation.");
        }
        return inTransaction(() -> {
            Result result = resultDAO.findById(resultId)
                    .orElseThrow(() -> new ResourceNotFoundException("Result not found."));
            if (!result.getStudent().getId().equals(studentId)) {
                throw new ResourceNotFoundException("Result not found.");
            }
            if (result.getStatus() != ResultStatus.PUBLISHED && result.getStatus() != ResultStatus.LOCKED) {
                throw new BusinessRuleException("Re-evaluation can only be requested for a published result.");
            }
            boolean alreadyInFlight = revaluationDAO.findByStudent(studentId).stream()
                    .anyMatch(r -> r.getResult().getId().equals(resultId)
                            && (r.getStatus() == RevaluationStatus.PENDING || r.getStatus() == RevaluationStatus.ASSIGNED));
            if (alreadyInFlight) {
                throw new BusinessRuleException("A re-evaluation request for this result is already in progress.");
            }

            RevaluationRequest request = new RevaluationRequest(result.getStudent(), result, reason);
            revaluationDAO.save(request);
            return request;
        });
    }

    /**
     * The admin's entire role in this workflow now ends here (Sec. "Admin
     * receives it and assigns it to any teacher") - no admin decision about
     * outcome or marks happens at this step, only delegation. "Any teacher"
     * is read literally: unlike TeacherAssignmentService, there is no check
     * that this teacher actually teaches the subject in question - the spec
     * gives the admin free choice, e.g. to bring in a second opinion from
     * outside the usual subject staff.
     *
     * @throws BusinessRuleException     if the request is not currently PENDING.
     * @throws ResourceNotFoundException if the request or teacher does not exist.
     */
    public RevaluationRequest assignToTeacher(Long requestId, Long teacherId, String adminRemark, Long adminId) {
        RevaluationRequest assigned = inTransaction(() -> {
            RevaluationRequest request = revaluationDAO.findById(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Re-evaluation request not found."));
            Teacher teacher = teacherDAO.findById(teacherId)
                    .orElseThrow(() -> new ResourceNotFoundException("Teacher not found."));
            User admin = userDAO.findById(adminId)
                    .orElseThrow(() -> new ResourceNotFoundException("Admin user not found."));

            request.assign(teacher, admin, adminRemark);
            revaluationDAO.update(request);
            return request;
        });
        notificationService.notifyRevaluationAssigned(assigned);
        return assigned;
    }

    /**
     * Resolves an ASSIGNED request. {@code newTheoryMarks}/{@code
     * newPracticalMarks}/{@code newInternalMarks} are the assigned
     * teacher's proposed corrected marks (pre-filled with the result's
     * current values by {@code teacher-revaluations.jsp}, so the form
     * always shows them regardless of outcome) - {@link ResultService#
     * doCorrectResult} is only actually invoked when {@code outcome} is
     * APPROVED *and* at least one of them genuinely differs from the
     * result's current value, matching Sec. 14's "if approved and marks
     * change" precisely rather than triggering a correction (and a
     * same-value history entry) on every approval regardless of whether
     * anything numeric actually moved. Both Admin and the student are
     * notified automatically once this returns (Sec. "System automatically
     * notifies both Admin and the student") - not before, since a
     * notification describing an outcome that hasn't been committed yet
     * would be worse than no notification at all.
     *
     * @throws BusinessRuleException     if {@code outcome} is not APPROVED/REJECTED,
     *         if the request is not currently ASSIGNED, or if
     *         {@code actingTeacherId} is not the teacher it was assigned to.
     * @throws ResourceNotFoundException if the request does not exist.
     */
    public RevaluationRequest resolveRequest(Long requestId, Long actingTeacherId, RevaluationStatus outcome,
                                              String teacherReport, BigDecimal newTheoryMarks,
                                              BigDecimal newPracticalMarks, BigDecimal newInternalMarks) {
        if (outcome != RevaluationStatus.APPROVED && outcome != RevaluationStatus.REJECTED) {
            throw new BusinessRuleException("A resolution must be Approved or Rejected.");
        }
        RevaluationRequest resolved = inTransaction(() -> {
            RevaluationRequest request = revaluationDAO.findById(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Re-evaluation request not found."));
            if (request.getStatus() != RevaluationStatus.ASSIGNED) {
                throw new BusinessRuleException("This request is not currently assigned to you for evaluation.");
            }
            if (!request.getAssignedTeacher().getId().equals(actingTeacherId)) {
                throw new BusinessRuleException("This request was assigned to a different teacher.");
            }
            User teacherUser = request.getAssignedTeacher().getUser();

            request.resolve(outcome, teacherReport, newTheoryMarks, newPracticalMarks, newInternalMarks, teacherUser);
            revaluationDAO.update(request);

            Result result = request.getResult();
            boolean marksActuallyChanged = outcome == RevaluationStatus.APPROVED
                    && (marksDiffer(result.getTheoryMarks(), newTheoryMarks)
                        || marksDiffer(result.getPracticalMarks(), newPracticalMarks)
                        || marksDiffer(result.getInternalMarks(), newInternalMarks));

            if (marksActuallyChanged) {
                resultService.doCorrectResult(result.getId(), newTheoryMarks, newPracticalMarks, newInternalMarks,
                        teacherUser, "Re-evaluation approved (request #" + requestId + "): " + teacherReport);
            }
            return request;
        });
        notificationService.notifyRevaluationResolved(resolved);
        notificationService.notifyRevaluationResolvedToAdmin(resolved);
        return resolved;
    }

    /** BigDecimal.equals() is scale-sensitive (45.0 != 45.00) and would wrongly treat a form re-submitting the same value at a different scale as a change; compareTo() is the numeric comparison this needs. */
    private boolean marksDiffer(BigDecimal existing, BigDecimal proposed) {
        if (existing == null && proposed == null) {
            return false;
        }
        if (existing == null || proposed == null) {
            return true;
        }
        return existing.compareTo(proposed) != 0;
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
}
