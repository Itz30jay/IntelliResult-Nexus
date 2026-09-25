package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.service.NotificationService;
import com.intelliresult.nexus.service.ResultService;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * {@code /admin/approvals} - the page {@code admin-head.jspf}'s sidebar has
 * linked to since Phase 5e, unbuilt until now. Same one-servlet-per-resource
 * shape as {@code ExamServlet}, and the same flashError/flashSuccess
 * session pattern. The one deliberate addition: this is the first
 * controller in this project that calls into {@code ResultCalculationService}
 * (via {@code ResultService}), so this is where the {@code module:
 * result-engine} Sentry tag PHASE7-RESULT-ENGINE.md's Decision 7 said would
 * eventually need a home lands, on the same unexpected-exception catch-all
 * every other admin servlet already uses.
 * <p>
 * Phase 13 adds bulk-publish/bulk-lock here rather than as a separate
 * servlet - Sec. 22's "Bulk publish eligible results, Bulk lock results"
 * loops the exact same {@code resultService.publishExam}/{@code lockExam}
 * this class already calls one exam at a time, so it belongs with them,
 * not beside them.
 */
@WebServlet(name = "ResultApprovalServlet", urlPatterns = {
        "/admin/approvals",
        "/admin/approvals/approve",
        "/admin/approvals/publish",
        "/admin/approvals/lock",
        "/admin/approvals/bulk-publish",
        "/admin/approvals/bulk-lock"
})
public class ResultApprovalServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(ResultApprovalServlet.class);

    private final ExamDAO examDAO = new ExamDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultService resultService = new ResultService();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        List<Exam> exams = examDAO.findAll();
        request.setAttribute("exams", exams);

        Long examId = resolveExamId(request, exams);
        if (examId != null) {
            request.setAttribute("selectedExamId", examId);
            request.setAttribute("pendingResults", resultDAO.findByExamAndStatus(examId, ResultStatus.SUBMITTED));
            request.setAttribute("approvedResults", resultDAO.findByExamAndStatus(examId, ResultStatus.APPROVED));
            request.setAttribute("publishedResults", resultDAO.findByExamAndStatus(examId, ResultStatus.PUBLISHED));
        }

        readFlashMessages(request);
        request.getRequestDispatcher("/admin/approvals.jsp").forward(request, response);
    }

    /** GET's own {@code examId} param if present and valid; otherwise the first exam in the list, so the page is never a blank prompt-to-choose on a system that already has exams - matching how {@code exams.jsp}'s own list is never gated behind a required filter either. Null only when there are no exams at all yet. */
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);

        if (path.endsWith("/bulk-publish") || path.endsWith("/bulk-lock")) {
            handleBulkAction(request, response, currentAdmin, path.endsWith("/bulk-publish"));
            return;
        }

        Long examId = Long.valueOf(request.getParameter("examId"));

        try {
            if (path.endsWith("/approve")) {
                List<Long> resultIds = parseResultIds(request);
                List<Result> approved = resultService.approveResults(resultIds, currentAdmin);
                activityLogDAO.save(new ActivityLog(currentAdmin, "RESULT_APPROVED",
                        approved.size() + " result(s) approved for exam #" + examId, request.getRemoteAddr()));
                flashSuccessAndRedirect(request, response, examId,
                        approved.size() + " result" + (approved.size() == 1 ? "" : "s") + " approved.");
            } else if (path.endsWith("/publish")) {
                List<ResultSummary> summaries = resultService.publishExam(examId);
                activityLogDAO.save(new ActivityLog(currentAdmin, "RESULT_PUBLISHED",
                        "Exam #" + examId + " published - " + summaries.size() + " student summary/summaries generated",
                        request.getRemoteAddr()));
                notifyPublishedStudents(summaries);
                flashSuccessAndRedirect(request, response, examId,
                        "Results published. " + summaries.size() + " student"
                                + (summaries.size() == 1 ? "'s" : "s'") + " overall percentage, SGPA, and rank are now calculated.");
            } else if (path.endsWith("/lock")) {
                int locked = resultService.lockExam(examId);
                activityLogDAO.save(new ActivityLog(currentAdmin, "RESULT_LOCKED",
                        locked + " result(s) locked for exam #" + examId, request.getRemoteAddr()));
                flashSuccessAndRedirect(request, response, examId,
                        locked + " result" + (locked == 1 ? "" : "s") + " locked. Further changes require the re-evaluation process.");
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
            }
        } catch (BaseApplicationException e) {
            LOGGER.info("Result approval operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/approvals?examId=" + examId);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in result approval", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "result-engine"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/approvals?examId=" + examId);
        }
    }

    private List<Long> parseResultIds(HttpServletRequest request) {
        String[] params = request.getParameterValues("resultIds");
        if (params == null) {
            return List.of();
        }
        return Arrays.stream(params).map(Long::valueOf).toList();
    }

    /**
     * Sec. 22's bulk publish/lock. Each {@code publishExam}/{@code
     * lockExam} call is already its own transaction (one {@code
     * inTransaction} per exam inside {@code ResultService}), so looping
     * them here gives correct semantics for free: exam #3 failing (no
     * approved results yet, say) neither undoes #1/#2's already-committed
     * action nor stops #4/#5 from being attempted. Every exam gets an
     * independent outcome, exactly matching what running the single-exam
     * action several times in a row would do - this is that, looped, not
     * a new operation with new rules.
     */
    private void handleBulkAction(HttpServletRequest request, HttpServletResponse response, User currentAdmin, boolean isPublish)
            throws IOException {
        List<Long> examIds = parseExamIds(request);
        if (examIds.isEmpty()) {
            request.getSession().setAttribute("flashError", "Select at least one examination first.");
            response.sendRedirect(request.getContextPath() + "/admin/approvals");
            return;
        }

        int succeeded = 0;
        List<String> skipped = new ArrayList<>();
        for (Long examId : examIds) {
            String examLabel = examDAO.findById(examId).map(Exam::getName).orElse("#" + examId);
            try {
                if (isPublish) {
                    List<ResultSummary> summaries = resultService.publishExam(examId);
                    activityLogDAO.save(new ActivityLog(currentAdmin, "RESULT_PUBLISHED",
                            "Exam #" + examId + " published (bulk action) - " + summaries.size() + " summary/summaries generated",
                            request.getRemoteAddr()));
                    notifyPublishedStudents(summaries);
                } else {
                    int locked = resultService.lockExam(examId);
                    activityLogDAO.save(new ActivityLog(currentAdmin, "RESULT_LOCKED",
                            locked + " result(s) locked for exam #" + examId + " (bulk action)", request.getRemoteAddr()));
                }
                succeeded++;
            } catch (BaseApplicationException e) {
                skipped.add(examLabel + " (" + e.getMessage() + ")");
            } catch (RuntimeException e) {
                LOGGER.error("Unexpected error during bulk {} of exam {}", isPublish ? "publish" : "lock", examId, e);
                Sentry.captureException(e, scope -> scope.setTag("module", "result-engine"));
                skipped.add(examLabel + " (an unexpected error occurred)");
            }
        }

        String action = isPublish ? "published" : "locked";
        StringBuilder message = new StringBuilder(succeeded + " of " + examIds.size() + " exam(s) " + action + ".");
        if (!skipped.isEmpty()) {
            message.append(" Skipped: ").append(String.join("; ", skipped));
        }
        request.getSession().setAttribute(succeeded > 0 ? "flashSuccess" : "flashError", message.toString());
        response.sendRedirect(request.getContextPath() + "/admin/approvals");
    }

    /**
     * Sec. 23's "Result published" trigger - one notification per student
     * in the newly-published summaries. Isolated per student and never
     * allowed to propagate: {@code resultService.publishExam} has already
     * committed by the time this runs, so a notification failure (a
     * genuine DB error, say) must not read back to the admin as "the
     * publish failed" when it didn't.
     */
    private void notifyPublishedStudents(List<ResultSummary> summaries) {
        for (ResultSummary summary : summaries) {
            try {
                notificationService.notifyResultPublished(summary.getStudent(), summary.getExam());
            } catch (RuntimeException e) {
                LOGGER.error("Could not create a Result Published notification for student {} / exam {}.",
                        summary.getStudent().getId(), summary.getExam().getId(), e);
            }
        }
    }

    private List<Long> parseExamIds(HttpServletRequest request) {
        String[] params = request.getParameterValues("examIds");
        if (params == null) {
            return List.of();
        }
        return Arrays.stream(params).map(Long::valueOf).toList();
    }

    private void flashSuccessAndRedirect(HttpServletRequest request, HttpServletResponse response, Long examId, String message)
            throws IOException {
        request.getSession().setAttribute("flashSuccess", message);
        response.sendRedirect(request.getContextPath() + "/admin/approvals?examId=" + examId);
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
}
