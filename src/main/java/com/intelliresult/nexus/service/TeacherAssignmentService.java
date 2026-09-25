package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.dao.TeacherSubjectDAO;
import com.intelliresult.nexus.dao.TeacherSubjectDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.TeacherSubject;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.LocalTime;
import java.util.List;

/**
 * Enforces the "no duplicate active assignment" rule PHASE2-DATABASE.md #6
 * assigns to this layer (MySQL 8 can't express it as a constraint). Removal
 * is unassign(), not delete - preserves TeacherSubjectDAO's assign/unassign
 * history.
 * <p>
 * Upgrade: also enforces "a teacher cannot be assigned to two classes
 * whose time ranges overlap" - two classes without a time set at all never
 * conflict (there is nothing to compare), but the moment both a new and an
 * existing assignment have a start/end time, they may not overlap. There is
 * deliberately no day-of-week dimension to this check: the assignment
 * itself carries no day field (see TeacherSubject's own class Javadoc), so
 * a time range here is treated as a standing, every-occurrence commitment -
 * simpler and safer than pretending to check "unless it's a different day"
 * with no day data to actually check it against.
 */
public class TeacherAssignmentService {

    private final TeacherSubjectDAO teacherSubjectDAO = new TeacherSubjectDAOImpl();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final SubjectDAO subjectDAO = new SubjectDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final UserDAO userDAO = new UserDAOImpl();

    /** classStartTime/classEndTime are both optional (Sec. "Teacher My Classes... Timing") - a timetable slot can be added or corrected later via updateTiming() without re-creating the assignment. */
    public TeacherSubject assign(Long teacherId, Long subjectId, Long sectionId, LocalTime classStartTime,
                                  LocalTime classEndTime, Long assignedByAdminId) {
        Teacher teacher = teacherDAO.findById(teacherId).orElseThrow(() -> new ResourceNotFoundException("Teacher not found."));
        Subject subject = subjectDAO.findById(subjectId).orElseThrow(() -> new ResourceNotFoundException("Subject not found."));
        Section section = sectionDAO.findById(sectionId).orElseThrow(() -> new ResourceNotFoundException("Section not found."));

        if (teacherSubjectDAO.findActiveAssignment(teacherId, subjectId, sectionId).isPresent()) {
            throw new BusinessRuleException(teacher.getUser().getFullName() + " is already assigned to "
                    + subject.getSubjectCode() + " for section " + section.getName() + ".");
        }
        requireValidRange(classStartTime, classEndTime);
        requireNoTimeConflict(teacherId, classStartTime, classEndTime, null);

        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            TeacherSubject assignment = new TeacherSubject(teacher, subject, section,
                    userDAO.findById(assignedByAdminId).orElse(null));
            assignment.setClassStartTime(classStartTime);
            assignment.setClassEndTime(classEndTime);
            teacherSubjectDAO.save(assignment);
            tx.commit();
            return assignment;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    /** Corrects or adds a timetable slot on an assignment made before one was finalized - deliberately allowed on an ended assignment too (unassign() only stops it counting as active going forward; the historical record of when a class used to meet is still worth keeping accurate). Ended assignments are exempt from the conflict check (see requireNoTimeConflict) since they no longer occupy the teacher's schedule. */
    public void updateTiming(Long teacherSubjectId, LocalTime classStartTime, LocalTime classEndTime) {
        TeacherSubject assignment = teacherSubjectDAO.findById(teacherSubjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found."));
        requireValidRange(classStartTime, classEndTime);
        if (assignment.isActive()) {
            requireNoTimeConflict(assignment.getTeacher().getId(), classStartTime, classEndTime, teacherSubjectId);
        }

        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            assignment.setClassStartTime(classStartTime);
            assignment.setClassEndTime(classEndTime);
            teacherSubjectDAO.update(assignment);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    private void requireValidRange(LocalTime start, LocalTime end) {
        if (start != null && end != null && !end.isAfter(start)) {
            throw new BusinessRuleException("Class end time must be after the start time.");
        }
    }

    /**
     * Rejects the new/updated time range if it overlaps any of this
     * teacher's OTHER currently-active assignments - "for one time period
     * teacher if assign than no one can assign for same time another
     * subject." A range with no start/end set never conflicts (nothing to
     * compare against); excludeAssignmentId lets updateTiming() compare a
     * row against every OTHER assignment without it always conflicting with
     * itself.
     */
    private void requireNoTimeConflict(Long teacherId, LocalTime newStart, LocalTime newEnd, Long excludeAssignmentId) {
        if (newStart == null || newEnd == null) {
            return;
        }
        List<TeacherSubject> existing = teacherSubjectDAO.findActiveByTeacher(teacherId);
        for (TeacherSubject other : existing) {
            if (excludeAssignmentId != null && other.getId().equals(excludeAssignmentId)) {
                continue;
            }
            LocalTime otherStart = other.getClassStartTime();
            LocalTime otherEnd = other.getClassEndTime();
            if (otherStart == null || otherEnd == null) {
                continue;
            }
            // Standard half-open interval overlap test: [newStart, newEnd) intersects [otherStart, otherEnd) iff each starts before the other ends.
            boolean overlaps = newStart.isBefore(otherEnd) && otherStart.isBefore(newEnd);
            if (overlaps) {
                throw new BusinessRuleException(other.getTeacher().getUser().getFullName()
                        + " is already assigned to " + other.getSubject().getSubjectCode() + " (Section "
                        + other.getSection().getName() + ") from " + otherStart + " to " + otherEnd
                        + " - choose a non-overlapping time.");
            }
        }
    }

    public void unassign(Long teacherSubjectId, Long unassignedByAdminId) {
        TeacherSubject assignment = teacherSubjectDAO.findById(teacherSubjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found."));
        if (!assignment.isActive()) {
            throw new BusinessRuleException("This assignment has already ended.");
        }

        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            assignment.unassign(userDAO.findById(unassignedByAdminId).orElse(null));
            teacherSubjectDAO.update(assignment);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }
}
