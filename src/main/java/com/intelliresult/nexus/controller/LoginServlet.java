package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.config.AppConfig;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.service.AuthenticationService;
import com.intelliresult.nexus.util.AppConstants;
import com.intelliresult.nexus.util.NavigationUtil;
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

/**
 * Public entry point (see security.public.paths). GET renders the form (or,
 * if a session already exists, skips straight to the dashboard - no reason
 * to show a login form to someone already logged in). POST authenticates and
 * is where session fixation protection actually happens: changeSessionId()
 * on the EXISTING session, not invalidate()+getSession(true), because the
 * latter would silently drop the CSRF token CsrfFilter already placed in the
 * pre-login session on this same request cycle. Deliberately thin - this
 * class does request/response mechanics and delegates every real decision
 * ("are these credentials valid") to AuthenticationService, matching Sec.
 * 37's "Controller... never contain complex calculations or business rules."
 */
@WebServlet(name = "LoginServlet", urlPatterns = "/login")
public class LoginServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(LoginServlet.class);
    private final AuthenticationService authenticationService = new AuthenticationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute(AppConstants.SESSION_USER_ID) != null) {
            response.sendRedirect(request.getContextPath()
                    + NavigationUtil.dashboardPathFor((UserRole) session.getAttribute(AppConstants.SESSION_USER_ROLE)));
            return;
        }
        // Upgrade: the demo-credentials hint this used to gate was removed
        // from login.jsp entirely (showing any account's ID/password on a
        // login screen reads as insecure regardless of an environment
        // guard) - isProduction is no longer read by that page at all.
        request.getRequestDispatcher("/common/login.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String identifier = request.getParameter("identifier");
        String password = request.getParameter("password");

        try {
            User user = authenticationService.login(identifier, password, request.getRemoteAddr());
            establishSession(request, user);

            String returnUrl = request.getParameter("returnUrl");
            String target = isSafeRelativeReturnUrl(returnUrl) ? returnUrl : NavigationUtil.dashboardPathFor(user.getRole());
            response.sendRedirect(request.getContextPath() + target);

        } catch (BaseApplicationException e) {
            // Authentication/validation failures are expected user-facing
            // outcomes, not bugs - logged at INFO (already captured in the
            // activity log by AuthenticationService) rather than sent to
            // Sentry, which is reserved for unexpected exceptions.
            LOGGER.info("Login failed for '{}': {}", identifier, e.getMessage());
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("identifier", identifier);
            request.getRequestDispatcher("/common/login.jsp").forward(request, response);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error during login for '{}'", identifier, e);
            Sentry.captureException(e, scope -> scope.setTag("module", "authentication"));
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE,
                    "Something went wrong. Please try again.");
            request.getRequestDispatcher("/common/login.jsp").forward(request, response);
        }
    }

    /**
     * Session fixation protection (Sec. 3): the session that existed before
     * login (and that CsrfFilter may have already stamped a token into on
     * this very request) gets a NEW id here rather than being thrown away
     * and recreated, so an attacker who fixed a victim's pre-login session
     * id cannot reuse it post-login, while any pre-login CSRF token remains
     * intact for this response's rendering.
     */
    private void establishSession(HttpServletRequest request, User user) {
        request.changeSessionId();
        HttpSession session = request.getSession();
        session.setAttribute(AppConstants.SESSION_USER_ID, user.getId());
        session.setAttribute(AppConstants.SESSION_USER_ROLE, user.getRole());
        session.setAttribute(AppConstants.SESSION_USER_NAME, user.getFullName());
        session.setMaxInactiveInterval(AppConfig.getInt("app.session.timeoutMinutes", 30) * 60);
    }

    /** Rejects anything that isn't an unambiguous same-site relative path, so a crafted returnUrl can never redirect a freshly-authenticated user off-site (an open-redirect phishing vector). */
    private boolean isSafeRelativeReturnUrl(String returnUrl) {
        return returnUrl != null
                && returnUrl.startsWith("/")
                && !returnUrl.startsWith("//")
                && !returnUrl.contains("://");
    }
}
