package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.service.PasswordResetService;
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
 * New (upgrade pass): {@code /admin/password-resets} - the admin half of
 * Forgot Password's "keep Admin in the loop" requirement. Deliberately a
 * plain approve/reject pair with no extra input needed (unlike Registration's
 * approval, which also assigns academic placement) - by the time a request
 * reaches here, OTP has already verified identity and the new password is
 * already hashed and staged; there is nothing left for an admin to supply,
 * only to authorize.
 */
@WebServlet(name = "AdminPasswordResetServlet", urlPatterns = {
        "/admin/password-resets",
        "/admin/password-resets/approve",
        "/admin/password-resets/reject"
})
public class AdminPasswordResetServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(AdminPasswordResetServlet.class);
    private final PasswordResetService passwordResetService = new PasswordResetService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("pendingRequests", passwordResetService.listPending());
        request.setAttribute("reviewedRequests", passwordResetService.listReviewed());
        readFlashMessages(request);
        request.getRequestDispatcher("/admin/password-resets.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Long requestId = Long.valueOf(request.getParameter("id"));
        boolean approving = request.getServletPath().endsWith("/approve");

        try {
            if (approving) {
                passwordResetService.approve(requestId, currentAdmin.getId());
                request.getSession().setAttribute("flashSuccess", "Password change approved and applied.");
            } else {
                passwordResetService.reject(requestId, currentAdmin.getId());
                request.getSession().setAttribute("flashSuccess", "Password change request rejected. The existing password is unchanged.");
            }
        } catch (BaseApplicationException e) {
            LOGGER.info("Password reset review failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error reviewing a password reset request", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "password-reset"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
        }
        response.sendRedirect(request.getContextPath() + "/admin/password-resets");
    }

    private void readFlashMessages(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return;
        }
        if (session.getAttribute("flashError") != null) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, session.getAttribute("flashError"));
            session.removeAttribute("flashError");
        }
        if (session.getAttribute("flashSuccess") != null) {
            request.setAttribute("successMessage", session.getAttribute("flashSuccess"));
            session.removeAttribute("flashSuccess");
        }
    }
}
