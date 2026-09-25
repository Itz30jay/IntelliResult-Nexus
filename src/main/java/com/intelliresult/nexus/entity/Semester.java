package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDate;

/** One running instance of "semester N of course X in academic year Y" - see PHASE2-DATABASE.md #1 for why this is a full entity rather than a bare number. */
@Entity
@Table(name = "semesters", uniqueConstraints = @UniqueConstraint(columnNames = {"course_id", "academic_year_id", "semester_number"}))
public class Semester extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(name = "semester_number", nullable = false)
    private short semesterNumber;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    protected Semester() {
    }

    public Semester(Course course, AcademicYear academicYear, int semesterNumber) {
        this.course = course;
        this.academicYear = academicYear;
        this.semesterNumber = (short) semesterNumber;
    }

    public Course getCourse() { return course; }
    public AcademicYear getAcademicYear() { return academicYear; }
    public int getSemesterNumber() { return semesterNumber; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
