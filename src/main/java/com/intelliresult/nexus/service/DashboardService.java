package com.intelliresult.nexus.service;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.NoticeDAO;
import com.intelliresult.nexus.dao.NoticeDAOImpl;
import com.intelliresult.nexus.dao.ResultDAO;
import com.intelliresult.nexus.dao.ResultDAOImpl;
import com.intelliresult.nexus.dao.RevaluationDAO;
import com.intelliresult.nexus.dao.RevaluationDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.enums.ExamStatus;
import com.intelliresult.nexus.entity.enums.RevaluationStatus;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.service.dto.DashboardStats;


/**
 * Read-only by nature - every method here is a query, never a write, so
 * unlike AuthenticationService this class never opens a transaction; a
 * Session already bound by HibernateSessionFilter is all any of these DAO
 * calls need.
 */
public class DashboardService {

    private static final int RECENT_ACTIVITY_LIMIT = 8;
    private static final int RECENT_NOTICES_LIMIT = 5;
    private static final int TOP_PERFORMERS_LIMIT = 5;

    private final UserDAO userDAO = new UserDAOImpl();
    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final SubjectDAO subjectDAO = new SubjectDAOImpl();
    private final ExamDAO examDAO = new ExamDAOImpl();
    private final ResultDAO resultDAO = new ResultDAOImpl();
    private final RevaluationDAO revaluationDAO = new RevaluationDAOImpl();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();
    private final NoticeDAO noticeDAO = new NoticeDAOImpl();

    public DashboardStats loadStats() {
        long activeExams = examDAO.findByStatus(ExamStatus.ACTIVE).size()
                + examDAO.findByStatus(ExamStatus.SUBMISSION).size()
                + examDAO.findByStatus(ExamStatus.APPROVAL).size();

        return new DashboardStats(
                userDAO.countByRole(UserRole.STUDENT),
                userDAO.countByRole(UserRole.TEACHER),
                subjectDAO.count(),
                courseDAO.count(),
                activeExams,
                resultDAO.countByStatus(ResultStatus.PUBLISHED),
                resultDAO.countByStatus(ResultStatus.SUBMITTED),
                revaluationDAO.findByStatus(RevaluationStatus.PENDING).size(),
                resultDAO.countPublishedByPass(true),
                resultDAO.countPublishedByPass(false),
                resultDAO.subjectPerformance(),
                resultDAO.gradeDistribution(),
                resultDAO.topPerformers(TOP_PERFORMERS_LIMIT),
                activityLogDAO.findRecent(RECENT_ACTIVITY_LIMIT),
                noticeDAO.findRecent(RECENT_NOTICES_LIMIT)
        );
    }
}
