package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.enums.UserRole;

import java.time.LocalDateTime;
import java.util.List;

public class ActivityLogDAOImpl extends AbstractDAO<ActivityLog, Long> implements ActivityLogDAO {

    public ActivityLogDAOImpl() {
        super(ActivityLog.class);
    }

    @Override
    public List<ActivityLog> findByUser(Long userId) {
        return namedQuery("FROM ActivityLog WHERE user.id = :userId ORDER BY createdAt DESC")
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public List<ActivityLog> findByAction(String action) {
        return namedQuery("FROM ActivityLog WHERE action = :action ORDER BY createdAt DESC")
                .setParameter("action", action)
                .getResultList();
    }

    @Override
    public List<ActivityLog> findByDateRange(LocalDateTime start, LocalDateTime end) {
        return namedQuery("FROM ActivityLog WHERE createdAt BETWEEN :start AND :end ORDER BY createdAt DESC")
                .setParameter("start", start)
                .setParameter("end", end)
                .getResultList();
    }

    @Override
    public List<ActivityLog> findByUserRole(UserRole role) {
        return namedQuery("FROM ActivityLog WHERE user.role = :role ORDER BY createdAt DESC")
                .setParameter("role", role)
                .getResultList();
    }

    @Override
    public List<ActivityLog> findRecent(int limit) {
        return namedQuery("FROM ActivityLog ORDER BY createdAt DESC")
                .setMaxResults(limit)
                .getResultList();
    }
}
