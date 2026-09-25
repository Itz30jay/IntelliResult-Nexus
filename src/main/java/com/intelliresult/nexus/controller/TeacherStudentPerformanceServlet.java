package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.service.StudentAnalyticsService;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * New (upgrade pass): {@code /teacher/student-performance}'s nav link
 * existed with nothing behind it - Sec. "Student Performance (Teacher) -
 * Make it fully working." A search bar (name or roll number, scoped to
 * students in a section this teacher actually teaches - see
 * StudentAnalyticsService.searchStudentsTaughtBy) that leads to a detail
 * view built entirely from the SAME analytics service the student's own
 * dashboard/analysis pages already use (performanceTrend,
 * subjectStrengthWeakness) - a teacher's view of a student's performance
 * and that student's own view of it are the same underlying facts, just
 * shown to a different audience, so this deliberately does not duplicate
 * that calculation logic.
 */
@WebServlet(name = "TeacherStudentPerformanceServlet", urlPatterns = "/teacher/student-performance")
public class TeacherStudentPerformanceServlet extends HttpServlet {

    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final StudentAnalyticsService analyticsService = new StudentAnalyticsService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        Teacher teacher = currentTeacher(request);
        String query = request.getParameter("query");
        request.setAttribute("query", query);

        String studentIdParam = request.getParameter("studentId");
        if (studentIdParam != null && !studentIdParam.isBlank()) {
            showDetail(request, response, teacher, Long.valueOf(studentIdParam));
            return;
        }

        request.setAttribute("results", analyticsService.searchStudentsTaughtBy(teacher.getId(), query));
        request.getRequestDispatcher("/teacher/student-performance.jsp").forward(request, response);
    }

    private void showDetail(HttpServletRequest request, HttpServletResponse response, Teacher teacher, Long studentId)
            throws ServletException, IOException {
        // Deliberately 404, not a redirect with an error message: a
        // student outside this teacher's sections should look exactly like
        // a student who does not exist, the same "don't confirm what you
        // can't authorize" reasoning TeacherRevaluationServlet's own
        // showResolveForm already applies.
        if (!analyticsService.teachesStudent(teacher.getId(), studentId)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        Student student = studentDAO.findById(studentId).orElse(null);
        if (student == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        request.setAttribute("student", student);
        request.setAttribute("trend", analyticsService.performanceTrend(studentId));
        request.setAttribute("insights", analyticsService.subjectStrengthWeakness(studentId));
        request.setAttribute("subjectHistory", subjectHistory(studentId));
        request.getRequestDispatcher("/teacher/student-performance-detail.jsp").forward(request, response);
    }

    /**
     * "All exams the student has taken (with semester), marks obtained vs
     * required marks" - grouped by subject (each subject's own passing
     * marks are what "required" means here) rather than flattened, so the
     * JSP can show one obtained-vs-required table per subject with its
     * exam history underneath, mirroring how subjectStrengthWeakness
     * already groups by subject for the same reason.
     */
    private Map<String, List<Result>> subjectHistory(Long studentId) {
        Map<String, List<Result>> bySubject = new LinkedHashMap<>();
        for (Result r : resultDAO.findCalculatedByStudent(studentId)) {
            String key = r.getSubject().getSubjectCode() + " - " + r.getSubject().getSubjectName();
            bySubject.computeIfAbsent(key, k -> new ArrayList<>()).add(r);
        }
        return bySubject;
    }

    private Teacher currentTeacher(HttpServletRequest request) {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        return teacherDAO.findByUserId(currentUser.getId())
                .orElseThrow(() -> new IllegalStateException("No teacher profile for user " + currentUser.getId()));
    }
}
