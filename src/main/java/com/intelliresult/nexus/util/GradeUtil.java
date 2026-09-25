package com.intelliresult.nexus.util;

import com.intelliresult.nexus.entity.GradingRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Optional;

/**
 * Pure percentage/grade/GPA arithmetic - no Hibernate session, no entity
 * persistence, nothing that can throw a business exception. Mirrors
 * DateUtil/ValidationUtil's own shape deliberately: a util answers a
 * question or computes a number; deciding what an answer *means* (is a
 * missing GradingRule a problem worth failing the request over?) belongs to
 * whichever service calls it - here, ResultCalculationService, the one
 * place {@link com.intelliresult.nexus.exception.ResultProcessingException}'s
 * own Javadoc names as its thrower. Referenced by name in
 * GradingRuleService's and ResultProcessingException's class Javadoc before
 * this file existed - see docs/architecture/PHASE7-RESULT-ENGINE.md.
 */
public final class GradeUtil {

    /** Matches the {@code percentage}/{@code overall_percentage} columns' DECIMAL(5,2) definition (schema.sql). */
    public static final int PERCENTAGE_SCALE = 2;
    /** Matches the {@code grade_point}/{@code sgpa}/{@code cgpa} columns' DECIMAL(4,2) definition. */
    public static final int GPA_SCALE = 2;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private GradeUtil() {
    }

    /**
     * {@code obtained / max * 100}, rounded once (not divided-then-multiplied-
     * then-rounded, which would round twice and drift from the single true
     * value) to {@value #PERCENTAGE_SCALE} decimal places, HALF_UP - the
     * rounding mode this method's numbers were verified against
     * ({@code 59.81/70 -> 85.44}, {@code 43.19/70 -> 61.70}, both matching
     * this project's own seed-data.sql exactly; see PHASE7-RESULT-ENGINE.md's
     * verification section for the full cross-check against a live MySQL
     * instance).
     *
     * @throws IllegalArgumentException if maxMarks is null or not positive - a
     *         caller precondition violation (a subject's own configured
     *         maximum should never be non-positive), not a business
     *         situation for {@code ResultProcessingException} to describe to
     *         an end user.
     */
    public static BigDecimal calculatePercentage(BigDecimal obtainedMarks, BigDecimal maxMarks) {
        if (maxMarks == null || maxMarks.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("maxMarks must be a positive value to calculate a percentage.");
        }
        return obtainedMarks.multiply(HUNDRED).divide(maxMarks, PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * The first (rules are pre-ordered by min_percentage - see
     * GradingRuleDAO.findActiveByAcademicYear) rule whose range contains
     * {@code percentage}, or empty if none does - callers decide whether an
     * empty result is a {@code ResultProcessingException} ("no grading rule
     * covers this percentage", per that exception's own Javadoc) or
     * something else. Deliberately reuses {@link GradingRule#covers}
     * rather than re-implementing the boundary comparison here - see that
     * method's own Javadoc for why it, not a DAO query, is the one place
     * that decision lives.
     */
    public static Optional<GradingRule> resolveGrade(List<GradingRule> activeRules, BigDecimal percentage) {
        return activeRules.stream().filter(rule -> rule.covers(percentage)).findFirst();
    }

    /** Whether one component's obtained marks meet or exceed that component's own configured passing marks - the building block for the compartmental (theory AND practical AND internal must each individually pass) rule Sec. 7's per-component passing_marks columns exist for; see ResultCalculationService for why this project reads those columns that way rather than only checking the blended percentage against the grading scale. */
    public static boolean meetsPassingThreshold(BigDecimal obtainedMarks, BigDecimal passingMarks) {
        return obtainedMarks.compareTo(passingMarks) >= 0;
    }

    /**
     * {@code sum(value_i * weight_i) / sum(weight_i)}, empty (not zero, not
     * an exception) when total weight is zero. One general weighted-average
     * used at two different levels by ResultCalculationService: SGPA (each
     * subject's grade point weighted by that subject's credits) and CGPA
     * (each completed semester's SGPA weighted by that semester's total
     * credits) are the same formula one level apart, so this stays generic
     * rather than being named/typed for grade points specifically - naming
     * it "weightedGradePointAverage" would have been actively misleading at
     * the CGPA call site, where the "value" being averaged is itself an
     * SGPA, not a single subject's grade point. A zero/empty result is not
     * an exception: a subject with null/zero credits contributes nothing to
     * this average by design (see ResultCalculationService's handling of
     * Subject.credits being nullable), and a wholly-zero-weight input has no
     * meaningful average to report, which is a display concern (Sec. 56's
     * empty state) far more than a util's problem to raise an exception over.
     */
    public static Optional<BigDecimal> weightedAverage(List<WeightedValue> values) {
        BigDecimal totalWeight = values.stream()
                .map(WeightedValue::weight)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (totalWeight.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }
        BigDecimal weightedSum = values.stream()
                .map(v -> v.value().multiply(v.weight()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return Optional.of(weightedSum.divide(totalWeight, GPA_SCALE, RoundingMode.HALF_UP));
    }

    /** {@code current - previous}, rounded to {@value #PERCENTAGE_SCALE} places - positive means improvement, matching Sec. 15/55's plain-English framing directly rather than a signed direction a caller has to remember to interpret. */
    public static BigDecimal percentageChange(BigDecimal currentPercentage, BigDecimal previousPercentage) {
        return currentPercentage.subtract(previousPercentage).setScale(PERCENTAGE_SCALE, RoundingMode.HALF_UP);
    }

    /** One input to {@link #weightedAverage} - a subject's (gradePoint, credits) for SGPA, or a semester's (SGPA, totalCredits) for CGPA. The weight being nullable/zero-able at the source (Subject.credits) is a ResultCalculationService concern - it decides what gets excluded before this record is ever built, not this record itself. */
    public record WeightedValue(BigDecimal value, BigDecimal weight) {
    }
}
