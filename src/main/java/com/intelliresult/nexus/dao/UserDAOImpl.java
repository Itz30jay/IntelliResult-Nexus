package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;

import java.util.List;
import java.util.Optional;

public class UserDAOImpl extends AbstractSoftDeletableDAO<User, Long> implements UserDAO {

    public UserDAOImpl() {
        super(User.class);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return namedQuery("FROM User WHERE email = :email AND deleted = false")
                .setParameter("email", email)
                .uniqueResultOptional();
    }

    @Override
    public List<User> findByRole(UserRole role) {
        return namedQuery("FROM User WHERE role = :role AND deleted = false")
                .setParameter("role", role)
                .getResultList();
    }

    @Override
    public long countByRole(UserRole role) {
        return session()
                .createQuery("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.deleted = false", Long.class)
                .setParameter("role", role)
                .getSingleResult();
    }

    @Override
    public boolean existsByEmail(String email) {
        Long count = session()
                .createQuery("SELECT COUNT(u) FROM User u WHERE u.email = :email AND u.deleted = false", Long.class)
                .setParameter("email", email)
                .getSingleResult();
        return count > 0;
    }
}
