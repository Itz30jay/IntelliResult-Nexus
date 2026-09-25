package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Semester;

import java.util.List;
import java.util.Optional;

public class SemesterDAOImpl extends AbstractDAO<Semester, Long> implements SemesterDAO {

    public SemesterDAOImpl() {
        super(Semester.class);
    }

    @Override
    public List<Semester> findByCourseAndAcademicYear(Long courseId, Long academicYearId) {
        return namedQuery("FROM Semester WHERE course.id = :courseId AND academicYear.id = :yearId ORDER BY semesterNumber")
                .setParameter("courseId", courseId)
                .setParameter("yearId", academicYearId)
                .getResultList();
    }

    @Override
    public Optional<Semester> findByCourseYearAndNumber(Long courseId, Long academicYearId, int semesterNumber) {
        return namedQuery("FROM Semester WHERE course.id = :courseId AND academicYear.id = :yearId AND semesterNumber = :num")
                .setParameter("courseId", courseId)
                .setParameter("yearId", academicYearId)
                .setParameter("num", (short) semesterNumber)
                .uniqueResultOptional();
    }
}
