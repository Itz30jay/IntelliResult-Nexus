package com.intelliresult.nexus.service.dto;

import com.intelliresult.nexus.entity.enums.ExamType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Mirrors SubjectRequest's role for Subject: a plain, validation-free carrier
 * from ExamServlet's parsed request parameters to ExamService, which is the
 * only place these values are checked and turned into an Exam. Deliberately
 * takes semesterId rather than academicYearId - see ExamService's class
 * Javadoc for why academic_year_id is derived from the chosen semester
 * instead of asked for as a second, independently-selectable field.
 * startTime/endTime are full timestamps (an HTML {@code datetime-local}
 * input's value) and attemptLimit is the configured number of times this
 * exam may be conducted - both upgrade additions, see Exam's own Javadoc.
 */
public record ExamRequest(
        String name,
        ExamType examType,
        Long semesterId,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer attemptLimit,
        BigDecimal defaultMaxMarks
) {
}
