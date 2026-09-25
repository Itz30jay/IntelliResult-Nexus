package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Course;

import java.util.List;
import java.util.Optional;

public interface CourseDAO extends SoftDeletableDAO<Course, Long> {
    Optional<Course> findByCode(String code);
    boolean existsByCode(String code);
    List<Course> findByDepartment(Long departmentId);
}
