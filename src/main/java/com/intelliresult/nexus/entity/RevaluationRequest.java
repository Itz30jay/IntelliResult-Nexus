package com.intelliresult.nexus.entity;

import com.intelliresult.nexus.entity.enums.RevaluationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * resolvedBy is an addition beyond the spec's literal field list, flagged
 * in PHASE2-DATABASE.md #7 - an approval/rejection with no recorded
 * approver fails Sec. 48's "who changed this" requirement.
 * <p>
 * Upgrade: the workflow gained a genuine delegation step that did not exist
 * before - PENDING (submitted) -> ASSIGNED (an admin hands it to any
 * teacher, via assign()) -> APPROVED/REJECTED (that teacher evaluates and
 * resolves it via resolve(), which is the point marks actually change - see
 * RevaluationService). assignedBy/assignedAt record the delegation itself,
 * kept separate from resolvedBy/resolvedAt (who actually evaluated it, a
 * different person at a different moment) for the same audit-trail
 * reasoning resolvedBy's own note gives. teacherReport is the assigned
 * teacher's detailed write-up, kept distinct from adminRemark (the admin's
 * own optional note made at assignment time, e.g. instructions to the
 * teacher) rather than overloading one free-text column to mean two
 * different authors' two different messages. newTheoryMarks/
 * newPracticalMarks/newInternalMarks are the teacher's proposed corrected
 * marks, staged here before ResultService.doCorrectResult ever runs.
 */
@Entity
@Table(name = "revaluation_requests")
public class RevaluationRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false)
    private Result result;

    @Column(name = "reason", nullable = false, columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RevaluationStatus status = RevaluationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_teacher_id")
    private Teacher assignedTeacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @Column(name = "assigned_at")
    private LocalDateTime assignedAt;

    @Column(name = "admin_remark", columnDefinition = "TEXT")
    private String adminRemark;

    @Column(name = "teacher_report", columnDefinition = "TEXT")
    private String teacherReport;

    @Column(name = "new_theory_marks", precision = 6, scale = 2)
    private BigDecimal newTheoryMarks;

    @Column(name = "new_practical_marks", precision = 6, scale = 2)
    private BigDecimal newPracticalMarks;

    @Column(name = "new_internal_marks", precision = 6, scale = 2)
    private BigDecimal newInternalMarks;

    @CreationTimestamp
    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by")
    private User resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    protected RevaluationRequest() {
    }

    public RevaluationRequest(Student student, Result result, String reason) {
        this.student = student;
        this.result = result;
        this.reason = reason;
    }

    public Student getStudent() { return student; }
    public Result getResult() { return result; }
    public String getReason() { return reason; }
    public RevaluationStatus getStatus() { return status; }
    public Teacher getAssignedTeacher() { return assignedTeacher; }
    public User getAssignedBy() { return assignedBy; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public String getAdminRemark() { return adminRemark; }
    public String getTeacherReport() { return teacherReport; }
    public BigDecimal getNewTheoryMarks() { return newTheoryMarks; }
    public BigDecimal getNewPracticalMarks() { return newPracticalMarks; }
    public BigDecimal getNewInternalMarks() { return newInternalMarks; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public User getResolvedBy() { return resolvedBy; }
    public LocalDateTime getResolvedAt() { return resolvedAt; }

    /** Admin's own entire contribution to this workflow, in one call - hands the request to a specific teacher, optionally with instructions, and stamps who/when so the delegation itself is as auditable as the eventual resolution (Sec. 48). Only valid from PENDING: a request can be assigned exactly once at a time, matching how a real registrar's office wouldn't hand the same file to two people simultaneously. */
    public void assign(Teacher assignedTeacher, User assignedBy, String adminRemark) {
        if (status != RevaluationStatus.PENDING) {
            throw new IllegalStateException("Only a PENDING request can be assigned (this one is " + status + ").");
        }
        this.assignedTeacher = assignedTeacher;
        this.assignedBy = assignedBy;
        this.assignedAt = LocalDateTime.now();
        this.adminRemark = adminRemark;
        this.status = RevaluationStatus.ASSIGNED;
    }

    /**
     * Resolves the request (APPROVED or REJECTED) in one call so status/
     * report/marks/resolvedBy/resolvedAt can never end up set inconsistently
     * with each other - mirrors SoftDeletableEntity.markDeleted()'s
     * reasoning. Upgrade: now requires ASSIGNED (not PENDING) - the assigned
     * teacher is who evaluates and resolves it, not the admin who delegated
     * it (Sec. "Marks are updated only after teacher confirmation").
     * resolvedBy is passed separately from assignedTeacher (rather than
     * assumed to be the same person) so the caller - RevaluationService -
     * remains the one place that enforces "only the actually-assigned
     * teacher may resolve this," not this entity.
     */
    public void resolve(RevaluationStatus outcome, String teacherReport, BigDecimal newTheoryMarks,
                         BigDecimal newPracticalMarks, BigDecimal newInternalMarks, User resolvedBy) {
        if (status != RevaluationStatus.ASSIGNED) {
            throw new IllegalStateException("Only an ASSIGNED request can be resolved (this one is " + status + ").");
        }
        if (outcome != RevaluationStatus.APPROVED && outcome != RevaluationStatus.REJECTED) {
            throw new IllegalArgumentException("resolve() requires APPROVED or REJECTED, not " + outcome);
        }
        this.status = outcome;
        this.teacherReport = teacherReport;
        this.newTheoryMarks = newTheoryMarks;
        this.newPracticalMarks = newPracticalMarks;
        this.newInternalMarks = newInternalMarks;
        this.resolvedBy = resolvedBy;
        this.resolvedAt = LocalDateTime.now();
    }
}
