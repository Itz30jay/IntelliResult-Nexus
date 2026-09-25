package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.AcademicYearDAO;
import com.intelliresult.nexus.dao.AcademicYearDAOImpl;
import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SemesterDAO;
import com.intelliresult.nexus.dao.SemesterDAOImpl;
import com.intelliresult.nexus.entity.Semester;
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
import java.time.format.DateTimeParseException;

/** No /delete mapping - Semester has no soft-delete, same reasoning as AcademicYear. */
@WebServlet(name = "SemesterServlet", urlPatterns = {
        "/admin/academic-setup/semesters",
        "/admin/academic-setup/semesters/new",
        "/admin/academic-setup/semesters/edit"
})
public class SemesterServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(SemesterServlet.class);
    private final AcademicSetupService academicSetupService = new AcademicSetupService();
    private final SemesterDAO semesterDAO = new SemesterDAOImpl();
    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final AcademicYearDAO academicYearDAO = new AcademicYearDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        request.setAttribute("courses", courseDAO.findAll());
        request.setAttribute("academicYears", academicYearDAO.findAll());

        if (path.endsWith("/new")) {
            request.getRequestDispatcher("/admin/semester-form.jsp").forward(request, response);
            return;
        }
        if (path.endsWith("/edit")) {
            Semester semester = semesterDAO.findById(Long.valueOf(request.getParameter("id"))).orElse(null);
            if (semester == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("editingSemester", semester);
            request.getRequestDispatcher("/admin/semester-form.jsp").forward(request, response);
            return;
        }

        // Grouped by course for a readable list rather than one flat table -
        // "Semester 3" only means something alongside which course it's for.
        // findByCourseAndAcademicYear() requires a specific year, but this
        // list view wants every semester for every course across all years,
        // so it's built here from repeated calls rather than adding a DAO
        // method whose only caller would be this one list page.
        java.util.Map<com.intelliresult.nexus.entity.Course, java.util.List<Semester>> grouped = new java.util.LinkedHashMap<>();
        for (var course : courseDAO.findAll()) {
            grouped.put(course, new java.util.ArrayList<>());
        }
        for (var year : academicYearDAO.findAll()) {
            for (var course : courseDAO.findAll()) {
                grouped.get(course).addAll(semesterDAO.findByCourseAndAcademicYear(course.getId(), year.getId()));
            }
        }
        request.setAttribute("semestersByCourse", grouped);

        // Upgrade: previously each semester row only had "Edit" - there was
        // no way to see or add that semester's Sections without leaving
        // this page and navigating to the separate, unscoped Sections list,
        // then manually picking the right semester from a dropdown there.
        // sections-by-id lets semesters.jsp show each semester's actual
        // sections inline and link "+ Add Section" straight to
        // section-form.jsp with semesterId pre-filled - the form already
        // supported that query param, nothing was ever generating it.
        java.util.Map<Long, java.util.List<com.intelliresult.nexus.entity.Section>> sectionsBySemesterId = new java.util.LinkedHashMap<>();
        for (java.util.List<Semester> semesters : grouped.values()) {
            for (Semester semester : semesters) {
                sectionsBySemesterId.put(semester.getId(), sectionDAO.findBySemester(semester.getId()));
            }
        }
        request.setAttribute("sectionsBySemesterId", sectionsBySemesterId);
        readFlashError(request);

        request.getRequestDispatcher("/admin/semesters.jsp").forward(request, response);
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
        try {
            LocalDate startDate = parseDate(request.getParameter("startDate"));
            LocalDate endDate = parseDate(request.getParameter("endDate"));
            String idParam = request.getParameter("id");

            if (idParam == null) {
                Long courseId = Long.valueOf(request.getParameter("courseId"));
                Long academicYearId = Long.valueOf(request.getParameter("academicYearId"));
                int semesterNumber = Integer.parseInt(request.getParameter("semesterNumber"));
                academicSetupService.createSemester(courseId, academicYearId, semesterNumber, startDate, endDate);
            } else {
                academicSetupService.updateSemester(Long.valueOf(idParam), startDate, endDate);
            }
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/semesters");

        } catch (ValidationException e) {
            request.setAttribute("courses", courseDAO.findAll());
            request.setAttribute("academicYears", academicYearDAO.findAll());
            String idParam = request.getParameter("id");
            if (idParam != null) {
                semesterDAO.findById(Long.valueOf(idParam)).ifPresent(s -> request.setAttribute("editingSemester", s));
            }
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/admin/semester-form.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            LOGGER.info("Semester operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/semesters");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in semester management", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "academic-setup"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/semesters");
        }
    }

    private LocalDate parseDate(String value) {
        try {
            return (value == null || value.isBlank()) ? null : LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
