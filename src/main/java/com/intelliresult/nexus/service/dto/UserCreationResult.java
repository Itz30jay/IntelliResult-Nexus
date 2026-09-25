package com.intelliresult.nexus.service.dto;

import com.intelliresult.nexus.entity.User;

/**
 * temporaryPassword exists only on this transient result object - never
 * persisted anywhere in plaintext, never logged (AuthenticationService's
 * activity-log calls for account creation/reset deliberately omit it from
 * the details string). The servlet that receives this shows the password
 * to the admin exactly once and then this object is discarded.
 */
public record UserCreationResult(User user, String temporaryPassword) {
}
