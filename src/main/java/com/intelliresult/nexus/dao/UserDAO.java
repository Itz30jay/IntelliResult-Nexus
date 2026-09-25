package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;

import java.util.List;
import java.util.Optional;

public interface UserDAO extends SoftDeletableDAO<User, Long> {
    /** The one lookup every login attempt starts with (Phase 4). */
    Optional<User> findByEmail(String email);

    List<User> findByRole(UserRole role);

    /** Dashboard "Total Students/Teachers" cards - a COUNT query, not findByRole(role).size(), so the user list itself never has to be materialized just to report a number. */
    long countByRole(UserRole role);

    /** Cheaper than findByEmail(...).isPresent() for pure existence checks (registration/import validation) - no entity hydration, just a scalar count. */
    boolean existsByEmail(String email);
}
