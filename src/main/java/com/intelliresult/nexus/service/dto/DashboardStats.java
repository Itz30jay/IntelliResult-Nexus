package com.intelliresult.nexus.service.dto;

import com.intelliresult.nexus.dao.dto.GradeDistributionDTO;
import com.intelliresult.nexus.dao.dto.StudentPerformanceDTO;
import com.intelliresult.nexus.dao.dto.SubjectPerformanceDTO;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.Notice;

import java.util.List;

/**
 * Everything the admin dashboard (Sec. 5) needs, assembled once by
 * DashboardService rather than the JSP making its own DAO calls - keeps
 * "how these numbers are computed" in the Service layer and the JSP a pure
 * renderer, per Sec. 37's Controller/JSP boundary.
 */
public record DashboardStats(
        long totalStudents,
        long totalTeachers,
        long totalSubjects,
        long totalCourses,
        long activeExams,
        long publishedResults,
        long pendingApprovals,
        long pendingRevaluations,
        long passCount,
        long failCount,
        List<SubjectPerformanceDTO> subjectPerformance,
        List<GradeDistributionDTO> gradeDistribution,
        List<StudentPerformanceDTO> topPerformers,
        List<ActivityLog> recentActivity,
        List<Notice> recentNotices
) {
    /** 0.0 when there is no published data yet, rather than dividing by zero - the JSP uses this to decide between the chart and Sec. 56's empty state. */
    public double passRatePercent() {
        long total = passCount + failCount;
        return total == 0 ? 0.0 : (100.0 * passCount / total);
    }

    public boolean hasPublishedResults() {
        return passCount + failCount > 0;
    }

    /** No Course exists yet - the first prerequisite for everything else (Semester needs a Course, Section/Subject need a Semester) - so this is the cheapest reliable signal that nobody has done any Academic Setup yet, worth a dedicated onboarding panel rather than seven separate empty-state messages an admin has to piece together themselves. */
    public boolean needsSetup() {
        return totalCourses == 0;
    }
}
