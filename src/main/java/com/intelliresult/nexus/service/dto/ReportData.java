package com.intelliresult.nexus.service.dto;

import java.util.List;

/**
 * Sec. 33's six report types (Class Result, Subject Analysis, Topper,
 * Fail/Improvement, Exam, Academic Year) are all, at bottom, a title, a
 * context line, a table, and a few summary statistics - so rather than
 * six bespoke DTOs, {@code ReportService} maps every one of them into
 * this single shape. The same {@code columnHeaders}/{@code rows} feed
 * {@code PDFUtil.buildTabularReport}, {@code ExcelUtil.writeXlsx}
 * (already generic over exactly this shape since Phase 13), and
 * {@code report-view.jsp}'s print layout without any per-report-type
 * branching in any of the three renderers - only {@code ReportService}
 * itself knows what each report type's columns mean.
 * <p>
 * Row values are kept as their natural Java types ({@link
 * java.math.BigDecimal}, {@link Integer}), not pre-formatted strings -
 * {@code ExcelUtil.writeXlsx} writes a {@link Number} as a real numeric
 * cell (sortable/filterable in the spreadsheet) rather than a
 * numeric-looking text string, and both {@code PDFUtil} and the print
 * view call {@code toString()} on whatever they're given, exactly
 * matching how {@code MarksheetSubjectRow} (Phase 12) already treats a
 * {@code null} entry as "not applicable" rather than zero.
 */
public record ReportData(String title, String subtitle, String[] columnHeaders,
                          List<Object[]> rows, List<String> summaryLines) {
}
