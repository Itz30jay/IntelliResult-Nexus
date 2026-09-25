package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Notification;

import java.util.List;

public class NotificationDAOImpl extends AbstractDAO<Notification, Long> implements NotificationDAO {

    public NotificationDAOImpl() {
        super(Notification.class);
    }

    @Override
    public List<Notification> findByUser(Long userId) {
        return namedQuery("FROM Notification WHERE user.id = :userId ORDER BY createdAt DESC")
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public List<Notification> findUnreadByUser(Long userId) {
        return namedQuery("FROM Notification WHERE user.id = :userId AND read = false ORDER BY createdAt DESC")
                .setParameter("userId", userId)
                .getResultList();
    }

    @Override
    public long countUnreadByUser(Long userId) {
        return session()
                .createQuery("SELECT COUNT(n) FROM Notification n WHERE n.user.id = :userId AND n.read = false", Long.class)
                .setParameter("userId", userId)
                .getSingleResult();
    }
}
