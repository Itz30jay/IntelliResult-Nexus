package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Subject;

import java.util.List;
import java.util.Optional;

public interface SubjectDAO extends SoftDeletableDAO<Subject, Long> {
    Optional<Subject> findByCode(String subjectCode);
    List<Subject> findBySemester(Long semesterId);
}
