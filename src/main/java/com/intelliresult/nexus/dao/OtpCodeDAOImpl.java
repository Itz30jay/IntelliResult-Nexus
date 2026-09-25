package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.OtpCode;

import java.util.Optional;

public class OtpCodeDAOImpl extends AbstractDAO<OtpCode, Long> implements OtpCodeDAO {

    public OtpCodeDAOImpl() {
        super(OtpCode.class);
    }

    @Override
    public Optional<OtpCode> findMostRecentByUser(Long userId) {
        return namedQuery("FROM OtpCode WHERE user.id = :userId ORDER BY createdAt DESC")
                .setParameter("userId", userId)
                .setMaxResults(1)
                .uniqueResultOptional();
    }
}
