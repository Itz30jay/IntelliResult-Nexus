package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.ResultHistoryDAO;
import com.intelliresult.nexus.dao.ResultHistoryDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SemesterDAO;
import com.intelliresult.nexus.dao.SemesterDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.ResultRankingService;
import com.intelliresult.nexus.service.ResultService;
import com.intelliresult.nexus.util.AppConstants;
import com.intelliresult.nexus.util.DateUtil;
import com.intelliresult.nexus.util.ExcelUtil;
import io.sentry.Sentry;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /admin/results} - Sec. "Result Section (Admin)": ranking by
 * percentage (high to low), full per-student mark detail, filters, and
 * Excel export, plus the pre-existing correction/history workflow this
 * page already had.
 * <p>
 * Upgrade: showBrowse() used to load flat per-subject Result rows for one
 * exam - now loads ResultRankingService's ranked, filtered ResultSummary
 * list instead. /admin/results/history and /admin/results/correct
 * (Sec. 13/47's audit trail and correction workflow) are untouched; ranking
 * is purely a different way of browsing the same underlying Result rows,
 * not a replacement for correcting them.
 */
@WebServlet(name = "ResultServlet", urlPatterns = {
        "/admin/results",
        "/admin/results/export",
        "/admin/results/history",
        "/admin/results/correct"
})
public class ResultServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(ResultServlet.class);

    private final ExamDAO examDAO = new ExamDAOImpl();
    private final SemesterDAO semesterDAO = new SemesterDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultHistoryDAO resultHistoryDAO = new ResultHistoryDAOImpl();
    private final ResultService resultService = new ResultService();
    private final ResultRankingService rankingService = new ResultRankingService();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.endsWith("/history")) {
            showHistory(request, response);
        } else if (path.endsWith("/export")) {
            exportExcel(request, response);
        } else {
            showBrowse(request, response);
        }
    }

    private void showBrowse(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        List<Exam> exams = examDAO.findAll();
        request.setAttribute("exams", exams);
        request.setAttribute("semesters", semesterDAO.findAll());
        request.setAttribute("sections", sectionDAO.findAll());

        Long examId = resolveExamId(request, exams);
        if (examId != null) {
            request.setAttribute("selectedExamId", examId);

            ResultFilters filters = ResultFilters.fromRequest(request);
            request.setAttribute("filters", filters);

            List<ResultSummary> ranked = rankingService.rankedSummaries(examId, filters.sectionId, filters.rankFrom,
                    filters.rankTo, filters.passOnly, filters.search);
            request.setAttribute("rankedSummaries", ranked);

            // "Full mark details for every student" - eagerly loaded per
            // row (typical section sizes here are tens, not thousands, of
            // students) so the JSP can expand any row's subject breakdown
            // without a second round trip per click.
            Map<Long, List<Result>> breakdownByStudent = new LinkedHashMap<>();
            for (ResultSummary summary : ranked) {
                breakdownByStudent.put(summary.getStudent().getId(),
                        rankingService.subjectBreakdown(summary.getStudent().getId(), examId));
            }
            request.setAttribute("breakdownByStudent", breakdownByStudent);
        }

        readFlashMessages(request);
        request.getRequestDispatcher("/admin/results.jsp").forward(request, response);
    }

    private void exportExcel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        List<Exam> exams = examDAO.findAll();
        Long examId = resolveExamId(request, exams);
        if (examId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "No exam selected.");
            return;
        }
        Exam exam = examDAO.findById(examId).orElse(null);
        if (exam == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        try {
            ResultFilters filters = ResultFilters.fromRequest(request);
            List<ResultSummary> ranked = rankingService.rankedSummaries(examId, filters.sectionId, filters.rankFrom,
                    filters.rankTo, filters.passOnly, filters.search);

            byte[] xlsx = ExcelUtil.writeXlsx("Results - " + exam.getName(),
                    ResultRankingService.EXCEL_HEADERS, rankingService.toExcelRows(ranked));

            activityLogDAO.save(new ActivityLog(currentAdmin, "RESULTS_EXPORTED",
                    "Exported ranked results for " + exam.getName() + " (" + ranked.size() + " students)", request.getRemoteAddr()));

            String filename = exam.getName().toLowerCase().replaceAll("[^a-z0-9]+", "_")
                    + "_results_" + DateUtil.formatForFilename(LocalDateTime.now()) + ".xlsx";
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
            response.setContentLength(xlsx.length);
            response.getOutputStream().write(xlsx);
            response.getOutputStream().flush();
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error exporting results to Excel", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "result-engine"));
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Could not generate the export. Please try again.");
        }
    }

    /** Same default-to-the-first-exam shape as {@code ResultApprovalServlet.resolveExamId} - kept as its own copy rather than shared, matching how this project has consistently preferred a second small copy over a shared helper class for controller-only logic (see, e.g., every service's own private {@code inTransaction}). */
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

    private void showHistory(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String resultIdParam = request.getParameter("resultId");
        Long resultId;
        try {
            resultId = Long.valueOf(resultIdParam);
        } catch (NumberFormatException | NullPointerException e) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Result result = resultDAO.findById(resultId).orElse(null);
        if (result == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        request.setAttribute("result", result);
        request.setAttribute("history", resultHistoryDAO.findByResult(resultId));

        readFlashMessages(request);
        request.getRequestDispatcher("/admin/result-history.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Long resultId = Long.valueOf(request.getParameter("resultId"));

        try {
            BigDecimal newTheory = parseMarks(request.getParameter("theoryMarks"), "Theory");
            BigDecimal newPractical = parseMarks(request.getParameter("practicalMarks"), "Practical");
            BigDecimal newInternal = parseMarks(request.getParameter("internalMarks"), "Internal");
            String reason = request.getParameter("reason");

            Result corrected = resultService.correctResult(resultId, newTheory, newPractical, newInternal, currentAdmin, reason);

            activityLogDAO.save(new ActivityLog(currentAdmin, "RESULT_CORRECTED",
                    "Result #" + resultId + " (" + corrected.getStudent().getRollNo() + ", "
                            + corrected.getSubject().getSubjectCode() + ") corrected: " + reason,
                    request.getRemoteAddr()));

            flashSuccessAndRedirect(request, response, resultId, "Result corrected. The previous marks are preserved in this result's history.");
        } catch (BaseApplicationException e) {
            LOGGER.info("Result correction failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/results/history?resultId=" + resultId);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error correcting a result", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "result-engine"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/results/history?resultId=" + resultId);
        }
    }

    /** Blank means "this subject has no such component" (the form only renders inputs for components the subject actually has - see result-history.jsp) and becomes null, not zero; a genuinely malformed number becomes a clean {@link ValidationException} instead of an uncaught NumberFormatException. */
    private BigDecimal parseMarks(String raw, String label) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new ValidationException(label + " marks must be a valid number.");
        }
    }

    private void flashSuccessAndRedirect(HttpServletRequest request, HttpServletResponse response, Long resultId, String message)
            throws IOException {
        request.getSession().setAttribute("flashSuccess", message);
        response.sendRedirect(request.getContextPath() + "/admin/results/history?resultId=" + resultId);
    }

    private void readFlashMessages(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return;
        }
        if (session.getAttribute("flashError") != null) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, session.getAttribute("flashError"));
            session.removeAttribute("flashError");
        }
        if (session.getAttribute("flashSuccess") != null) {
            request.setAttribute("successMessage", session.getAttribute("flashSuccess"));
            session.removeAttribute("flashSuccess");
        }
    }

    /** Bundles the five optional filter params (Sec. "Ranking range, Section, Semester, Exam, any other useful filters") into one value the JSP can read back to keep form fields sticky after a submit - Semester itself narrows the Exam dropdown client-side (JS below on results.jsp) rather than being a server-side filter, since a ResultSummary belongs to exactly one Exam already. */
    private record ResultFilters(Long sectionId, Integer rankFrom, Integer rankTo, Boolean passOnly, String search) {
        static ResultFilters fromRequest(HttpServletRequest request) {
            return new ResultFilters(
                    parseLong(request.getParameter("sectionId")),
                    parseInt(request.getParameter("rankFrom")),
                    parseInt(request.getParameter("rankTo")),
                    parseBoolean(request.getParameter("passOnly")),
                    request.getParameter("search"));
        }

        private static Long parseLong(String v) { return (v == null || v.isBlank()) ? null : Long.valueOf(v); }
        private static Integer parseInt(String v) { return (v == null || v.isBlank()) ? null : Integer.valueOf(v); }
        private static Boolean parseBoolean(String v) {
            if (v == null || v.isBlank()) return null;
            return "true".equals(v) ? Boolean.TRUE : "false".equals(v) ? Boolean.FALSE : null;
        }
    }
}
