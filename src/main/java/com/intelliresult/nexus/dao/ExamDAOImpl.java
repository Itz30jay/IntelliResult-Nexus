package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.enums.ExamStatus;

import java.util.List;

public class ExamDAOImpl extends AbstractSoftDeletableDAO<Exam, Long> implements ExamDAO {

    public ExamDAOImpl() {
        super(Exam.class);
    }

    @Override
    public List<Exam> findBySemester(Long semesterId) {
        return namedQuery("FROM Exam WHERE semester.id = :semId AND deleted = false ORDER BY startTime")
                .setParameter("semId", semesterId)
                .getResultList();
    }

    @Override
    public List<Exam> findByStatus(ExamStatus status) {
        return namedQuery("FROM Exam WHERE status = :status AND deleted = false ORDER BY startTime")
                .setParameter("status", status)
                .getResultList();
    }

    @Override
    public List<Exam> findByAcademicYear(Long academicYearId) {
        return namedQuery("FROM Exam WHERE academicYear.id = :yearId AND deleted = false ORDER BY startTime")
                .setParameter("yearId", academicYearId)
                .getResultList();
    }
}
