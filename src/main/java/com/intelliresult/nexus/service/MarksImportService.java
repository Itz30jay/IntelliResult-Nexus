package com.intelliresult.nexus.service;

import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.AuthorizationException;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.DataImportException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.StudentMarksInput;
import com.intelliresult.nexus.util.ExcelUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Sec. 21's "Support importing... Marks", for one (exam, subject, section)
 * combination chosen on the upload form - the same granularity {@code
 * MarksEntryService}'s manual grid already works at. Two layers of
 * all-or-nothing, both surfaced to the caller as the same {@link
 * DataImportException} type:
 * <ol>
 *   <li><b>Parsing</b> (this class): a roll number that doesn't resolve to
 *       a student in this section, a duplicate roll number within the
 *       file, or a non-numeric marks cell - collected across every row
 *       before anything is handed to {@code MarksEntryService} at all.</li>
 *   <li><b>Marks-range validation</b> ({@code MarksEntryService.saveDraft}/
 *       {@code submitGrid}, Phase 6b, unmodified): negative marks, marks
 *       over a component's configured maximum, or a value supplied for a
 *       component the subject doesn't have. That method's own {@code
 *       ValidationException} is caught here and re-thrown as {@code
 *       DataImportException} purely so the import JSP has one exception
 *       type to render a row report from, never because Phase 6b's
 *       validation itself was insufficient or needed changing.</li>
 * </ol>
 * Authorization ({@code requireAssignment}) and exam-state ({@code
 * requireOpenExam}) failures are deliberately NOT wrapped the same way -
 * those mean "this import cannot happen at all", a different kind of
 * failure than "some rows in an otherwise-valid import are wrong", so they
 * propagate as their own exception types for the servlet to show as a
 * single top-level message instead of a row report.
 */
public class MarksImportService {

    private static final Logger LOGGER = LogManager.getLogger(MarksImportService.class);

    // Column order matches exportTemplate's own header order in
    // MarksImportServlet exactly. Column 1 (Student Name) exists for a
    // human reviewing/editing the file and is never read on import -
    // matching is by roll number alone, the same identifier
    // MarksEntryService's own grid keys everything on.
    private static final int COL_ROLL_NO = 0;
    private static final int COL_THEORY = 2;
    private static final int COL_PRACTICAL = 3;
    private static final int COL_INTERNAL = 4;

    private final MarksEntryService marksEntryService = new MarksEntryService();
    private final StudentDAO studentDAO = new StudentDAOImpl();

    /**
     * @return the number of results saved/submitted.
     * @throws AuthorizationException if the teacher isn't assigned to this subject+section.
     * @throws BusinessRuleException  if the exam isn't currently open for marks entry.
     * @throws DataImportException    if any row fails to parse, or the batch fails Phase 6b's own marks-range validation.
     * @throws ValidationException    if the file has no recognizable data rows.
     */
    public int importMarks(InputStream file, Long examId, Long subjectId, Long sectionId,
                            Long teacherId, User actingUser, boolean alsoSubmit) throws IOException {
        // Same two gates the manual grid enforces before a single mark can
        // be written - checked before the file is even parsed, so an
        // import a teacher isn't allowed to make fails immediately.
        marksEntryService.requireAssignment(teacherId, subjectId, sectionId);
        marksEntryService.requireOpenExam(examId);

        Map<Long, Student> sectionStudents = new HashMap<>();
        for (Student s : studentDAO.findBySection(sectionId)) {
            sectionStudents.put(s.getId(), s);
        }

        List<StudentMarksInput> candidates = new ArrayList<>();
        List<String> rowErrors = new ArrayList<>();
        Set<Long> seenStudentIds = new HashSet<>();

        try (XSSFWorkbook workbook = ExcelUtil.openWorkbook(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            for (int rowIdx = 1; rowIdx <= sheet.getLastRowNum(); rowIdx++) { // row 0 is the header
                Row row = sheet.getRow(rowIdx);
                String rollNo = ExcelUtil.readCellAsText(row, COL_ROLL_NO);
                if (rollNo == null) {
                    continue; // blank trailing row - not a data row
                }
                String label = "Row " + (rowIdx + 1) + " (" + rollNo + ")";

                Student student = studentDAO.findByRollNo(rollNo).orElse(null);
                if (student == null) {
                    rowErrors.add(label + ": Roll number not found.");
                } else if (!sectionStudents.containsKey(student.getId())) {
                    rowErrors.add(label + ": This student is not enrolled in the selected section.");
                } else if (!seenStudentIds.add(student.getId())) {
                    rowErrors.add(label + ": This roll number appears more than once in the file.");
                } else {
                    try {
                        BigDecimal theory = ExcelUtil.readCellAsDecimal(row, COL_THEORY);
                        BigDecimal practical = ExcelUtil.readCellAsDecimal(row, COL_PRACTICAL);
                        BigDecimal internal = ExcelUtil.readCellAsDecimal(row, COL_INTERNAL);
                        candidates.add(new StudentMarksInput(student.getId(), theory, practical, internal));
                    } catch (NumberFormatException e) {
                        rowErrors.add(label + ": Theory, Practical, and Internal must be plain numbers.");
                    }
                }
            }
        }

        if (!rowErrors.isEmpty()) {
            throw new DataImportException(
                    rowErrors.size() + " row(s) could not be read. No marks were imported - fix the rows below and re-upload.",
                    rowErrors);
        }
        if (candidates.isEmpty()) {
            throw new ValidationException("The uploaded file has no recognizable data rows.");
        }

        try {
            int result = alsoSubmit
                    ? marksEntryService.submitGrid(examId, subjectId, sectionId, teacherId, actingUser, candidates)
                    : marksEntryService.saveDraft(examId, subjectId, sectionId, teacherId, actingUser, candidates);
            LOGGER.info("Bulk marks import: {} row(s) {} for exam {} / subject {} / section {} (teacher {}).",
                    candidates.size(), alsoSubmit ? "submitted" : "saved as draft", examId, subjectId, sectionId, teacherId);
            return result;
        } catch (ValidationException e) {
            List<String> details = new ArrayList<>();
            e.getFieldErrors().forEach((who, why) -> details.add(who + ": " + why));
            throw new DataImportException("Please correct the marks below - nothing was saved.", details);
        }
    }
}
