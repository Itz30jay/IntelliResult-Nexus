package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.AcademicYearDAO;
import com.intelliresult.nexus.dao.AcademicYearDAOImpl;
import com.intelliresult.nexus.entity.AcademicYear;
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
import java.time.LocalDate;

/** No /delete mapping here - AcademicYear has no soft-delete (see AcademicSetupService's class note). /set-current replaces it as the one non-CRUD action this resource needs. */
@WebServlet(name = "AcademicYearServlet", urlPatterns = {
        "/admin/academic-setup/academic-years",
        "/admin/academic-setup/academic-years/new",
        "/admin/academic-setup/academic-years/edit",
        "/admin/academic-setup/academic-years/set-current"
})
public class AcademicYearServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(AcademicYearServlet.class);
    private final AcademicSetupService academicSetupService = new AcademicSetupService();
    private final AcademicYearDAO academicYearDAO = new AcademicYearDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();

        if (path.endsWith("/new")) {
            request.getRequestDispatcher("/admin/academic-year-form.jsp").forward(request, response);
            return;
        }
        if (path.endsWith("/edit")) {
            AcademicYear year = academicYearDAO.findById(Long.valueOf(request.getParameter("id"))).orElse(null);
            if (year == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("editingYear", year);
            request.getRequestDispatcher("/admin/academic-year-form.jsp").forward(request, response);
            return;
        }

        request.setAttribute("academicYears", academicYearDAO.findAll());
        readFlashError(request);
        request.getRequestDispatcher("/admin/academic-years.jsp").forward(request, response);
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

        try {
            if (path.endsWith("/set-current")) {
                academicSetupService.setCurrentAcademicYear(Long.valueOf(request.getParameter("id")));
            } else {
                String label = request.getParameter("label");
                LocalDate startDate = parseDate(request.getParameter("startDate"));
                LocalDate endDate = parseDate(request.getParameter("endDate"));
                String idParam = request.getParameter("id");
                if (idParam == null) {
                    academicSetupService.createAcademicYear(label, startDate, endDate);
                } else {
                    academicSetupService.updateAcademicYear(Long.valueOf(idParam), label, startDate, endDate);
                }
            }
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/academic-years");

        } catch (ValidationException e) {
            String idParam = request.getParameter("id");
            if (idParam != null) {
                academicYearDAO.findById(Long.valueOf(idParam)).ifPresent(y -> request.setAttribute("editingYear", y));
            }
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/admin/academic-year-form.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            LOGGER.info("Academic year operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/academic-years");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in academic year management", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "academic-setup"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/academic-years");
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return (value == null || value.isBlank()) ? null : LocalDate.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }
}
