package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.dao.TeacherSubjectDAO;
import com.intelliresult.nexus.dao.TeacherSubjectDAOImpl;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
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
 * One data fetch, two views: "My Classes" (Sec. 69) groups a teacher's
 * active assignments by section - which classes do I teach, and what in
 * each; "My Subjects" groups the identical data by subject - which
 * subjects do I teach, and to which sections. Both read-only: a teacher
 * doesn't create or edit assignments (Sec. 8 - that's Admin's Teacher
 * Assignments screen, Phase 5d), so there's no mutation path here to
 * justify a dedicated Service - straight DAO reads, same as any other
 * read-only admin list (e.g. ExamServlet.doGet's own direct
 * examDAO.findAll() call).
 */
@WebServlet(name = "TeacherClassesServlet", urlPatterns = {"/teacher/my-classes", "/teacher/my-subjects"})
public class TeacherClassesServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(TeacherClassesServlet.class);
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final TeacherSubjectDAO teacherSubjectDAO = new TeacherSubjectDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Teacher teacher = teacherDAO.findByUserId(currentUser.getId()).orElse(null);
        if (teacher == null) {
            LOGGER.error("User {} has role TEACHER but no matching Teacher profile.", currentUser.getEmail());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        request.setAttribute("assignments", teacherSubjectDAO.findActiveByTeacher(teacher.getId()));

        String view = request.getServletPath().endsWith("/my-subjects") ? "my-subjects" : "my-classes";
        request.getRequestDispatcher("/teacher/" + view + ".jsp").forward(request, response);
    }
}
