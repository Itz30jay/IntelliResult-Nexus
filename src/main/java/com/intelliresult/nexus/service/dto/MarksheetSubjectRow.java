package com.intelliresult.nexus.service.dto;

import java.math.BigDecimal;

/** One subject's line on a marksheet PDF, already flattened out of a {@code Result} entity - PDFUtil renders documents, it does not walk lazy Hibernate associations, so MarksheetService resolves every field here before PDFUtil ever sees it (the same reason {@link MarksheetData} exists at all). Any of the three mark components may be null - a subject without that component (Subject.hasPractical == false, say) - and PDFUtil renders a null component as an em-dash, matching how {@code student/results.jsp} already displays the identical case on screen. */
public record MarksheetSubjectRow(String subjectCode, String subjectName, BigDecimal credits,
                                   BigDecimal theoryMarks, BigDecimal practicalMarks, BigDecimal internalMarks,
                                   BigDecimal totalMarks, BigDecimal maxMarks,
                                   BigDecimal percentage, String grade, BigDecimal gradePoint) {
}
