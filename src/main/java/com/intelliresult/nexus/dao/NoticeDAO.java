package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.Notice;
import com.intelliresult.nexus.entity.enums.UserRole;

import java.util.List;

public interface NoticeDAO extends SoftDeletableDAO<Notice, Long> {
    /** Published, non-expired, non-deleted notices whose audience includes the given role - what a student/teacher/admin actually sees on their notice board (Sec. 25). */
    List<Notice> findPublishedForAudience(UserRole role);

    /** Every notice regardless of audience/expiry, newest first - the admin dashboard's own oversight view (Sec. 5), distinct from findPublishedForAudience which is scoped to what one viewing role should see. */
    List<Notice> findRecent(int limit);
}
