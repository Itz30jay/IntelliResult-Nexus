package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.config.AppConfig;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.TeacherSubject;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.DataImportException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.MarksEntryService;
import com.intelliresult.nexus.service.MarksImportService;
import com.intelliresult.nexus.service.dto.MarksEntryRow;
import com.intelliresult.nexus.util.AppConstants;
import com.intelliresult.nexus.util.DateUtil;
import com.intelliresult.nexus.util.ExcelUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Sec. 21/22's Teacher-side bulk marks import for one (exam, subject,
 * section) - the granularity {@code MarksEntryServlet}'s manual grid
 * already works at, reached from the same picker row rather than its own
 * separate selection flow. Export shares this servlet for the same reason
 * {@code StudentImportServlet} does: the exported file's first five
 * columns are exactly what {@link MarksImportService} reads back, so
 * "download the current grid, edit offline, re-upload" is one coherent
 * round trip, not two unrelated features.
 */
@WebServlet(name = "MarksImportServlet", urlPatterns = {"/teacher/marks-entry/import", "/teacher/marks-entry/export"})
@MultipartConfig(maxFileSize = 20_000_000) // hard ceiling; validateUpload() enforces the real, configurable Sec. 65 limit below this
public class MarksImportServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(MarksImportServlet.class);
    private static final String CONTENT_TYPE_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final MarksEntryService marksEntryService = new MarksEntryService();
    private final MarksImportService marksImportService = new MarksImportService();

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
        if (examId == null || subjectId == null || sectionId == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "examId, subjectId, and sectionId are all required.");
            return;
        }

        try {
            TeacherSubject assignment = marksEntryService.requireAssignment(teacher.getId(), subjectId, sectionId);
            Exam exam = marksEntryService.requireViewableExam(examId);

            if (request.getServletPath().endsWith("/export")) {
                handleExport(response, examId, subjectId, sectionId, exam, assignment);
                return;
            }

            request.setAttribute("exam", exam);
            request.setAttribute("assignment", assignment);
            request.getRequestDispatcher("/teacher/marks-import.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/teacher/marks-entry");
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

        Long examId = parseLongOrNull(request.getParameter("examId"));
        Long subjectId = parseLongOrNull(request.getParameter("subjectId"));
        Long sectionId = parseLongOrNull(request.getParameter("sectionId"));
        boolean alsoSubmit = "true".equals(request.getParameter("alsoSubmit"));

        try {
            request.setAttribute("exam", marksEntryService.requireViewableExam(examId));
            request.setAttribute("assignment", marksEntryService.requireAssignment(teacher.getId(), subjectId, sectionId));

            Part filePart = request.getPart("file");
            if (filePart == null || filePart.getSize() == 0) {
                throw new ValidationException("Please choose a file to upload.");
            }
            validateUpload(filePart);

            int count;
            try (InputStream in = filePart.getInputStream()) {
                count = marksImportService.importMarks(in, examId, subjectId, sectionId, teacher.getId(), currentUser, alsoSubmit);
            }
            request.setAttribute("importSuccessCount", count);
            request.setAttribute("wasSubmitted", alsoSubmit);
        } catch (DataImportException e) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("rowErrors", e.getRowErrors());
        } catch (BaseApplicationException e) {
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/teacher/marks-entry");
            return;
        }

        request.getRequestDispatcher("/teacher/marks-import.jsp").forward(request, response);
    }

    /** Reuses MarksEntryService.loadGrid - the identical query the manual grid page itself runs - rather than a second, parallel way of assembling the same roster+marks data. */
    private void handleExport(HttpServletResponse response, Long examId, Long subjectId, Long sectionId,
                               Exam exam, TeacherSubject assignment) throws IOException {
        List<MarksEntryRow> grid = marksEntryService.loadGrid(examId, subjectId, sectionId);

        List<Object[]> rows = new ArrayList<>();
        for (MarksEntryRow row : grid) {
            rows.add(new Object[]{
                    row.student().getRollNo(),
                    row.student().getUser().getFullName(),
                    row.hasExistingResult() ? row.existingResult().getTheoryMarks() : null,
                    row.hasExistingResult() ? row.existingResult().getPracticalMarks() : null,
                    row.hasExistingResult() ? row.existingResult().getInternalMarks() : null
            });
        }

        byte[] xlsx = ExcelUtil.writeXlsx("Marks",
                new String[]{"Roll No", "Student Name", "Theory", "Practical", "Internal"}, rows);

        String safeExamName = exam.getName().replaceAll("[^A-Za-z0-9]+", "_");
        String filename = "marks_" + safeExamName + "_" + assignment.getSubject().getSubjectCode()
                + "_" + DateUtil.formatForFilename(LocalDateTime.now()) + ".xlsx";
        response.setContentType(CONTENT_TYPE_XLSX);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
        response.getOutputStream().flush();
    }

    private void validateUpload(Part filePart) {
        String filename = filePart.getSubmittedFileName();
        if (filename == null || !filename.toLowerCase().endsWith(".xlsx")) {
            throw new ValidationException("Only .xlsx Excel files are supported.");
        }
        long maxBytes = AppConfig.getLong("import.excel.maxFileSizeBytes", 5_242_880L);
        if (filePart.getSize() > maxBytes) {
            throw new ValidationException("File is too large - the maximum is " + (maxBytes / (1024 * 1024)) + "MB.");
        }
        String contentType = filePart.getContentType();
        if (contentType != null
                && !contentType.contains("spreadsheet")
                && !contentType.equals("application/octet-stream")) {
            LOGGER.warn("Rejected marks-import upload '{}' with unexpected content type '{}'.", filename, contentType);
            throw new ValidationException("The uploaded file does not appear to be a valid Excel file.");
        }
    }

    private Long parseLongOrNull(String value) {
        return (value == null || value.isBlank()) ? null : Long.valueOf(value);
    }
}
