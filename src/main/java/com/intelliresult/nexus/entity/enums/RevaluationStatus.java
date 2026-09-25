package com.intelliresult.nexus.entity.enums;

/**
 * Mirrors revaluation_requests.chk_revaluation_status in schema.sql exactly
 * (Sec. 14).
 * Upgrade: ASSIGNED added between PENDING and APPROVED/REJECTED - the
 * delegation step Sec. "Admin receives it and assigns it to any teacher"
 * requires. PENDING -> ASSIGNED (an admin picks any teacher) ->
 * APPROVED/REJECTED (that teacher evaluates and resolves it - the point
 * marks actually change, see RevaluationService.resolveRequest).
 */
public enum RevaluationStatus {
    PENDING,
    ASSIGNED,
    APPROVED,
    REJECTED
}
