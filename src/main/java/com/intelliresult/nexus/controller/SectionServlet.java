package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SemesterDAO;
import com.intelliresult.nexus.dao.SemesterDAOImpl;
import com.intelliresult.nexus.entity.Section;
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

@WebServlet(name = "SectionServlet", urlPatterns = {
        "/admin/academic-setup/sections",
        "/admin/academic-setup/sections/new",
        "/admin/academic-setup/sections/edit",
        "/admin/academic-setup/sections/delete",
        "/admin/academic-setup/sections/restore"
})
public class SectionServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(SectionServlet.class);
    private final AcademicSetupService academicSetupService = new AcademicSetupService();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final SemesterDAO semesterDAO = new SemesterDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        request.setAttribute("semesters", semesterDAO.findAll());

        if (path.endsWith("/new")) {
            request.getRequestDispatcher("/admin/section-form.jsp").forward(request, response);
            return;
        }
        if (path.endsWith("/edit")) {
            Section section = sectionDAO.findById(Long.valueOf(request.getParameter("id"))).orElse(null);
            if (section == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("editingSection", section);
            request.getRequestDispatcher("/admin/section-form.jsp").forward(request, response);
            return;
        }

        request.setAttribute("sections", sectionDAO.findAll());
        request.setAttribute("deletedSections", sectionDAO.findDeleted());
        readFlashError(request);
        request.getRequestDispatcher("/admin/sections.jsp").forward(request, response);
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
                academicSetupService.softDeleteSection(Long.valueOf(request.getParameter("id")), currentAdmin.getId());
            } else if (path.endsWith("/restore")) {
                academicSetupService.restoreSection(Long.valueOf(request.getParameter("id")));
            } else {
                String name = request.getParameter("name");
                String capacityParam = request.getParameter("capacity");
                Integer capacity = (capacityParam == null || capacityParam.isBlank()) ? null : Integer.valueOf(capacityParam);
                String sectionTypeParam = request.getParameter("sectionType");
                com.intelliresult.nexus.entity.enums.SectionType sectionType = (sectionTypeParam == null || sectionTypeParam.isBlank())
                        ? null : com.intelliresult.nexus.entity.enums.SectionType.valueOf(sectionTypeParam);
                String idParam = request.getParameter("id");
                if (idParam == null) {
                    Long semesterId = Long.valueOf(request.getParameter("semesterId"));
                    academicSetupService.createSection(semesterId, name, capacity, sectionType);
                } else {
                    academicSetupService.updateSection(Long.valueOf(idParam), name, capacity, sectionType);
                }
            }
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/sections");

        } catch (ValidationException e) {
            request.setAttribute("semesters", semesterDAO.findAll());
            String idParam = request.getParameter("id");
            if (idParam != null) {
                sectionDAO.findById(Long.valueOf(idParam)).ifPresent(s -> request.setAttribute("editingSection", s));
            }
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/admin/section-form.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            LOGGER.info("Section operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/sections");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in section management", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "academic-setup"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/academic-setup/sections");
        }
    }
}
