package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.AcademicYearDAO;
import com.intelliresult.nexus.dao.AcademicYearDAOImpl;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.SystemSetting;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.ReportService;
import com.intelliresult.nexus.service.ReportType;
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
 * Sec. 33/34's Admin reporting suite - one servlet across four actions
 * (form, print view, PDF, Excel), matching {@code ResultApprovalServlet}'s
 * own several-related-actions shape. Every report type is a thin
 * dispatch to {@code ReportService}; this class's only real job is
 * turning request parameters into a validated call and one of {@link
 * ReportData} into whichever output format was asked for.
 * <p>
 * Both the print view and PDF/Excel exports share exactly one {@link
 * #buildReportData} call per request - a report is computed once, not
 * once per renderer, even though a person could reasonably view it on
 * screen and then also download it as a separate follow-up request.
 */
@WebServlet(name = "ReportServlet", urlPatterns = {
        "/admin/reports", "/admin/reports/view", "/admin/reports/pdf", "/admin/reports/excel"
})
public class ReportServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(ReportServlet.class);
    private static final int TOPPER_LIMIT = 10;

    private final ReportService reportService = new ReportService();
    private final ExamDAO examDAO = new ExamDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final SubjectDAO subjectDAO = new SubjectDAOImpl();
    private final AcademicYearDAO academicYearDAO = new AcademicYearDAOImpl();
    private final SystemSettingsService systemSettingsService = new SystemSettingsService();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        if (path.endsWith("/view")) {
            renderPrintView(request, response);
        } else if (path.endsWith("/pdf")) {
            streamPdf(request, response);
        } else if (path.endsWith("/excel")) {
            streamExcel(request, response);
        } else {
            loadFormData(request);
            request.getRequestDispatcher("/admin/reports.jsp").forward(request, response);
        }
    }

    private void loadFormData(HttpServletRequest request) {
        request.setAttribute("exams", examDAO.findAll());
        request.setAttribute("sections", sectionDAO.findAll());
        request.setAttribute("subjects", subjectDAO.findAll());
        request.setAttribute("academicYears", academicYearDAO.findAll());
    }

    private void renderPrintView(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            request.setAttribute("report", buildReportData(request));
            request.setAttribute("settings", systemSettingsService.getSettings());
            request.setAttribute("generatedAtDisplay", DateUtil.formatForDisplay(LocalDateTime.now()));
            request.getRequestDispatcher("/common/report-view.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            request.getSession().setAttribute("flashError", "Could not generate report: " + e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/reports");
        }
    }

    private void streamPdf(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        try {
            ReportData data = buildReportData(request);
            SystemSetting settings = systemSettingsService.getSettings();
            byte[] pdf = PDFUtil.buildTabularReport(data, settings, LocalDateTime.now());

            activityLogDAO.save(new ActivityLog(currentUser, "REPORT_GENERATED",
                    data.title() + " - " + data.subtitle() + " (PDF)", request.getRemoteAddr()));

            streamFile(response, pdf, "application/pdf", filenameFor(data, "pdf"));
        } catch (BaseApplicationException e) {
            request.getSession().setAttribute("flashError", "Could not generate report: " + e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/reports");
        } catch (DocumentException e) {
            LOGGER.error("PDF rendering failed for an admin report.", e);
            request.getSession().setAttribute("flashError", "The report could not be generated right now. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/reports");
        }
    }

    private void streamExcel(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        try {
            ReportData data = buildReportData(request);
            byte[] xlsx = ExcelUtil.writeXlsx(data.title(), data.columnHeaders(), data.rows());

            activityLogDAO.save(new ActivityLog(currentUser, "REPORT_GENERATED",
                    data.title() + " - " + data.subtitle() + " (Excel)", request.getRemoteAddr()));

            streamFile(response, xlsx,
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", filenameFor(data, "xlsx"));
        } catch (BaseApplicationException e) {
            request.getSession().setAttribute("flashError", "Could not generate report: " + e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/reports");
        }
    }

    /**
     * @throws ValidationException if the report type is missing/unknown or a required parameter for that type is absent.
     */
    private ReportData buildReportData(HttpServletRequest request) {
        String typeParam = request.getParameter("type");
        ReportType type;
        try {
            type = ReportType.valueOf(typeParam);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ValidationException("Please select a valid report type.");
        }

        Long examId = parseLongOrNull(request.getParameter("examId"));
        Long sectionId = parseLongOrNull(request.getParameter("sectionId"));
        Long subjectId = parseLongOrNull(request.getParameter("subjectId"));
        Long academicYearId = parseLongOrNull(request.getParameter("academicYearId"));

        return switch (type) {
            case CLASS_RESULT -> {
                requireParam(examId, "an examination");
                requireParam(sectionId, "a section");
                yield reportService.classResultReport(examId, sectionId);
            }
            case TOPPER -> {
                requireParam(examId, "an examination");
                yield reportService.topperReport(examId, sectionId, TOPPER_LIMIT);
            }
            case IMPROVEMENT -> {
                requireParam(examId, "an examination");
                yield reportService.improvementReport(examId, sectionId);
            }
            case SUBJECT_ANALYSIS -> {
                requireParam(examId, "an examination");
                requireParam(subjectId, "a subject");
                yield reportService.subjectAnalysisReport(examId, subjectId);
            }
            case EXAM_SUMMARY -> {
                requireParam(examId, "an examination");
                yield reportService.examSummaryReport(examId);
            }
            case ACADEMIC_YEAR -> {
                requireParam(academicYearId, "an academic year");
                yield reportService.academicYearReport(academicYearId);
            }
        };
    }

    private void requireParam(Long value, String label) {
        if (value == null) {
            throw new ValidationException("Please select " + label + ".");
        }
    }

    private void streamFile(HttpServletResponse response, byte[] content, String contentType, String filename)
            throws IOException {
        response.setContentType(contentType);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLength(content.length);
        response.getOutputStream().write(content);
        response.getOutputStream().flush();
    }

    private String filenameFor(ReportData data, String extension) {
        String safeTitle = data.title().toLowerCase().replaceAll("[^a-z0-9]+", "_");
        return safeTitle + "_" + DateUtil.formatForFilename(LocalDateTime.now()) + "." + extension;
    }

    private Long parseLongOrNull(String value) {
        return (value == null || value.isBlank()) ? null : Long.valueOf(value);
    }
}
