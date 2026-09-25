package com.intelliresult.nexus.service.dto;

import java.math.BigDecimal;

/**
 * One subject's history for one student, reduced to what Sec. 16's
 * Strength &amp; Weakness Analysis actually needs: an average (across every
 * calculated result the student has for that subject) and a trend (their
 * most recent result compared against the average of every earlier one -
 * null when there is only one result, since a trend needs at least two
 * points to mean anything). Computed by {@code StudentAnalyticsService}
 * from {@code ResultDAO.findCalculatedByStudent}, not a DAO projection
 * itself - the grouping-by-subject and trend arithmetic is calculation
 * logic (Sec. 37 keeps it out of the DAO layer), unlike
 * {@link com.intelliresult.nexus.dao.dto.SubjectAverageDTO}'s single
 * {@code AVG()} which a database can compute directly.
 */
public record SubjectInsight(String subjectCode, String subjectName, BigDecimal averagePercentage,
                              BigDecimal trend, int examCount) {
}
