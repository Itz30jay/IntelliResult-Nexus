package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Department;

import java.util.Optional;

public class DepartmentDAOImpl extends AbstractSoftDeletableDAO<Department, Long> implements DepartmentDAO {

    public DepartmentDAOImpl() {
        super(Department.class);
    }

    @Override
    public Optional<Department> findByCode(String code) {
        return namedQuery("FROM Department WHERE code = :code AND deleted = false")
                .setParameter("code", code)
                .uniqueResultOptional();
    }

    @Override
    public boolean existsByCode(String code) {
        Long count = session()
                .createQuery("SELECT COUNT(d) FROM Department d WHERE d.code = :code AND d.deleted = false", Long.class)
                .setParameter("code", code)
                .getSingleResult();
        return count > 0;
    }
}
