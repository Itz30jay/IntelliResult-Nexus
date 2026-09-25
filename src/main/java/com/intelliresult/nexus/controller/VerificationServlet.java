package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.service.VerificationService;
import com.intelliresult.nexus.service.dto.VerificationResult;
import com.intelliresult.nexus.util.DateUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Sec. 20's public verification endpoint - {@code /verify/result/{token}},
 * already listed in {@code security.public.paths} (application.properties)
 * so {@code AuthenticationFilter} lets it through with no session at all,
 * exactly as a QR code scanned by a stranger with no IntelliResult Nexus
 * account of their own requires. Mapped with a trailing {@code /*} and read
 * via {@link HttpServletRequest#getPathInfo()} rather than a query
 * parameter, matching Sec. 20's literal URL shape.
 * <p>
 * Every outcome - valid, invalid, or malformed request - renders the same
 * {@code verify/result.jsp} with a {@link VerificationResult}; nothing here
 * ever throws its way to a generic error page, since "this code doesn't
 * match anything" is this endpoint's single most common, entirely expected
 * response, not a fault (Sec. 56).
 */
@WebServlet(name = "VerificationServlet", urlPatterns = {"/verify/result/*"})
public class VerificationServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(VerificationServlet.class);

    private final VerificationService verificationService = new VerificationService();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String token = extractToken(request);
        VerificationResult result = verificationService.verify(token);

        // user=null - this request has no session by design (see class
        // Javadoc), and ActivityLog.user is nullable for exactly this kind
        // of anonymous, publicly-reachable action (the same nullability
        // that already covers failed-login attempts). The token itself is
        // never logged, only the outcome - Sec. 41 names "sensitive
        // tokens" explicitly among what must never reach a log.
        activityLogDAO.save(new ActivityLog(null, "MARKSHEET_VERIFICATION_CHECKED",
                result.valid()
                        ? "Valid - " + result.examName() + " (" + result.academicYearLabel() + ")"
                        : "No matching record for the supplied code",
                request.getRemoteAddr()));

        if (!result.valid()) {
            LOGGER.info("Verification check from {} did not match any issued marksheet.", request.getRemoteAddr());
        }

        request.setAttribute("verification", result);
        request.setAttribute("verifiedAtDisplay", DateUtil.formatForDisplay(result.verifiedAt()));
        request.getRequestDispatcher("/verify/result.jsp").forward(request, response);
    }

    /** Strips the leading slash {@code getPathInfo()} always includes ("/abc123" -&gt; "abc123"); a request to the bare {@code /verify/result} with nothing after it (getPathInfo() == null) is treated the same as an empty token, which {@code VerificationService#verify} already renders as invalid rather than throwing. */
    private String extractToken(HttpServletRequest request) {
        String pathInfo = request.getPathInfo();
        if (pathInfo == null || pathInfo.isBlank()) {
            return null;
        }
        return pathInfo.startsWith("/") ? pathInfo.substring(1) : pathInfo;
    }
}
