package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.TeacherSubject;

import java.util.List;
import java.util.Optional;

public interface TeacherSubjectDAO extends GenericDAO<TeacherSubject, Long> {
    /** "My assigned subjects" for the teacher dashboard (Sec. 28) - only currently-active assignments, i.e. unassignedAt IS NULL. */
    List<TeacherSubject> findActiveByTeacher(Long teacherId);

    /** Who currently teaches this subject+section - used by admin's assignment view and by marks-entry authorization ("is this teacher actually assigned here"). */
    List<TeacherSubject> findActiveBySubjectAndSection(Long subjectId, Long sectionId);

    /** The exact row TeacherSubjectService (Phase 5) checks before creating a new assignment, to enforce "no second active assignment for the same teacher+subject+section" (PHASE2-DATABASE.md #6 - a rule MySQL 8 can't express as a constraint here). */
    Optional<TeacherSubject> findActiveAssignment(Long teacherId, Long subjectId, Long sectionId);
}
