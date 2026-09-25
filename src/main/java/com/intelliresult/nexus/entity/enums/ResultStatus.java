package com.intelliresult.nexus.entity.enums;

import java.util.Collection;

/** Mirrors results.chk_results_status in schema.sql exactly - the result workflow engine from Sec. 10. */
public enum ResultStatus {
    DRAFT,
    SUBMITTED,
    APPROVED,
    PUBLISHED,
    LOCKED;

    /**
     * The single most-final status among a student's per-subject
     * {@code Result} rows for one exam - Phase 12's "what do I print/show
     * as this marksheet's status" question, needed identically by
     * {@code MarksheetService} (the PDF's own status line) and {@code
     * VerificationService} (the public verify page's "Result status"
     * field), hence living here once rather than in either. LOCKED wins
     * over PUBLISHED if any subject reached it - Sec. 10 treats LOCKED as
     * strictly more final, never the reverse, and a caller only ever
     * invokes this after already confirming (via {@code
     * ResultSummary#isComplete()}) that every subject is at least
     * PUBLISHED, so DRAFT/SUBMITTED/APPROVED never actually reach here.
     *
     * @throws IllegalArgumentException if {@code statuses} is empty - there
     *         is no "most final" status of nothing, and a caller reaching
     *         this with an empty collection has a bug worth failing loudly
     *         for rather than silently returning a guess.
     */
    public static ResultStatus mostFinal(Collection<ResultStatus> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            throw new IllegalArgumentException("Cannot determine the most-final status of an empty result set.");
        }
        return statuses.contains(LOCKED) ? LOCKED : PUBLISHED;
    }
}
