package com.intelliresult.nexus.service.dto;

import java.math.BigDecimal;

/** One subject's student-vs-class comparison - {@code StudentAnalyticsService.subjectComparison}'s output, built by joining {@code ResultDAO.subjectAveragesForExamSection} against the student's own {@code Result} rows in Java (see {@code SubjectAverageDTO}'s own Javadoc for why not a correlated subquery). {@code studentPercentage} is null when the student has no calculated result for that subject in this exam yet - a fact worth showing as "not yet available" rather than silently omitting the subject from the comparison entirely. */
public record SubjectComparison(String subjectCode, String subjectName, BigDecimal studentPercentage, BigDecimal classAveragePercentage) {
}
