package com.intelliresult.nexus.entity.enums;

/**
 * Sec. "Add a one-time / permanent section field if it is missing" - purely
 * descriptive metadata about a section's own lifecycle, distinct from
 * anything academic (semester, capacity). PERMANENT is the default: an
 * ongoing institutional cohort that persists across semesters/years, the
 * common case. ONE_TIME is an ad-hoc grouping stood up for a single exam
 * cycle or elective batch. Nothing in the Result Engine or Marks Entry
 * branches on this value today - it exists for Teacher My Classes and
 * Academic Setup to display, not to change any calculation.
 */
public enum SectionType {
    ONE_TIME,
    PERMANENT
}
