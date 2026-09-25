package com.intelliresult.nexus.entity.enums;

/** Mirrors exams.chk_exams_status in schema.sql exactly - the exam lifecycle from Sec. 9. Declared in enum-declaration order matching the lifecycle's natural progression, so ordinal() (not used for persistence - see UserRole's note on EnumType.STRING - but used for UI ordering) sorts correctly without extra effort. */
public enum ExamStatus {
    CREATED,
    SCHEDULED,
    ACTIVE,
    SUBMISSION,
    APPROVAL,
    PUBLISHED,
    LOCKED;

    /**
     * The next stage in the forward-only lifecycle above, or {@code null} at
     * LOCKED - the lifecycle has no defined path backward, matching how
     * Result.LOCKED is described (Sec. 10) as immutable under normal
     * operations. ExamService.advanceStatus() (Phase 5e) is the only caller;
     * safe to lean on ordinal() specifically because this enum's own
     * class-level Javadoc already establishes that declaration order IS
     * lifecycle order.
     */
    public ExamStatus next() {
        ExamStatus[] all = values();
        int nextOrdinal = ordinal() + 1;
        return nextOrdinal < all.length ? all[nextOrdinal] : null;
    }

    /** Whether marks entry/submission activity is expected to be happening for an exam in this status - the one piece of exam-status semantics Phase 6's marks-entry screen (Teacher module) will need to gate on once it exists. Kept here now, even before that caller exists, because it is a pure function of this enum's own meaning, not a guess about Phase 6's implementation. */
    public boolean isOpenForMarksEntry() {
        return this == ACTIVE || this == SUBMISSION;
    }

    /**
     * Whether this exam has ever been open for marks entry, even if it has
     * since moved on - CREATED/SCHEDULED never have; ACTIVE onward always
     * does. Phase 6b's read-only Submitted Results history view needs
     * exactly this, distinct from {@link #isOpenForMarksEntry()}: that one
     * gates whether new marks can be written right now; this one gates
     * whether there's anything meaningful to look at at all. An exam that
     * has progressed to APPROVAL or PUBLISHED still has real submitted
     * marks worth reviewing; one still at CREATED never had any.
     */
    public boolean hasStartedMarksEntry() {
        return ordinal() >= ACTIVE.ordinal();
    }
}
