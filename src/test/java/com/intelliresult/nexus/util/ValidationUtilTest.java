package com.intelliresult.nexus.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers ValidationUtil in isolation since it is pure, deterministic logic
 * with zero external dependencies - exactly the kind of unit this phase can
 * genuinely test now, ahead of Phase 17's much larger integration/workflow/
 * security test suite that needs entities, services, and a running context
 * that don't exist yet.
 */
class ValidationUtilTest {

    @ParameterizedTest
    @ValueSource(strings = {"student@example.edu", "a.b+tag@sub.example.co.in", "T.Teacher123@college.ac.in"})
    @DisplayName("Well-formed addresses are accepted")
    void isValidEmail_acceptsWellFormedAddresses(String email) {
        assertTrue(ValidationUtil.isValidEmail(email));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "not-an-email", "missing-domain@", "@missing-local.com", "spaces in@email.com"})
    @DisplayName("Malformed addresses are rejected")
    void isValidEmail_rejectsMalformedAddresses(String email) {
        assertFalse(ValidationUtil.isValidEmail(email));
    }

    @Test
    void isValidEmail_rejectsNull() {
        assertFalse(ValidationUtil.isValidEmail(null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"password1", "Str0ngPass", "12345678a"})
    @DisplayName("Passwords with 8+ chars, a letter, and a digit are accepted")
    void isValidPassword_acceptsPolicyCompliantPasswords(String password) {
        assertTrue(ValidationUtil.isValidPassword(password));
    }

    @ParameterizedTest
    @ValueSource(strings = {"short1", "alllettersnodigits", "12345678", ""})
    @DisplayName("Passwords missing length, a letter, or a digit are rejected")
    void isValidPassword_rejectsPolicyViolations(String password) {
        assertFalse(ValidationUtil.isValidPassword(password));
    }

    @Test
    void isValidPassword_rejectsNull() {
        assertFalse(ValidationUtil.isValidPassword(null));
    }

    @ParameterizedTest
    @CsvSource({
            "50, 0, 100, true",   // typical marks-in-range case
            "0, 0, 100, true",    // lower boundary inclusive
            "100, 0, 100, true",  // upper boundary inclusive
            "100.01, 0, 100, false", // just over the boundary
            "-0.01, 0, 100, false"   // just under the boundary
    })
    void isWithinRange_respectsInclusiveBoundaries(double value, double min, double max, boolean expected) {
        assertTrue(ValidationUtil.isWithinRange(value, min, max) == expected);
    }

    @Test
    void isBlank_and_isNotBlank_areExactInverses() {
        for (String s : new String[]{null, "", "   ", "x", " x "}) {
            assertTrue(ValidationUtil.isBlank(s) != ValidationUtil.isNotBlank(s));
        }
    }

    @Test
    void isValidNonNegativeInteger_acceptsDigitsOnly() {
        assertTrue(ValidationUtil.isValidNonNegativeInteger("42"));
        assertTrue(ValidationUtil.isValidNonNegativeInteger("0"));
    }

    @Test
    void isValidNonNegativeInteger_rejectsNonDigits() {
        assertFalse(ValidationUtil.isValidNonNegativeInteger("-5"));
        assertFalse(ValidationUtil.isValidNonNegativeInteger("4.2"));
        assertFalse(ValidationUtil.isValidNonNegativeInteger("abc"));
        assertFalse(ValidationUtil.isValidNonNegativeInteger(""));
    }
}
