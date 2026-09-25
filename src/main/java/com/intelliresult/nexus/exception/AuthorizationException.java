package com.intelliresult.nexus.exception;

/**
 * Thrown when an authenticated user attempts an action their role does not
 * permit - e.g. a Student manually entering an Admin URL, or a Teacher trying
 * to modify a Locked result. This is what Sec. 2's "a user must never access
 * another role's protected pages by manually entering URLs" rule resolves to
 * at the exception-handling level: AuthorizationFilter (Phase 4) throws this,
 * and it is always logged to the audit trail (Phase 4/26) since a caught
 * AuthorizationException is itself a security-relevant event worth recording,
 * not just a UI inconvenience to smooth over.
 */
public class AuthorizationException extends BaseApplicationException {

    public AuthorizationException(String message) {
        super(message);
    }

    public AuthorizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
