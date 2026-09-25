package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

/**
 * One student's aggregate result for one exam - total/percentage/SGPA/CGPA/
 * rank/pass-fail across every {@link Result} (subject) row that student has
 * in that exam. This table's existence is not a Phase 2 oversight: both
 * {@code Result}'s own class Javadoc and schema.sql's header comment on the
 * {@code results} table say so explicitly - "rank is an aggregate across
 * every subject a student took in one exam, not a per-subject-result fact...
 * whether it gets cached in a dedicated table is a decision Phase 7 is
 * better placed to make once the calculation engine's real query patterns
 * are known." This is that decision: yes, cached here, for the same reason
 * {@code Subject.totalMaxMarks} is a stored column rather than recomputed on
 * every read (Sec. 49) - SGPA/rank/CGPA are read far more often (every
 * dashboard load, every marksheet) than they change (once per exam
 * lifecycle stage), and ranking one student requires knowing every other
 * student's percentage in the same cohort, not just that one row.
 * <p>
 * {@code classRank}/{@code overallRank} have deliberately no Java setter -
 * see {@link com.intelliresult.nexus.dao.ResultSummaryDAOImpl#recalculateRanks}
 * for why they are populated exclusively by one bulk native-SQL statement,
 * the same "exactly one seam decides this value" reasoning
 * {@link Result#applyCalculatedScore} already establishes for its own five
 * fields.
 * <p>
 * No soft-delete triple (deleted/deletedBy/deletedAt): this row is entirely
 * derived from {@code Result} rows that already carry their own audit trail
 * (Sec. 13/48) - there is nothing here an admin "created by mistake" and
 * would want to restore from a recycle bin (Sec. 27 - deliberately not
 * applied to {@code result_history}/{@code activity_logs} for the same
 * "nothing to undelete, only to regenerate" reasoning). If a summary is ever
 * wrong, the fix is recomputing it from the underlying Results, not
 * restoring an old version of the cache.
 */
