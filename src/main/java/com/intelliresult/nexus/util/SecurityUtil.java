package com.intelliresult.nexus.util;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cryptographically-secure random token generation - CSRF tokens now
 * (Phase 4), QR-verification tokens later (Phase 12: Sec. 20 requires the
 * verification token itself not to expose or be derivable from internal
 * database IDs, which a random token trivially satisfies and a UUID
 * derived from a sequential id would not). One SecureRandom instance is
 * reused (it is thread-safe) rather than constructed per call - repeated
 * SecureRandom construction is a well-known avoidable cost, not a
 * correctness issue, but avoiding it costs nothing here.
 */
public final class SecurityUtil {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SecurityUtil() {
        // Static-only utility class.
    }

    /** A URL-safe, unpadded base64 token from byteLength bytes of secure randomness. 32 bytes (256 bits) is the default call site's choice throughout this project - well beyond brute-forceable for a session-lived CSRF token or a long-lived verification token. */
    public static String generateToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String generateToken() {
        return generateToken(32);
    }
}
