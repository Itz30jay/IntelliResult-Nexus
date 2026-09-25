package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.RegistrationRequest;
import com.intelliresult.nexus.entity.enums.RegistrationStatus;
import com.intelliresult.nexus.entity.enums.UserRole;

import java.util.List;

public interface RegistrationRequestDAO extends GenericDAO<RegistrationRequest, Long> {

    List<RegistrationRequest> findByStatus(RegistrationStatus status);

    /** Backs the admin sidebar's pending-count badge (AuthenticationFilter) - a dedicated COUNT so that badge never has to load every pending row just to call .size() on it, the same reasoning GenericDAO.count() itself documents. */
    long countByStatus(RegistrationStatus status);

    /** Used by RegistrationService before inserting a new submission, so two people can never both have a live PENDING claim on the same roll_no/employee_code at once - the same "uniqueness enforced in the Service layer, not a DB constraint" pattern this schema already uses for teacher_subjects/grading_rules. */
    boolean existsPendingByIdentifier(String identifier, UserRole role);
}
