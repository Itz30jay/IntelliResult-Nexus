package com.intelliresult.nexus.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Central place for the handful of date/time formats this system needs, so
 * "how does a date look in the UI" and "how does a timestamp look in an
 * export filename" are answered once instead of ad-hoc DateTimeFormatter
 * instances scattered across JSPs, services, and report/export code in later
 * phases. All formatters are built once as static finals since
 * DateTimeFormatter is thread-safe and immutable - safe to share across every
 * concurrent request without synchronization.
 */
public final class DateUtil {

    /** "14 Aug 2026" - used anywhere a date is shown to a person (marksheets, dashboards, notices). */
    public static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");

    /** "14 Aug 2026, 09:41" - used for activity-log and notification timestamps. */
    public static final DateTimeFormatter DISPLAY_DATE_TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    /** "20260814_094132" - filesystem/URL-safe, used for generated report and marksheet filenames (Phase 12/15). */
    public static final DateTimeFormatter FILENAME_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    private DateUtil() {
        // Static-only utility class.
    }

    public static String formatForDisplay(LocalDate date) {
        return date == null ? "" : date.format(DISPLAY_DATE);
    }

    public static String formatForDisplay(LocalDateTime dateTime) {
        return dateTime == null ? "" : dateTime.format(DISPLAY_DATE_TIME);
    }

    public static String formatForFilename(LocalDateTime dateTime) {
        return (dateTime == null ? LocalDateTime.now() : dateTime).format(FILENAME_TIMESTAMP);
    }

    /** True when {@code date} falls within [start, end] inclusive - used by exam-window and grading-rule academic-year checks. Returns false if any argument is null rather than throwing, since "no date to compare" is a normal, expected input in optional-field checks. */
    public static boolean isWithinRange(LocalDate date, LocalDate start, LocalDate end) {
        if (date == null || start == null || end == null) {
            return false;
        }
        return !date.isBefore(start) && !date.isAfter(end);
    }
}
