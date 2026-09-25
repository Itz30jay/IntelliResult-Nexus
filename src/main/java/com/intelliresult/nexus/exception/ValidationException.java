package com.intelliresult.nexus.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Thrown when submitted input fails validation (Sec. 46: server-side
 * validation is mandatory regardless of what client-side JavaScript already
 * checked). Carries an optional field-name -> message map so a JSP can
 * redisplay a form with each invalid field flagged individually - "Marks
 * cannot exceed 100" next to the marks input, not a single banner that makes
 * the user re-scan the whole form. A LinkedHashMap preserves the order fields
 * were validated in, so error messages appear in the same order as the form
 * fields rather than shuffled.
 */
public class ValidationException extends BaseApplicationException {

    private final Map<String, String> fieldErrors;

    public ValidationException(String message) {
        super(message);
        this.fieldErrors = Collections.emptyMap();
    }

    public ValidationException(String message, Map<String, String> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
    }

    /** Never null; empty when this exception represents a single non-field-specific validation failure. */
    public Map<String, String> getFieldErrors() {
        return fieldErrors;
    }

    public boolean hasFieldErrors() {
        return !fieldErrors.isEmpty();
    }
}
