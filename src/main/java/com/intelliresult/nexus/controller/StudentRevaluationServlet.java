package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.RevaluationDAO;
import com.intelliresult.nexus.dao.RevaluationDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.RevaluationRequest;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
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

/**
 * {@code /student/revaluation} - Sec. 14's student half: select an eligible
 * result, enter a reason, submit; see the status of every request made so
 * far. One page for both, not a separate "browse my results" page first -
 * see PHASE10-REVALUATION.md's Decisions for why a full Sec. 18 "My
 * Results" experience is left to Phase 11 rather than built piecemeal here.
 */
@WebServlet(name = "StudentRevaluationServlet", urlPatterns = {"/student/revaluation"})
public class StudentRevaluationServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(StudentRevaluationServlet.class);

    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final RevaluationDAO revaluationDAO = new RevaluationDAOImpl();
    private final RevaluationService revaluationService = new RevaluationService();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Student student = resolveStudent(request, response);
        if (student == null) {
            return;
        }

        request.setAttribute("eligibleResults", resultDAO.findVisibleToStudent(student.getId()));
        request.setAttribute("myRequests", revaluationDAO.findByStudent(student.getId()));

        readFlashMessages(request);
        request.getRequestDispatcher("/student/revaluation.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Student student = resolveStudent(request, response);
        if (student == null) {
            return;
        }
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);

        try {
            Long resultId = Long.valueOf(request.getParameter("resultId"));
            String reason = request.getParameter("reason");

            RevaluationRequest submitted = revaluationService.submitRequest(student.getId(), resultId, reason);

            activityLogDAO.save(new ActivityLog(currentUser, "REVALUATION_REQUESTED",
                    "Request #" + submitted.getId() + " submitted for result #" + resultId, request.getRemoteAddr()));

            flashSuccessAndRedirect(request, response, "Your re-evaluation request has been submitted. You'll see its status below once an administrator reviews it.");
        } catch (BaseApplicationException e) {
            LOGGER.info("Re-evaluation request failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/student/revaluation");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error submitting a re-evaluation request", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "result-engine"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/student/revaluation");
        }
    }

    /** Same User-&gt;Student resolution and 404-on-mismatch as {@code StudentDashboardServlet} - kept as its own copy per this project's established preference for a small per-controller copy over a shared helper (see student-head.jspf's own comment). Returns null and has already sent the response when resolution fails, matching the caller's own early-return check. */
    private Student resolveStudent(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Student student = studentDAO.findByUserId(currentUser.getId()).orElse(null);
        if (student == null) {
            LOGGER.error("User {} has role STUDENT but no matching Student profile.", currentUser.getEmail());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
        return student;
    }

    private void flashSuccessAndRedirect(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        request.getSession().setAttribute("flashSuccess", message);
        response.sendRedirect(request.getContextPath() + "/student/revaluation");
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
