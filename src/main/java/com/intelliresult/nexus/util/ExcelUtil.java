package com.intelliresult.nexus.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;

/**
 * Sec. 40's still-unbuilt utility, arriving with Phase 13 as its own
 * Javadoc note already promised (see README's Project Structure entry).
 * Deliberately thin and entity-free, matching {@code PDFUtil}/{@code
 * QRCodeUtil} from Phase 12: this class only knows how to move data
 * between a {@link Row}/{@link Cell} and a plain Java value. Deciding
 * which columns exist, resolving a roll number to a {@code Student}, and
 * turning a bad cell into a reportable row error are all
 * {@code StudentImportService}/{@code MarksImportService} concerns, not
 * this one's - the same Util/Service split Phase 12 established.
 * <p>
 * Reads/writes {@code .xlsx} only (XSSF, via {@code poi-ooxml}) - Sec. 21
 * never asks for legacy {@code .xls} support, and supporting both formats
 * doubles the cell-type edge cases below for a format this project's own
 * templates will never actually produce.
 */
public final class ExcelUtil {

    private ExcelUtil() {
        // Static-only utility class.
    }

    /** Opens an uploaded file as a workbook. Caller is responsible for closing it (try-with-resources) once done reading. */
    public static XSSFWorkbook openWorkbook(InputStream in) throws IOException {
        return (XSSFWorkbook) WorkbookFactory.create(in);
    }

    /**
     * The cell's text, trimmed, or {@code null} if missing/blank. Routed
     * through {@link DataFormatter} rather than {@code cell.getStringCellValue()}
     * so a roll number Excel auto-detected as numeric (a spreadsheet
     * habit no import form can prevent) still comes back as the exact
     * digit string typed, not silently reinterpreted as a double.
     */
    public static String readCellAsText(Row row, int columnIndex) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        String text = new DataFormatter().formatCellValue(cell).trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * The cell's numeric value, or {@code null} if missing/blank - {@code
     * StudentMarksInput}'s own nullability (Phase 6b) for exactly this
     * reason: a blank marks cell means "not entered", not zero.
     *
     * @throws NumberFormatException if the cell holds non-blank text that
     *         is not a valid number - a formatting mistake in the uploaded
     *         file, which the calling service turns into a per-row import
     *         error rather than letting propagate.
     */
    public static BigDecimal readCellAsDecimal(Row row, int columnIndex) {
        if (row == null) {
            return null;
        }
        Cell cell = row.getCell(columnIndex);
        if (cell == null || cell.getCellType() == CellType.BLANK) {
            return null;
        }
        if (cell.getCellType() == CellType.NUMERIC) {
            // BigDecimal.valueOf(double), not `new BigDecimal(double)` -
            // the former goes through Double.toString first, giving the
            // decimal value a spreadsheet author actually typed (78.5)
            // rather than that double's exact binary representation
            // (78.4999999999999... or similar).
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String text = new DataFormatter().formatCellValue(cell).trim();
        return text.isEmpty() ? null : new BigDecimal(text);
    }

    /**
     * Builds a single-sheet {@code .xlsx} with a bold header row and the
     * given data rows below it, returned as bytes ready to stream as
     * {@code application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}.
     * A {@code null} cell value is left blank; every other value is
     * written via {@code toString()} except {@link Number}, which is
     * written as a real numeric cell so a re-imported roll number or
     * marks value round-trips as text/number correctly rather than
     * becoming a numeric-formatted string.
     * <p>
     * Column widths are fixed, not {@code Sheet.autoSizeColumn} - that
     * method estimates width from AWT font metrics, an environment
     * dependency this class has no way to verify is available on every
     * deployment target; a fixed, generous width has no such dependency
     * and is indistinguishable in practice for the short columns (roll
     * numbers, names, marks) both of this project's templates use.
     */
    public static byte[] writeXlsx(String sheetName, String[] headers, List<Object[]> rows) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet(sheetName);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int col = 0; col < headers.length; col++) {
                Cell cell = headerRow.createCell(col);
                cell.setCellValue(headers[col]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(col, 22 * 256);
            }

            int rowIndex = 1;
            for (Object[] rowValues : rows) {
                Row row = sheet.createRow(rowIndex++);
                for (int col = 0; col < rowValues.length; col++) {
                    Object value = rowValues[col];
                    if (value == null) {
                        continue;
                    }
                    Cell cell = row.createCell(col);
                    if (value instanceof Number number) {
                        cell.setCellValue(number.doubleValue());
                    } else {
                        cell.setCellValue(value.toString());
                    }
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return out.toByteArray();
        }
    }
}

