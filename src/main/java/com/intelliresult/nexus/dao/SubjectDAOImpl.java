package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Subject;

import java.util.List;
import java.util.Optional;

public class SubjectDAOImpl extends AbstractSoftDeletableDAO<Subject, Long> implements SubjectDAO {

    public SubjectDAOImpl() {
        super(Subject.class);
    }

    @Override
    public Optional<Subject> findByCode(String subjectCode) {
        return namedQuery("FROM Subject WHERE subjectCode = :code AND deleted = false")
                .setParameter("code", subjectCode)
                .uniqueResultOptional();
    }

    @Override
    public List<Subject> findBySemester(Long semesterId) {
        return namedQuery("FROM Subject WHERE semester.id = :semId AND deleted = false ORDER BY subjectCode")
                .setParameter("semId", semesterId)
                .getResultList();
    }
}
