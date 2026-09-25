package com.intelliresult.nexus.util;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateUtilTest {

    @Test
    void formatForDisplay_formatsLocalDateAsExpected() {
        assertEquals("14 Aug 2026", DateUtil.formatForDisplay(LocalDate.of(2026, 8, 14)));
    }

    @Test
    void formatForDisplay_returnsEmptyStringForNullDate() {
        assertEquals("", DateUtil.formatForDisplay((LocalDate) null));
    }

    @Test
    void formatForDisplay_formatsLocalDateTimeAsExpected() {
        assertEquals("14 Aug 2026, 09:41",
                DateUtil.formatForDisplay(LocalDateTime.of(2026, 8, 14, 9, 41)));
    }

    @Test
    void formatForFilename_producesSortableNumericTimestamp() {
        String result = DateUtil.formatForFilename(LocalDateTime.of(2026, 8, 14, 9, 41, 32));
        assertEquals("20260814_094132", result);
    }

    @Test
    void isWithinRange_trueWhenDateFallsInsideInclusiveBounds() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        assertTrue(DateUtil.isWithinRange(LocalDate.of(2026, 1, 1), start, end));
        assertTrue(DateUtil.isWithinRange(LocalDate.of(2026, 12, 31), start, end));
        assertTrue(DateUtil.isWithinRange(LocalDate.of(2026, 6, 15), start, end));
    }

    @Test
    void isWithinRange_falseWhenDateFallsOutsideBounds() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 12, 31);
        assertFalse(DateUtil.isWithinRange(LocalDate.of(2025, 12, 31), start, end));
        assertFalse(DateUtil.isWithinRange(LocalDate.of(2027, 1, 1), start, end));
    }

    @Test
    void isWithinRange_falseWhenAnyArgumentIsNull() {
        LocalDate d = LocalDate.of(2026, 1, 1);
        assertFalse(DateUtil.isWithinRange(null, d, d));
        assertFalse(DateUtil.isWithinRange(d, null, d));
        assertFalse(DateUtil.isWithinRange(d, d, null));
    }
}
