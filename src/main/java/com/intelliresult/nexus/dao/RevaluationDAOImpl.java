package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.RevaluationRequest;
import com.intelliresult.nexus.entity.enums.RevaluationStatus;

import java.util.List;

public class RevaluationDAOImpl extends AbstractDAO<RevaluationRequest, Long> implements RevaluationDAO {

    public RevaluationDAOImpl() {
        super(RevaluationRequest.class);
    }

    @Override
    public List<RevaluationRequest> findByStudent(Long studentId) {
        return namedQuery("FROM RevaluationRequest WHERE student.id = :studentId ORDER BY requestedAt DESC")
                .setParameter("studentId", studentId)
                .getResultList();
    }

    @Override
    public List<RevaluationRequest> findByStatus(RevaluationStatus status) {
        return namedQuery("FROM RevaluationRequest WHERE status = :status ORDER BY requestedAt")
                .setParameter("status", status)
                .getResultList();
    }

    @Override
    public List<RevaluationRequest> findByAssignedTeacher(Long teacherId) {
        return namedQuery("FROM RevaluationRequest WHERE assignedTeacher.id = :teacherId ORDER BY assignedAt DESC")
                .setParameter("teacherId", teacherId)
                .getResultList();
    }
}
