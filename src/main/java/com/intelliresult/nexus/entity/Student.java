package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * One-to-one academic extension of a User. No department_id here - it is
 * reachable via course.department, and storing it twice would let the two
 * silently disagree (PHASE2-DATABASE.md, schema.sql header note). No
 * soft-delete fields either: a student is "deleted" by soft-deleting their
 * User row, so there is exactly one place that answers "is this person gone"
 * for a user of any role - see SoftDeletableEntity's own note on this and
 * the User entity, which is the one that actually carries deleted/deletedBy/
 * deletedAt for every role.
 */
@Entity
@Table(name = "students")
public class Student extends TimestampedEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "roll_no", nullable = false, length = 30, unique = true)
    private String rollNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "current_section_id")
    private Section currentSection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "admission_year_id")
    private AcademicYear admissionYear;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Column(name = "contact_number", length = 20)
    private String contactNumber;

    protected Student() {
    }

    public Student(User user, String rollNo, Course course) {
        this.user = user;
        this.rollNo = rollNo;
        this.course = course;
    }

    public User getUser() { return user; }
    public String getRollNo() { return rollNo; }
    public void setRollNo(String rollNo) { this.rollNo = rollNo; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public Section getCurrentSection() { return currentSection; }
    public void setCurrentSection(Section currentSection) { this.currentSection = currentSection; }
    public AcademicYear getAdmissionYear() { return admissionYear; }
    public void setAdmissionYear(AcademicYear admissionYear) { this.admissionYear = admissionYear; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getContactNumber() { return contactNumber; }
    public void setContactNumber(String contactNumber) { this.contactNumber = contactNumber; }
}
