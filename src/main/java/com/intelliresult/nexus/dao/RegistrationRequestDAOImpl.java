package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.RegistrationRequest;
import com.intelliresult.nexus.entity.enums.RegistrationStatus;
import com.intelliresult.nexus.entity.enums.UserRole;

import java.util.List;

public class RegistrationRequestDAOImpl extends AbstractDAO<RegistrationRequest, Long> implements RegistrationRequestDAO {

    public RegistrationRequestDAOImpl() {
        super(RegistrationRequest.class);
    }

    @Override
    public List<RegistrationRequest> findByStatus(RegistrationStatus status) {
        return namedQuery("FROM RegistrationRequest WHERE status = :status ORDER BY submittedAt ASC")
                .setParameter("status", status)
                .getResultList();
    }

    @Override
    public long countByStatus(RegistrationStatus status) {
        return session()
                .createQuery("SELECT COUNT(r) FROM RegistrationRequest r WHERE r.status = :status", Long.class)
                .setParameter("status", status)
                .getSingleResult();
    }

    @Override
    public boolean existsPendingByIdentifier(String identifier, UserRole role) {
        Long count = session()
                .createQuery("SELECT COUNT(r) FROM RegistrationRequest r WHERE r.identifier = :identifier "
                        + "AND r.role = :role AND r.status = :status", Long.class)
                .setParameter("identifier", identifier)
                .setParameter("role", role)
                .setParameter("status", RegistrationStatus.PENDING)
                .getSingleResult();
        return count > 0;
    }
}
