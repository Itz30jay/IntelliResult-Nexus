package com.intelliresult.nexus.service.dto;

import java.math.BigDecimal;

/** Plain carrier from GradingRuleServlet to GradingRuleService - see ExamRequest's note on why these DTOs stay validation-free by convention. */
public record GradingRuleRequest(
        Long academicYearId,
        BigDecimal minPercentage,
        BigDecimal maxPercentage,
        String grade,
        BigDecimal gradePoint
) {
}
