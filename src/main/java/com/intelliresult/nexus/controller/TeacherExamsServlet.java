package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.dao.TeacherSubjectDAO;
import com.intelliresult.nexus.dao.TeacherSubjectDAOImpl;
import com.intelliresult.nexus.entity.Exam;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Every exam for every semester this teacher's active assignments touch,
 * any status - an awareness view of the exam calendar, deliberately
 * broader than TeacherDashboardService's "open for marks entry" filter
 * (which only shows what's actionable right now). No "Enter Marks" action
 * on this list on purpose: that link belongs here once Phase 6b actually
 * builds the marks-entry screen it would point to - adding it now would
 * ship a button that goes nowhere, a different situation from a sidebar
 * nav item 404ing (an understood, temporary state per NavigationUtil's own
 * note) versus a specific in-page action button doing the same.
 */
@WebServlet(name = "TeacherExamsServlet", urlPatterns = {"/teacher/exams"})
public class TeacherExamsServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(TeacherExamsServlet.class);
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final TeacherSubjectDAO teacherSubjectDAO = new TeacherSubjectDAOImpl();
    private final ExamDAO examDAO = new ExamDAOImpl();

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

        Set<Long> semesterIds = teacherSubjectDAO.findActiveByTeacher(teacher.getId()).stream()
                .map(a -> a.getSubject().getSemester().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Each semesterId appears once in the set, so findBySemester() is
        // called at most once per semester - no risk of the same exam
        // being added twice even though two different assignments could
        // otherwise share a semester.
        List<Exam> exams = semesterIds.stream()
                .flatMap(semesterId -> examDAO.findBySemester(semesterId).stream())
                .toList();

        request.setAttribute("exams", exams);
        request.getRequestDispatcher("/teacher/exams.jsp").forward(request, response);
    }
}
