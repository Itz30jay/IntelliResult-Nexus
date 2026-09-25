package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.MarksheetVerification;

import java.util.Optional;

public interface MarksheetVerificationDAO extends GenericDAO<MarksheetVerification, Long> {

    /** The one row {@code uq_marksheet_verifications_student_exam} guarantees exists at most once - VerificationService's find-before-mint check, so a student who downloads the same marksheet twice gets back the same QR code both times rather than a second, equally-valid one. */
    Optional<MarksheetVerification> findByStudentAndExam(Long studentId, Long examId);

    /** The public {@code /verify/result/{token}} lookup - the one query this whole feature exists to make fast, hence the unique index {@code uq_marksheet_verifications_token} already gives it for free. */
    Optional<MarksheetVerification> findByToken(String token);
}
