package com.intelliresult.nexus.exception;

/**
 * Thrown when a request is well-formed and the user is authorized, but the
 * operation itself violates a domain rule - publishing a result that is still
 * in DRAFT, approving marks that exceed the exam's maximum, editing a LOCKED
 * result outside the re-evaluation process. Kept distinct from
 * ValidationException (malformed input) because the fix for a business-rule
 * violation is usually "do a different thing", not "correct this field".
 */
public class BusinessRuleException extends BaseApplicationException {

    public BusinessRuleException(String message) {
        super(message);
    }

    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
