package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Forgot Password's one-time codes. otpHash (never the plain code) goes
 * through the exact same {@code PasswordUtil.hash()/matches()} BCrypt pair
 * every password in this system already uses - an OTP is itself a short
 * secret credential, so reusing the one hashing seam this codebase already
 * has (rather than a second, weaker "just compare strings" path for this
 * one table) is the same "exactly one seam" reasoning PasswordUtil's own
 * class Javadoc gives for passwords themselves.
 * attemptCount bounds brute-force guessing of a 6-digit code within its own
 * short expiry window (see OtpService); consumedAt (once set) makes a code
 * single-use even if it is still technically unexpired.
 */
@Entity
@Table(name = "otp_codes")
public class OtpCode extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "otp_hash", nullable = false, length = 60)
    private String otpHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount = 0;

    @Column(name = "requested_ip", length = 45)
    private String requestedIp;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected OtpCode() {
    }

    public OtpCode(User user, String otpHash, LocalDateTime expiresAt, String requestedIp) {
        this.user = user;
        this.otpHash = otpHash;
        this.expiresAt = expiresAt;
        this.requestedIp = requestedIp;
    }

    public User getUser() { return user; }
    public String getOtpHash() { return otpHash; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getConsumedAt() { return consumedAt; }
    public int getAttemptCount() { return attemptCount; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    public boolean isExpired() { return LocalDateTime.now().isAfter(expiresAt); }
    public boolean isConsumed() { return consumedAt != null; }

    /** A code is usable exactly once and only within its own window - both checked here so every caller gets the same definition of "usable" rather than each re-deriving it from isExpired()/isConsumed() separately. */
    public boolean isUsable() {
        return !isExpired() && !isConsumed() && attemptCount < 5;
    }

    public void registerFailedAttempt() {
        this.attemptCount++;
    }

    public void markConsumed() {
        this.consumedAt = LocalDateTime.now();
    }
}
