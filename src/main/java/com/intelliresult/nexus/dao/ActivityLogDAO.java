package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.enums.UserRole;

import java.time.LocalDateTime;
import java.util.List;

public interface ActivityLogDAO extends GenericDAO<ActivityLog, Long> {
    List<ActivityLog> findByUser(Long userId);
    List<ActivityLog> findByAction(String action);

    /** Sec. 26's audit-log viewer filtering by date range - role filtering (also required by Sec. 26) is a join through user.role, added on the implementation as a second, explicitly-named method rather than overloading this one with an optional/nullable role parameter. */
    List<ActivityLog> findByDateRange(LocalDateTime start, LocalDateTime end);

    /** The role-filter half of Sec. 26 this interface's own findByDateRange comment already promised - see AdminActivityLogServlet, its first real caller. */
    List<ActivityLog> findByUserRole(UserRole role);

    /** Most recent N entries system-wide - the dashboard's activity timeline (Sec. 5). */
    List<ActivityLog> findRecent(int limit);
}
