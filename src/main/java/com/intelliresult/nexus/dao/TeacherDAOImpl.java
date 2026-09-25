package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Teacher;

import java.util.List;
import java.util.Optional;

public class TeacherDAOImpl extends AbstractDAO<Teacher, Long> implements TeacherDAO {

    public TeacherDAOImpl() {
        super(Teacher.class);
    }

    @Override
    public Optional<Teacher> findByEmployeeCode(String employeeCode) {
        return namedQuery("FROM Teacher WHERE employeeCode = :code")
                .setParameter("code", employeeCode)
                .uniqueResultOptional();
    }

    @Override
    public boolean existsByEmployeeCode(String employeeCode) {
        Long count = session()
                .createQuery("SELECT COUNT(t) FROM Teacher t WHERE t.employeeCode = :code", Long.class)
                .setParameter("code", employeeCode)
                .getSingleResult();
        return count > 0;
    }

    @Override
    public Optional<Teacher> findByUserId(Long userId) {
        return namedQuery("FROM Teacher WHERE user.id = :userId")
                .setParameter("userId", userId)
                .uniqueResultOptional();
    }

    @Override
    public List<Teacher> findByDepartment(Long departmentId) {
        return namedQuery("FROM Teacher WHERE department.id = :deptId")
                .setParameter("deptId", departmentId)
                .getResultList();
    }
}
