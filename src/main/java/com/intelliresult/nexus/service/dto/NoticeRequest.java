package com.intelliresult.nexus.service.dto;

import com.intelliresult.nexus.entity.enums.NoticePriority;
import com.intelliresult.nexus.entity.enums.UserRole;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * Plain carrier from NoticeServlet to NoticeService - see ExamRequest's note
 * (PHASE5E-EXAM-GRADING.md) on why these DTOs stay validation-free by
 * convention. expiryDate is already a full LocalDateTime here, not the
 * LocalDate the &lt;input type="date"&gt; actually submits - NoticeServlet
 * does that conversion (end of the chosen day) before this record is built,
 * so NoticeService never needs to know the form used a date-only picker.
 */
public record NoticeRequest(
        String title,
        String content,
        Set<UserRole> audience,
        NoticePriority priority,
        LocalDateTime expiryDate
) {
}
