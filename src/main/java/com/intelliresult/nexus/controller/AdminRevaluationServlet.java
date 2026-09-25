package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.RevaluationDAO;
import com.intelliresult.nexus.dao.RevaluationDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.RevaluationRequest;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.RevaluationStatus;
import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
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
import java.util.List;

/**
 * {@code /admin/revaluations} - Sec. 14's admin half. Upgrade: an admin's
 * only action here is now assignment, not resolution - {@code
 * /admin/revaluations/assign} replaces the old {@code /resolve} endpoint
 * entirely, since Sec. "Admin receives it and assigns it to any teacher"
 * moved the actual evaluate-and-decide step to the assigned teacher (see
 * {@link TeacherRevaluationServlet}). This servlet still shows the full
 * queue across every status (PENDING/ASSIGNED/APPROVED/REJECTED) for
 * visibility, but only PENDING rows get an action.
 */
@WebServlet(name = "AdminRevaluationServlet", urlPatterns = {
        "/admin/revaluations",
        "/admin/revaluations/assign"
})
public class AdminRevaluationServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(AdminRevaluationServlet.class);

    private final RevaluationDAO revaluationDAO = new RevaluationDAOImpl();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final RevaluationService revaluationService = new RevaluationService();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (request.getServletPath().endsWith("/assign")) {
            showAssignForm(request, response);
        } else {
            showList(request, response);
        }
    }

    /** Defaults to PENDING - the actionable queue - rather than every request ever made; "ALL" and each specific status (now including ASSIGNED) remain one click away via the filter. */
    private void showList(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String statusParam = request.getParameter("status");
        List<RevaluationRequest> requests;
        if (statusParam == null || statusParam.isBlank()) {
            statusParam = "PENDING";
            requests = revaluationDAO.findByStatus(RevaluationStatus.PENDING);
        } else if ("ALL".equals(statusParam)) {
            requests = revaluationDAO.findAll();
        } else {
            requests = revaluationDAO.findByStatus(RevaluationStatus.valueOf(statusParam));
        }

        request.setAttribute("requests", requests);
        request.setAttribute("statusFilter", statusParam);
        readFlashMessages(request);
        request.getRequestDispatcher("/admin/revaluations.jsp").forward(request, response);
    }

    private void showAssignForm(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Long requestId;
        try {
            requestId = Long.valueOf(request.getParameter("requestId"));
        } catch (NumberFormatException | NullPointerException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        RevaluationRequest revaluationRequest = revaluationDAO.findById(requestId).orElse(null);
        if (revaluationRequest == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        request.setAttribute("revaluationRequest", revaluationRequest);
        request.setAttribute("teachers", teacherDAO.findAll());
        readFlashMessages(request);
        request.getRequestDispatcher("/admin/revaluation-assign.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Long requestId = Long.valueOf(request.getParameter("requestId"));
        Long teacherId = Long.valueOf(request.getParameter("teacherId"));
        String adminRemark = request.getParameter("adminRemark");

        try {
            revaluationService.assignToTeacher(requestId, teacherId, adminRemark, currentAdmin.getId());

            activityLogDAO.save(new ActivityLog(currentAdmin, "REVALUATION_ASSIGNED",
                    "Request #" + requestId + " assigned to teacher #" + teacherId, request.getRemoteAddr()));

            request.getSession().setAttribute("flashSuccess", "Request #" + requestId + " assigned. The teacher has been notified.");
            response.sendRedirect(request.getContextPath() + "/admin/revaluations");
        } catch (BaseApplicationException e) {
            LOGGER.info("Re-evaluation assignment failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/revaluations/assign?requestId=" + requestId);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error assigning a re-evaluation request", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "result-engine"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/revaluations/assign?requestId=" + requestId);
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
