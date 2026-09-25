package com.intelliresult.nexus.entity;

import com.intelliresult.nexus.entity.enums.RegistrationStatus;
import com.intelliresult.nexus.entity.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * A public Registration submission, staged here until an admin approves or
 * rejects it - never written directly into users/students/teachers. See
 * schema.sql's header comment on this table for why: a Student/Teacher
 * needs course/section or department/designation to exist as a real
 * academic record, and Registration's own field list (full name,
 * roll_no/employee_code, phone, password) never collects that. Keeping the
 * raw submission here until an admin supplies the missing placement means
 * students.course_id and teachers.department_id never have to become
 * nullable for every other feature that already, correctly, assumes they
 * are always set.
 * <p>
 * identifier is the prospective roll_no (role == STUDENT) or employee_code
 * (role == TEACHER) - it only becomes the real column of that name once
 * RegistrationService materializes the Student/Teacher row on approval.
 * createdUserId is populated only then, so an approved row still shows
 * an admin exactly which live account it became without a live FK join
 * being required for the (much more common) pending/rejected case.
 */
@Entity
@Table(name = "registration_requests")
public class RegistrationRequest extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "identifier", nullable = false, length = 30)
    private String identifier;

    @Column(name = "phone", nullable = false, length = 20)
    private String phone;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RegistrationStatus status = RegistrationStatus.PENDING;

    @Column(name = "admin_remark", columnDefinition = "TEXT")
    private String adminRemark;

    @Column(name = "created_user_id")
    private Long createdUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "submitted_at", nullable = false, updatable = false)
    private LocalDateTime submittedAt;

    protected RegistrationRequest() {
    }

    public RegistrationRequest(UserRole role, String fullName, String identifier, String phone,
                                String email, String passwordHash) {
        this.role = role;
        this.fullName = fullName;
        this.identifier = identifier;
        this.phone = phone;
        this.email = email;
        this.passwordHash = passwordHash;
    }

    public UserRole getRole() { return role; }
    public String getFullName() { return fullName; }
    public String getIdentifier() { return identifier; }
    public String getPhone() { return phone; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public RegistrationStatus getStatus() { return status; }
    public String getAdminRemark() { return adminRemark; }
    public Long getCreatedUserId() { return createdUserId; }
    public User getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }

    /** Marks this request APPROVED and records which live account it became, in one call so status/createdUserId/reviewedBy/reviewedAt can never end up inconsistent with each other - the same reasoning as SoftDeletableEntity.markDeleted()/RevaluationRequest.resolve(). */
    public void approve(Long createdUserId, User reviewedBy) {
        requirePending();
        this.status = RegistrationStatus.APPROVED;
        this.createdUserId = createdUserId;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    /** Marks this request REJECTED with the admin's reason. No account is ever created for a rejected request - there is nothing else to clean up. */
    public void reject(String adminRemark, User reviewedBy) {
        requirePending();
        this.status = RegistrationStatus.REJECTED;
        this.adminRemark = adminRemark;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    private void requirePending() {
        if (status != RegistrationStatus.PENDING) {
            throw new IllegalStateException("This registration request was already " + status + ".");
        }
    }
}
