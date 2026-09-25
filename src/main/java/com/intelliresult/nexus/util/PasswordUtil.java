package com.intelliresult.nexus.util;

import com.intelliresult.nexus.config.AppConfig;
import org.mindrot.jbcrypt.BCrypt;

import java.security.SecureRandom;

/**
 * The only place in this codebase that touches jBCrypt directly - every
 * other class hashes/verifies a password by calling these two methods, never
 * BCrypt.* itself, so "how passwords are hashed" has exactly one seam if it
 * ever needs to change. Cost factor is configurable (security.bcrypt.cost,
 * default 10 - the same default jBCrypt itself uses) rather than hardcoded,
 * since raising it is a legitimate, config-only way to respond to faster
 * hardware over the project's lifetime without a code change.
 */
public final class PasswordUtil {

    private static final int COST_FACTOR = AppConfig.getInt("security.bcrypt.cost", 10);

    private PasswordUtil() {
        // Static-only utility class.
    }

    /** Hashes a plain-text password for storage. Never logged, never returned to a client - the caller is responsible for discarding the plain-text value immediately after this call. */
    public static String hash(String plainTextPassword) {
        return BCrypt.hashpw(plainTextPassword, BCrypt.gensalt(COST_FACTOR));
    }

    /** True if plainTextPassword, once hashed, matches storedHash - BCrypt's own constant-time comparison, not a manual String.equals() on the hash (which would be a timing-attack surface for absolutely no benefit, since BCrypt already provides the safe comparison). */
    public static boolean matches(String plainTextPassword, String storedHash) {
        return BCrypt.checkpw(plainTextPassword, storedHash);
    }

    private static final SecureRandom RANDOM = new SecureRandom();
    // Excludes 0/O and 1/l/I - a temporary password an admin has to read
    // aloud or type in front of someone shouldn't hinge on distinguishing
    // those by font.
    private static final String TEMP_PASSWORD_CHARS = "ABCDEFGHJKMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789";

    /**
     * A random, human-typeable temporary password for admin-initiated
     * resets (Sec. 6) - satisfies ValidationUtil.isValidPassword() by
     * construction (guaranteed letter + digit, well over the length floor),
     * so it never gets rejected by the same policy every other password is
     * validated against. Phase 14 (Email) is what will eventually deliver
     * this to the user directly instead of an admin reading it off screen.
     */
    public static String generateTemporaryPassword() {
        StringBuilder sb = new StringBuilder(12);
        for (int i = 0; i < 12; i++) {
            sb.append(TEMP_PASSWORD_CHARS.charAt(RANDOM.nextInt(TEMP_PASSWORD_CHARS.length())));
        }
        return sb.toString();
    }
}
