package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.SemesterDAO;
import com.intelliresult.nexus.dao.SemesterDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ExamType;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.ExamService;
import com.intelliresult.nexus.service.dto.ExamRequest;
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
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One servlet per resource, same shape as every other Phase 5 admin
 * controller (SectionServlet, SubjectServlet, ...). The one addition beyond
 * that established shape is a "flashSuccess" companion to the existing
 * "flashError" session pattern: Sec. 45 wants explicit positive confirmation
 * ("Exam advanced to Scheduled.") for a workflow action, not just a silent
 * redirect back to a list the admin has to visually diff themselves. Earlier
 * Phase 5 controllers don't carry flashSuccess - not a gap being fixed here,
 * just a lower-stakes surface (editing a department's name) where a
 * re-rendered list already speaks for itself.
 */
@WebServlet(name = "ExamServlet", urlPatterns = {
        "/admin/exams",
        "/admin/exams/new",
        "/admin/exams/edit",
        "/admin/exams/delete",
        "/admin/exams/restore",
        "/admin/exams/advance-status"
})
public class ExamServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(ExamServlet.class);
    private final ExamService examService = new ExamService();
    private final ExamDAO examDAO = new ExamDAOImpl();
    private final SemesterDAO semesterDAO = new SemesterDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();

        if (path.endsWith("/new")) {
            request.setAttribute("semesters", semesterDAO.findAll());
            request.setAttribute("examTypes", ExamType.values());
            request.getRequestDispatcher("/admin/exam-form.jsp").forward(request, response);
            return;
        }
        if (path.endsWith("/edit")) {
            Exam exam = examDAO.findById(Long.valueOf(request.getParameter("id"))).orElse(null);
            if (exam == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("editingExam", exam);
            request.getRequestDispatcher("/admin/exam-form.jsp").forward(request, response);
            return;
        }

        request.setAttribute("exams", examDAO.findAll());
        request.setAttribute("deletedExams", examDAO.findDeleted());
        readFlashMessages(request);
        request.getRequestDispatcher("/admin/exams.jsp").forward(request, response);
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

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);

        try {
            if (path.endsWith("/delete")) {
                examService.softDelete(parseId(request), currentAdmin.getId());
                flashSuccessAndRedirect(request, response, "Exam moved to the recycle bin.");
            } else if (path.endsWith("/restore")) {
                examService.restore(parseId(request));
                flashSuccessAndRedirect(request, response, "Exam restored.");
            } else if (path.endsWith("/advance-status")) {
                Exam updated = examService.advanceStatus(parseId(request), currentAdmin.getId());
                flashSuccessAndRedirect(request, response, "Exam advanced to " + updated.getStatus().name() + ".");
            } else {
                String idParam = request.getParameter("id");
                if (idParam == null || idParam.isBlank()) {
                    examService.createExam(parseCreateRequest(request), currentAdmin.getId());
                    flashSuccessAndRedirect(request, response, "Exam created.");
                } else {
                    examService.updateExam(Long.valueOf(idParam), request.getParameter("name"),
                            parseDateTime(request.getParameter("startTime")), parseDateTime(request.getParameter("endTime")),
                            parseInt(request.getParameter("attemptLimit")), parseDecimal(request.getParameter("defaultMaxMarks")));
                    flashSuccessAndRedirect(request, response, "Exam updated.");
                }
            }
        } catch (ValidationException e) {
            request.setAttribute("semesters", semesterDAO.findAll());
            request.setAttribute("examTypes", ExamType.values());
            String idParam = request.getParameter("id");
            if (idParam != null && !idParam.isBlank()) {
                examDAO.findById(Long.valueOf(idParam)).ifPresent(ex -> request.setAttribute("editingExam", ex));
            }
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/admin/exam-form.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            LOGGER.info("Exam operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/exams");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in exam management", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "exam-management"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/exams");
        }
    }

    private void flashSuccessAndRedirect(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        request.getSession().setAttribute("flashSuccess", message);
        response.sendRedirect(request.getContextPath() + "/admin/exams");
    }

    private Long parseId(HttpServletRequest request) {
        return Long.valueOf(request.getParameter("id"));
    }

    private ExamRequest parseCreateRequest(HttpServletRequest request) {
        ExamType examType = null;
        String examTypeParam = request.getParameter("examType");
        if (examTypeParam != null && !examTypeParam.isBlank()) {
            examType = ExamType.valueOf(examTypeParam);
        }
        Long semesterId = null;
        String semesterIdParam = request.getParameter("semesterId");
        if (semesterIdParam != null && !semesterIdParam.isBlank()) {
            semesterId = Long.valueOf(semesterIdParam);
        }
        return new ExamRequest(request.getParameter("name"), examType, semesterId,
                parseDateTime(request.getParameter("startTime")), parseDateTime(request.getParameter("endTime")),
                parseInt(request.getParameter("attemptLimit")), parseDecimal(request.getParameter("defaultMaxMarks")));
    }

    /** Plain {@code LocalDateTime.parse}, no custom formatter - the ISO {@code yyyy-MM-dd'T'HH:mm} this expects is exactly what an HTML {@code <input type="datetime-local">} submits. */
    private LocalDateTime parseDateTime(String value) {
        return (value == null || value.isBlank()) ? null : LocalDateTime.parse(value);
    }

    /** @return null on a blank/missing field (caught by ExamService's own "attempt limit is required" check) rather than defaulting silently here, so a malformed submission is reported the same way any other missing required field is. */
    private Integer parseInt(String value) {
        return (value == null || value.isBlank()) ? null : Integer.valueOf(value.trim());
    }

    private BigDecimal parseDecimal(String value) {
        return (value == null || value.isBlank()) ? null : new BigDecimal(value);
    }
}
