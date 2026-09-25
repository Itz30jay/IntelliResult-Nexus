package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Department;

import java.util.Optional;

public interface DepartmentDAO extends SoftDeletableDAO<Department, Long> {
    Optional<Department> findByCode(String code);

    boolean existsByCode(String code);
}
