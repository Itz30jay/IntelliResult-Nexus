package com.intelliresult.nexus.entity;

import com.intelliresult.nexus.entity.enums.ResultStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * The central transactional entity. totalMarks/percentage/grade/gradePoint/
 * isPass are plain mutable fields populated exclusively by
 * ResultCalculationService (Phase 7) - never computed here on the entity and
 * never DB-generated, so there is exactly one place (that service) which
 * decides how a result is scored (Sec. 11). No classRank/overallRank fields -
 * rank is an aggregate across every subject a student took in one exam, not
 * a per-subject-result fact, so it doesn't belong here (PHASE2-DATABASE.md
 * on the results table). The unique constraint below is what enforces
 * Sec. 47's "prevent duplicate result records" at the database level, not
 * just in application logic.
 */
@Entity
@Table(name = "results", uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "exam_id", "subject_id"}))
public class Result extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @Column(name = "theory_marks", precision = 6, scale = 2)
    private BigDecimal theoryMarks;
    @Column(name = "practical_marks", precision = 6, scale = 2)
    private BigDecimal practicalMarks;
    @Column(name = "internal_marks", precision = 6, scale = 2)
    private BigDecimal internalMarks;

    @Column(name = "total_marks", precision = 6, scale = 2)
    private BigDecimal totalMarks;
    @Column(name = "percentage", precision = 5, scale = 2)
    private BigDecimal percentage;
    @Column(name = "grade", length = 10)
    private String grade;
    @Column(name = "grade_point", precision = 4, scale = 2)
    private BigDecimal gradePoint;
    @Column(name = "is_pass")
    private Boolean pass;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ResultStatus status = ResultStatus.DRAFT;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "submitted_by")
    private User submittedBy;
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by")
    private User approvedBy;
    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;
    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    protected Result() {
    }

    public Result(Student student, Exam exam, Subject subject) {
        this.student = student;
        this.exam = exam;
        this.subject = subject;
    }

    public Student getStudent() { return student; }
    public Exam getExam() { return exam; }
    public Subject getSubject() { return subject; }

    public BigDecimal getTheoryMarks() { return theoryMarks; }
    public void setTheoryMarks(BigDecimal theoryMarks) { this.theoryMarks = theoryMarks; }
    public BigDecimal getPracticalMarks() { return practicalMarks; }
    public void setPracticalMarks(BigDecimal practicalMarks) { this.practicalMarks = practicalMarks; }
    public BigDecimal getInternalMarks() { return internalMarks; }
    public void setInternalMarks(BigDecimal internalMarks) { this.internalMarks = internalMarks; }

    public BigDecimal getTotalMarks() { return totalMarks; }
    public BigDecimal getPercentage() { return percentage; }
    public String getGrade() { return grade; }
    public BigDecimal getGradePoint() { return gradePoint; }
    public Boolean getPass() { return pass; }

    /** The only way totalMarks/percentage/grade/gradePoint/isPass get set - called exclusively by ResultCalculationService, so this is the one seam where "how a result gets scored" enters the entity. Package-private-by-convention in intent, public because Service and Entity live in different packages; the real enforcement of "only the calculation engine calls this" is a code-review/architecture discipline, not a language-level one. */
    public void applyCalculatedScore(BigDecimal totalMarks, BigDecimal percentage, String grade, BigDecimal gradePoint, boolean pass) {
        this.totalMarks = totalMarks;
        this.percentage = percentage;
        this.grade = grade;
        this.gradePoint = gradePoint;
        this.pass = pass;
    }

    public ResultStatus getStatus() { return status; }

    public User getSubmittedBy() { return submittedBy; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }
    public void markSubmitted(User teacher) {
        this.status = ResultStatus.SUBMITTED;
        this.submittedBy = teacher;
        this.submittedAt = LocalDateTime.now();
    }

    public User getApprovedBy() { return approvedBy; }
    public LocalDateTime getApprovedAt() { return approvedAt; }
    public void markApproved(User admin) {
        this.status = ResultStatus.APPROVED;
        this.approvedBy = admin;
        this.approvedAt = LocalDateTime.now();
    }

    public LocalDateTime getPublishedAt() { return publishedAt; }
    public void markPublished() {
        this.status = ResultStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public LocalDateTime getLockedAt() { return lockedAt; }
    public void markLocked() {
        this.status = ResultStatus.LOCKED;
        this.lockedAt = LocalDateTime.now();
    }

    /** Drops status back to DRAFT for corrections while marks are still editable - used before SUBMITTED, never after (post-submission corrections go through the re-evaluation + result-history process, Phase 9/10). */
    public void revertToDraft() {
        this.status = ResultStatus.DRAFT;
    }
}
