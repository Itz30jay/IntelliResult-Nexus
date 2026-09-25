package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.ResultSummaryDAO;
import com.intelliresult.nexus.dao.ResultSummaryDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Result;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code /student/analysis} - Sec. 15's Comparative Performance Analytics
 * and Sec. 16's Subject Strength &amp; Weakness Analysis, on one page
 * rather than the up-to-four separate ones Sec. 69's nav names ("Performance
 * Analytics", "Subject Analysis", "Comparison") - see PHASE11-ANALYTICS.md's
 * Decisions for the consolidation reasoning. All the real computation lives
 * in {@link StudentAnalyticsService} (built this same phase); this class is
 * the usual controller shape - resolve the exam being viewed, call the
 * service, set attributes, forward.
 */
@WebServlet(name = "StudentAnalysisServlet", urlPatterns = {"/student/analysis"})
public class StudentAnalysisServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(StudentAnalysisServlet.class);

    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultSummaryDAO resultSummaryDAO = new ResultSummaryDAOImpl();
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

        List<Result> visible = resultDAO.findVisibleToStudent(student.getId());
        // findVisibleToStudent orders by exam.startTime DESC already; a LinkedHashSet
        // collapses the per-subject rows down to distinct exams while preserving that
        // same most-recent-first order for the picker.
        Set<Exam> examsSeen = new LinkedHashSet<>();
        for (Result r : visible) {
            examsSeen.add(r.getExam());
        }
        List<Exam> exams = List.copyOf(examsSeen);
        request.setAttribute("exams", exams);

        Long examId = resolveExamId(request, exams);
        if (examId != null) {
            request.setAttribute("selectedExamId", examId);
            resultSummaryDAO.findByStudentAndExam(student.getId(), examId).ifPresent(summary ->
                    request.setAttribute("myPercentage", summary.getOverallPercentage()));

            // classAverage/topperScore/subjectComparisons genuinely need a
            // section (they compare against classmates in it) - myPercentage
            // just above does not, and used to be nested inside this same
            // null-check, so a student with no section yet (an accepted
            // outcome now that Registration approval can leave sectionId
            // unset - see RegistrationService) saw their OWN percentage
            // disappear too, not just the parts that actually required a
            // section to compute.
            if (student.getCurrentSection() != null) {
                Long sectionId = student.getCurrentSection().getId();
                request.setAttribute("hasSection", true);
                request.setAttribute("classAverage", analyticsService.classAverage(examId, sectionId).orElse(null));
                request.setAttribute("topperScore", analyticsService.topperScore(examId, sectionId).orElse(null));
                request.setAttribute("subjectComparisons", analyticsService.subjectComparison(student.getId(), examId, sectionId));
            }
        }

        applyStrengthWeaknessSplit(request, student.getId());

        request.getRequestDispatcher("/student/analysis.jsp").forward(request, response);
    }

    /** Same default-to-the-first-item shape used throughout this project's other exam pickers (approvals.jsp, results.jsp/ResultServlet) - kept as its own copy per the established preference for a small per-controller copy over a shared helper. */
    private Long resolveExamId(HttpServletRequest request, List<Exam> exams) {
        String param = request.getParameter("examId");
        if (param != null && !param.isBlank()) {
            try {
                Long candidate = Long.valueOf(param);
                if (exams.stream().anyMatch(e -> e.getId().equals(candidate))) {
                    return candidate;
                }
            } catch (NumberFormatException ignored) {
                // falls through to the default below
            }
        }
        return exams.isEmpty() ? null : exams.get(0).getId();
    }

    /**
     * Splits {@link StudentAnalyticsService#subjectStrengthWeakness}'s
     * already-sorted-descending list into the front (strengths) and back
     * (improvement areas) without letting the same subject land in both -
     * a real risk with very few subjects (a student with only two or three
     * results would otherwise see one subject labeled both a strength and
     * a weakness simultaneously). {@code improvementStart} is clamped to
     * never go below {@code strengthCount}, which is what prevents the
     * overlap.
     */
    private void applyStrengthWeaknessSplit(HttpServletRequest request, Long studentId) {
        List<SubjectInsight> insights = analyticsService.subjectStrengthWeakness(studentId);
        int strengthCount = Math.min(2, insights.size());
        int improvementStart = Math.max(strengthCount, insights.size() - 2);

        request.setAttribute("strengths", insights.subList(0, strengthCount));
        request.setAttribute("improvementAreas", insights.subList(improvementStart, insights.size()));
    }
}
