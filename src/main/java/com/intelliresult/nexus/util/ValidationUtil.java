package com.intelliresult.nexus.util;

import java.util.regex.Pattern;

/**
 * Server-side validation building blocks shared by every phase that accepts
 * user input - marks entry, user management, Excel row validation, and every
 * JSP form in between. Client-side JavaScript gives immediate feedback, but
 * every one of these checks is re-run here because a request can always
 * arrive having skipped the browser entirely (Sec. 46).
 * Deliberately generic: this class knows how to check "is this a valid
 * email" or "is this number in range", not "is this a valid Student ID" -
 * entity-specific formats (Student ID pattern, Subject code pattern) belong
 * in Phase 3 alongside the entities that define what "valid" means for them,
 * not guessed at here before those formats exist.
 */
public final class ValidationUtil {

    // RFC 5322 is far more permissive than institutions' real email formats
    // need; this pattern intentionally trades some edge-case permissiveness
    // for being easy to read and audit - local-part, @, domain-with-a-dot.
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private static final int PASSWORD_MIN_LENGTH = 8;

    private ValidationUtil() {
        // Static-only utility class.
    }

    public static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static boolean isNotBlank(String value) {
        return !isBlank(value);
    }

    public static boolean isValidEmail(String email) {
        return isNotBlank(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Minimum viable password policy: at least {@value #PASSWORD_MIN_LENGTH}
     * characters, at least one letter, and at least one digit. Intentionally
     * not more elaborate than this - composition rules beyond length are
     * widely considered to push people toward predictable substitutions
     * ("Password1!") rather than meaningfully stronger passwords, so this
     * stays simple rather than performatively strict.
     */
    public static boolean isValidPassword(String password) {
        if (password == null || password.length() < PASSWORD_MIN_LENGTH) {
            return false;
        }
        boolean hasLetter = false;
        boolean hasDigit = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            hasLetter = hasLetter || Character.isLetter(c);
            hasDigit = hasDigit || Character.isDigit(c);
        }
        return hasLetter && hasDigit;
    }

    /** Inclusive range check shared by every "marks cannot exceed maximum" / "percentage must be 0-100" rule across the Result Engine, Grading Engine, and bulk-import validators. */
    public static boolean isWithinRange(double value, double min, double max) {
        return value >= min && value <= max;
    }

    public static boolean isNonNegative(double value) {
        return value >= 0;
    }

    /** True when {@code value} is a syntactically valid non-negative integer string - used to validate raw form/Excel-cell input before it is parsed into a real numeric type, so a parse failure never surfaces as an unhandled NumberFormatException deep in a service. */
    public static boolean isValidNonNegativeInteger(String value) {
        if (isBlank(value)) {
            return false;
        }
        return value.trim().chars().allMatch(Character::isDigit);
    }
}
