package com.intelliresult.nexus.util;

/**
 * Session and request attribute key names used by the authentication/
 * authorization layer, defined once here so a typo in a string literal
 * can't silently create two different session keys that mean the same
 * thing. Deliberately not created in Phase 1 alongside the other utility
 * classes - these keys have no meaning until Phase 4's filters/services
 * actually use them, and an empty constants class waiting for a future
 * consumer is exactly the kind of premature scaffolding this project's own
 * rules argue against (see Phase 1's notes on skipping GradeUtil/PDFUtil
 * stubs for the same reason).
 */
public final class AppConstants {

    private AppConstants() {
        // Static-only constants class.
    }

    // ---- HttpSession attribute keys ----
    public static final String SESSION_USER_ID = "sessionUserId";
    public static final String SESSION_USER_ROLE = "sessionUserRole";
    public static final String SESSION_USER_NAME = "sessionUserName";
    public static final String SESSION_CSRF_TOKEN = "csrfToken";

    // ---- HttpServletRequest attribute keys (filter -> JSP handoff) ----
    public static final String REQUEST_ATTR_ERROR_MESSAGE = "errorMessage";
    public static final String REQUEST_ATTR_CURRENT_USER = "currentUser";

    // ---- Request parameter name the CSRF synchronizer token pattern reads from every state-changing form ----
    public static final String CSRF_PARAM_NAME = "csrfToken";
}
