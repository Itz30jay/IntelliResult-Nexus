package com.intelliresult.nexus.exception;

/**
 * Thrown by ResultCalculationService (Phase 7) when marks cannot be turned
 * into a percentage/grade/GPA/rank as requested - e.g. no active GradingRule
 * covers the computed percentage, or a subject's component marks
 * (theory + practical + internal) don't reconcile with its configured maximum.
 * Kept separate from BusinessRuleException because result computation sits on
 * the most audit-sensitive path in the whole system (Sec. 47/48: result
 * integrity and auditability), so it is useful to filter for this exact type
 * in logs and Sentry independently of every other business-rule violation.
 */
public class ResultProcessingException extends BaseApplicationException {

    public ResultProcessingException(String message) {
        super(message);
    }

    public ResultProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}
