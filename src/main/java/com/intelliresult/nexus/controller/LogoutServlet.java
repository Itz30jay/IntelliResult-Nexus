package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.service.AuthenticationService;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * POST-only by design (see login.jsp's logout form) - a logout triggered by
 * a plain GET link is itself a minor CSRF-adjacent footgun (any page could
 * embed <img src="/logout"> and silently log a user out), and it costs
 * nothing here to require the same POST+CSRF-token path every other
 * state-changing action already uses.
 */
@WebServlet(name = "LogoutServlet", urlPatterns = "/logout")
public class LogoutServlet extends HttpServlet {

    private final AuthenticationService authenticationService = new AuthenticationService();

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session != null) {
            User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
            if (currentUser != null) {
                authenticationService.logout(currentUser, request.getRemoteAddr());
            }
            // Proper invalidation (not just clearing attributes) is what
            // makes Sec. 3's "proper invalidation on logout" actually true -
            // the old session id becomes entirely unusable, not just empty.
            session.invalidate();
        }
        response.sendRedirect(request.getContextPath() + "/login");
    }
}
