package com.intelliresult.nexus.entity.enums;

/**
 * Lifecycle of a public self-registration submission (Sec. "Registration +
 * Verification System"). Deliberately only three states, no "IN_REVIEW" -
 * an admin approving/rejecting is a single atomic action in this system
 * (RegistrationService.approveStudent/approveTeacher/reject all run inside
 * one transaction), so there is never a real, observable in-between state
 * worth modelling.
 */
public enum RegistrationStatus {
    PENDING,
    APPROVED,
    REJECTED
}
