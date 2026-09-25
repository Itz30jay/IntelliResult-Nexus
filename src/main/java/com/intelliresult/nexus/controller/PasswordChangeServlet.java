package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.service.AuthenticationService;
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

/**
 * Not under /admin, /teacher, or /student - AuthorizationFilter's
 * role-restricted prefixes deliberately don't cover this path, so any
 * authenticated role can reach it (Sec. 32/3: every role gets self-service
 * password change, not just one). AuthenticationFilter still requires a
 * valid session to reach here at all.
 */
@WebServlet(name = "PasswordChangeServlet", urlPatterns = "/change-password")
public class PasswordChangeServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(PasswordChangeServlet.class);
    private final AuthenticationService authenticationService = new AuthenticationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/common/change-password.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        String currentPassword = request.getParameter("currentPassword");
        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");

        try {
            if (!newPassword.equals(confirmPassword)) {
                request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE,
                        "New password and confirmation do not match.");
                request.getRequestDispatcher("/common/change-password.jsp").forward(request, response);
                return;
            }

            authenticationService.changePassword(currentUser.getId(), currentPassword, newPassword);
            request.setAttribute("successMessage", "Password changed successfully.");
            request.getRequestDispatcher("/common/change-password.jsp").forward(request, response);

        } catch (BaseApplicationException e) {
            LOGGER.info("Password change failed for user {}: {}", currentUser.getEmail(), e.getMessage());
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.getRequestDispatcher("/common/change-password.jsp").forward(request, response);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error changing password for user {}", currentUser.getEmail(), e);
            Sentry.captureException(e, scope -> scope.setTag("module", "authentication"));
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, "Something went wrong. Please try again.");
            request.getRequestDispatcher("/common/change-password.jsp").forward(request, response);
        }
    }
}
