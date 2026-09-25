package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.DepartmentDAO;
import com.intelliresult.nexus.dao.DepartmentDAOImpl;
import com.intelliresult.nexus.entity.Course;
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

@WebServlet(name = "CourseServlet", urlPatterns = {
        "/admin/academic-setup/courses",
        "/admin/academic-setup/courses/new",
        "/admin/academic-setup/courses/edit",
        "/admin/academic-setup/courses/delete",
        "/admin/academic-setup/courses/restore"
})
public class CourseServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(CourseServlet.class);
    private final AcademicSetupService academicSetupService = new AcademicSetupService();
    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        request.setAttribute("departments", departmentDAO.findAll());

        if (path.endsWith("/new")) {
            request.getRequestDispatcher("/admin/course-form.jsp").forward(request, response);
            return;
        }
        if (path.endsWith("/edit")) {
            Course course = courseDAO.findById(Long.valueOf(request.getParameter("id"))).orElse(null);
            if (course == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("editingCourse", course);
            request.getRequestDispatcher("/admin/course-form.jsp").forward(request, response);
            return;
        }

        request.setAttribute("courses", courseDAO.findAll());
        request.setAttribute("deletedCourses", courseDAO.findDeleted());
        readFlashError(request);
        request.getRequestDispatcher("/admin/courses.jsp").forward(request, response);
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
                academicSetupService.softDeleteCourse(Long.valueOf(request.getParameter("id")), currentAdmin.getId());
            } else if (path.endsWith("/restore")) {
                academicSetupService.restoreCourse(Long.valueOf(request.getParameter("id")));
            } else {
                Long departmentId = Long.valueOf(request.getParameter("departmentId"));
                String name = request.getParameter("name");
                String code = request.getParameter("code");
                int totalSemesters = parseIntSafe(request.getParameter("totalSemesters"));
                String idParam = request.getParameter("id");
                if (idParam == null) {
                    academicSetupService.createCourse(departmentId, name, code, totalSemesters);
                } else {
                    academicSetupService.updateCourse(Long.valueOf(idParam), departmentId, name, code, totalSemesters);
                }
            }
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/courses");

        } catch (ValidationException e) {
            request.setAttribute("departments", departmentDAO.findAll());
            String idParam = request.getParameter("id");
            if (idParam != null) {
                courseDAO.findById(Long.valueOf(idParam)).ifPresent(c -> request.setAttribute("editingCourse", c));
            }
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/admin/course-form.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            LOGGER.info("Course operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/courses");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in course management", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "academic-setup"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/courses");
        }
    }

    private int parseIntSafe(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException | NullPointerException e) {
            return 0;
        }
    }
}
