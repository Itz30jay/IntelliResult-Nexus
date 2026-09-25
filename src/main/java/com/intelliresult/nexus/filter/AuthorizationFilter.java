package com.intelliresult.nexus.filter;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.util.AppConstants;
import com.intelliresult.nexus.util.NavigationUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registered in web.xml directly after AuthenticationFilter (see that
 * filter's note on why chain order is explicit here). Assumes
 * REQUEST_ATTR_CURRENT_USER is already set - true for every request that
 * reaches this filter, since AuthenticationFilter either sets it or has
 * already redirected the request away before this filter ever runs.
 * The prefix -> required-role map below is Sec. 68's access control matrix
 * (User Management/Academic Setup/Exam Creation/etc. = Admin only; Marks
 * Entry = Teacher; etc.) expressed at the URL level, which is what makes
 * "a Student must never reach an Admin page by typing the URL" (Sec. 2) a
 * property the filter chain enforces for every request, not something each
 * of the ~70 controllers across Phases 5-6 has to individually remember.
 */
public class AuthorizationFilter implements jakarta.servlet.Filter {

    private static final Logger LOGGER = LogManager.getLogger(AuthorizationFilter.class);

    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    // LinkedHashMap: prefixes are checked in this declared order, most
    // specific first, so a hypothetical future overlapping prefix can't
    // silently shadow another by iteration-order luck.
    private static final Map<String, UserRole> ROLE_RESTRICTED_PREFIXES = new LinkedHashMap<>();
    static {
        ROLE_RESTRICTED_PREFIXES.put("/admin", UserRole.ADMIN);
        ROLE_RESTRICTED_PREFIXES.put("/teacher", UserRole.TEACHER);
        ROLE_RESTRICTED_PREFIXES.put("/student", UserRole.STUDENT);
        // Any other path (e.g. /common/change-password) is open to any
        // authenticated role - AuthenticationFilter already guaranteed that
        // much by the time a request reaches here.
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        String path = request.getRequestURI().substring(request.getContextPath().length());
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);

        UserRole requiredRole = requiredRoleFor(path);
        if (requiredRole != null && currentUser != null && currentUser.getRole() != requiredRole) {
            LOGGER.warn("User {} (role {}) denied access to {} (requires {})",
                    currentUser.getEmail(), currentUser.getRole(), path, requiredRole);
            activityLogDAO.save(new ActivityLog(currentUser, "UNAUTHORIZED_ACCESS_ATTEMPT",
                    "Attempted to access " + path + " (requires " + requiredRole + ")",
                    request.getRemoteAddr()));

            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            request.setAttribute("dashboardUrl",
                    request.getContextPath() + NavigationUtil.dashboardPathFor(currentUser.getRole()));
            request.getRequestDispatcher("/common/access-denied.jsp").forward(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    private UserRole requiredRoleFor(String path) {
        for (Map.Entry<String, UserRole> entry : ROLE_RESTRICTED_PREFIXES.entrySet()) {
            if (path.equals(entry.getKey()) || path.startsWith(entry.getKey() + "/")) {
                return entry.getValue();
            }
        }
        return null;
    }
}
