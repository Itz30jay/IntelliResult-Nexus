package com.intelliresult.nexus.entity.enums;

/**
 * Sec. "Forgot Password + OTP" - explicitly asks to "keep Admin in the loop"
 * for the actual credential change even after OTP verifies identity, so
 * this mirrors RegistrationStatus's exact three-state shape and vocabulary
 * rather than inventing a new spelling for the same PENDING/APPROVED/
 * REJECTED concept a second time.
 */
public enum PasswordResetStatus {
    PENDING,
    APPROVED,
    REJECTED
}
