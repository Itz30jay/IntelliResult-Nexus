package com.intelliresult.nexus.service.dto;

import java.math.BigDecimal;

/**
 * Parsed from one grid row's {@code theoryMarks_<studentId>} /
 * {@code practicalMarks_<studentId>} / {@code internalMarks_<studentId>}
 * form fields. All three are nullable - a subject with only theory and
 * internal components never has a practicalMarks value, and a component
 * left blank mid-entry is a legitimate "not entered yet" rather than a
 * validation failure (Sec. 29 lists range/negativity/duplication as what
 * to prevent; completeness isn't on that list).
 */
public record StudentMarksInput(Long studentId, BigDecimal theoryMarks, BigDecimal practicalMarks, BigDecimal internalMarks) {

    public boolean isBlank() {
        return theoryMarks == null && practicalMarks == null && internalMarks == null;
    }
}
