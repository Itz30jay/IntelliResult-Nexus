package com.intelliresult.nexus.util;

import com.intelliresult.nexus.entity.SystemSetting;
import com.intelliresult.nexus.service.dto.MarksheetData;
import com.intelliresult.nexus.service.dto.MarksheetSubjectRow;
import com.intelliresult.nexus.service.dto.ReportData;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * Sec. 19's official marksheet PDF, built with OpenPDF (see pom.xml's own
 * "Phase 12" comment for why OpenPDF over AGPL-only iText 5 - functionally
 * the same {@code com.lowagie.text} API this class uses throughout).
 * Session-independent and entity-free on purpose - every value this class
 * touches comes from {@link MarksheetData}, already resolved by
 * MarksheetService while the Hibernate Session was open, matching every
 * other class in this package (DateUtil, GradeUtil, ValidationUtil: none of
 * them know what an Entity or a Session is either).
 * <p>
 * Layout is single-pass, top-to-bottom {@code document.add(...)} calls for
 * the body (header, student info, subjects table, summary, QR/signature) -
 * OpenPDF paginates a {@link PdfPTable} across pages on its own when body
 * content overflows one page, so nothing here needs to reason about page
 * breaks directly. The one thing that DOES need explicit per-page handling
 * - the watermark and footer, which must repeat identically on every page
 * regardless of how many the body ends up needing - is delegated to
 * {@link MarksheetPageEvents}, the standard OpenPDF pattern for exactly
 * this (confirmed against the library's own current header/footer/
 * watermark examples, not assumed from a possibly-stale API shape).
 */
public final class PDFUtil {

    private static final Logger LOGGER = LogManager.getLogger(PDFUtil.class);

    // Brand palette mirrored from assets/css/tokens.css (see that file's own
    // header comment for the design rationale). A CSS custom property cannot
    // be read from Java, so this is a deliberate, documented parallel, not a
    // shared source of truth - Sec. 52's "PDF branding" setting is where a
    // future admin-configurable palette would eventually live instead.
    private static final Color COLOR_INK = new Color(0x10, 0x19, 0x2B);
    private static final Color COLOR_INK_SOFT = new Color(0x5B, 0x64, 0x72);
    private static final Color COLOR_SEAL = new Color(0xB8, 0x92, 0x5A);
    private static final Color COLOR_VERIFIED = new Color(0x2F, 0x6B, 0x52);
    private static final Color COLOR_DANGER = new Color(0xA3, 0x38, 0x4A);
    private static final Color COLOR_RULE = new Color(0xDA, 0xDE, 0xE3);
    private static final Color COLOR_PAPER_SOFT = new Color(0xE8, 0xE6, 0xDF);
    private static final Color COLOR_WHITE = Color.WHITE;

