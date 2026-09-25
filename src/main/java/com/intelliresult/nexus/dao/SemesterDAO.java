package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Semester;

import java.util.List;
import java.util.Optional;

public interface SemesterDAO extends GenericDAO<Semester, Long> {
    List<Semester> findByCourseAndAcademicYear(Long courseId, Long academicYearId);
    Optional<Semester> findByCourseYearAndNumber(Long courseId, Long academicYearId, int semesterNumber);
}
