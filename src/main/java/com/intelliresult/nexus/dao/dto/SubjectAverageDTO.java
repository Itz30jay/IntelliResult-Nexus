package com.intelliresult.nexus.dao.dto;

/**
 * One subject's class average within one exam+section - Sec. 15's Subject
 * Comparison. {@code subjectId} rides along specifically so
 * StudentAnalyticsService can join this against the querying student's own
 * per-subject percentage (from {@code ResultDAO.findByStudentAndExam}) in
 * Java, rather than this query trying to also compute "and what did this
 * one specific student get" via a correlated subquery inside the same
 * constructor expression - the same "DAO fetches simply, service composes"
 * split {@link SubjectPerformanceDTO}'s own class Javadoc already
 * establishes for a system-wide aggregate.
 */
public record SubjectAverageDTO(Long subjectId, String subjectCode, String subjectName, double averagePercentage) {
}
