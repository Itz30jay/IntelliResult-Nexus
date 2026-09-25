package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Sec. 20's QR-verification token store - one row per (student, exam),
 * minted the first time that student's marksheet PDF is generated and
 * reused for every later download of the same marksheet, the same
 * find-or-create idiom {@code SystemSettingsService.getSettings()} already
 * establishes for a different singleton-ish table. Deliberately NOT a
 * snapshot of the result at generation time - {@code VerificationService
 * #verify} always re-reads the live {@code results}/{@code
 * result_summaries} rows for this student+exam, so an authorized
 * correction (Sec. 14) made after a marksheet was first issued is reflected
 * immediately the next time anyone scans that same, still-valid QR code,
 * with nothing here needing to change. Extends {@link CreatedAtEntity}, not
 * {@link SoftDeletableEntity} - the same "nothing here to restore, only to
 * regenerate" reasoning {@link ResultSummary}'s own class Javadoc already
 * gives for the identical choice.
 */
@Entity
@Table(name = "marksheet_verifications",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"student_id", "exam_id"}),
                @UniqueConstraint(columnNames = {"verification_token"})
        })
public class MarksheetVerification extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    /**
     * {@link com.intelliresult.nexus.util.SecurityUtil#generateToken()} -
     * 256 bits of {@link java.security.SecureRandom}, URL-safe base64. Not
     * derived from {@code id}/{@code student_id}/{@code exam_id} in any way
     * (Sec. 20's "do not expose sensitive database IDs" is satisfied by
     * construction: nothing about this string can be reversed back to
     * either).
     */
    @Column(name = "verification_token", nullable = false, unique = true, length = 64)
    private String verificationToken;

    /**
     * Who first caused this token to be minted - always the requesting
     * user at the moment a marksheet was first generated. Plain {@code
     * @ManyToOne}, not a raw id, unlike {@link SoftDeletableEntity
     * #getDeletedBy()}: this table has no admin-facing screen that would
     * benefit from avoiding the join, and audit context ("which teacher/
     * admin/student action produced this token") is this row's entire
     * purpose.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "generated_by")
    private User generatedBy;

    protected MarksheetVerification() {
    }

    public MarksheetVerification(Student student, Exam exam, String verificationToken, User generatedBy) {
        this.student = student;
        this.exam = exam;
        this.verificationToken = verificationToken;
        this.generatedBy = generatedBy;
    }

    public Student getStudent() { return student; }
    public Exam getExam() { return exam; }
    public String getVerificationToken() { return verificationToken; }
    public User getGeneratedBy() { return generatedBy; }
}
