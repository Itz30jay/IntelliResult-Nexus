package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.DepartmentDAO;
import com.intelliresult.nexus.dao.DepartmentDAOImpl;
import com.intelliresult.nexus.dao.RegistrationRequestDAO;
import com.intelliresult.nexus.dao.RegistrationRequestDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.entity.RegistrationRequest;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.RegistrationService;
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
 * Admin's verification queue for public Registration submissions. Split
 * into a list (/admin/registrations) and a per-request review screen
 * (/admin/registrations/review) rather than inline actions on the list row,
 * because approval needs extra input (course+section or
 * department+designation) that a list row has no room for - the same
 * "list vs. dedicated form" split already established by
 * exams/exam-form.jsp and users/user-form.jsp.
 */
@WebServlet(name = "AdminRegistrationServlet", urlPatterns = {
        "/admin/registrations",
        "/admin/registrations/review",
        "/admin/registrations/approve",
        "/admin/registrations/reject"
})
public class AdminRegistrationServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(AdminRegistrationServlet.class);
    private final RegistrationService registrationService = new RegistrationService();
    private final RegistrationRequestDAO registrationRequestDAO = new RegistrationRequestDAOImpl();
    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();

        if (path.endsWith("/review")) {
            RegistrationRequest reviewing = registrationRequestDAO.findById(Long.valueOf(request.getParameter("id"))).orElse(null);
            if (reviewing == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("reviewing", reviewing);
            request.setAttribute("courses", courseDAO.findAll());
            request.setAttribute("sections", sectionDAO.findAll());
            request.setAttribute("departments", departmentDAO.findAll());
            request.getRequestDispatcher("/admin/registration-review.jsp").forward(request, response);
            return;
        }

        request.setAttribute("pendingRequests", registrationService.listPending());
        request.setAttribute("reviewedRequests", registrationService.listReviewed());
        readFlashMessages(request);
        request.getRequestDispatcher("/admin/registrations.jsp").forward(request, response);
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Long requestId = Long.valueOf(request.getParameter("id"));

        try {
            if (path.endsWith("/reject")) {
                registrationService.reject(requestId, currentAdmin.getId(), request.getParameter("remark"));
                flashSuccessAndRedirect(request, response, "Registration request rejected.");
            } else {
                String role = request.getParameter("role");
                if ("TEACHER".equals(role)) {
                    registrationService.approveTeacher(requestId, Long.valueOf(request.getParameter("departmentId")),
                            request.getParameter("designation"), currentAdmin.getId());
                } else {
                    String sectionIdParam = request.getParameter("sectionId");
                    Long sectionId = (sectionIdParam == null || sectionIdParam.isBlank()) ? null : Long.valueOf(sectionIdParam);
                    registrationService.approveStudent(requestId, Long.valueOf(request.getParameter("courseId")),
                            sectionId, currentAdmin.getId());
                }
                flashSuccessAndRedirect(request, response, "Registration approved - the account is now active.");
            }
        } catch (ValidationException e) {
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/registrations/review?id=" + requestId);
        } catch (BaseApplicationException e) {
            LOGGER.info("Registration review action failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/registrations");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error reviewing registration request", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "registration-review"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/registrations");
        }
    }

    private void flashSuccessAndRedirect(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        request.getSession().setAttribute("flashSuccess", message);
        response.sendRedirect(request.getContextPath() + "/admin/registrations");
    }
}
