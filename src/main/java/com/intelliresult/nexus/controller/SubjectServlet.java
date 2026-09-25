package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.DepartmentDAO;
import com.intelliresult.nexus.dao.DepartmentDAOImpl;
import com.intelliresult.nexus.dao.SemesterDAO;
import com.intelliresult.nexus.dao.SemesterDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.SubjectService;
import com.intelliresult.nexus.service.dto.SubjectComponentInput;
import com.intelliresult.nexus.service.dto.SubjectRequest;
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

@WebServlet(name = "SubjectServlet", urlPatterns = {
        "/admin/subjects", "/admin/subjects/new", "/admin/subjects/edit",
        "/admin/subjects/delete", "/admin/subjects/restore"
})
public class SubjectServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(SubjectServlet.class);
    private final SubjectService subjectService = new SubjectService();
    private final SubjectDAO subjectDAO = new SubjectDAOImpl();
    private final SemesterDAO semesterDAO = new SemesterDAOImpl();
    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        request.setAttribute("semesters", semesterDAO.findAll());
        request.setAttribute("departments", departmentDAO.findAll());

        if (path.endsWith("/new")) {
            request.getRequestDispatcher("/admin/subject-form.jsp").forward(request, response);
            return;
        }
        if (path.endsWith("/edit")) {
            Subject subject = subjectDAO.findById(Long.valueOf(request.getParameter("id"))).orElse(null);
            if (subject == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("editingSubject", subject);
            request.getRequestDispatcher("/admin/subject-form.jsp").forward(request, response);
            return;
        }

        request.setAttribute("subjects", subjectDAO.findAll());
        request.setAttribute("deletedSubjects", subjectDAO.findDeleted());
        readFlashError(request);
        request.getRequestDispatcher("/admin/subjects.jsp").forward(request, response);
    }

    private void readFlashError(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute("flashError") != null) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, session.getAttribute("flashError"));
            session.removeAttribute("flashError");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);

        try {
            if (path.endsWith("/delete")) {
                subjectService.softDelete(Long.valueOf(request.getParameter("id")), currentAdmin.getId());
            } else if (path.endsWith("/restore")) {
                subjectService.restore(Long.valueOf(request.getParameter("id")));
            } else {
                SubjectRequest req = new SubjectRequest(
                        Long.valueOf(request.getParameter("semesterId")),
                        Long.valueOf(request.getParameter("departmentId")),
                        request.getParameter("subjectCode"),
                        request.getParameter("subjectName"),
                        parseDecimal(request.getParameter("credits")),
                        component(request, "theory"),
                        component(request, "practical"),
                        component(request, "internal")
                );
                String idParam = request.getParameter("id");
                if (idParam == null) {
                    subjectService.createSubject(req);
                } else {
                    subjectService.updateSubject(Long.valueOf(idParam), req);
                }
            }
            response.sendRedirect(request.getContextPath() + "/admin/subjects");

        } catch (ValidationException e) {
            request.setAttribute("semesters", semesterDAO.findAll());
            request.setAttribute("departments", departmentDAO.findAll());
            String idParam = request.getParameter("id");
            if (idParam != null) {
                subjectDAO.findById(Long.valueOf(idParam)).ifPresent(s -> request.setAttribute("editingSubject", s));
            }
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/admin/subject-form.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            LOGGER.info("Subject operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/subjects");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in subject management", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "academic-setup"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/subjects");
        }
    }

    /** Null if the "has {label}" checkbox wasn't submitted at all - HTML checkboxes only send a parameter when checked. */
    private SubjectComponentInput component(HttpServletRequest request, String label) {
        if (request.getParameter("has" + capitalize(label)) == null) {
            return null;
        }
        return new SubjectComponentInput(
                parseDecimal(request.getParameter(label + "MaxMarks")),
                parseDecimal(request.getParameter(label + "PassingMarks")));
    }

    private String capitalize(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private BigDecimal parseDecimal(String value) {
        try {
            return (value == null || value.isBlank()) ? null : new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
