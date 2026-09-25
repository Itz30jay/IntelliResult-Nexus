package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * The teacher-subject assignment junction (Sec. 8). assignedBy/unassignedBy
 * ARE mapped as full @ManyToOne User relationships - unlike
 * SoftDeletableEntity.deletedBy's plain Long - because assignment history is
 * a primary, frequently-displayed feature here (Sec. 8's "View assignments"),
 * not an occasional recycle-bin audit lookup, so resolving the assigner's
 * name via the relationship directly is worth it.
 * "Removing" an assignment sets unassignedAt rather than deleting the row -
 * see PHASE2-DATABASE.md #6, including why "no second active assignment for
 * the same teacher+subject+section" is a Service-layer rule (Phase 5) and
 * not a constraint MySQL 8 can express here.
 * <p>
 * Upgrade: classStartTime/classEndTime - Sec. "Teacher My Classes must show
 * Class Name + Timing" - live here rather than on Section, because timing
 * is a property of one taught class (this teacher, this subject, this
 * section), and two different subjects taught to the SAME section
 * legitimately meet at different times. Both nullable: an assignment made
 * before a timetable is finalized is still a valid assignment, just one
 * that displays as "Not scheduled" until timing is added via an edit.
 * A day-of-week field was deliberately not added alongside these - see
 * TeacherAssignmentService's own note on why conflict detection is
 * time-range-only, which is what actually needed a data point here.
 */
@Entity
@Table(name = "teacher_subjects")
public class TeacherSubject extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "teacher_id", nullable = false)
    private Teacher teacher;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id", nullable = false)
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(name = "class_start_time")
    private LocalTime classStartTime;

    @Column(name = "class_end_time")
    private LocalTime classEndTime;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by", nullable = false)
    private User assignedBy;

    @CreationTimestamp
    @Column(name = "assigned_at", nullable = false, updatable = false)
    private LocalDateTime assignedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "unassigned_by")
    private User unassignedBy;

    @Column(name = "unassigned_at")
    private LocalDateTime unassignedAt;

    protected TeacherSubject() {
    }

    public TeacherSubject(Teacher teacher, Subject subject, Section section, User assignedBy) {
        this.teacher = teacher;
        this.subject = subject;
        this.section = section;
        this.assignedBy = assignedBy;
    }

    public Teacher getTeacher() { return teacher; }
    public Subject getSubject() { return subject; }
    public Section getSection() { return section; }
    public LocalTime getClassStartTime() { return classStartTime; }
    public void setClassStartTime(LocalTime classStartTime) { this.classStartTime = classStartTime; }
    public LocalTime getClassEndTime() { return classEndTime; }
    public void setClassEndTime(LocalTime classEndTime) { this.classEndTime = classEndTime; }
    public User getAssignedBy() { return assignedBy; }
    public LocalDateTime getAssignedAt() { return assignedAt; }
    public User getUnassignedBy() { return unassignedBy; }
    public LocalDateTime getUnassignedAt() { return unassignedAt; }
    public boolean isActive() { return unassignedAt == null; }

    /** Ends this assignment by stamping who and when, rather than deleting the row - preserves it as history per the class-level note. */
    public void unassign(User unassignedBy) {
        this.unassignedBy = unassignedBy;
        this.unassignedAt = LocalDateTime.now();
    }
}
