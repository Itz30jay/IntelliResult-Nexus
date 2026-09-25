package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Course;

import java.util.List;
import java.util.Optional;

public class CourseDAOImpl extends AbstractSoftDeletableDAO<Course, Long> implements CourseDAO {

    public CourseDAOImpl() {
        super(Course.class);
    }

    @Override
    public Optional<Course> findByCode(String code) {
        return namedQuery("FROM Course WHERE code = :code AND deleted = false")
                .setParameter("code", code)
                .uniqueResultOptional();
    }

    @Override
    public boolean existsByCode(String code) {
        Long count = session()
                .createQuery("SELECT COUNT(c) FROM Course c WHERE c.code = :code AND c.deleted = false", Long.class)
                .setParameter("code", code)
                .getSingleResult();
        return count > 0;
    }

    @Override
    public List<Course> findByDepartment(Long departmentId) {
        return namedQuery("FROM Course WHERE department.id = :deptId AND deleted = false")
                .setParameter("deptId", departmentId)
                .getResultList();
    }
}
