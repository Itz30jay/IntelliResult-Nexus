package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
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

/** Uses the same session-flash pattern as account creation to show the new temporary password exactly once - see UserListServlet's note on why. Upgrade: same whitelisted returnTo redirect as UserStatusServlet, since this is now posted to from three different pages. */
@WebServlet(name = "UserPasswordResetServlet", urlPatterns = "/admin/users/reset-password")
public class UserPasswordResetServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(UserPasswordResetServlet.class);
    private static final Set<String> ALLOWED_RETURN_PATHS = Set.of("/admin/users", "/admin/students", "/admin/teachers");
    private final UserService userService = new UserService();
    private final UserDAO userDAO = new UserDAOImpl();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Long targetUserId = Long.valueOf(request.getParameter("id"));

        try {
            String temporaryPassword = userService.resetPassword(targetUserId, currentAdmin.getId());
            String email = userDAO.findById(targetUserId).map(User::getEmail).orElse("");
            request.getSession().setAttribute("flashTempPassword", temporaryPassword);
            request.getSession().setAttribute("flashTempPasswordFor", email);
        } catch (BaseApplicationException e) {
            LOGGER.info("Password reset failed for user {}: {}", targetUserId, e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error resetting password", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "user-management"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
        }
        String returnTo = request.getParameter("returnTo");
        response.sendRedirect(request.getContextPath() + (ALLOWED_RETURN_PATHS.contains(returnTo) ? returnTo : "/admin/users"));
    }
}
