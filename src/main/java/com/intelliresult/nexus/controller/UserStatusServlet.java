package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.service.UserService;
import com.intelliresult.nexus.util.AppConstants;
import io.sentry.Sentry;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.Set;

/**
 * POST-only, confirmed client-side via SweetAlert2 before the form ever
 * submits (Sec. 22 - a consequential single-user action, not just a bulk
 * one). Upgrade: gained delete/restore alongside the original
 * enable/disable toggle - the same three actions Sec. "Admin can only:
 * change password / disable-enable / soft-delete-remove" names, all
 * operating on the same target-user-id shape, so one servlet covering all
 * three avoids three near-identical classes. Also upgraded to a whitelisted
 * returnTo redirect (defaulting to /admin/users for any caller that doesn't
 * supply one) rather than a hardcoded target, since this same servlet is
 * now posted to from three different pages (Admins/Students/Teachers) that
 * each need to land back on themselves, not always on Admins.
 */
@WebServlet(name = "UserStatusServlet", urlPatterns = {
        "/admin/users/toggle-status", "/admin/users/delete", "/admin/users/restore"
})
public class UserStatusServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(UserStatusServlet.class);
    private static final Set<String> ALLOWED_RETURN_PATHS = Set.of("/admin/users", "/admin/students", "/admin/teachers");
    private final UserService userService = new UserService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Long targetUserId = Long.valueOf(request.getParameter("id"));
        String path = request.getServletPath();

        try {
            if (path.endsWith("/delete")) {
                userService.softDelete(targetUserId, currentAdmin.getId());
            } else if (path.endsWith("/restore")) {
                userService.restore(targetUserId, currentAdmin.getId());
            } else {
                boolean activate = "true".equals(request.getParameter("activate"));
                userService.setActive(targetUserId, activate, currentAdmin.getId());
            }
        } catch (BaseApplicationException e) {
            LOGGER.info("User action rejected for user {}: {}", targetUserId, e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error updating user", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "user-management"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
        }
        response.sendRedirect(request.getContextPath() + resolveReturnPath(request));
    }

    private String resolveReturnPath(HttpServletRequest request) {
        String returnTo = request.getParameter("returnTo");
        return ALLOWED_RETURN_PATHS.contains(returnTo) ? returnTo : "/admin/users";
    }
}
