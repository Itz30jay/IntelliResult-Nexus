package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.service.TeacherDashboardService;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Every Teacher-facing controller needs the same first step - resolve the
 * session's User (Phase 4) to their academic Teacher profile - the same
 * role student controllers will need Student for. Done directly here via
 * TeacherDAO rather than through a shared helper: this is the only
 * Teacher controller Phase 6a adds, so a shared "current teacher" resolver
 * would be premature abstraction for a single caller; worth revisiting once
 * Phase 6b's several new controllers make the repetition real.
 */
@WebServlet(name = "TeacherDashboardServlet", urlPatterns = {"/teacher/dashboard"})
public class TeacherDashboardServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(TeacherDashboardServlet.class);
    private final TeacherDashboardService teacherDashboardService = new TeacherDashboardService();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Teacher teacher = teacherDAO.findByUserId(currentUser.getId()).orElse(null);
        if (teacher == null) {
            // A TEACHER-role User with no Teacher profile row is a data
            // problem, not a "page not found" - logged distinctly so it's
            // findable, not silently swallowed as a routine 404.
            LOGGER.error("User {} has role TEACHER but no matching Teacher profile.", currentUser.getEmail());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        request.setAttribute("stats", teacherDashboardService.loadStats(teacher.getId(), currentUser.getId()));
        request.getRequestDispatcher("/teacher/dashboard.jsp").forward(request, response);
    }
}
