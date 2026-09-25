package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Notification;

import java.util.List;

public interface NotificationDAO extends GenericDAO<Notification, Long> {
    List<Notification> findByUser(Long userId);

    /** Feeds both the unread-count badge and the "unread notifications" list (Sec. 23). */
    List<Notification> findUnreadByUser(Long userId);

    /** The badge itself (Sec. 23) - a COUNT query, not findUnreadByUser(id).size(), so AuthenticationFilter's per-request check (every authenticated page needs this number) never has to hydrate the notifications themselves just to report how many there are. */
    long countUnreadByUser(Long userId);
}
