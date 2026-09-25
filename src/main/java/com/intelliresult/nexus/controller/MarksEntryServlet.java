package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.TeacherSubject;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.AuthorizationException;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.MarksEntryService;
import com.intelliresult.nexus.service.dto.MarksEntryGroup;
import com.intelliresult.nexus.service.dto.StudentMarksInput;
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
import java.util.ArrayList;
import java.util.List;

/**
 * One URL, two GET shapes: no examId/subjectId/sectionId shows the picker
 * (every assignment x open exam combination); all three present shows the
 * grid for that specific combination, re-validated server-side
 * (MarksEntryService.requireAssignment/requireOpenExam) on every request -
 * never trusted just because the picker only linked to combinations the
 * teacher was actually allowed to see. On ValidationException, the grid is
 * re-forwarded to (not redirected) so the request's own POST parameters are
 * still live for ${param.xxx} lookups to redisplay exactly what was typed -
 * a redirect would lose that.
 */
@WebServlet(name = "MarksEntryServlet", urlPatterns = {"/teacher/marks-entry"})
public class MarksEntryServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(MarksEntryServlet.class);
    private final MarksEntryService marksEntryService = new MarksEntryService();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

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

        Long examId = parseLongOrNull(request.getParameter("examId"));
        Long subjectId = parseLongOrNull(request.getParameter("subjectId"));
        Long sectionId = parseLongOrNull(request.getParameter("sectionId"));

        if (examId != null && subjectId != null && sectionId != null) {
            try {
                TeacherSubject assignment = marksEntryService.requireAssignment(teacher.getId(), subjectId, sectionId);
                Exam exam = marksEntryService.requireViewableExam(examId);
                request.setAttribute("exam", exam);
                request.setAttribute("assignment", assignment);
                request.setAttribute("rows", marksEntryService.loadGrid(examId, subjectId, sectionId));
                request.setAttribute("examOpenForEntry", exam.getStatus().isOpenForMarksEntry());
                readFlashMessages(request);
                request.getRequestDispatcher("/teacher/marks-entry-grid.jsp").forward(request, response);
                return;
            } catch (AuthorizationException e) {
                logUnauthorizedAttempt(currentUser, request, e);
                request.getSession().setAttribute("flashError", e.getMessage());
                response.sendRedirect(request.getContextPath() + "/teacher/marks-entry");
                return;
            } catch (BaseApplicationException e) {
                request.getSession().setAttribute("flashError", e.getMessage());
                response.sendRedirect(request.getContextPath() + "/teacher/marks-entry");
                return;
            }
        }

        List<MarksEntryGroup> groups = marksEntryService.listGroupsForTeacher(teacher.getId()).stream()
                .filter(g -> g.exam().getStatus().isOpenForMarksEntry())
                .toList();
        request.setAttribute("groups", groups);
        readFlashMessages(request);
        request.getRequestDispatcher("/teacher/marks-entry-select.jsp").forward(request, response);
    }

    private void logUnauthorizedAttempt(User currentUser, HttpServletRequest request, AuthorizationException e) {
        LOGGER.warn("Teacher {} attempted marks entry outside their assignments: {}", currentUser.getEmail(), e.getMessage());
        // AuthorizationException's own class Javadoc requires this: "always
        // logged to the audit trail... a security-relevant event worth
        // recording, not just a UI inconvenience to smooth over."
        activityLogDAO.save(new ActivityLog(currentUser, "UNAUTHORIZED_MARKS_ENTRY_ATTEMPT", e.getMessage(), request.getRemoteAddr()));
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
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Teacher teacher = teacherDAO.findByUserId(currentUser.getId()).orElse(null);
        if (teacher == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        Long examId = Long.valueOf(request.getParameter("examId"));
        Long subjectId = Long.valueOf(request.getParameter("subjectId"));
        Long sectionId = Long.valueOf(request.getParameter("sectionId"));
        boolean isSubmit = "submit".equals(request.getParameter("action"));

        try {
            List<StudentMarksInput> inputs = parseInputs(request, sectionId);

            int count;
            String message;
            String activityAction;
            if (isSubmit) {
                count = marksEntryService.submitGrid(examId, subjectId, sectionId, teacher.getId(), currentUser, inputs);
                message = "Submitted " + count + " result" + (count == 1 ? "" : "s") + " for administrative review.";
                activityAction = "RESULTS_SUBMITTED";
            } else {
                count = marksEntryService.saveDraft(examId, subjectId, sectionId, teacher.getId(), currentUser, inputs);
                message = "Saved " + count + " row" + (count == 1 ? "" : "s") + " as draft.";
                activityAction = "MARKS_ENTRY_SAVED";
            }
            activityLogDAO.save(new ActivityLog(currentUser, activityAction,
                    "Exam #" + examId + ", subject #" + subjectId + ", section #" + sectionId + ": " + count + " row(s)",
                    request.getRemoteAddr()));

            request.getSession().setAttribute("flashSuccess", message);
            response.sendRedirect(request.getContextPath() + "/teacher/marks-entry?examId=" + examId
                    + "&subjectId=" + subjectId + "&sectionId=" + sectionId);
        } catch (ValidationException e) {
            reRenderGridWithError(request, response, teacher, examId, subjectId, sectionId, e);
        } catch (AuthorizationException e) {
            logUnauthorizedAttempt(currentUser, request, e);
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/teacher/marks-entry");
        } catch (BaseApplicationException e) {
            LOGGER.info("Marks entry operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/teacher/marks-entry");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in marks entry", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "marks-entry"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/teacher/marks-entry");
        }
    }

    /** Forwards, not redirects, back to the grid: the still-live request's own POST parameters are what let the grid redisplay exactly what the teacher typed via ${param.xxx} - a redirect would issue a fresh GET with none of that. */
    private void reRenderGridWithError(HttpServletRequest request, HttpServletResponse response, Teacher teacher,
                                        Long examId, Long subjectId, Long sectionId, ValidationException e)
            throws ServletException, IOException {
        try {
            TeacherSubject assignment = marksEntryService.requireAssignment(teacher.getId(), subjectId, sectionId);
            Exam exam = marksEntryService.requireOpenExam(examId);
            request.setAttribute("exam", exam);
            request.setAttribute("assignment", assignment);
            request.setAttribute("rows", marksEntryService.loadGrid(examId, subjectId, sectionId));
            request.setAttribute("examOpenForEntry", true);
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/teacher/marks-entry-grid.jsp").forward(request, response);
        } catch (BaseApplicationException stateChanged) {
            // Assignment or exam status changed since this form was loaded -
            // fall back to the picker with a clear message rather than force
            // a grid render that would immediately fail again.
            request.getSession().setAttribute("flashError", stateChanged.getMessage());
            response.sendRedirect(request.getContextPath() + "/teacher/marks-entry");
        }
    }

    private List<StudentMarksInput> parseInputs(HttpServletRequest request, Long sectionId) {
        List<StudentMarksInput> inputs = new ArrayList<>();
        for (Student student : studentDAO.findBySection(sectionId)) {
            BigDecimal theory = parseDecimal(request.getParameter("theoryMarks_" + student.getId()));
            BigDecimal practical = parseDecimal(request.getParameter("practicalMarks_" + student.getId()));
            BigDecimal internal = parseDecimal(request.getParameter("internalMarks_" + student.getId()));
            inputs.add(new StudentMarksInput(student.getId(), theory, practical, internal));
        }
        return inputs;
    }

    private BigDecimal parseDecimal(String value) {
        return (value == null || value.isBlank()) ? null : new BigDecimal(value);
    }

    private Long parseLongOrNull(String value) {
        return (value == null || value.isBlank()) ? null : Long.valueOf(value);
    }
}
