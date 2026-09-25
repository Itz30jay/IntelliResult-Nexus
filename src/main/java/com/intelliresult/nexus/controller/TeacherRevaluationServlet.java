package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.RevaluationDAO;
import com.intelliresult.nexus.dao.RevaluationDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.entity.RevaluationRequest;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.RevaluationStatus;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.RevaluationService;
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
import java.math.BigDecimal;
import java.util.List;

/**
 * New (upgrade pass): the teacher-facing half of the re-evaluation
 * delegation workflow, which did not exist at all before this - Sec.
 * "Teacher evaluates and submits: New marks, Detailed re-evaluation
 * report." {@code /teacher/revaluations} lists everything ever assigned to
 * this teacher (their active ASSIGNED queue plus their own resolved
 * history, partitioned in the JSP); {@code /resolve} is where they submit
 * their report and proposed marks. RevaluationService.resolveRequest itself
 * enforces that only the specific teacher a request was assigned to may
 * resolve it - this servlet passes the acting teacher's own id from the
 * session rather than trusting a posted value, so that check can never be
 * bypassed by tampering with a form field.
 */
@WebServlet(name = "TeacherRevaluationServlet", urlPatterns = {
        "/teacher/revaluations",
        "/teacher/revaluations/resolve"
})
public class TeacherRevaluationServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(TeacherRevaluationServlet.class);

    private final RevaluationDAO revaluationDAO = new RevaluationDAOImpl();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final RevaluationService revaluationService = new RevaluationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (request.getServletPath().endsWith("/resolve")) {
            showResolveForm(request, response);
        } else {
            showQueue(request, response);
        }
    }

    private void showQueue(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Teacher teacher = currentTeacher(request);
        List<RevaluationRequest> all = revaluationDAO.findByAssignedTeacher(teacher.getId());

        request.setAttribute("assignedRequests", all.stream().filter(r -> r.getStatus() == RevaluationStatus.ASSIGNED).toList());
        request.setAttribute("resolvedRequests", all.stream()
                .filter(r -> r.getStatus() == RevaluationStatus.APPROVED || r.getStatus() == RevaluationStatus.REJECTED)
                .toList());
        readFlashMessages(request);
        request.getRequestDispatcher("/teacher/revaluations.jsp").forward(request, response);
    }

    private void showResolveForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Teacher teacher = currentTeacher(request);
        Long requestId;
        try {
            requestId = Long.valueOf(request.getParameter("requestId"));
        } catch (NumberFormatException | NullPointerException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        RevaluationRequest revaluationRequest = revaluationDAO.findById(requestId).orElse(null);
        // Deliberately 404, not 403: whether a given re-evaluation request
        // even exists is not information this teacher needs about a request
        // assigned to someone else, mirroring RevaluationService.
        // submitRequest's own "not found" reasoning for a different
        // student's result.
        if (revaluationRequest == null || revaluationRequest.getAssignedTeacher() == null
                || !revaluationRequest.getAssignedTeacher().getId().equals(teacher.getId())) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        request.setAttribute("revaluationRequest", revaluationRequest);
        readFlashMessages(request);
        request.getRequestDispatcher("/teacher/revaluation-resolve.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Teacher teacher = currentTeacher(request);
        Long requestId = Long.valueOf(request.getParameter("requestId"));

        try {
            RevaluationStatus outcome = RevaluationStatus.valueOf(request.getParameter("outcome"));
            String teacherReport = request.getParameter("teacherReport");
            if (teacherReport == null || teacherReport.isBlank()) {
                throw new ValidationException("Please provide a detailed report explaining your evaluation.");
            }
            BigDecimal newTheory = parseMarks(request.getParameter("theoryMarks"), "Theory");
            BigDecimal newPractical = parseMarks(request.getParameter("practicalMarks"), "Practical");
            BigDecimal newInternal = parseMarks(request.getParameter("internalMarks"), "Internal");

            revaluationService.resolveRequest(requestId, teacher.getId(), outcome, teacherReport,
                    newTheory, newPractical, newInternal);

            request.getSession().setAttribute("flashSuccess",
                    "Evaluation submitted. The student and admin have been notified.");
            response.sendRedirect(request.getContextPath() + "/teacher/revaluations");
        } catch (BaseApplicationException e) {
            LOGGER.info("Re-evaluation resolution failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/teacher/revaluations/resolve?requestId=" + requestId);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error resolving a re-evaluation request", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "result-engine"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/teacher/revaluations/resolve?requestId=" + requestId);
        }
    }

    /** Every /teacher/* page already has the logged-in User on the request (AuthenticationFilter) - this resolves their Teacher profile from it once per request rather than each method repeating the same lookup. */
    private Teacher currentTeacher(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        return teacherDAO.findByUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("No teacher profile for user " + currentUser.getId()));
    }

    private BigDecimal parseMarks(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(label + " marks must be a valid number.");
        }
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
