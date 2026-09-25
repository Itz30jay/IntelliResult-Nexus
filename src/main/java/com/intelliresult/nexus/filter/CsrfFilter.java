package com.intelliresult.nexus.filter;

import com.intelliresult.nexus.util.AppConstants;
import com.intelliresult.nexus.util.SecurityUtil;
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
import java.util.Set;

/**
 * The classic synchronizer token pattern (Sec. 4's "basic CSRF token
 * protection for state-changing requests"): a random token lives in the
 * session, every form embeds it in a hidden field, and this filter rejects
 * any mutating request whose submitted token doesn't match. GET/HEAD/OPTIONS
 * are never validated - by HTTP semantics they shouldn't mutate state, so
 * requiring a token on them would only break plain links and browser
 * prefetching for no security benefit.
 * Registered in web.xml after AuthorizationFilter: by the time a request
 * could reach a state-changing operation, it has already been established
 * as both authenticated and authorized, so CSRF is the last gate before the
 * request reaches a Controller.
 */
public class CsrfFilter implements jakarta.servlet.Filter {

    private static final Logger LOGGER = LogManager.getLogger(CsrfFilter.class);
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS");

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;

        HttpSession session = request.getSession(true);
        String sessionToken = (String) session.getAttribute(AppConstants.SESSION_CSRF_TOKEN);
        if (sessionToken == null) {
            sessionToken = SecurityUtil.generateToken();
            session.setAttribute(AppConstants.SESSION_CSRF_TOKEN, sessionToken);
        }

        if (!SAFE_METHODS.contains(request.getMethod())) {
            String submittedToken = request.getParameter(AppConstants.CSRF_PARAM_NAME);
            if (submittedToken == null || !submittedToken.equals(sessionToken)) {
                LOGGER.warn("CSRF token mismatch on {} {} from {}",
                        request.getMethod(), request.getRequestURI(), request.getRemoteAddr());
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid or missing security token. Please refresh the page and try again.");
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
