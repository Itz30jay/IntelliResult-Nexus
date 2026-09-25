package com.intelliresult.nexus.service;

import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.DataImportException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.UserCreateRequest;
import com.intelliresult.nexus.util.ExcelUtil;
import com.intelliresult.nexus.util.ValidationUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sec. 21's "Support importing Students", built all-or-nothing per {@link
 * DataImportException}'s own pre-existing Javadoc (Phase 1): every row is
 * validated first, with zero writes, and only if every row passes does
 * this class actually create anything - never a file where some rows
 * silently succeeded and others didn't. Course and section are chosen
 * once on the upload form, not read per row - Sec. 21's own phrasing
 * ("for selected... Class / Section") and {@code UserCreateRequest}'s
 * shape (a single {@code courseId}/{@code sectionId}, not a per-row one)
 * already establish that.
 * <p>
 * Reuses {@code UserService.createUser} for the actual account creation
 * (password generation, User + Student rows, activity log) rather than
 * duplicating it - only the read-only pre-checks that mirror {@code
 * createUser}'s own validation are repeated here, so that a file which
 * passes this class's validation pass is guaranteed (short of a genuine
 * concurrent write from another admin session, an accepted, undefended
 * edge case for a single-admin bulk operation) to succeed when {@code
 * createUser} is actually called.
 */
public class StudentImportService {

    private static final Logger LOGGER = LogManager.getLogger(StudentImportService.class);

    // Column order matches exportTemplate's own header order in
    // StudentImportServlet exactly - the same file this method reads is
    // the file that method produces.
    private static final int COL_ROLL_NO = 0;
    private static final int COL_FULL_NAME = 1;
    private static final int COL_EMAIL = 2;
    private static final int COL_PHONE = 3;

    private final UserService userService = new UserService();
    private final UserDAO userDAO = new UserDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();

    /**
     * @return the number of students created - equal to every data row in
     *         the file, since a partial count is never possible here.
     * @throws ResourceNotFoundException if {@code courseId}/{@code sectionId} don't resolve.
     * @throws DataImportException       if any row fails validation - lists every failing row, not just the first.
     * @throws ValidationException       if the file has no data rows at all.
     */
    public int importStudents(InputStream file, Long courseId, Long sectionId, Long adminId) throws IOException {
        courseDAO.findById(courseId).orElseThrow(() -> new ResourceNotFoundException("Selected course not found."));
        if (sectionId != null) {
            sectionDAO.findById(sectionId).orElseThrow(() -> new ResourceNotFoundException("Selected section not found."));
        }

        List<UserCreateRequest> candidates = new ArrayList<>();
        List<String> rowErrors = new ArrayList<>();
        Set<String> seenRollNos = new HashSet<>();
        Set<String> seenEmails = new HashSet<>();

        try (XSSFWorkbook workbook = ExcelUtil.openWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) { // row 0 is the header
                Row row = sheet.getRow(rowIdx);
                String rollNo = ExcelUtil.readCellAsText(row, COL_ROLL_NO);
                String fullName = ExcelUtil.readCellAsText(row, COL_FULL_NAME);
                String email = ExcelUtil.readCellAsText(row, COL_EMAIL);
                String phone = ExcelUtil.readCellAsText(row, COL_PHONE);
                if (rollNo == null && fullName == null && email == null && phone == null) {
                    continue; // fully blank row - a common trailing spreadsheet artifact, not a data row
                }

                // rowIdx+1: POI's 0-based row index -> the row number Excel
                // itself shows a person looking at the same file (Excel is
                // also 1-based, and row 0 here is that file's header row).
                String label = "Row " + (rowIdx + 1) + (rollNo != null ? " (" + rollNo + ")" : "");

                if (rollNo == null || fullName == null || email == null) {
                    rowErrors.add(label + ": Roll No, Full Name, and Email are all required.");
                } else if (!ValidationUtil.isValidEmail(email)) {
                    rowErrors.add(label + ": '" + email + "' is not a valid email address.");
                } else if (!seenRollNos.add(rollNo)) {
                    rowErrors.add(label + ": Roll number is duplicated elsewhere in this file.");
                } else if (!seenEmails.add(email.toLowerCase())) {
                    rowErrors.add(label + ": Email is duplicated elsewhere in this file.");
                } else if (studentDAO.existsByRollNo(rollNo)) {
                    rowErrors.add(label + ": A student with this roll number already exists.");
                } else if (userDAO.existsByEmail(email)) {
                    rowErrors.add(label + ": A user with this email already exists.");
                } else {
                    candidates.add(new UserCreateRequest(
                            UserRole.STUDENT, email, fullName, phone));
                }
            }
        }

        if (!rowErrors.isEmpty()) {
            throw new DataImportException(
                    rowErrors.size() + " of " + (candidates.size() + rowErrors.size())
                            + " row(s) failed validation. No students were imported - fix the rows below and re-upload.",
                    rowErrors);
        }
        if (candidates.isEmpty()) {
            throw new ValidationException("The uploaded file has no data rows.");
        }

        for (UserCreateRequest req : candidates) {
            userService.createUser(req, adminId);
        }
        LOGGER.info("Bulk student import: {} student(s) created for course {} (admin {}).",
                candidates.size(), courseId, adminId);
        return candidates.size();
    }
}
