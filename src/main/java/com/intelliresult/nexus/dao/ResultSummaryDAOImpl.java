package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.enums.ExamType;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public class ResultSummaryDAOImpl extends AbstractDAO<ResultSummary, Long> implements ResultSummaryDAO {

    public ResultSummaryDAOImpl() {
        super(ResultSummary.class);
    }

    @Override
    public Optional<ResultSummary> findByStudentAndExam(Long studentId, Long examId) {
        return namedQuery("FROM ResultSummary WHERE student.id = :studentId AND exam.id = :examId")
                .setParameter("studentId", studentId)
                .setParameter("examId", examId)
                .uniqueResultOptional();
    }

    @Override
    public List<ResultSummary> findByExam(Long examId) {
        return namedQuery("FROM ResultSummary WHERE exam.id = :examId")
                .setParameter("examId", examId)
                .getResultList();
    }

    @Override
    public List<ResultSummary> findByExamAndSection(Long examId, Long sectionId) {
        return namedQuery("FROM ResultSummary WHERE exam.id = :examId AND student.currentSection.id = :sectionId "
                        + "ORDER BY student.rollNo")
                .setParameter("examId", examId)
                .setParameter("sectionId", sectionId)
                .getResultList();
    }

    @Override
    public Optional<ResultSummary> findMostRecentBefore(Long studentId, LocalDateTime beforeStartTime) {
        return namedQuery("FROM ResultSummary WHERE student.id = :studentId AND exam.startTime < :beforeStartTime "
                        + "ORDER BY exam.startTime DESC")
                .setParameter("studentId", studentId)
                .setParameter("beforeStartTime", beforeStartTime)
                .setMaxResults(1)
                .uniqueResultOptional();
    }

    @Override
    public List<ResultSummary> findFinalExamSummariesForStudent(Long studentId) {
        return namedQuery("FROM ResultSummary WHERE student.id = :studentId AND exam.examType = :examType "
                        + "ORDER BY exam.startTime ASC")
                .setParameter("studentId", studentId)
                .setParameter("examType", ExamType.FINAL_EXAMINATION)
                .getResultList();
    }

    @Override
    public List<ResultSummary> findAllForStudent(Long studentId) {
        return namedQuery("FROM ResultSummary WHERE student.id = :studentId ORDER BY exam.startTime ASC")
                .setParameter("studentId", studentId)
                .getResultList();
    }

    /** Queried as Double, not BigDecimal - matching the same JPQL-aggregate-result precedent {@code SubjectPerformanceDTO.averagePercentage} (Phase 5a) already established for AVG() in this codebase, rather than assuming Hibernate preserves BigDecimal through an aggregate function. */
    @Override
    public Optional<BigDecimal> classAveragePercentage(Long examId, Long sectionId) {
        Double average = session()
                .createQuery("SELECT AVG(rs.overallPercentage) FROM ResultSummary rs "
                        + "WHERE rs.exam.id = :examId AND rs.student.currentSection.id = :sectionId", Double.class)
                .setParameter("examId", examId)
                .setParameter("sectionId", sectionId)
                .uniqueResult();
        return average == null ? Optional.empty() : Optional.of(BigDecimal.valueOf(average).setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    public Optional<BigDecimal> topperPercentage(Long examId, Long sectionId) {
        Double max = session()
                .createQuery("SELECT MAX(rs.overallPercentage) FROM ResultSummary rs "
                        + "WHERE rs.exam.id = :examId AND rs.student.currentSection.id = :sectionId", Double.class)
                .setParameter("examId", examId)
                .setParameter("sectionId", sectionId)
                .uniqueResult();
        return max == null ? Optional.empty() : Optional.of(BigDecimal.valueOf(max).setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * {@code RANK() OVER}, not a hand-rolled Java sort: MySQL 8 (this
     * project's mandated minimum - see the master spec's Technology Stack
     * section) supports window functions natively, and this exact query
     * shape was executed against this project's own seeded Mid Semester
     * Examination data before being committed here - confirmed the
     * intentionally-struggling seeded student (4 subjects failed) correctly
     * lands last, matching PHASE2-DATABASE.md's own verification note that
     * anticipated "the actual shape of query... Phase 7... will need."
     * A single UPDATE...JOIN against a derived table, rather than SELECT-ing
     * ranks into Java and issuing one UPDATE per student: ranking is a
     * whole-cohort computation by nature, and this keeps it a single
     * indexed round trip regardless of cohort size (Sec. 49).
     * <p>
     * Per Hibernate's own documented behavior for bulk update/delete
     * statements ("the effect... is not reflected in the persistence
     * context, nor in the state of entity objects held in memory... it is
     * the responsibility of the application to maintain synchronization" -
     * confirmed via Context7 against Hibernate ORM's current userguide
     * before this method was written, not assumed), {@link #session()}{@code
     * .clear()} runs immediately after: any ResultSummary this same
     * request-scoped Session already loaded earlier (e.g. from the
     * generateResultSummary() calls that normally precede this one - see
     * ResultCalculationService.recalculateRanksForExam) would otherwise keep
     * returning its pre-rank in-memory classRank/overallRank (null) to
     * anything that reads it again later in the same request, silently
     * contradicting what the database now actually holds.
     */
    @Override
    public void recalculateRanks(Long examId) {
        session().createNativeQuery("""
                UPDATE result_summaries rs
                JOIN students st ON rs.student_id = st.id
                JOIN (
                    SELECT rs2.id AS summary_id,
                           RANK() OVER (PARTITION BY st2.current_section_id ORDER BY rs2.overall_percentage DESC) AS class_rank,
                           RANK() OVER (ORDER BY rs2.overall_percentage DESC) AS overall_rank
                    FROM result_summaries rs2
                    JOIN students st2 ON rs2.student_id = st2.id
                    WHERE rs2.exam_id = :examId
                ) ranked ON ranked.summary_id = rs.id
                SET rs.class_rank = ranked.class_rank,
                    rs.overall_rank = ranked.overall_rank
                WHERE rs.exam_id = :examId
                """)
                .setParameter("examId", examId)
                .executeUpdate();
        session().clear();
    }
}
