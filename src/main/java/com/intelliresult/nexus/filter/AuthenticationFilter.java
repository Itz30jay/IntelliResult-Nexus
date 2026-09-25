package com.intelliresult.nexus.filter;

import com.intelliresult.nexus.config.AppConfig;
import com.intelliresult.nexus.dao.NotificationDAO;
import com.intelliresult.nexus.dao.NotificationDAOImpl;
import com.intelliresult.nexus.dao.PasswordResetRequestDAO;
import com.intelliresult.nexus.dao.PasswordResetRequestDAOImpl;
import com.intelliresult.nexus.dao.RegistrationRequestDAO;
import com.intelliresult.nexus.dao.RegistrationRequestDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.PasswordResetStatus;
import com.intelliresult.nexus.entity.enums.RegistrationStatus;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Registered in web.xml (not @WebFilter) at a specific position after
 * HibernateSessionFilter and before AuthorizationFilter - see web.xml's own
 * comment on why filter chain ORDER is correctness-critical here and can't
 * be left to annotation scan order, which the Servlet spec does not
 * guarantee. This filter needs a Hibernate Session already bound to the
 * thread (to re-verify the session user via UserDAO), so it must run after
 * HibernateSessionFilter and before anything that assumes "the user is known".
 * Re-verifies the session's user against the database on every request
 * (active, not soft-deleted) rather than trusting the session attributes
 * alone - directly implementing Sec. 3's "Invalid session handling": a
 * session that was valid when created but whose account has since been
 * disabled must stop working immediately, not at next login.
 */
public class AuthenticationFilter implements jakarta.servlet.Filter {

    private static final Logger LOGGER = LogManager.getLogger(AuthenticationFilter.class);

    private final UserDAO userDAO = new UserDAOImpl();
    private final NotificationDAO notificationDAO = new NotificationDAOImpl();
    private final RegistrationRequestDAO registrationRequestDAO = new RegistrationRequestDAOImpl();
    private final PasswordResetRequestDAO passwordResetRequestDAO = new PasswordResetRequestDAOImpl();
    private List<String> publicPaths;

    @Override
    public void init(jakarta.servlet.FilterConfig filterConfig) {
        publicPaths = Arrays.asList(
                AppConfig.getString("security.public.paths", "/,/login").split(","));
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.isEmpty()) {
            path = "/";
        }

        if (isPublicPath(path)) {
            chain.doFilter(request, response);
            return;
        }

        HttpSession session = request.getSession(false);
        Long sessionUserId = (session == null) ? null : (Long) session.getAttribute(AppConstants.SESSION_USER_ID);

        if (sessionUserId == null) {
            redirectToLogin(request, response, path);
            return;
        }

        User user = userDAO.findById(sessionUserId).orElse(null);
        if (user == null || !user.isActive()) {
            LOGGER.warn("Session referenced a user (id={}) that is no longer valid - invalidating session.", sessionUserId);
            session.invalidate();
            redirectToLogin(request, response, path);
            return;
        }

        request.setAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER, user);
        // Sec. 23's notification badge needs to appear on every authenticated
        // page, not just /admin|student/notifications itself - this filter
        // already does one real DB round-trip per request to re-verify the
        // account (see class Javadoc), so one more cheap indexed COUNT query
        // alongside it is the same, already-accepted per-request cost, not a
        // new category of one. Added here rather than in every individual
        // servlet for the identical reason currentUser itself lives here.
        request.setAttribute("unreadNotificationCount", notificationDAO.countUnreadByUser(user.getId()));
        // Same reasoning as the notification badge just above, scoped to
        // ADMIN specifically: only an admin ever sees the Registrations nav
        // link this badge decorates, so a teacher/student request never
        // pays for a query whose result they'd never render.
        if (user.getRole() == UserRole.ADMIN) {
            request.setAttribute("pendingRegistrationCount", registrationRequestDAO.countByStatus(RegistrationStatus.PENDING));
            request.setAttribute("pendingPasswordResetCount", passwordResetRequestDAO.countByStatus(PasswordResetStatus.PENDING));
        }
        chain.doFilter(request, response);
    }

    private boolean isPublicPath(String path) {
        return publicPaths.stream().anyMatch(publicPath ->
                path.equals(publicPath) || path.startsWith(publicPath + "/"));
    }

    private void redirectToLogin(HttpServletRequest request, HttpServletResponse response, String originalPath)
            throws IOException {
        String returnUrl = URLEncoder.encode(originalPath, StandardCharsets.UTF_8);
        response.sendRedirect(request.getContextPath() + "/login?returnUrl=" + returnUrl);
    }
}
