package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Student;

import java.util.List;
import java.util.Optional;

public class StudentDAOImpl extends AbstractDAO<Student, Long> implements StudentDAO {

    public StudentDAOImpl() {
        super(Student.class);
    }

    @Override
    public Optional<Student> findByRollNo(String rollNo) {
        return namedQuery("FROM Student WHERE rollNo = :rollNo")
                .setParameter("rollNo", rollNo)
                .uniqueResultOptional();
    }

    @Override
    public boolean existsByRollNo(String rollNo) {
        Long count = session()
                .createQuery("SELECT COUNT(s) FROM Student s WHERE s.rollNo = :rollNo", Long.class)
                .setParameter("rollNo", rollNo)
                .getSingleResult();
        return count > 0;
    }

    @Override
    public Optional<Student> findByUserId(Long userId) {
        return namedQuery("FROM Student WHERE user.id = :userId")
                .setParameter("userId", userId)
                .uniqueResultOptional();
    }

    @Override
    public List<Student> findBySection(Long sectionId) {
        return namedQuery("FROM Student WHERE currentSection.id = :sectionId ORDER BY rollNo")
                .setParameter("sectionId", sectionId)
                .getResultList();
    }

    @Override
    public List<Student> findByCourse(Long courseId) {
        return namedQuery("FROM Student WHERE course.id = :courseId ORDER BY rollNo")
                .setParameter("courseId", courseId)
                .getResultList();
    }
}
