package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.PasswordResetRequest;
import com.intelliresult.nexus.entity.enums.PasswordResetStatus;

import java.util.List;

public class PasswordResetRequestDAOImpl extends AbstractDAO<PasswordResetRequest, Long> implements PasswordResetRequestDAO {

    public PasswordResetRequestDAOImpl() {
        super(PasswordResetRequest.class);
    }

    @Override
    public List<PasswordResetRequest> findByStatus(PasswordResetStatus status) {
        return namedQuery("FROM PasswordResetRequest WHERE status = :status ORDER BY createdAt ASC")
                .setParameter("status", status)
                .getResultList();
    }

    @Override
    public long countByStatus(PasswordResetStatus status) {
        return session()
                .createQuery("SELECT COUNT(p) FROM PasswordResetRequest p WHERE p.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }
}
