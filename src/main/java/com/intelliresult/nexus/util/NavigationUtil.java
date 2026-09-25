package com.intelliresult.nexus.util;

import com.intelliresult.nexus.entity.enums.UserRole;

/**
 * The one place "which URL is this role's home" is defined. Phase 5/6 add
 * the actual controllers behind these paths - this mapping is correct
 * today even though /admin/dashboard etc. don't resolve to anything yet
 * (they 404 via the Phase 1 error page), which is an honest reflection of
 * "not built yet," not a bug in this routing logic.
 */
public final class NavigationUtil {

    private NavigationUtil() {
        // Static-only utility class.
    }

    public static String dashboardPathFor(UserRole role) {
        if (role == null) {
            return "/";
        }
        return switch (role) {
            case ADMIN -> "/admin/dashboard";
            case TEACHER -> "/teacher/dashboard";
            case STUDENT -> "/student/dashboard";
        };
    }
}
