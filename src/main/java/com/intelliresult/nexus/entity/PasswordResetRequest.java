package com.intelliresult.nexus.entity;

import com.intelliresult.nexus.entity.enums.PasswordResetStatus;
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
 * The self-service half of Forgot Password stops at OTP verification,
 * deliberately - Sec. "the password-change request goes to Admin for final
 * approval... keep Admin in the loop" - so the newly-chosen password is
 * staged here, hashed, and only copied onto {@code User.passwordHash} once
 * an admin approves it, mirroring RegistrationRequest's identical
 * "verified-but-not-yet-live" shape. otpVerifiedAt is stamped the moment
 * OTP verification succeeds, before this row even exists, so a reviewing
 * admin can always see the request was never possible without that step
 * happening first - it is not merely descriptive, it is proof of the
 * precondition.
 */
@Entity
@Table(name = "password_reset_requests")
public class PasswordResetRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "new_password_hash", nullable = false, length = 60)
    private String newPasswordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PasswordResetStatus status = PasswordResetStatus.PENDING;

    @Column(name = "otp_verified_at", nullable = false)
    private LocalDateTime otpVerifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    private User reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected PasswordResetRequest() {
    }

    public PasswordResetRequest(User user, String newPasswordHash, LocalDateTime otpVerifiedAt) {
        this.user = user;
        this.newPasswordHash = newPasswordHash;
        this.otpVerifiedAt = otpVerifiedAt;
    }

    public User getUser() { return user; }
    public String getNewPasswordHash() { return newPasswordHash; }
    public PasswordResetStatus getStatus() { return status; }
    public LocalDateTime getOtpVerifiedAt() { return otpVerifiedAt; }
    public User getReviewedBy() { return reviewedBy; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /** Copies the staged hash onto the live account - the caller (PasswordResetService) is responsible for actually doing that copy; this method only marks this request's own side of the transaction as decided, the same division of responsibility RegistrationRequest.approve() has with RegistrationService. */
    public void approve(User reviewedBy) {
        requirePending();
        this.status = PasswordResetStatus.APPROVED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    public void reject(User reviewedBy) {
        requirePending();
        this.status = PasswordResetStatus.REJECTED;
        this.reviewedBy = reviewedBy;
        this.reviewedAt = LocalDateTime.now();
    }

    private void requirePending() {
        if (status != PasswordResetStatus.PENDING) {
            throw new IllegalStateException("This password reset request was already " + status + ".");
        }
    }
}
