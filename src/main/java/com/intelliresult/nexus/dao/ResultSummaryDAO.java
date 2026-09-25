package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.ResultSummary;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface ResultSummaryDAO extends GenericDAO<ResultSummary, Long> {

    /** The one row {@code uq_result_summaries_student_exam} guarantees exists at most once - used both to load an existing summary for a student's dashboard/marksheet and to check-before-insert when (re)generating one. */
    Optional<ResultSummary> findByStudentAndExam(Long studentId, Long examId);

    /** Every summary for one exam, across every section - the input {@link #recalculateRanks} ranks over, and Sec. 33's Exam Report / Topper Report source data once Phase 15 exists. */
    List<ResultSummary> findByExam(Long examId);

    /** Same data as {@link #findByExam}, scoped to one section - Sec. 33's Class Result / Topper / Fail-Improvement reports (Phase 15) never need every section at once, so this avoids loading and then discarding every other section's rows just to filter them out in Java. */
    List<ResultSummary> findByExamAndSection(Long examId, Long sectionId);

    /**
     * The most recent summary this student has for an exam that started
     * strictly before {@code beforeStartDate}, or empty if none exists yet -
     * Sec. 15/55's "current exam vs previous exam" comparison. Ordered by
     * the exam's own start date rather than this summary's created_at: the
     * *academic* sequence of exams is what "previous" means here, not the
     * order results happened to be calculated in (a late-approved older
     * exam must still count as "before" a more promptly-approved recent
     * one).
     */
    Optional<ResultSummary> findMostRecentBefore(Long studentId, java.time.LocalDateTime beforeStartTime);

    /**
     * Every FINAL_EXAMINATION-type summary this student has, ordered by the
     * exam's own start time ascending - CGPA's raw material (Sec. 11): one
     * row per completed semester, oldest first, ready for a credit-weighted
     * running average. Deliberately not filtered further here (e.g. "most
     * recent per semester") - a subject's semester could in principle have
     * more than one FINAL_EXAMINATION row (a retake), and deciding which one
     * is authoritative is calculation logic (ResultCalculationService.
     * computeCgpa), not a query concern.
     */
    List<ResultSummary> findFinalExamSummariesForStudent(Long studentId);

    /**
     * Every summary a student has, across every exam, ordered by the
     * exam's own start date ascending - Sec. 17's "Performance trend" line
     * chart, and Sec. 11's "Historical trends" more generally. Similar
     * shape to {@link #findFinalExamSummariesForStudent} but deliberately
     * unfiltered by exam type - a trend line is more informative including
     * every exam a student has a summary for, not just semester-defining
     * ones.
     */
    List<ResultSummary> findAllForStudent(Long studentId);

    /**
     * The class average for one exam, scoped to one section - Sec. 15's
     * "Student vs Class Average," and Sec. 15's own "never expose sensitive
     * individual student information... use aggregate statistics"
     * satisfied by construction: this is a single {@code AVG()}, which can
     * never itself carry any one other student's row back to the caller.
     * Empty (not zero) when no summary exists yet for anyone in this
     * section/exam - Sec. 56's empty state, not a number that would read
     * as a real average of nothing.
     */
    Optional<BigDecimal> classAveragePercentage(Long examId, Long sectionId);

    /** The top score for one exam, scoped to one section - Sec. 15's "Student vs Topper." Same aggregate-only, empty-not-zero reasoning as {@link #classAveragePercentage}. */
    Optional<BigDecimal> topperPercentage(Long examId, Long sectionId);

    /**
     * Bulk-populates classRank/overallRank for every summary belonging to
     * one exam in a single statement, using MySQL 8's {@code RANK() OVER}
     * (verified directly against this project's own seed data before this
     * method was written - see docs/architecture/PHASE7-RESULT-ENGINE.md).
     * A native UPDATE...JOIN rather than loading every ResultSummary into
     * Java and sorting: ranking is inherently a whole-cohort operation
     * (hundreds of rows for a large exam), and looping N individual
     * entity updates for what the database can do in one indexed pass is
     * exactly the "unnecessary database calls" Sec. 49 warns against.
     * <p>
     * Bypasses the Hibernate persistence context by design (a native bulk
     * UPDATE always does) - the caller is responsible for not relying on
     * any ResultSummary entity it already holds in this same Session to
     * reflect the new rank values afterward without re-fetching; see
     * ResultCalculationService.recalculateRanksForExam's own Javadoc for how
     * this is handled.
     */
    void recalculateRanks(Long examId);
}