@Entity
@Table(name = "result_summaries", uniqueConstraints = @UniqueConstraint(columnNames = {"student_id", "exam_id"}))
public class ResultSummary extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = false)
    private Exam exam;

    /** How many of this student's Result rows for this exam already carry a calculated score (Sec. 47/54: never blend an uncalculated subject into an aggregate silently). */
    @Column(name = "subjects_counted", nullable = false)
    private int subjectsCounted;

    /** How many subjects exist in this exam's semester in total - {@code subjectsCounted < subjectsExpected} means this summary is provisional; see {@link #isComplete()}. */
    @Column(name = "subjects_expected", nullable = false)
    private int subjectsExpected;

    @Column(name = "total_obtained_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalObtainedMarks;
    @Column(name = "total_max_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalMaxMarks;
    /** Sum of {@code credits} across exactly the subjects counted above - stored (not re-derived from Result/Subject every time CGPA needs it) so a later semester's CGPA computation never has to re-walk every prior exam's Result rows just to learn how many credits that semester was worth (Sec. 49). */
    @Column(name = "total_credits", nullable = false, precision = 5, scale = 1)
    private BigDecimal totalCredits;

    @Column(name = "overall_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal overallPercentage;
    @Column(name = "overall_grade", length = 10)
    private String overallGrade;
    @Column(name = "overall_grade_point", precision = 4, scale = 2)
    private BigDecimal overallGradePoint;

    @Column(name = "sgpa", precision = 4, scale = 2)
    private BigDecimal sgpa;

    /**
     * Only ever populated when {@code exam.examType == FINAL_EXAMINATION} -
     * CGPA is a cumulative-across-*semesters* figure, and a Final Examination
     * is the one exam type that represents "this semester is done" (Sec. 9's
     * type list); populating it on every Unit Test would make CGPA jump
     * around with interim assessments in a way no real transcript does. Null
     * on every other exam type's summary, not zero - "not applicable here"
     * and "computed as zero" are different facts and must not be conflated.
     */
    @Column(name = "cgpa", precision = 4, scale = 2)
    private BigDecimal cgpa;

    @Column(name = "is_pass", nullable = false)
    private boolean pass;

    /** Rank within the student's own section, among this exam's other summaries. No Java setter - see the class Javadoc. */
    @Column(name = "class_rank")
    private Integer classRank;
    /** Rank across every section sitting this exam. No Java setter - see the class Javadoc. */
    @Column(name = "overall_rank")
    private Integer overallRank;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_exam_id")
    private Exam previousExam;
    @Column(name = "previous_percentage", precision = 5, scale = 2)
    private BigDecimal previousPercentage;
    /** {@code overallPercentage - previousPercentage}; positive is improvement, matching Sec. 15/55's plain-English reading rather than a signed direction someone has to remember. */
    @Column(name = "percentage_change", precision = 6, scale = 2)
    private BigDecimal percentageChange;

    protected ResultSummary() {
    }

    public ResultSummary(Student student, Exam exam) {
        this.student = student;
        this.exam = exam;
    }

    public Student getStudent() { return student; }
    public Exam getExam() { return exam; }

    public int getSubjectsCounted() { return subjectsCounted; }
    public int getSubjectsExpected() { return subjectsExpected; }

    /** Whether every subject this exam's semester defines already has a calculated Result for this student - the fact Phase 8's "prevent publishing incomplete results" (Sec. 47) needs, computed here rather than re-derived at every call site. */
    public boolean isComplete() {
        return subjectsExpected > 0 && subjectsCounted >= subjectsExpected;
    }

    public BigDecimal getTotalObtainedMarks() { return totalObtainedMarks; }
    public BigDecimal getTotalMaxMarks() { return totalMaxMarks; }
    public BigDecimal getTotalCredits() { return totalCredits; }
    public BigDecimal getOverallPercentage() { return overallPercentage; }
    public String getOverallGrade() { return overallGrade; }
    public BigDecimal getOverallGradePoint() { return overallGradePoint; }
    public BigDecimal getSgpa() { return sgpa; }
    public BigDecimal getCgpa() { return cgpa; }
    public boolean isPass() { return pass; }
    public Integer getClassRank() { return classRank; }
    public Integer getOverallRank() { return overallRank; }
    public Exam getPreviousExam() { return previousExam; }
    public BigDecimal getPreviousPercentage() { return previousPercentage; }
    public BigDecimal getPercentageChange() { return percentageChange; }

    /**
     * The only way the nine core aggregate fields get set - called
     * exclusively by ResultCalculationService, mirroring
     * {@link Result#applyCalculatedScore} one layer up: one seam, one
     * method, so "how a student's exam-level result gets aggregated" has
     * exactly one implementation to audit.
     */
    public void applyComputedResult(int subjectsCounted, int subjectsExpected,
                                     BigDecimal totalObtainedMarks, BigDecimal totalMaxMarks, BigDecimal totalCredits,
                                     BigDecimal overallPercentage, String overallGrade, BigDecimal overallGradePoint,
                                     BigDecimal sgpa, boolean pass) {
        this.subjectsCounted = subjectsCounted;
        this.subjectsExpected = subjectsExpected;
        this.totalObtainedMarks = totalObtainedMarks;
        this.totalMaxMarks = totalMaxMarks;
        this.totalCredits = totalCredits;
        this.overallPercentage = overallPercentage;
        this.overallGrade = overallGrade;
        this.overallGradePoint = overallGradePoint;
        this.sgpa = sgpa;
        this.pass = pass;
    }

    /** Separate from {@link #applyComputedResult}: CGPA is optional (FINAL_EXAMINATION only - see the field Javadoc) and computed from a different data source (this student's prior-semester summaries, not this exam's own subjects), so bundling it into the nine-argument method above would misleadingly suggest it is always supplied. */
    public void applyCgpa(BigDecimal cgpa) {
        this.cgpa = cgpa;
    }

    /** Also separate: improvement-vs-previous is optional (null when no prior exam exists to compare against - Sec. 56's empty-state case) and, like CGPA, sourced from a lookup rather than this exam's own subjects. */
    public void applyImprovement(Exam previousExam, BigDecimal previousPercentage, BigDecimal percentageChange) {
        this.previousExam = previousExam;
        this.previousPercentage = previousPercentage;
        this.percentageChange = percentageChange;
    }
}
