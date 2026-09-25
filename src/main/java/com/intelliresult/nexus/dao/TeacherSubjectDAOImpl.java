package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.TeacherSubject;

import java.util.List;
import java.util.Optional;

public class TeacherSubjectDAOImpl extends AbstractDAO<TeacherSubject, Long> implements TeacherSubjectDAO {

    public TeacherSubjectDAOImpl() {
        super(TeacherSubject.class);
    }

    @Override
    public List<TeacherSubject> findActiveByTeacher(Long teacherId) {
        return namedQuery("FROM TeacherSubject WHERE teacher.id = :teacherId AND unassignedAt IS NULL")
                .setParameter("teacherId", teacherId)
                .getResultList();
    }

    @Override
    public List<TeacherSubject> findActiveBySubjectAndSection(Long subjectId, Long sectionId) {
        return namedQuery("FROM TeacherSubject WHERE subject.id = :subjectId AND section.id = :sectionId AND unassignedAt IS NULL")
                .setParameter("subjectId", subjectId)
                .setParameter("sectionId", sectionId)
                .getResultList();
    }

    @Override
    public Optional<TeacherSubject> findActiveAssignment(Long teacherId, Long subjectId, Long sectionId) {
        return namedQuery("FROM TeacherSubject WHERE teacher.id = :teacherId AND subject.id = :subjectId "
                        + "AND section.id = :sectionId AND unassignedAt IS NULL")
                .setParameter("teacherId", teacherId)
                .setParameter("subjectId", subjectId)
                .setParameter("sectionId", sectionId)
                .uniqueResultOptional();
    }
}
