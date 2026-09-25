package com.intelliresult.nexus.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Matches this project's established "pure utility classes only" test
 * scope (Phase 7/12's own {@code GradeUtilTest}/{@code QRCodeUtilTest}) -
 * {@code StudentImportService}/{@code MarksImportService} are Hibernate-
 * backed like every other service, so Phase 17 covers them. This class
 * round-trips {@link ExcelUtil#writeXlsx} straight back through {@link
 * ExcelUtil#openWorkbook}, which exercises both halves of the class
 * against each other rather than needing a hand-crafted fixture file.
 */
class ExcelUtilTest {

    @Test
    void writeXlsx_producesAValidZipBasedXlsxFile() throws Exception {
        byte[] xlsx = ExcelUtil.writeXlsx("Sheet1", new String[]{"A"}, List.<Object[]>of(new Object[]{"x"}));

        assertTrue(xlsx.length > 0);
        // .xlsx is a ZIP container - the 4-byte "PK\3\4" local-file-header
        // signature is the cheapest possible check that POI actually wrote
        // a real archive, not silently empty/corrupt output.
        byte[] zipSignature = {0x50, 0x4B, 0x03, 0x04};
        byte[] actualHeader = new byte[4];
        System.arraycopy(xlsx, 0, actualHeader, 0, 4);
        assertArrayEquals(zipSignature, actualHeader);
    }

    @Test
    void readCellAsText_roundTripsThroughWriteXlsx() throws Exception {
        byte[] xlsx = ExcelUtil.writeXlsx("Students",
                new String[]{"Roll No", "Full Name"},
                List.<Object[]>of(new Object[]{"STU2024001", "Priya Sharma"}));

        try (XSSFWorkbook workbook = ExcelUtil.openWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = workbook.getSheetAt(0);
            Row headerRow = sheet.getRow(0);
            Row dataRow = sheet.getRow(1);

            assertEquals("Roll No", ExcelUtil.readCellAsText(headerRow, 0));
            assertEquals("STU2024001", ExcelUtil.readCellAsText(dataRow, 0));
            assertEquals("Priya Sharma", ExcelUtil.readCellAsText(dataRow, 1));
        }
    }

    @Test
    void readCellAsText_returnsNullForBlankOrMissingCell() throws Exception {
        byte[] xlsx = ExcelUtil.writeXlsx("Sheet1", new String[]{"A", "B"}, List.<Object[]>of(new Object[]{"present", null}));

        try (XSSFWorkbook workbook = ExcelUtil.openWorkbook(new ByteArrayInputStream(xlsx))) {
            Row dataRow = workbook.getSheetAt(0).getRow(1);
            assertNull(ExcelUtil.readCellAsText(dataRow, 1));
            assertNull(ExcelUtil.readCellAsText(dataRow, 5)); // column past the end of the row entirely
        }
    }

    @Test
    void readCellAsDecimal_roundTripsNumericValuesWithoutFloatingPointDrift() throws Exception {
        byte[] xlsx = ExcelUtil.writeXlsx("Marks", new String[]{"Theory"},
                List.<Object[]>of(new Object[]{BigDecimal.valueOf(78.5)}));

        try (XSSFWorkbook workbook = ExcelUtil.openWorkbook(new ByteArrayInputStream(xlsx))) {
            Row dataRow = workbook.getSheetAt(0).getRow(1);
            assertEquals(0, BigDecimal.valueOf(78.5).compareTo(ExcelUtil.readCellAsDecimal(dataRow, 0)));
        }
    }

    @Test
    void readCellAsDecimal_returnsNullForBlankCell() throws Exception {
        byte[] xlsx = ExcelUtil.writeXlsx("Marks", new String[]{"Theory"}, List.<Object[]>of(new Object[]{null}));

        try (XSSFWorkbook workbook = ExcelUtil.openWorkbook(new ByteArrayInputStream(xlsx))) {
            Row dataRow = workbook.getSheetAt(0).getRow(1);
            assertNull(ExcelUtil.readCellAsDecimal(dataRow, 0));
        }
    }

    @Test
    void readCellAsDecimal_rejectsNonNumericText() throws Exception {
        byte[] xlsx = ExcelUtil.writeXlsx("Marks", new String[]{"Theory"}, List.<Object[]>of(new Object[]{"not-a-number"}));

        try (XSSFWorkbook workbook = ExcelUtil.openWorkbook(new ByteArrayInputStream(xlsx))) {
            Row dataRow = workbook.getSheetAt(0).getRow(1);
            assertThrows(NumberFormatException.class, () -> ExcelUtil.readCellAsDecimal(dataRow, 0));
        }
    }
}

