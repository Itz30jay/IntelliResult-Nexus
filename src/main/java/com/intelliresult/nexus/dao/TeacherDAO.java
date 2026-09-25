package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Teacher;

import java.util.List;
import java.util.Optional;

public interface TeacherDAO extends GenericDAO<Teacher, Long> {
    Optional<Teacher> findByEmployeeCode(String employeeCode);

    boolean existsByEmployeeCode(String employeeCode);

    Optional<Teacher> findByUserId(Long userId);
    List<Teacher> findByDepartment(Long departmentId);
}
