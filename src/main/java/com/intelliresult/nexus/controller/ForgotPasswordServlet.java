package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.AuthenticationException;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.PasswordResetService;
import com.intelliresult.nexus.util.AppConstants;
import io.sentry.Sentry;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * New (upgrade pass): {@code /forgot-password}, public (see
 * security.public.paths - by definition nobody using this is logged in).
 * Three steps on one page, tracked via session attributes rather than
 * request params carried across redirects, since "which step is this
 * browser on" needs to survive exactly one page (no back/forward
 * navigation concern worth solving here): request an OTP, verify it, then
 * choose a new password. The password step never re-collects or re-sends
 * the OTP - {@link #VERIFIED_WINDOW_MINUTES} is a short-lived session flag
 * stamped the instant {@link PasswordResetService#verifyOtp} succeeds, so a
 * person can't reach the password form without having actually verified a
 * code first, but also isn't asked to type it twice.
 */
@WebServlet(name = "ForgotPasswordServlet", urlPatterns = {"/forgot-password"})
public class ForgotPasswordServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(ForgotPasswordServlet.class);
    private static final String SESSION_VERIFIED_USER_ID = "otpVerifiedUserId";
    private static final String SESSION_VERIFIED_AT = "otpVerifiedAt";
    private static final String SESSION_VERIFIED_IDENTIFIER = "otpVerifiedIdentifier";
    /** A verified OTP unlocks the password step for a short window only - long enough to type a new password, not long enough to be a standing bypass of the OTP step if the tab is left open. */
    private static final long VERIFIED_WINDOW_MINUTES = 10;

    private final PasswordResetService passwordResetService = new PasswordResetService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("currentStep", resolveStep(request));
        request.getRequestDispatcher("/common/forgot-password.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String step = request.getParameter("step");
        try {
            if (step == null) {
                throw new IllegalArgumentException("Missing step.");
            }
            switch (step) {
                case "request-otp" -> handleRequestOtp(request);
                case "verify-otp" -> handleVerifyOtp(request);
                case "submit-password" -> handleSubmitPassword(request);
                default -> throw new IllegalArgumentException("Unknown step: " + step);
            }
        } catch (BaseApplicationException e) {
            LOGGER.info("Forgot-password step '{}' failed: {}", step, e.getMessage());
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in forgot-password step '{}'", step, e);
            Sentry.captureException(e, scope -> scope.setTag("module", "password-reset"));
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, "Something went wrong. Please try again.");
        }

        request.setAttribute("currentStep", resolveStep(request));
        request.getRequestDispatcher("/common/forgot-password.jsp").forward(request, response);
    }

    private void handleRequestOtp(HttpServletRequest request) {
        String identifier = request.getParameter("identifier");
        passwordResetService.requestOtp(identifier, request.getRemoteAddr());
        request.setAttribute("identifier", identifier);
        request.setAttribute("otpRequested", true);
    }

    private void handleVerifyOtp(HttpServletRequest request) {
        String identifier = request.getParameter("identifier");
        User verified = passwordResetService.verifyOtp(identifier, request.getParameter("otp"));

        HttpSession session = request.getSession();
        session.setAttribute(SESSION_VERIFIED_USER_ID, verified.getId());
        session.setAttribute(SESSION_VERIFIED_AT, LocalDateTime.now());
        session.setAttribute(SESSION_VERIFIED_IDENTIFIER, identifier);
        request.setAttribute("otpVerified", true);
        request.setAttribute("identifier", identifier);
    }

    private void handleSubmitPassword(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Long verifiedUserId = (session == null) ? null : (Long) session.getAttribute(SESSION_VERIFIED_USER_ID);
        LocalDateTime verifiedAt = (session == null) ? null : (LocalDateTime) session.getAttribute(SESSION_VERIFIED_AT);

        if (verifiedUserId == null || verifiedAt == null || verifiedAt.plusMinutes(VERIFIED_WINDOW_MINUTES).isBefore(LocalDateTime.now())) {
            if (session != null) {
                clearVerification(session);
            }
            throw new AuthenticationException(
                    "Your verification has expired. Please request a new code.");
        }

        String newPassword = request.getParameter("newPassword");
        String confirmPassword = request.getParameter("confirmPassword");
        if (newPassword == null || confirmPassword == null || !newPassword.equals(confirmPassword)) {
            throw new ValidationException("The two passwords do not match.");
        }

        passwordResetService.submitNewPassword(verifiedUserId, newPassword, verifiedAt);
        clearVerification(session);
        request.setAttribute("submitted", true);
    }

    private void clearVerification(HttpSession session) {
        session.removeAttribute(SESSION_VERIFIED_USER_ID);
        session.removeAttribute(SESSION_VERIFIED_AT);
        session.removeAttribute(SESSION_VERIFIED_IDENTIFIER);
    }

    /** Reconstructs which of the three steps to show purely from session/request state, so a GET (page refresh) after a successful step lands back on the right form instead of always restarting at step 1. */
    private String resolveStep(HttpServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute("submitted"))) {
            return "done";
        }
        HttpSession session = request.getSession(false);
        boolean hasVerifiedSession = session != null && session.getAttribute(SESSION_VERIFIED_USER_ID) != null;
        if (hasVerifiedSession || Boolean.TRUE.equals(request.getAttribute("otpVerified"))) {
            return "new-password";
        }
        if (Boolean.TRUE.equals(request.getAttribute("otpRequested"))) {
            return "verify-otp";
        }
        return "request-otp";
    }
}
