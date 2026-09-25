package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.ResultHistoryDAO;
import com.intelliresult.nexus.dao.ResultHistoryDAOImpl;
import com.intelliresult.nexus.entity.Result;
import com.intelliresult.nexus.entity.ResultHistory;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Sec. 37 lists {@code ResultService} and {@code ResultCalculationService}
 * as two separate services, and this is why: this class owns Sec. 10's
 * forward-only SUBMITTED -&gt; APPROVED -&gt; PUBLISHED -&gt; LOCKED workflow
 * and the Sec. 47 result-integrity guards around each transition (only a
 * SUBMITTED result can be approved, only APPROVED can be published, only
 * PUBLISHED can be locked - {@code Result.markApproved}/{@code
 * markPublished}/{@code markLocked} themselves perform no such check, by
 * design, matching how {@code applyCalculatedScore} also trusts its caller
 * completely). {@code ResultCalculationService} owns turning marks into
 * numbers; this class owns deciding *when in the workflow* that's allowed
 * to happen and what happens alongside it. The two compose deliberately:
 * approving a result calculates it in the same breath (see
 * {@link #approveResults}), and publishing an exam generates every
 * affected student's summary and rank in the same breath (see
 * {@link #publishExam}) - both as one atomic write each (Sec. 66), which is
 * exactly why {@link ResultCalculationService#doCalculateSubjectResult} and
 * {@link ResultCalculationService#doGenerateAllSummariesAndRanks} exist as
 * package-private methods alongside their transactional public
 * counterparts: this class calls straight through to the untransacted
 * logic from inside its own transaction, rather than nesting a second
 * {@code session.beginTransaction()} inside the first, which Hibernate does
 * not support on one session.
 * <p>
 * This is also, not incidentally, the first controller-reachable seam this
 * project has had for Sec. 11's calculation engine at all - see
 * {@code ResultApprovalServlet} for where the {@code module:result-engine}
 * Sentry tag PHASE7-RESULT-ENGINE.md's Decision 7 pointed to finally lands.
 * <p>
 * Phase 9 adds {@link #correctResult} to this same class rather than a new
 * one - Sec. 37 names exactly one {@code ResultService}, and "an authorized
 * correction changes a result's marks with an audit trail" sits squarely
 * inside "this class owns what's allowed to happen to a result and when,"
 * the same boundary {@link #approveResults}/{@link #publishExam}/
 * {@link #lockExam} already draw.
 */
public class ResultService {

    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ResultHistoryDAO resultHistoryDAO = new ResultHistoryDAOImpl();
    private final ResultCalculationService resultCalculationService = new ResultCalculationService();

    /**
     * Calculates and approves a batch of SUBMITTED results together - the
     * granularity a teacher's own submit works at (one subject, one
     * section, one exam; Phase 6b), so an admin approving right behind them
     * naturally selects the same batch. Deliberately not exam-wide like
     * {@link #publishExam}/{@link #lockExam}: different subjects' teachers
     * submit at different times, and forcing an admin to wait for every
     * subject in an exam before approving any of them would serialize work
     * that has no real dependency on each other.
     * <p>
     * All-or-nothing (Sec. 66): every id is checked to currently be
     * SUBMITTED *before* any write happens, so a batch that includes one
     * already-approved row (a stale checkbox from a page the admin had open
     * in two tabs, say) fails the whole batch with a specific message
     * naming the offending row, rather than silently approving the other
     * nineteen and leaving the admin to guess which one didn't take.
     *
     * @throws BusinessRuleException      if {@code resultIds} is empty, or if
     *         any selected result is not currently SUBMITTED.
     * @throws ResourceNotFoundException  if any id does not exist.
     * @throws com.intelliresult.nexus.exception.ResultProcessingException
     *         if calculation fails for any selected result (see
     *         {@link ResultCalculationService#doCalculateSubjectResult}) -
     *         which, per the same all-or-nothing guarantee, rolls back
     *         every other result in the batch too, not just the one that
     *         failed.
     */
    public List<Result> approveResults(List<Long> resultIds, User admin) {
        if (resultIds == null || resultIds.isEmpty()) {
            throw new BusinessRuleException("Select at least one result to approve.");
        }
        return inTransaction(() -> {
            List<Result> results = new ArrayList<>(resultIds.size());
            for (Long id : resultIds) {
                Result result = resultDAO.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Result not found."));
                if (result.getStatus() != ResultStatus.SUBMITTED) {
                    throw new BusinessRuleException("Cannot approve " + result.getStudent().getRollNo() + "'s "
                            + result.getSubject().getSubjectCode() + " result: it is currently "
                            + result.getStatus().name().toLowerCase() + ", not submitted.");
                }
                results.add(result);
            }
            for (Result result : results) {
                // markApproved before calculation, not after, so the calculated fields and the
                // approval fields land in the one resultDAO.update() call doCalculateSubjectResult
                // already makes - one UPDATE per row instead of two.
                result.markApproved(admin);
                resultCalculationService.doCalculateSubjectResult(result);
            }
            return results;
        });
    }

    /**
     * Publishes every currently-APPROVED result in this exam, then
     * generates (or regenerates) every affected student's
     * {@link ResultSummary} - overall percentage/grade/SGPA/CGPA/pass-fail -
     * and recomputes rank across the whole exam cohort, all as one
     * transaction. Sec. 47's "prevent publishing incomplete results" is
     * satisfied by construction here rather than by an extra check: only
     * APPROVED rows are ever selected, and a result cannot reach APPROVED
     * without first passing through {@link #approveResults}'s calculation
     * step, so there is no code path that could publish an uncalculated
     * result. {@link ResultSummary#isComplete()} remains available as the
     * finer-grained *"how many of this student's subjects are in this
     * summary"* signal for whichever later phase's dashboard needs to
     * decide whether a given student's SGPA is ready to show as final,
     * rather than being enforced as a hard gate here - a class of 60 where
     * 58 students have every subject graded should not have its publish
     * blocked by the 2 who don't.
     *
     * @throws BusinessRuleException      if no result in this exam is
     *         currently APPROVED.
     * @throws ResourceNotFoundException  if the exam does not exist.
     */
    public List<ResultSummary> publishExam(Long examId) {
        return inTransaction(() -> {
            List<Result> approved = resultDAO.findByExamAndStatus(examId, ResultStatus.APPROVED);
            if (approved.isEmpty()) {
                throw new BusinessRuleException(
                        "No approved results are available to publish for this exam. Approve at least one result first.");
            }
            for (Result result : approved) {
                result.markPublished();
                resultDAO.update(result);
            }
            return resultCalculationService.doGenerateAllSummariesAndRanks(examId);
        });
    }

    /**
     * Locks every currently-PUBLISHED result in this exam. Sec. 10:
     * "Immutable under normal operations" from this point on - any further
     * change goes through the re-evaluation/correction process (Phase 10),
     * not this class. No calculation happens here; a locked result's
     * numbers are exactly whatever they were the moment before locking.
     *
     * @throws BusinessRuleException  if no result in this exam is currently
     *         PUBLISHED.
     */
    public int lockExam(Long examId) {
        return inTransaction(() -> {
            List<Result> published = resultDAO.findByExamAndStatus(examId, ResultStatus.PUBLISHED);
            if (published.isEmpty()) {
                throw new BusinessRuleException(
                        "No published results are available to lock for this exam. Publish results first.");
            }
            for (Result result : published) {
                result.markLocked();
                resultDAO.update(result);
            }
            return published.size();
        });
    }

    /**
     * Sec. 13's change-tracking mechanism, and the "authorized correction"
     * Sec. 47 says a post-lock modification must go through - this is that
     * process, callable on a result in any status, not gated behind LOCKED
     * specifically. (A DRAFT/SUBMITTED result's marks could just be
     * re-entered directly through Phase 6b's own grid without needing an
     * audit trail; nothing stops an admin from using this instead, and
     * getting one anyway, if for instance the correction happens after a
     * student complaint that itself deserves a recorded reason regardless
     * of what stage the result was at when the complaint came in.)
     * <p>
     * Every one of theory/practical/internal is snapshotted as "old" and
     * written as "new" whether or not that particular component actually
     * changed - {@link ResultHistory}'s constructor takes a complete
     * three-component snapshot on each side (Sec. 13's literal field list),
     * not a diff of only what moved, so a correction that only touched
     * practical marks still records theory/internal's old value equal to
     * their new value rather than leaving two of six columns to mean
     * "unchanged," which is a fact about the row a reader would otherwise
     * have to infer rather than see.
     * <p>
     * Recalculates the corrected result in the same transaction as writing
     * the history row (Sec. 66) - if this exam has already published a
     * {@link ResultSummary} for this student, that summary and the whole
     * exam's ranks are refreshed too (see {@link ResultCalculationService
     * #refreshSummaryIfPublished}), so a correction can never leave an
     * already-visible SGPA/rank silently wrong. Nothing happens to the
     * result's {@code status} - a LOCKED result stays LOCKED; this changes
     * what happened, not what stage it's at.
     *
     * @throws BusinessRuleException      if {@code reason} is blank (mirrors
     *         {@code result_history.change_reason}'s NOT NULL constraint,
     *         checked here so the failure is a clear message rather than a
     *         raw constraint violation), or if any new mark is negative.
     * @throws ResourceNotFoundException  if the result does not exist.
     * @throws com.intelliresult.nexus.exception.ResultProcessingException
     *         if the corrected marks cannot be recalculated (a new value
     *         exceeds its component's configured maximum, or was supplied
     *         for a component this subject doesn't have - see
     *         {@link ResultCalculationService#doCalculateSubjectResult}).
     */
    public Result correctResult(Long resultId, BigDecimal newTheoryMarks, BigDecimal newPracticalMarks,
                                 BigDecimal newInternalMarks, User admin, String reason) {
        return inTransaction(() -> doCorrectResult(resultId, newTheoryMarks, newPracticalMarks, newInternalMarks, admin, reason));
    }

    /**
     * Package-private, not private - the same composability reason as
     * {@link ResultCalculationService#doCalculateSubjectResult}: Phase 10's
     * {@code RevaluationService.resolveRequest} needs "resolve the request
     * and correct the marks" to be one transaction (Sec. 66's "Re-evaluation
     * update... Marks modification + history creation" is explicit about
     * this), so it calls straight through to this method from inside its
     * own already-open transaction rather than through {@link
     * #correctResult}'s separately-transactional wrapper. Validation lives
     * here rather than only in the public wrapper, so every caller gets it
     * uniformly regardless of which entry point they use - the third time
     * this project has drawn this exact private-logic/public-wrapper line
     * (see {@code ResultCalculationService.doCalculateSubjectResult} and
     * {@code .doGenerateAllSummariesAndRanks}), which is worth naming as an
     * established pattern at this point rather than a one-off each time.
     */
    Result doCorrectResult(Long resultId, BigDecimal newTheoryMarks, BigDecimal newPracticalMarks,
                            BigDecimal newInternalMarks, User admin, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessRuleException("A reason is required to correct a result.");
        }
        validateNonNegative(newTheoryMarks, "Theory");
        validateNonNegative(newPracticalMarks, "Practical");
        validateNonNegative(newInternalMarks, "Internal");

        Result result = resultDAO.findById(resultId)
                .orElseThrow(() -> new ResourceNotFoundException("Result not found."));

        BigDecimal oldTheory = result.getTheoryMarks();
        BigDecimal oldPractical = result.getPracticalMarks();
        BigDecimal oldInternal = result.getInternalMarks();

        result.setTheoryMarks(newTheoryMarks);
        result.setPracticalMarks(newPracticalMarks);
        result.setInternalMarks(newInternalMarks);
        resultCalculationService.doCalculateSubjectResult(result);

        resultHistoryDAO.save(new ResultHistory(result, oldTheory, oldPractical, oldInternal,
                newTheoryMarks, newPracticalMarks, newInternalMarks, admin, reason));

        resultCalculationService.refreshSummaryIfPublished(result.getStudent(), result.getExam());
        return result;
    }

    private void validateNonNegative(BigDecimal marks, String label) {
        if (marks != null && marks.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessRuleException(label + " marks cannot be negative.");
        }
    }

    // ---------------------------------------------------------------- transaction helpers

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
