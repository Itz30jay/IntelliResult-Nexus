package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.MarksheetVerificationDAO;
import com.intelliresult.nexus.dao.MarksheetVerificationDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.MarksheetVerification;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.service.dto.VerificationResult;
import com.intelliresult.nexus.util.SecurityUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.List;
import java.util.Optional;

/**
 * Sec. 37 names this service explicitly. Two distinct halves, deliberately
 * kept in one class rather than split further - Sec. 20's own framing
 * ("every marksheet contains a QR... linking to /verify/result/{token}")
 * treats minting and later checking a token as one coherent feature, the
 * same granularity {@code ResultService} already draws around "everything
 * that can happen to a result's workflow state":
 * <ul>
 *   <li>{@link #getOrCreateVerificationToken} - called only from inside an
 *       authenticated request (MarksheetService, generating a PDF), so it
 *       is the one half of this class that ever touches a real
 *       {@code User}.</li>
 *   <li>{@link #verify} - called from the public, unauthenticated
 *       {@code /verify/result/{token}} endpoint, so it is deliberately the
 *       lighter of the two: no PDF/QR dependency anywhere on this class,
 *       specifically so the code path a stranger on the internet can reach
 *       never has to load OpenPDF/ZXing at all.</li>
 * </ul>
 */
public class VerificationService {

    private final MarksheetVerificationDAO marksheetVerificationDAO = new MarksheetVerificationDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ExamDAO examDAO = new ExamDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();

    /**
     * Returns this student+exam's verification token, minting one on first
     * call and reusing the same one on every later call - the same
     * find-or-create idiom {@code SystemSettingsService.getSettings()}
     * already establishes. The token itself never changes once minted (see
     * {@link MarksheetVerification}'s own class Javadoc for why a later
     * authorized correction doesn't need it to).
     *
     * @throws ResourceNotFoundException if {@code studentId} or {@code
     *         examId} does not resolve to a real record - MarksheetService
     *         has already loaded both by this point for its own purposes,
     *         so in practice this never actually fires; it exists so this
     *         method's contract is correct even if called from somewhere
     *         else in the future that hasn't already validated both ids.
     */
    public String getOrCreateVerificationToken(Long studentId, Long examId, User requestedBy) {
        Optional<MarksheetVerification> existing = marksheetVerificationDAO.findByStudentAndExam(studentId, examId);
        if (existing.isPresent()) {
            return existing.get().getVerificationToken();
        }
        return inTransaction(() -> {
            // Re-check inside the transaction: two near-simultaneous first
            // downloads of the same marksheet (two browser tabs, say) would
            // otherwise both pass the check above and both try to insert -
            // uq_marksheet_verifications_student_exam turns the loser of
            // that race into a constraint violation instead of a silent
            // second, equally-valid token, so this re-check is what makes
            // that the *expected*, avoided case rather than a 500 error.
            Optional<MarksheetVerification> recheck = marksheetVerificationDAO.findByStudentAndExam(studentId, examId);
            if (recheck.isPresent()) {
                return recheck.get().getVerificationToken();
            }
            Student student = studentDAO.findById(studentId)
                    .orElseThrow(() -> new ResourceNotFoundException("Student not found."));
            Exam exam = examDAO.findById(examId)
                    .orElseThrow(() -> new ResourceNotFoundException("Examination not found."));
            String token = SecurityUtil.generateToken();
            marksheetVerificationDAO.save(new MarksheetVerification(student, exam, token, requestedBy));
            return token;
        });
    }

    /**
     * The public verification lookup. Always re-reads the student's
     * current {@code Result} rows for this token's exam rather than
     * trusting anything cached at token-mint time - see {@link
     * MarksheetVerification}'s class Javadoc for why. Never throws for an
     * unrecognized token; {@link VerificationResult#invalid()} is the
     * entire error-handling story here, since Sec. 20 treats "no matching
     * record" as a normal outcome for this page to render, not a fault.
     */
    public VerificationResult verify(String token) {
        if (token == null || token.isBlank()) {
            return VerificationResult.invalid();
        }
        Optional<MarksheetVerification> found = marksheetVerificationDAO.findByToken(token.trim());
        if (found.isEmpty()) {
            return VerificationResult.invalid();
        }

        MarksheetVerification verification = found.get();
        Long studentId = verification.getStudent().getId();
        Long examId = verification.getExam().getId();

        List<Result> results = resultDAO.findByStudentAndExam(studentId, examId);
        if (results.isEmpty()) {
            // Every code path that mints a token requires a complete
            // ResultSummary to already exist (MarksheetService's own gate),
            // which in turn requires at least one Result row - reaching
            // here with none would mean the underlying results were
            // deleted out from under an already-issued token, not a normal
            // "nothing to show yet" case, so this reads as invalid too
            // rather than a different, more alarming state.
            return VerificationResult.invalid();
        }
        ResultStatus statusLabel = ResultStatus.mostFinal(results.stream().map(Result::getStatus).toList());

        return new VerificationResult(
                true,
                verification.getStudent().getUser().getFullName(),
                verification.getExam().getName(),
                verification.getExam().getAcademicYear().getLabel(),
                statusLabel.name(),
                java.time.LocalDateTime.now());
    }

    // ---------------------------------------------------------------- transaction helper

    private interface TransactionalWork<T> {
        T run();
    }

    private <T> T inTransaction(TransactionalWork<T> work) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            T result = work.run();
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }
}