    private static final Font FONT_INSTITUTION = new Font(Font.HELVETICA, 17, Font.BOLD, COLOR_INK);
    private static final Font FONT_ADDRESS = new Font(Font.HELVETICA, 8, Font.NORMAL, COLOR_INK_SOFT);
    private static final Font FONT_DOC_TITLE = new Font(Font.HELVETICA, 12, Font.BOLD, COLOR_SEAL);
    private static final Font FONT_EXAM_LINE = new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_INK_SOFT);
    private static final Font FONT_LABEL = new Font(Font.HELVETICA, 7, Font.BOLD, COLOR_INK_SOFT);
    private static final Font FONT_VALUE = new Font(Font.HELVETICA, 9.5f, Font.NORMAL, COLOR_INK);
    private static final Font FONT_TABLE_HEAD = new Font(Font.HELVETICA, 7.5f, Font.BOLD, COLOR_WHITE);
    private static final Font FONT_TABLE_CELL = new Font(Font.HELVETICA, 9, Font.NORMAL, COLOR_INK);
    private static final Font FONT_SUMMARY_LABEL = new Font(Font.HELVETICA, 8, Font.NORMAL, COLOR_INK_SOFT);
    private static final Font FONT_SUMMARY_VALUE = new Font(Font.HELVETICA, 13, Font.BOLD, COLOR_INK);
    private static final Font FONT_PASS = new Font(Font.HELVETICA, 13, Font.BOLD, COLOR_VERIFIED);
    private static final Font FONT_FAIL = new Font(Font.HELVETICA, 13, Font.BOLD, COLOR_DANGER);
    private static final Font FONT_SMALL = new Font(Font.HELVETICA, 6.5f, Font.NORMAL, COLOR_INK_SOFT);
    private static final Font FONT_SIGNATURE = new Font(Font.HELVETICA, 8, Font.NORMAL, COLOR_INK);

    private static final float QR_DISPLAY_SIZE_PT = 78f;

    private PDFUtil() {
        // Static-only utility class.
    }

    /**
     * Renders the complete marksheet and returns it as PDF bytes.
     *
     * @throws DocumentException if OpenPDF cannot assemble the document
     *         (malformed content passed to a text element, for instance).
     * @throws IOException       if the institution logo or the QR image
     *         bytes cannot be read/decoded.
     */
    public static byte[] buildMarksheet(MarksheetData data) throws DocumentException, IOException {
        // 36pt side margins, generous 92pt top (institution header) and
        // 54pt bottom (footer) margins - both reserved so the per-page
        // watermark/footer drawn directly to PdfContentByte never overlaps
        // body content OpenPDF lays out independently.
        Document document = new Document(PageSize.A4, 36, 36, 92, 54);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new MarksheetPageEvents(data));

        document.open();
        addHeader(document, data);
        addStudentInfoTable(document, data);
        addSubjectsTable(document, data);
        addSummarySection(document, data);
        addVerificationBlock(document, data);
        document.close();

        LOGGER.info("Built marksheet PDF for roll {} / exam '{}' ({} bytes).",
                data.rollNo(), data.examName(), out.size());
        return out.toByteArray();
    }

    // ---------------------------------------------------------------- header

    private static void addHeader(Document document, MarksheetData data) throws DocumentException, IOException {
        PdfPTable header = new PdfPTable(new float[]{1f, 4f});
        header.setWidthPercentage(100);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Image logo = loadLogo(data.institutionLogoPath());
        if (logo != null) {
            logo.scaleToFit(50, 50);
            logoCell.addElement(logo);
        }
        header.addCell(logoCell);

        PdfPCell nameCell = new PdfPCell();
        nameCell.setBorder(Rectangle.NO_BORDER);
        nameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        nameCell.addElement(new Paragraph(data.institutionName(), FONT_INSTITUTION));
        if (data.institutionAddress() != null && !data.institutionAddress().isBlank()) {
            nameCell.addElement(new Paragraph(data.institutionAddress(), FONT_ADDRESS));
        }
        header.addCell(nameCell);
        document.add(header);

        Paragraph rule = new Paragraph(" ");
        rule.setSpacingBefore(4);
        document.add(rule);
        document.add(horizontalRule());

        Paragraph title = new Paragraph("STATEMENT OF MARKS", FONT_DOC_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(10);
        document.add(title);

        Paragraph examLine = new Paragraph(
                data.examName() + "  \u00b7  " + data.examTypeLabel() + "  \u00b7  " + data.academicYearLabel(),
                FONT_EXAM_LINE);
        examLine.setAlignment(Element.ALIGN_CENTER);
        examLine.setSpacingBefore(2);
        examLine.setSpacingAfter(12);
        document.add(examLine);
    }

    /** Never lets a missing/unreadable logo file abort marksheet generation - Sec. 52's institution logo is a nice-to-have on the document, not a precondition for a student receiving an official record they are otherwise entitled to. */
    private static Image loadLogo(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) {
            return null;
        }
        try {
            return Image.getInstance(logoPath);
        } catch (Exception e) {
            LOGGER.warn("Institution logo at '{}' could not be loaded - rendering the marksheet without it.", logoPath);
            return null;
        }
    }

    // ------------------------------------------------------------- student info

    private static void addStudentInfoTable(Document document, MarksheetData data) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{1f, 1.4f, 1f, 1.4f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);

        addInfoCell(table, "STUDENT NAME", data.studentName());
        addInfoCell(table, "ROLL NUMBER", data.rollNo());
        addInfoCell(table, "COURSE", data.courseName());
        addInfoCell(table, "DEPARTMENT", data.departmentName());
        addInfoCell(table, "SEMESTER", data.semesterLabel());
        addInfoCell(table, "SECTION", orDash(data.sectionName()));

        document.add(table);
    }

    private static void addInfoCell(PdfPTable table, String label, String value) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(COLOR_RULE);
        cell.setPadding(5f);
        cell.addElement(new Paragraph(label, FONT_LABEL));
        Paragraph valuePara = new Paragraph(value == null || value.isBlank() ? "\u2014" : value, FONT_VALUE);
        valuePara.setSpacingBefore(1f);
        cell.addElement(valuePara);
        table.addCell(cell);
    }

    // -------------------------------------------------------------- subjects

    private static void addSubjectsTable(Document document, MarksheetData data) throws DocumentException {
        PdfPTable table = new PdfPTable(new float[]{2.6f, 0.8f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f, 0.9f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        table.setHeaderRows(1);

        for (String h : new String[]{"SUBJECT", "CR", "THEORY", "PRAC.", "INT.", "TOTAL", "MAX", "%", "GRADE", "GP"}) {
            PdfPCell head = new PdfPCell(new Phrase(h, FONT_TABLE_HEAD));
            head.setBackgroundColor(COLOR_INK);
            head.setHorizontalAlignment(Element.ALIGN_CENTER);
            head.setVerticalAlignment(Element.ALIGN_MIDDLE);
            head.setPadding(5f);
            head.setBorderColor(COLOR_INK);
            table.addCell(head);
        }

        boolean shaded = false;
        for (MarksheetSubjectRow row : data.subjects()) {
            Color rowColor = shaded ? COLOR_PAPER_SOFT : COLOR_WHITE;
            addBodyCell(table, row.subjectCode() + " \u2014 " + row.subjectName(), Element.ALIGN_LEFT, rowColor);
            addBodyCell(table, orDash(row.credits()), Element.ALIGN_CENTER, rowColor);
            addBodyCell(table, orDash(row.theoryMarks()), Element.ALIGN_CENTER, rowColor);
            addBodyCell(table, orDash(row.practicalMarks()), Element.ALIGN_CENTER, rowColor);
            addBodyCell(table, orDash(row.internalMarks()), Element.ALIGN_CENTER, rowColor);
            addBodyCell(table, orDash(row.totalMarks()), Element.ALIGN_CENTER, rowColor);
            addBodyCell(table, orDash(row.maxMarks()), Element.ALIGN_CENTER, rowColor);
            addBodyCell(table, orDash(row.percentage()), Element.ALIGN_CENTER, rowColor);
            addBodyCell(table, orDash(row.grade()), Element.ALIGN_CENTER, rowColor);
            addBodyCell(table, orDash(row.gradePoint()), Element.ALIGN_CENTER, rowColor);
            shaded = !shaded;
        }

        document.add(table);
    }

    private static void addBodyCell(PdfPTable table, String text, int alignment, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, FONT_TABLE_CELL));
        cell.setHorizontalAlignment(alignment);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(4.5f);
        cell.setBackgroundColor(background);
        cell.setBorderColor(COLOR_RULE);
        table.addCell(cell);
    }

    // -------------------------------------------------------------- summary

    private static void addSummarySection(Document document, MarksheetData data) throws DocumentException {
        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setSpacingAfter(10);

        addSummaryCell(table, "TOTAL", data.totalObtainedMarks() + " / " + data.totalMaxMarks(), FONT_SUMMARY_VALUE);
        addSummaryCell(table, "PERCENTAGE", orDash(data.overallPercentage()) + "%", FONT_SUMMARY_VALUE);
        addSummaryCell(table, "GRADE", orDash(data.overallGrade()), FONT_SUMMARY_VALUE);
        addSummaryCell(table, "SGPA", orDash(data.sgpa()), FONT_SUMMARY_VALUE);
        addSummaryCell(table, data.cgpa() != null ? "CGPA" : "CLASS RANK",
                data.cgpa() != null ? orDash(data.cgpa()) : orDash(data.classRank()), FONT_SUMMARY_VALUE);
        addSummaryCell(table, "RESULT", data.pass() ? "PASS" : "FAIL", data.pass() ? FONT_PASS : FONT_FAIL);

        document.add(table);

        if (data.classRank() != null || data.overallRank() != null) {
            Paragraph ranks = new Paragraph(
                    rankLine(data.classRank(), data.overallRank()), FONT_EXAM_LINE);
            ranks.setSpacingAfter(10);
            document.add(ranks);
        }
    }

    private static String rankLine(Integer classRank, Integer overallRank) {
        StringBuilder sb = new StringBuilder();
        if (classRank != null) sb.append("Class Rank: ").append(classRank);
        if (overallRank != null) {
            if (sb.length() > 0) sb.append("   \u00b7   ");
            sb.append("Overall Rank: ").append(overallRank);
        }
        return sb.toString();
    }

    private static void addSummaryCell(PdfPTable table, String label, String value, Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColor(COLOR_INK);
        cell.setBorderWidthTop(1.4f);
        cell.setPadding(6f);
        cell.addElement(new Paragraph(label, FONT_SUMMARY_LABEL));
        Paragraph valuePara = new Paragraph(value, valueFont);
        valuePara.setSpacingBefore(2f);
        cell.addElement(valuePara);
        table.addCell(cell);
    }

    // --------------------------------------------------- QR + signature block

    private static void addVerificationBlock(Document document, MarksheetData data) throws DocumentException, IOException {
        PdfPTable table = new PdfPTable(new float[]{1f, 2f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(16);

        PdfPCell qrCell = new PdfPCell();
        qrCell.setBorder(Rectangle.NO_BORDER);
        qrCell.setVerticalAlignment(Element.ALIGN_TOP);
        Image qr = Image.getInstance(data.qrCodePng());
        qr.scaleToFit(QR_DISPLAY_SIZE_PT, QR_DISPLAY_SIZE_PT);
        qrCell.addElement(qr);
        Paragraph caption = new Paragraph("Scan to verify authenticity", FONT_SMALL);
        caption.setSpacingBefore(3f);
        qrCell.addElement(caption);
        Paragraph url = new Paragraph(data.verificationUrl(), FONT_SMALL);
        qrCell.addElement(url);
        table.addCell(qrCell);

        PdfPCell signatureCell = new PdfPCell();
        signatureCell.setBorder(Rectangle.NO_BORDER);
        signatureCell.setVerticalAlignment(Element.ALIGN_BOTTOM);
        signatureCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        Paragraph statusLine = new Paragraph(
                "This is a " + data.resultStatusLabel().toLowerCase() + " academic record, generated on "
                        + DateUtil.formatForDisplay(data.generatedAt()) + ".", FONT_SMALL);
        statusLine.setAlignment(Element.ALIGN_RIGHT);
        signatureCell.addElement(statusLine);

        Paragraph spacer = new Paragraph(" ");
        spacer.setSpacingBefore(20f);
        signatureCell.addElement(spacer);

        if (data.signatoryName() != null && !data.signatoryName().isBlank()) {
            Paragraph sigLine = new Paragraph("________________________", FONT_SIGNATURE);
            sigLine.setAlignment(Element.ALIGN_RIGHT);
            signatureCell.addElement(sigLine);
            Paragraph sigName = new Paragraph(data.signatoryName(), FONT_SIGNATURE);
            sigName.setAlignment(Element.ALIGN_RIGHT);
            signatureCell.addElement(sigName);
            if (data.signatoryDesignation() != null && !data.signatoryDesignation().isBlank()) {
                Paragraph sigTitle = new Paragraph(data.signatoryDesignation(), FONT_SMALL);
                sigTitle.setAlignment(Element.ALIGN_RIGHT);
                signatureCell.addElement(sigTitle);
            }
        }
        table.addCell(signatureCell);

        document.add(table);
    }

    // ------------------------------------------------------------- helpers

    /** A plain horizontal rule, built the same confirmed way every other border in this document is (a bordered {@link PdfPCell}, not the separate {@code com.lowagie.text.pdf.draw.LineSeparator} utility class) - one less distinct OpenPDF API surface for this class to depend on. */
    private static PdfPTable horizontalRule() {
        PdfPTable rule = new PdfPTable(1);
        rule.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell(new Phrase(" "));
        cell.setFixedHeight(1f);
        cell.setBorder(Rectangle.BOTTOM);
        cell.setBorderColor(COLOR_RULE);
        cell.setBorderWidthBottom(1.2f);
        rule.addCell(cell);
        return rule;
    }

    private static String orDash(Object value) {
        return value == null ? "\u2014" : value.toString();
    }

    /**
     * Repeats the same watermark and footer on every page this document
     * ends up needing, regardless of how the {@link PdfPTable} body split
     * across them - the standard OpenPDF {@link PdfPageEventHelper} pattern
     * (confirmed against the library's own current header/footer/watermark
     * examples). Drawn directly to {@link PdfContentByte} rather than added
     * via {@code document.add(...)}: page-event content is explicitly
     * positioned and explicitly NOT part of the flowing body layout, which
     * is exactly what a watermark and a running footer both need to be.
     */
    private static final class MarksheetPageEvents extends PdfPageEventHelper {

        private final MarksheetData data;
        private BaseFont watermarkFont;
        private BaseFont footerFont;

        MarksheetPageEvents(MarksheetData data) {
            this.data = data;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                watermarkFont = BaseFont.createFont(BaseFont.HELVETICA_BOLD, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
                footerFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                // The 14 standard PDF fonts (Helvetica among them) never
                // actually fail to load - this catch exists only because
                // BaseFont.createFont's signature declares the checked
                // exceptions, not because this path is reachable in practice.
                throw new IllegalStateException("Standard PDF font failed to load.", e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float pageWidth = document.getPageSize().getWidth();
            float pageHeight = document.getPageSize().getHeight();

            // Watermark: institution name repeated diagonally at very low
            // opacity - visible under normal lighting, deliberately not
            // interfering with reading the marks table beneath it.
            cb.saveState();
            PdfGState gs = new PdfGState();
            gs.setFillOpacity(0.06f);
            cb.setGState(gs);
            cb.setColorFill(COLOR_INK);
            cb.beginText();
            cb.setFontAndSize(watermarkFont, 46);
            cb.showTextAligned(Element.ALIGN_CENTER, data.institutionName().toUpperCase(),
                    pageWidth / 2, pageHeight / 2, 45);
            cb.endText();
            cb.restoreState();

            // Footer: generation timestamp on the left, page number on the
            // right - a plain PdfContentByte.showTextAligned pair rather
            // than a PdfPTable, since two independently-aligned text runs
            // don't need a table's layout machinery.
            cb.saveState();
            cb.setColorStroke(COLOR_RULE);
            cb.setLineWidth(0.75f);
            cb.moveTo(document.leftMargin(), 40);
            cb.lineTo(pageWidth - document.rightMargin(), 40);
            cb.stroke();

            cb.setColorFill(COLOR_INK_SOFT);
            cb.beginText();
            cb.setFontAndSize(footerFont, 7.5f);
            cb.showTextAligned(Element.ALIGN_LEFT,
                    "IntelliResult Nexus \u00b7 System-generated document \u00b7 " + DateUtil.formatForDisplay(data.generatedAt()),
                    document.leftMargin(), 28, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, "Page " + writer.getPageNumber(),
                    pageWidth - document.rightMargin(), 28, 0);
            cb.endText();
            cb.restoreState();
        }
    }

    // ================================================================
    // Sec. 33's reporting suite (Phase 15) - a single flexible builder
    // shared by all six report types, none of which touch or call
    // anything above this line. buildMarksheet's own call chain is
    // completely unchanged; everything below only reads the font/color
    // constants and loadLogo/horizontalRule/addBodyCell/orDash helpers
    // already defined above, the same way any other method in this class
    // already does.
    // ================================================================

    /**
     * Sec. 33's shared report shell - one builder for Class Result,
     * Subject Analysis, Topper, Improvement, Exam, and Academic Year
     * reports alike, since {@link ReportData} is already the one shape
     * {@code ReportService} maps every one of them into. Landscape
     * orientation (unlike the marksheet's portrait) - the Class Result
     * Report alone can have one column per subject sat, and a wide table
     * needs the width a rotated A4 page gives it.
     */
    public static byte[] buildTabularReport(ReportData data, SystemSetting settings, LocalDateTime generatedAt)
            throws DocumentException, IOException {
        Document document = new Document(PageSize.A4.rotate(), 36, 36, 92, 54);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfWriter writer = PdfWriter.getInstance(document, out);
        writer.setPageEvent(new ReportPageEvents(settings.getInstitutionName(), generatedAt));

        document.open();
        addReportHeader(document, settings, data);
        addReportTable(document, data);
        addReportSummary(document, data);
        document.close();

        LOGGER.info("Built '{}' report PDF ({} row(s), {} bytes).", data.title(), data.rows().size(), out.size());
        return out.toByteArray();
    }

    private static void addReportHeader(Document document, SystemSetting settings, ReportData data)
            throws DocumentException, IOException {
        PdfPTable header = new PdfPTable(new float[]{1f, 6f});
        header.setWidthPercentage(100);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        logoCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        Image logo = loadLogo(settings.getInstitutionLogoPath());
        if (logo != null) {
            logo.scaleToFit(46, 46);
            logoCell.addElement(logo);
        }
        header.addCell(logoCell);

        PdfPCell nameCell = new PdfPCell();
        nameCell.setBorder(Rectangle.NO_BORDER);
        nameCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        nameCell.addElement(new Paragraph(settings.getInstitutionName(), FONT_INSTITUTION));
        if (settings.getInstitutionAddress() != null && !settings.getInstitutionAddress().isBlank()) {
            nameCell.addElement(new Paragraph(settings.getInstitutionAddress(), FONT_ADDRESS));
        }
        header.addCell(nameCell);
        document.add(header);

        Paragraph rule = new Paragraph(" ");
        rule.setSpacingBefore(4);
        document.add(rule);
        document.add(horizontalRule());

        Paragraph title = new Paragraph(data.title().toUpperCase(), FONT_DOC_TITLE);
        title.setAlignment(Element.ALIGN_CENTER);
        title.setSpacingBefore(10);
        document.add(title);

        if (data.subtitle() != null && !data.subtitle().isBlank()) {
            Paragraph subtitle = new Paragraph(data.subtitle(), FONT_EXAM_LINE);
            subtitle.setAlignment(Element.ALIGN_CENTER);
            subtitle.setSpacingBefore(2);
            document.add(subtitle);
        }

        Paragraph generated = new Paragraph("Generated " + DateUtil.formatForDisplay(LocalDateTime.now()), FONT_SMALL);
        generated.setAlignment(Element.ALIGN_CENTER);
        generated.setSpacingBefore(2);
        generated.setSpacingAfter(12);
        document.add(generated);
    }

    private static void addReportTable(Document document, ReportData data) throws DocumentException {
        if (data.rows().isEmpty()) {
            Paragraph empty = new Paragraph("No data available for this report.", FONT_EXAM_LINE);
            empty.setAlignment(Element.ALIGN_CENTER);
            empty.setSpacingBefore(20);
            document.add(empty);
            return;
        }

        int columnCount = data.columnHeaders().length;
        PdfPTable table = new PdfPTable(columnCount);
        table.setWidthPercentage(100);
        table.setSpacingAfter(12);
        table.setHeaderRows(1);

        for (String columnHeader : data.columnHeaders()) {
            PdfPCell head = new PdfPCell(new Phrase(columnHeader, FONT_TABLE_HEAD));
            head.setBackgroundColor(COLOR_INK);
            head.setHorizontalAlignment(Element.ALIGN_CENTER);
            head.setVerticalAlignment(Element.ALIGN_MIDDLE);
            head.setPadding(5f);
            head.setBorderColor(COLOR_INK);
            table.addCell(head);
        }

        boolean shaded = false;
        for (Object[] row : data.rows()) {
            Color rowColor = shaded ? COLOR_PAPER_SOFT : COLOR_WHITE;
            for (int i = 0; i < columnCount; i++) {
                Object value = i < row.length ? row[i] : null;
                addBodyCell(table, orDash(value), i < 2 ? Element.ALIGN_LEFT : Element.ALIGN_CENTER, rowColor);
            }
            shaded = !shaded;
        }
        document.add(table);
    }

    private static void addReportSummary(Document document, ReportData data) throws DocumentException {
        if (data.summaryLines().isEmpty()) {
            return;
        }
        PdfPTable box = new PdfPTable(1);
        box.setWidthPercentage(100);
        box.setSpacingBefore(6);
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.TOP);
        cell.setBorderColor(COLOR_INK);
        cell.setBorderWidthTop(1.4f);
        cell.setPadding(8f);
        for (String line : data.summaryLines()) {
            cell.addElement(new Paragraph(line, FONT_VALUE));
        }
        box.addCell(cell);
        document.add(box);
    }

    /** The report footer - institution name, generation timestamp, page number. No watermark, unlike {@link MarksheetPageEvents}: a report is an internal administrative document, not an official certified record handed to a third party, so the two intentionally look different. */
    private static final class ReportPageEvents extends PdfPageEventHelper {

        private final String institutionName;
        private final LocalDateTime generatedAt;
        private BaseFont footerFont;

        ReportPageEvents(String institutionName, LocalDateTime generatedAt) {
            this.institutionName = institutionName;
            this.generatedAt = generatedAt;
        }

        @Override
        public void onOpenDocument(PdfWriter writer, Document document) {
            try {
                footerFont = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            } catch (DocumentException | IOException e) {
                throw new IllegalStateException("Standard PDF font failed to load.", e);
            }
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            PdfContentByte cb = writer.getDirectContent();
            float pageWidth = document.getPageSize().getWidth();

            cb.saveState();
            cb.setColorStroke(COLOR_RULE);
            cb.setLineWidth(0.75f);
            cb.moveTo(document.leftMargin(), 40);
            cb.lineTo(pageWidth - document.rightMargin(), 40);
            cb.stroke();

            cb.setColorFill(COLOR_INK_SOFT);
            cb.beginText();
            cb.setFontAndSize(footerFont, 7.5f);
            cb.showTextAligned(Element.ALIGN_LEFT,
                    institutionName + " \u00b7 Generated " + DateUtil.formatForDisplay(generatedAt),
                    document.leftMargin(), 28, 0);
            cb.showTextAligned(Element.ALIGN_RIGHT, "Page " + writer.getPageNumber(),
                    pageWidth - document.rightMargin(), 28, 0);
            cb.endText();
            cb.restoreState();
        }
    }
}
