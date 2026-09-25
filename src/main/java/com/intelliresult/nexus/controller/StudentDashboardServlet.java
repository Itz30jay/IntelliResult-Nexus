package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.service.StudentAnalyticsService;
import com.intelliresult.nexus.service.dto.SubjectInsight;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /student/dashboard} - Sec. 17's Student Performance Dashboard.
 * Phase 10 left this deliberately bare (two counts, no analytics, its own
 * class Javadoc said so explicitly) precisely so this phase could fill it
 * in without unwinding anything - every field below already existed before
 * this class was rewritten: {@link ResultSummary}'s nine core columns are
 * Phase 7's, {@link StudentAnalyticsService} is this phase's own new
 * service (built earlier in this same phase, see PHASE11-ANALYTICS.md).
 * "Current percentage/SGPA/rank" reads as the *most recent* exam this
 * student has a summary for - {@link StudentAnalyticsService#
 * performanceTrend} already returns every summary ordered oldest-first for
 * the trend chart, so the last element of that same list is the current
 * snapshot rather than a second, separately-ordered query for what is
 * really the same data viewed from the other end.
 * <p>
 * BUGFIX (upgrade pass): CGPA is different from percentage/SGPA/rank and
 * cannot use that same "just take the last element" logic - cgpa is, by
 * design (see ResultSummary's own schema comment), only ever populated on a
 * FINAL_EXAMINATION-type summary. A student whose most recent exam is a
 * Unit Test or Mid-Semester (the common case mid-semester) would have a
 * {@code null} latestSummary.cgpa even with a perfectly real CGPA on record
 * from an earlier completed semester - the dashboard showed "-" in exactly
 * that situation. currentCgpa instead scans the trend backward for the most
 * recent summary that actually has one, so a student's CGPA correctly
 * persists across every non-final exam that happens after it, the same way
 * a real transcript's cumulative GPA doesn't reset to blank between terms.
 */
@WebServlet(name = "StudentDashboardServlet", urlPatterns = {"/student/dashboard"})
public class StudentDashboardServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(StudentDashboardServlet.class);

    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final StudentAnalyticsService analyticsService = new StudentAnalyticsService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Student student = studentDAO.findByUserId(currentUser.getId()).orElse(null);
        if (student == null) {
            LOGGER.error("User {} has role STUDENT but no matching Student profile.", currentUser.getEmail());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        request.setAttribute("student", student);

        List<ResultSummary> trend = analyticsService.performanceTrend(student.getId());
        request.setAttribute("trend", trend);

        if (!trend.isEmpty()) {
            ResultSummary latest = trend.get(trend.size() - 1);
            request.setAttribute("latestSummary", latest);
            request.setAttribute("gradeDistribution", gradeDistributionFor(student.getId(), latest.getExam().getId()));
            request.setAttribute("currentCgpa", mostRecentKnownCgpa(trend));
        }

        List<SubjectInsight> insights = analyticsService.subjectStrengthWeakness(student.getId());
        if (!insights.isEmpty()) {
            request.setAttribute("bestSubject", insights.get(0));
        }
        if (insights.size() > 1) {
            request.setAttribute("improvementSubject", insights.get(insights.size() - 1));
        }

        request.getRequestDispatcher("/student/dashboard.jsp").forward(request, response);
    }

    /** trend is oldest-first (see this class's own Javadoc); scanning backward stops at the first non-null cgpa, which is this student's true current cumulative GPA regardless of how many non-final exams have happened since it was set. Returns null only when this student has never completed a single Final Examination - the one case where "-" on the dashboard is actually correct. */
    private java.math.BigDecimal mostRecentKnownCgpa(List<ResultSummary> trend) {
        for (int i = trend.size() - 1; i >= 0; i--) {
            if (trend.get(i).getCgpa() != null) {
                return trend.get(i).getCgpa();
            }
        }
        return null;
    }

    /** How many of this student's subjects in one exam landed on each letter grade - Sec. 17's "Grade distribution" chart, a fact about the student's own spread across subjects, not a comparison against anyone else (Sec. 15's charts are {@code /student/analysis}'s job, not this dashboard's - see PHASE11-ANALYTICS.md). LinkedHashMap, not a sorted/grouped query result, so insertion order (first grade encountered) is stable rather than alphabetical - a small enough list (at most one entry per subject) that Chart.js's own rendering order matters more than any particular sort. */
    private Map<String, Integer> gradeDistributionFor(Long studentId, Long examId) {
        Map<String, Integer> distribution = new LinkedHashMap<>();
        for (Result r : resultDAO.findByStudentAndExam(studentId, examId)) {
            if (r.getGrade() != null) {
                distribution.merge(r.getGrade(), 1, Integer::sum);
            }
        }
        return distribution;
    }
}
