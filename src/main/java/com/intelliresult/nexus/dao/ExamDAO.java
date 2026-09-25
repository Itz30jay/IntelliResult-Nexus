package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.enums.ExamStatus;

import java.util.List;

public interface ExamDAO extends SoftDeletableDAO<Exam, Long> {
    List<Exam> findBySemester(Long semesterId);
    List<Exam> findByStatus(ExamStatus status);
    List<Exam> findByAcademicYear(Long academicYearId);
}
