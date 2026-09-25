package com.intelliresult.nexus.service.dto;

/** Plain carrier from SystemSettingsServlet to SystemSettingsService - see ExamRequest's note on why these DTOs stay validation-free by convention. */
public record SystemSettingsRequest(
        String institutionName,
        String institutionAddress,
        String institutionLogoPath,
        String signatoryName,
        String signatoryDesignation
) {
}
