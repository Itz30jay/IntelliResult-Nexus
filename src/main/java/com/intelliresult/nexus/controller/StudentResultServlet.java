package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.ResultSummaryDAO;
import com.intelliresult.nexus.dao.ResultSummaryDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Student;
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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /student/results} - Sec. 18's Student Result Module, deferred
 * explicitly by both Phase 10 ("a full Sec. 18 'My Results' experience is
 * left to Phase 11") and PHASE9-VERSIONING.md's own guess about which
 * phase would build it, which turned out right. "Only published results
 * are visible" (Sec. 18, verbatim) is satisfied by construction, the same
 * way as everywhere else this project reads that sentence:
 * {@link ResultDAO#findVisibleToStudent} only ever returns PUBLISHED or
 * LOCKED rows, so there is no separate visibility check to remember here.
 * <p>
 * Grouped by exam, one {@link ResultSummary} plus its subject-level
 * {@link Result} rows per exam - the same "aggregate fact, then its
 * components" shape {@code result-history.jsp} already uses for one
 * result's current state versus its detail, one level up.
 * <p>
 * Upgrade: added an exam/semester selector - every published exam used to
 * render stacked on one page; resultsByExam/summariesByExamId are unchanged
 * (still every exam, still needed to populate the selector itself), but
 * results.jsp now renders only the one matching selectedExamId.
 */
@WebServlet(name = "StudentResultServlet", urlPatterns = {"/student/results"})
public class StudentResultServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(StudentResultServlet.class);

    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultSummaryDAO resultSummaryDAO = new ResultSummaryDAOImpl();

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

        // One exam's worth of subject rows, grouped in Java rather than queried
        // pre-grouped - findVisibleToStudent already orders by exam.startTime DESC,
        // so a LinkedHashMap keyed by exam preserves that same most-recent-first
        // order across groups without a second sort.
        Map<Exam, List<Result>> byExam = new LinkedHashMap<>();
        for (Result r : visible) {
            byExam.computeIfAbsent(r.getExam(), k -> new ArrayList<>()).add(r);
        }

        Map<Long, ResultSummary> summariesByExamId = new LinkedHashMap<>();
        for (Exam exam : byExam.keySet()) {
            resultSummaryDAO.findByStudentAndExam(student.getId(), exam.getId())
                    .ifPresent(summary -> summariesByExamId.put(exam.getId(), summary));
        }

        request.setAttribute("student", student);
        request.setAttribute("resultsByExam", byExam);
        request.setAttribute("summariesByExamId", summariesByExamId);

        // Upgrade: "add a selector so the student can choose which exam /
        // semester result to view" - byExam is already ordered most-recent-
        // first (see this class's own Javadoc), so defaulting to its first
        // key when no examId is given, or falling back to it if an invalid/
        // foreign examId is passed, both mean "show the latest exam" without
        // a second query.
        Long selectedExamId = resolveSelectedExamId(request, byExam);
        request.setAttribute("selectedExamId", selectedExamId);

        request.getRequestDispatcher("/student/results.jsp").forward(request, response);
    }

    private Long resolveSelectedExamId(HttpServletRequest request, Map<Exam, List<Result>> byExam) {
        String param = request.getParameter("examId");
        if (param != null && !param.isBlank()) {
            try {
                Long candidate = Long.valueOf(param);
                if (byExam.keySet().stream().anyMatch(e -> e.getId().equals(candidate))) {
                    return candidate;
                }
            } catch (NumberFormatException ignored) {
                // falls through to the default below
            }
        }
        return byExam.isEmpty() ? null : byExam.keySet().iterator().next().getId();
    }
}
