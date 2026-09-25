package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.math.BigDecimal;

/**
 * has_theory/has_practical/has_internal (not a single subjectType enum) -
 * see PHASE2-DATABASE.md #2 for why a real subject can have more than one
 * mark component simultaneously.
 * totalMaxMarks is mapped read-only (insertable=false, updatable=false) with
 * @Generated(event = {EventType.INSERT, EventType.UPDATE}): the column is a MySQL
 * GENERATED ALWAYS AS (...) STORED expression (see schema.sql), so Hibernate
 * must never try to write it and must re-select it after every insert/update
 * to pick up the value MySQL computed - exactly what @Generated instructs it
 * to do, confirmed against Hibernate's own current documentation rather than
 * assumed from an older API shape.
 */
@Entity
@Table(name = "subjects")
public class Subject extends SoftDeletableEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id", nullable = false)
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(name = "subject_code", nullable = false, length = 20, unique = true)
    private String subjectCode;

    @Column(name = "subject_name", nullable = false, length = 150)
    private String subjectName;

    @Column(name = "credits", precision = 3, scale = 1)
    private BigDecimal credits;

    @Column(name = "has_theory", nullable = false)
    private boolean hasTheory = true;
    @Column(name = "theory_max_marks", precision = 6, scale = 2)
    private BigDecimal theoryMaxMarks;
    @Column(name = "theory_passing_marks", precision = 6, scale = 2)
    private BigDecimal theoryPassingMarks;

    @Column(name = "has_practical", nullable = false)
    private boolean hasPractical = false;
    @Column(name = "practical_max_marks", precision = 6, scale = 2)
    private BigDecimal practicalMaxMarks;
    @Column(name = "practical_passing_marks", precision = 6, scale = 2)
    private BigDecimal practicalPassingMarks;

    @Column(name = "has_internal", nullable = false)
    private boolean hasInternal = false;
    @Column(name = "internal_max_marks", precision = 6, scale = 2)
    private BigDecimal internalMaxMarks;
    @Column(name = "internal_passing_marks", precision = 6, scale = 2)
    private BigDecimal internalPassingMarks;

    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "total_max_marks", insertable = false, updatable = false, precision = 7, scale = 2)
    private BigDecimal totalMaxMarks;

    protected Subject() {
    }

    public Subject(Semester semester, Department department, String subjectCode, String subjectName) {
        this.semester = semester;
        this.department = department;
        this.subjectCode = subjectCode;
        this.subjectName = subjectName;
    }

    public Semester getSemester() { return semester; }
    public Department getDepartment() { return department; }
    public String getSubjectCode() { return subjectCode; }
    public void setSubjectCode(String subjectCode) { this.subjectCode = subjectCode; }
    public String getSubjectName() { return subjectName; }
    public void setSubjectName(String subjectName) { this.subjectName = subjectName; }
    public BigDecimal getCredits() { return credits; }
    public void setCredits(BigDecimal credits) { this.credits = credits; }

    public boolean isHasTheory() { return hasTheory; }
    public BigDecimal getTheoryMaxMarks() { return theoryMaxMarks; }
    public BigDecimal getTheoryPassingMarks() { return theoryPassingMarks; }

    /** Sets all three theory-component fields together so hasTheory can never end up true with null marks (or vice versa) - exactly the inconsistency chk_subjects_theory_marks guards against at the DB level. */
    public void setTheoryComponent(BigDecimal maxMarks, BigDecimal passingMarks) {
        this.hasTheory = true;
        this.theoryMaxMarks = maxMarks;
        this.theoryPassingMarks = passingMarks;
    }
    public void clearTheoryComponent() {
        this.hasTheory = false;
        this.theoryMaxMarks = null;
        this.theoryPassingMarks = null;
    }

    public boolean isHasPractical() { return hasPractical; }
    public BigDecimal getPracticalMaxMarks() { return practicalMaxMarks; }
    public BigDecimal getPracticalPassingMarks() { return practicalPassingMarks; }
    public void setPracticalComponent(BigDecimal maxMarks, BigDecimal passingMarks) {
        this.hasPractical = true;
        this.practicalMaxMarks = maxMarks;
        this.practicalPassingMarks = passingMarks;
    }
    public void clearPracticalComponent() {
        this.hasPractical = false;
        this.practicalMaxMarks = null;
        this.practicalPassingMarks = null;
    }

    public boolean isHasInternal() { return hasInternal; }
    public BigDecimal getInternalMaxMarks() { return internalMaxMarks; }
    public BigDecimal getInternalPassingMarks() { return internalPassingMarks; }
    public void setInternalComponent(BigDecimal maxMarks, BigDecimal passingMarks) {
        this.hasInternal = true;
        this.internalMaxMarks = maxMarks;
        this.internalPassingMarks = passingMarks;
    }
    public void clearInternalComponent() {
        this.hasInternal = false;
        this.internalMaxMarks = null;
        this.internalPassingMarks = null;
    }

    /** Database-computed; there is no setter on purpose - see the class-level note on @Generated. */
    public BigDecimal getTotalMaxMarks() { return totalMaxMarks; }
}

