package com.intelliresult.nexus.exception;

/**
 * Thrown when a lookup by ID (student, exam, result, etc.) finds nothing -
 * including when the row exists but is soft-deleted, since Sec. 27's recycle
 * bin design means a "deleted" record must behave as not-found to every part
 * of the system except the recycle-bin views built specifically to see it.
 */
public class ResourceNotFoundException extends BaseApplicationException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
