package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.service.MarksheetService;
import com.intelliresult.nexus.util.AppConstants;
import com.intelliresult.nexus.util.DateUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * {@code /student/marksheet/download} - separate from {@code
 * MarksheetServlet} for the same reason {@code MarksEntryServlet} is
 * separate from {@code TeacherResultsServlet}: showing a page and
 * streaming a binary response are different enough concerns to earn their
 * own small, single-purpose class rather than one servlet branching on
 * whether a request parameter happens to be present. {@code studentId} is
 * deliberately never read from the request here - it is always the
 * current session's own {@code Student}, the same IDOR-proof pattern
 * {@code StudentResultServlet} already uses, so a student can never even
 * attempt to pass someone else's id (see {@code MarksheetService}'s own
 * Javadoc for where an admin-facing equivalent belongs instead).
 */
@WebServlet(name = "MarksheetDownloadServlet", urlPatterns = {"/student/marksheet/download"})
public class MarksheetDownloadServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(MarksheetDownloadServlet.class);

    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();
    private final MarksheetService marksheetService = new MarksheetService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Student student = studentDAO.findByUserId(currentUser.getId()).orElse(null);
        if (student == null) {
            LOGGER.error("User {} has role STUDENT but no matching Student profile.", currentUser.getEmail());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Long examId;
        try {
            examId = parseLongOrNull(request.getParameter("examId"));
        } catch (NumberFormatException e) {
            examId = null;
        }
        if (examId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "A valid examId is required.");
            return;
        }

        try {
            byte[] pdf = marksheetService.generateMarksheetPdf(
                    student.getId(), examId, currentUser, resolveVerificationBaseUrl(request));

            activityLogDAO.save(new ActivityLog(currentUser, "MARKSHEET_DOWNLOADED",
                    "Downloaded marksheet for exam id " + examId, request.getRemoteAddr()));

            String filename = "marksheet_" + student.getRollNo() + "_"
                    + DateUtil.formatForFilename(LocalDateTime.now()) + ".pdf";
            response.setContentType("application/pdf");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setContentLength(pdf.length);
            response.getOutputStream().write(pdf);
            response.getOutputStream().flush();
        } catch (ResourceNotFoundException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, e.getMessage());
        } catch (BusinessRuleException e) {
            // Expected, recoverable state (exam not fully published yet) -
            // not a server fault, so a 409 with the exception's own
            // human-readable message rather than a generic error page.
            response.sendError(HttpServletResponse.SC_CONFLICT, e.getMessage());
        }
    }

    /**
     * scheme://host[:port]/contextPath, derived from the live request
     * rather than a static config value - Sec. 64 asks for the application
     * URL to be configurable, but a QR code is only useful if it points at
     * whatever host actually served this request (localhost:8080 in dev,
     * the real domain in production), which a fixed property can drift out
     * of sync with. Standard port suppression (no ":80"/" :443") matches
     * how a browser's own address bar renders these same URLs.
     */
    private String resolveVerificationBaseUrl(HttpServletRequest request) {
        StringBuilder url = new StringBuilder();
        url.append(request.getScheme()).append("://").append(request.getServerName());
        boolean isDefaultPort = ("http".equals(request.getScheme()) && request.getServerPort() == 80)
                || ("https".equals(request.getScheme()) && request.getServerPort() == 443);
        if (!isDefaultPort) {
            url.append(':').append(request.getServerPort());
        }
        url.append(request.getContextPath());
        return url.toString();
    }

    private Long parseLongOrNull(String value) {
        return (value == null || value.isBlank()) ? null : Long.valueOf(value);
    }
}
