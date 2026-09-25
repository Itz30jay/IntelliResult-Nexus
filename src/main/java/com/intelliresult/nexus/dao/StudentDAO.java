package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Student;

import java.util.List;
import java.util.Optional;

public interface StudentDAO extends GenericDAO<Student, Long> {
    Optional<Student> findByRollNo(String rollNo);

    boolean existsByRollNo(String rollNo);

    /** Resolves "the logged-in User" (Phase 4's session) to their academic Student profile - needed on essentially every student-facing page. */
    Optional<Student> findByUserId(Long userId);

    List<Student> findBySection(Long sectionId);
    List<Student> findByCourse(Long courseId);
}
