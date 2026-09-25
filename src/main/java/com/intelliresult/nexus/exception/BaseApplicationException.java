package com.intelliresult.nexus.exception;

/**
 * Root of every application-specific exception. Extends RuntimeException
 * (unchecked) deliberately: a checked-exception hierarchy would force every
 * method signature across Controller -> Service -> DAO to either declare or
 * swallow it, for exceptions that almost always mean "stop this request and
 * show the user a specific, actionable message" rather than something each
 * caller up the stack needs to individually decide how to recover from.
 * Every subclass keeps the same two constructors (message-only, and
 * message-plus-cause) so callers never have to check which one a particular
 * exception type happens to support.
 */
public abstract class BaseApplicationException extends RuntimeException {

    protected BaseApplicationException(String message) {
        super(message);
    }

    protected BaseApplicationException(String message, Throwable cause) {
        super(message, cause);
    }
}
