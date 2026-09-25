package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.PasswordResetRequest;
import com.intelliresult.nexus.entity.enums.PasswordResetStatus;

import java.util.List;

public interface PasswordResetRequestDAO extends GenericDAO<PasswordResetRequest, Long> {
    List<PasswordResetRequest> findByStatus(PasswordResetStatus status);

    /** Backs the admin sidebar's pending-count badge, same reasoning as RegistrationRequestDAO.countByStatus. */
    long countByStatus(PasswordResetStatus status);
}
