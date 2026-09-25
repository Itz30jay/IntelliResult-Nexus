package com.intelliresult.nexus.entity;

import com.intelliresult.nexus.entity.enums.ExamStatus;
import com.intelliresult.nexus.entity.enums.ExamType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * defaultMaxMarks is a reference/display value, NOT authoritative -
 * subjects.totalMaxMarks is - see PHASE2-DATABASE.md #3 for why two
 * subjects in the same exam can have different real maximums.
 * <p>
 * Upgrade: startDate/endDate (LocalDate) widened to startTime/endTime
 * (LocalDateTime) - the literal "Start Time / End Time (date + time)" ask -
 * and attemptLimit added (how many times this exam may be conducted, e.g. a
 * supplementary sitting). attemptLimit is captured/validated/displayed only;
 * it deliberately does not yet gate repeat submissions, which would require
 * adding an attempt dimension to results' own (student, exam, subject)
 * uniqueness - a schema-key change with much wider blast radius than this
 * field's own addition, left as a flagged follow-up rather than forced
 * through here.
 */
@Entity
@Table(name = "exams")
public class Exam extends SoftDeletableEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "exam_type", nullable = false, length = 30)
    private ExamType examType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "attempt_limit", nullable = false)
    private int attemptLimit = 1;

    @Column(name = "default_max_marks", precision = 6, scale = 2)
    private BigDecimal defaultMaxMarks;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ExamStatus status = ExamStatus.CREATED;

    protected Exam() {
    }

    public Exam(String name, ExamType examType, AcademicYear academicYear, Semester semester,
                LocalDateTime startTime, LocalDateTime endTime, int attemptLimit) {
        this.name = name;
        this.examType = examType;
        this.academicYear = academicYear;
        this.semester = semester;
        this.startTime = startTime;
        this.endTime = endTime;
        this.attemptLimit = attemptLimit;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public ExamType getExamType() { return examType; }
    public AcademicYear getAcademicYear() { return academicYear; }
    public Semester getSemester() { return semester; }
    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }
    public int getAttemptLimit() { return attemptLimit; }
    public void setAttemptLimit(int attemptLimit) { this.attemptLimit = attemptLimit; }
    public BigDecimal getDefaultMaxMarks() { return defaultMaxMarks; }
    public void setDefaultMaxMarks(BigDecimal defaultMaxMarks) { this.defaultMaxMarks = defaultMaxMarks; }
    public ExamStatus getStatus() { return status; }
    public void setStatus(ExamStatus status) { this.status = status; }
}
