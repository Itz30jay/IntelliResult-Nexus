package com.intelliresult.nexus.service.dto;

import java.math.BigDecimal;

public record SubjectRequest(
        Long semesterId,
        Long departmentId,
        String subjectCode,
        String subjectName,
        BigDecimal credits,
        SubjectComponentInput theory,
        SubjectComponentInput practical,
        SubjectComponentInput internal
) {
}
