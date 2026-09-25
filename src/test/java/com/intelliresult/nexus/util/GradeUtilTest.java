package com.intelliresult.nexus.util;

import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.GradingRule;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradeUtilTest {

    private final AcademicYear year = new AcademicYear("2025-2026",
            LocalDate.of(2025, 7, 1), LocalDate.of(2026, 6, 30));

    // ---------------------------------------------------------------- calculatePercentage

    @Test
    void calculatePercentage_matchesThisProjectsOwnSeedData() {
        // CS301/CS25001, Unit Test 1: 59.81 obtained out of a 70 theory-only
        // denominator (not the subject's full 100) - re-derived against the
        // live seeded database before this test was written; see
        // PHASE7-RESULT-ENGINE.md's verification section.
        assertEquals(new BigDecimal("85.44"),
                GradeUtil.calculatePercentage(new BigDecimal("59.81"), new BigDecimal("70")));
        assertEquals(new BigDecimal("61.70"),
                GradeUtil.calculatePercentage(new BigDecimal("43.19"), new BigDecimal("70")));
    }

    @Test
    void calculatePercentage_handlesExactMaximum() {
        assertEquals(new BigDecimal("100.00"),
                GradeUtil.calculatePercentage(new BigDecimal("100"), new BigDecimal("100")));
    }

    @Test
    void calculatePercentage_throwsForZeroOrNegativeMax() {
        assertThrows(IllegalArgumentException.class,
                () -> GradeUtil.calculatePercentage(BigDecimal.TEN, BigDecimal.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> GradeUtil.calculatePercentage(BigDecimal.TEN, new BigDecimal("-5")));
        assertThrows(IllegalArgumentException.class,
                () -> GradeUtil.calculatePercentage(BigDecimal.TEN, null));
    }

    // ---------------------------------------------------------------- resolveGrade

    @Test
    void resolveGrade_findsTheRuleWhoseRangeContainsThePercentage() {
        List<GradingRule> rules = List.of(
                new GradingRule(year, new BigDecimal("0.00"), new BigDecimal("39.99"), "F", new BigDecimal("0.00")),
                new GradingRule(year, new BigDecimal("40.00"), new BigDecimal("49.99"), "D", new BigDecimal("5.00")),
                new GradingRule(year, new BigDecimal("50.00"), new BigDecimal("59.99"), "C", new BigDecimal("6.00")),
                new GradingRule(year, new BigDecimal("80.00"), new BigDecimal("89.99"), "A", new BigDecimal("9.00")));

        Optional<GradingRule> result = GradeUtil.resolveGrade(rules, new BigDecimal("85.44"));

        assertTrue(result.isPresent());
        assertEquals("A", result.get().getGrade());
    }

    @Test
    void resolveGrade_honoursInclusiveBoundaries() {
        List<GradingRule> rules = List.of(
                new GradingRule(year, new BigDecimal("40.00"), new BigDecimal("49.99"), "D", new BigDecimal("5.00")));

        assertTrue(GradeUtil.resolveGrade(rules, new BigDecimal("40.00")).isPresent());
        assertTrue(GradeUtil.resolveGrade(rules, new BigDecimal("49.99")).isPresent());
        assertTrue(GradeUtil.resolveGrade(rules, new BigDecimal("39.99")).isEmpty());
        assertTrue(GradeUtil.resolveGrade(rules, new BigDecimal("50.00")).isEmpty());
    }

    @Test
    void resolveGrade_emptyWhenNoRuleCoversThePercentage() {
        List<GradingRule> rules = List.of(
                new GradingRule(year, new BigDecimal("90.00"), new BigDecimal("100.00"), "A+", new BigDecimal("10.00")));

        assertTrue(GradeUtil.resolveGrade(rules, new BigDecimal("45.00")).isEmpty());
    }

    // ---------------------------------------------------------------- meetsPassingThreshold

    @Test
    void meetsPassingThreshold_trueAtOrAboveTheThreshold() {
        assertTrue(GradeUtil.meetsPassingThreshold(new BigDecimal("28.00"), new BigDecimal("28.00")));
        assertTrue(GradeUtil.meetsPassingThreshold(new BigDecimal("60.69"), new BigDecimal("28.00")));
    }

    @Test
    void meetsPassingThreshold_falseBelowTheThreshold() {
        assertFalse(GradeUtil.meetsPassingThreshold(new BigDecimal("27.71"), new BigDecimal("28.00")));
    }

    // ---------------------------------------------------------------- weightedAverage (SGPA/CGPA)

    @Test
    void weightedAverage_matchesThisProjectsOwnSeedDataForCS25001() {
        // Mid Semester Examination, CS25001: (9.00,4.0) (10.00,4.0) (9.00,3.0)
        // (9.00,3.5) (9.00,3.5) -> SGPA 9.22, re-derived against the live
        // seeded database before this test was written.
        List<GradeUtil.WeightedValue> grades = List.of(
                new GradeUtil.WeightedValue(new BigDecimal("9.00"), new BigDecimal("4.0")),
                new GradeUtil.WeightedValue(new BigDecimal("10.00"), new BigDecimal("4.0")),
                new GradeUtil.WeightedValue(new BigDecimal("9.00"), new BigDecimal("3.0")),
                new GradeUtil.WeightedValue(new BigDecimal("9.00"), new BigDecimal("3.5")),
                new GradeUtil.WeightedValue(new BigDecimal("9.00"), new BigDecimal("3.5")));

        Optional<BigDecimal> sgpa = GradeUtil.weightedAverage(grades);

        assertTrue(sgpa.isPresent());
        assertEquals(new BigDecimal("9.22"), sgpa.get());
    }

    @Test
    void weightedAverage_emptyWhenTotalWeightIsZero() {
        List<GradeUtil.WeightedValue> grades = List.of(
                new GradeUtil.WeightedValue(new BigDecimal("9.00"), BigDecimal.ZERO));

        assertTrue(GradeUtil.weightedAverage(grades).isEmpty());
    }

    @Test
    void weightedAverage_emptyForNoInputs() {
        assertTrue(GradeUtil.weightedAverage(List.of()).isEmpty());
    }

    // ---------------------------------------------------------------- percentageChange

    @Test
    void percentageChange_positiveMeansImprovement() {
        assertEquals(new BigDecimal("5.30"),
                GradeUtil.percentageChange(new BigDecimal("85.44"), new BigDecimal("80.14")));
    }

    @Test
    void percentageChange_negativeMeansDecline() {
        assertEquals(new BigDecimal("-10.00"),
                GradeUtil.percentageChange(new BigDecimal("60.00"), new BigDecimal("70.00")));
    }
}
