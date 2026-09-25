package com.intelliresult.nexus.dao.dto;

/**
 * A read-only projection, not an entity - built directly by an HQL
 * constructor expression (SELECT NEW ...) rather than loading full Subject/
 * Result entities just to compute an average, which is exactly the kind of
 * unnecessary-object-loading Sec. 49 warns against for dashboard queries.
 */
public record SubjectPerformanceDTO(String subjectCode, String subjectName, double averagePercentage) {
}
