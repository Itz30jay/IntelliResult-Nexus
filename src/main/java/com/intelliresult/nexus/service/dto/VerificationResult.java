package com.intelliresult.nexus.service.dto;

import java.time.LocalDateTime;

/**
 * The public {@code /verify/result/{token}} page's entire payload - Sec.
 * 20's exact field list (valid/invalid, student name, examination,
 * academic year, result status, verification timestamp) and nothing more.
 * No student id, roll number, marks, percentage, grade, SGPA, or rank -
 * Sec. 53's "return only minimum required information" is enforced by this
 * record simply not having a component for anything beyond what Sec. 20
 * names, not by trusting every call site to remember to omit it.
 */
public record VerificationResult(boolean valid, String studentName, String examName,
                                  String academicYearLabel, String resultStatusLabel,
                                  LocalDateTime verifiedAt) {

    /** No matching (or no longer active) token - rendered as a plain "not verified" state, never a stack trace or 404, since an expired/mistyped/forged code is an expected outcome for this page, not an error. */
    public static VerificationResult invalid() {
        return new VerificationResult(false, null, null, null, null, LocalDateTime.now());
    }
}
