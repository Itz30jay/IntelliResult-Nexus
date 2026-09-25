package com.intelliresult.nexus.service.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Everything {@code PDFUtil.buildMarksheet} needs to lay out one Sec. 19
 * marksheet, resolved to plain values by {@code MarksheetService} while the
 * request's Hibernate Session is still open - keeps PDFUtil a pure,
 * session-independent renderer (no entity/lazy-proxy access, matching every
 * other class in the {@code util} package) and, incidentally, makes it
 * trivially unit-testable with a hand-built record instead of a mocked
 * Session. Grouped by comment, not by nested record, to match this
 * project's existing preference for flat records over a deeper DTO
 * hierarchy (see every {@code service/dto} sibling).
 */
public record MarksheetData(
        // Institution branding (Sec. 52 / SystemSetting)
        String institutionName, String institutionAddress, String institutionLogoPath,
        String signatoryName, String signatoryDesignation,

        // Student
        String studentName, String rollNo, String courseName, String departmentName,
        String sectionName, String semesterLabel, String academicYearLabel,

        // Exam
        String examName, String examTypeLabel, LocalDate examStartDate, LocalDate examEndDate,

        // Subject-wise marks
        List<MarksheetSubjectRow> subjects,

        // Aggregate result (from ResultSummary)
        BigDecimal totalObtainedMarks, BigDecimal totalMaxMarks, BigDecimal overallPercentage,
        String overallGrade, BigDecimal sgpa, BigDecimal cgpa, boolean pass,
        Integer classRank, Integer overallRank,

        /** The most final workflow status found across this student's results for this exam - "LOCKED" if any subject has reached it, else "PUBLISHED" (Sec. 10's two "this is official" stages; DRAFT/SUBMITTED/APPROVED never reach here at all - MarksheetService gates on {@code ResultSummary.isComplete()}, which cannot be true before every subject is at least PUBLISHED). */
        String resultStatusLabel,

        // QR verification (Sec. 20)
        String verificationToken, byte[] qrCodePng, String verificationUrl,

        LocalDateTime generatedAt
) {
}
