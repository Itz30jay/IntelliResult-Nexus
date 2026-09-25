package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.DepartmentDAO;
import com.intelliresult.nexus.dao.DepartmentDAOImpl;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.AcademicSetupService;
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
 * One servlet for the whole Department resource - list, create, edit,
 * soft-delete, restore - branching on the exact servlet path rather than
 * five separate classes, matching UserFormServlet's precedent from Phase
 * 5b for the same reason: the operations share enough (same DAO, same
 * redirect target, same error handling) that five classes would mean
 * keeping that overlap in sync instead of reading it top-to-bottom in one.
 */
@WebServlet(name = "DepartmentServlet", urlPatterns = {
        "/admin/academic-setup/departments",
        "/admin/academic-setup/departments/new",
        "/admin/academic-setup/departments/edit",
        "/admin/academic-setup/departments/delete",
        "/admin/academic-setup/departments/restore"
})
public class DepartmentServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(DepartmentServlet.class);
    private final AcademicSetupService academicSetupService = new AcademicSetupService();
    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();

        if (path.endsWith("/new")) {
            request.getRequestDispatcher("/admin/department-form.jsp").forward(request, response);
            return;
        }
        if (path.endsWith("/edit")) {
            Department department = departmentDAO.findById(Long.valueOf(request.getParameter("id"))).orElse(null);
            if (department == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("editingDepartment", department);
            request.getRequestDispatcher("/admin/department-form.jsp").forward(request, response);
            return;
        }

        request.setAttribute("departments", departmentDAO.findAll());
        request.setAttribute("deletedDepartments", departmentDAO.findDeleted());
        readFlashError(request);
        request.getRequestDispatcher("/admin/departments.jsp").forward(request, response);
    }

    /** Reads the one-shot session flash set by a failed delete/restore POST (BusinessRuleException path), so the list page can show it once and never again. */
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
                academicSetupService.softDeleteDepartment(Long.valueOf(request.getParameter("id")), currentAdmin.getId());
            } else if (path.endsWith("/restore")) {
                academicSetupService.restoreDepartment(Long.valueOf(request.getParameter("id")));
            } else {
                String idParam = request.getParameter("id");
                if (idParam == null) {
                    academicSetupService.createDepartment(request.getParameter("name"), request.getParameter("code"));
                } else {
                    academicSetupService.updateDepartment(Long.valueOf(idParam), request.getParameter("name"), request.getParameter("code"));
                }
            }
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/departments");

        } catch (ValidationException e) {
            String idParam = request.getParameter("id");
            if (idParam != null) {
                departmentDAO.findById(Long.valueOf(idParam)).ifPresent(d -> request.setAttribute("editingDepartment", d));
            }
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/admin/department-form.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            LOGGER.info("Department operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/departments");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in department management", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "academic-setup"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/departments");
        }
    }
}
