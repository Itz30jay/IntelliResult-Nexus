package com.intelliresult.nexus.exception;

/**
 * Thrown when a login attempt or session check fails to establish who the
 * caller is - wrong credentials, expired session, or an invalid/missing
 * session token. Kept distinct from AuthorizationException (which assumes
 * identity is already known and denies an action based on role) so the
 * AuthenticationFilter built in Phase 4 can redirect the former to the login
 * page and the latter to an access-denied page, rather than showing the same
 * generic message for two very different situations.
 */
public class AuthenticationException extends BaseApplicationException {

    public AuthenticationException(String message) {
        super(message);
    }

    public AuthenticationException(String message, Throwable cause) {
        super(message, cause);
    }
}
