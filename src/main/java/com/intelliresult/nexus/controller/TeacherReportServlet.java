package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.service.MarksEntryService;
import com.intelliresult.nexus.service.ReportService;
import com.intelliresult.nexus.service.SystemSettingsService;
import com.intelliresult.nexus.service.dto.ReportData;
import com.intelliresult.nexus.util.AppConstants;
import com.intelliresult.nexus.util.DateUtil;
import com.intelliresult.nexus.util.ExcelUtil;
import com.intelliresult.nexus.util.PDFUtil;
import com.lowagie.text.DocumentException;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Sec. 68's "Reports: Teacher Limited" - Class Result and Subject
 * Analysis only (the two report types that make sense at a single
 * subject+section grain), never Topper/Improvement/Exam/Academic Year,
 * which are cross-subject or cross-exam admin views. Authorization is the
 * exact same {@code marksEntryService.requireAssignment} guard {@code
 * MarksImportServlet} (Phase 13) already uses - a teacher reaches this
 * only from their own row on {@code /teacher/marks-entry}, and this
 * servlet re-checks independently regardless of what that page shows.
 * <p>
 * {@code ReportService}/{@code PDFUtil}/{@code ExcelUtil} are exactly the
 * same calls {@code ReportServlet} (Admin) makes - this class differs
 * only in authorization scope and its two-type restriction, not in how a
 * report is built or rendered.
 */
@WebServlet(name = "TeacherReportServlet", urlPatterns = {
        "/teacher/reports/view", "/teacher/reports/pdf", "/teacher/reports/excel"
})
public class TeacherReportServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(TeacherReportServlet.class);

    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final MarksEntryService marksEntryService = new MarksEntryService();
    private final ReportService reportService = new ReportService();
    private final SystemSettingsService systemSettingsService = new SystemSettingsService();
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
        boolean isSubjectAnalysis = "SUBJECT_ANALYSIS".equals(request.getParameter("type"));

        try {
            marksEntryService.requireAssignment(teacher.getId(), subjectId, sectionId);

            ReportData data = isSubjectAnalysis
                    ? reportService.subjectAnalysisReport(examId, subjectId)
                    : reportService.classResultReport(examId, sectionId);

            String path = request.getServletPath();
            if (path.endsWith("/pdf")) {
                byte[] pdf = PDFUtil.buildTabularReport(data, systemSettingsService.getSettings(), LocalDateTime.now());
                logAndStream(request, response, currentUser, data, pdf, "application/pdf", "pdf");
            } else if (path.endsWith("/excel")) {
                byte[] xlsx = ExcelUtil.writeXlsx(data.title(), data.columnHeaders(), data.rows());
                logAndStream(request, response, currentUser, data,
                        xlsx, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx");
            } else {
                request.setAttribute("report", data);
                request.setAttribute("settings", systemSettingsService.getSettings());
                request.setAttribute("generatedAtDisplay", DateUtil.formatForDisplay(LocalDateTime.now()));
                request.getRequestDispatcher("/common/report-view.jsp").forward(request, response);
            }
        } catch (BaseApplicationException e) {
            request.getSession().setAttribute("flashError", "Could not generate report: " + e.getMessage());
            response.sendRedirect(request.getContextPath() + "/teacher/marks-entry");
        } catch (DocumentException e) {
            LOGGER.error("PDF rendering failed for a teacher report.", e);
            request.getSession().setAttribute("flashError", "The report could not be generated right now. Please try again.");
            response.sendRedirect(request.getContextPath() + "/teacher/marks-entry");
        }
    }

    private void logAndStream(HttpServletRequest request, HttpServletResponse response, User currentUser,
                               ReportData data, byte[] content, String contentType, String extension) throws IOException {
        activityLogDAO.save(new ActivityLog(currentUser, "REPORT_GENERATED",
                data.title() + " - " + data.subtitle() + " (" + extension.toUpperCase() + ")", request.getRemoteAddr()));

        String filename = data.title().toLowerCase().replaceAll("[^a-z0-9]+", "_")
                + "_" + DateUtil.formatForFilename(LocalDateTime.now()) + "." + extension;
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
        response.getOutputStream().flush();
    }

    private Long parseLongOrNull(String value) {
        return (value == null || value.isBlank()) ? null : Long.valueOf(value);
    }
}
