package com.intelliresult.nexus.service;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.NoticeDAO;
import com.intelliresult.nexus.dao.NoticeDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.TeacherSubjectDAO;
import com.intelliresult.nexus.dao.TeacherSubjectDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Notice;
import com.intelliresult.nexus.entity.TeacherSubject;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.service.dto.TeacherDashboardStats;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Read-only, same as DashboardService - never opens a transaction, only
 * queries. "Active exams" is scoped to the distinct semesters this
 * teacher's active assignments actually touch (not every exam
 * system-wide), then filtered to ExamStatus.isOpenForMarksEntry() - the
 * first real caller of that method since it was added, with no consumer
 * yet, in Phase 5e.
 */
public class TeacherDashboardService {

    private static final int RECENT_ACTIVITY_LIMIT = 8;
    private static final int RECENT_NOTICES_LIMIT = 5;

    private final TeacherSubjectDAO teacherSubjectDAO = new TeacherSubjectDAOImpl();
    private final ExamDAO examDAO = new ExamDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();
    private final NoticeDAO noticeDAO = new NoticeDAOImpl();

    public TeacherDashboardStats loadStats(Long teacherId, Long userId) {
        List<TeacherSubject> assignments = teacherSubjectDAO.findActiveByTeacher(teacherId);

        // LinkedHashSet: a handful of distinct semesters at most for any
        // real teacher, so one findBySemester() call per semester (rather
        // than a new batch DAO method) is the same "fine at this data
        // volume" call GradingRuleService.findAllForYear (Phase 5e) made.
        Set<Long> semesterIds = assignments.stream()
                .map(a -> a.getSubject().getSemester().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<Exam> openExams = semesterIds.stream()
                .flatMap(semesterId -> examDAO.findBySemester(semesterId).stream())
                .filter(exam -> exam.getStatus().isOpenForMarksEntry())
                .toList();

        long submittedAwaitingApproval = resultDAO.findBySubmittedByAndStatus(userId, ResultStatus.SUBMITTED).size();

        List<ActivityLog> recentActivity = activityLogDAO.findByUser(userId).stream()
                .limit(RECENT_ACTIVITY_LIMIT)
                .toList();

        List<Notice> recentNotices = noticeDAO.findPublishedForAudience(UserRole.TEACHER).stream()
                .limit(RECENT_NOTICES_LIMIT)
                .toList();

        return new TeacherDashboardStats(assignments, openExams, submittedAwaitingApproval, recentActivity, recentNotices);
    }
}
