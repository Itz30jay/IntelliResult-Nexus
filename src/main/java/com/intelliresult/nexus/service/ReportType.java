package com.intelliresult.nexus.service;

/** Sec. 33's six report types - a plain, unpersisted dispatch key (never a database column, unlike ExamType/ResultStatus in entity.enums), so it lives in the service package rather than alongside the mapped enums. */
public enum ReportType {
    CLASS_RESULT,
    SUBJECT_ANALYSIS,
    TOPPER,
    IMPROVEMENT,
    EXAM_SUMMARY,
    ACADEMIC_YEAR
}
