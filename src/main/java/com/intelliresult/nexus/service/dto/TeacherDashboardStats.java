package com.intelliresult.nexus.service.dto;

import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Notice;
import com.intelliresult.nexus.entity.TeacherSubject;

import java.util.List;

/**
 * Everything the Teacher Dashboard (Sec. 28) needs, assembled once by
 * TeacherDashboardService - same "Service decides how the numbers are
 * computed, JSP just renders" boundary DashboardStats documents for Admin.
 * Deliberately narrower than Sec. 28's full bullet list: no "class
 * performance" or draft-marks count here - see
 * PHASE6A-TEACHER-DASHBOARD.md decision 2 for why those need either
 * calculated data that doesn't exist until Phase 7, or a section-scoped
 * tally that only makes sense on Phase 6b's own marks-entry screen, not a
 * cross-assignment dashboard aggregate.
 */
public record TeacherDashboardStats(
        List<TeacherSubject> activeAssignments,
        List<Exam> openForMarksEntry,
        long submittedAwaitingApproval,
        List<ActivityLog> recentActivity,
        List<Notice> recentNotices
) {
    public boolean hasAssignments() {
        return !activeAssignments.isEmpty();
    }

    public int activeAssignmentCount() {
        return activeAssignments.size();
    }

    public int openForMarksEntryCount() {
        return openForMarksEntry.size();
    }
}
