package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.config.AppConfig;
import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.DataImportException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.StudentImportService;
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
 * Sec. 21/22's Admin bulk student import, plus the reciprocal Excel export
 * (the current roster, or an empty header-only file when nothing matches
 * the filter yet - either way, exactly the columns {@link
 * StudentImportService} reads back). Kept as one servlet, two urlPatterns
 * - the same one-class-several-related-actions shape {@code
 * ResultApprovalServlet} already established, since import and export are
 * two sides of a single feature reading/writing the identical column
 * layout, not two independent ones.
 * <p>
 * Lives under {@code /admin/users/...}, not {@code /admin/students/...} -
 * {@code StudentListServlet}'s own class Javadoc already establishes that
 * account creation is a {@code /admin/users} concern and {@code
 * /admin/students} is a read-only academic-roster view of the same data;
 * bulk creation belongs with the single-row creation it is the bulk form
 * of.
 */
@WebServlet(name = "StudentImportServlet", urlPatterns = {"/admin/users/import", "/admin/users/export"})
@MultipartConfig(maxFileSize = 20_000_000) // hard ceiling; validateUpload() enforces the real, configurable Sec. 65 limit below this
public class StudentImportServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(StudentImportServlet.class);
    private static final String CONTENT_TYPE_XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final StudentImportService studentImportService = new StudentImportService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (request.getServletPath().endsWith("/export")) {
            handleExport(request, response);
            return;
        }
        loadDropdownData(request);
        request.getRequestDispatcher("/admin/student-import.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        loadDropdownData(request);

        Long courseId = parseLongOrNull(request.getParameter("courseId"));
        Long sectionId = parseLongOrNull(request.getParameter("sectionId"));
        request.setAttribute("selectedCourseId", courseId);
        request.setAttribute("selectedSectionId", sectionId);

        try {
            Part filePart = request.getPart("file");
            if (filePart == null || filePart.getSize() == 0) {
                throw new ValidationException("Please choose a file to upload.");
            }
            validateUpload(filePart);

            int created;
            try (InputStream in = filePart.getInputStream()) {
                created = studentImportService.importStudents(in, courseId, sectionId, currentAdmin.getId());
            }
            request.setAttribute("importSuccessCount", created);
        } catch (DataImportException e) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("rowErrors", e.getRowErrors());
        } catch (BaseApplicationException e) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
        }

        request.getRequestDispatcher("/admin/student-import.jsp").forward(request, response);
    }

    /** Filtered by the same courseId/sectionId query params the picker form uses, so "export what I'm about to overwrite" and "download a blank template for a fresh course" are the same action with different filters, not two features. */
    private void handleExport(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Long courseId = parseLongOrNull(request.getParameter("courseId"));
        Long sectionId = parseLongOrNull(request.getParameter("sectionId"));

        List<Student> students;
        if (sectionId != null) {
            students = studentDAO.findBySection(sectionId);
        } else if (courseId != null) {
            students = studentDAO.findByCourse(courseId);
        } else {
            students = List.of();
        }

        List<Object[]> rows = new ArrayList<>();
        for (Student s : students) {
            rows.add(new Object[]{
                    s.getRollNo(), s.getUser().getFullName(), s.getUser().getEmail(), s.getUser().getPhone(),
                    s.getCourse().getName(), s.getCurrentSection() != null ? s.getCurrentSection().getName() : ""
            });
        }

        // Roll No/Full Name/Email/Phone are the exact 4 columns
        // StudentImportService reads by index; Course/Section are trailing
        // reference-only columns for a human, never read back on import.
        byte[] xlsx = ExcelUtil.writeXlsx("Students",
                new String[]{"Roll No", "Full Name", "Email", "Phone", "Course", "Section"}, rows);

        String filename = "students_" + DateUtil.formatForFilename(LocalDateTime.now()) + ".xlsx";
        response.setContentType(CONTENT_TYPE_XLSX);
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setContentLength(xlsx.length);
        response.getOutputStream().write(xlsx);
        response.getOutputStream().flush();
    }

    /** Sec. 65's file-upload security: extension, declared content type, and a server-enforced size ceiling read from config (Sec. 64) - checked before ExcelUtil ever opens the stream. */
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
            LOGGER.warn("Rejected student-import upload '{}' with unexpected content type '{}'.", filename, contentType);
            throw new ValidationException("The uploaded file does not appear to be a valid Excel file.");
        }
    }

    private void loadDropdownData(HttpServletRequest request) {
        request.setAttribute("courses", courseDAO.findAll());
        request.setAttribute("sections", sectionDAO.findAll());
    }

    private Long parseLongOrNull(String value) {
        return (value == null || value.isBlank()) ? null : Long.valueOf(value);
    }
}
