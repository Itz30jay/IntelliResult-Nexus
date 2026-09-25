package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.enums.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * New (upgrade pass): the sidebar's "Activity Logs" link had no servlet or
 * page behind it - Sec. 26's audit-log viewer, despite ActivityLogDAO
 * already having findByUser/findByAction/findByDateRange (and now
 * findByUserRole - see that DAO's own comment, which promised this filter
 * but never had a caller until this servlet) ready to serve it. Every
 * write this system makes to ActivityLog (logins, corrections,
 * registrations, exports, deletions...) already happens; this is the first
 * screen that actually surfaces it.
 * Defaults to the most recent 200 entries unread-filtered - a real
 * institution's log can grow large fast, and "everything, unpaginated" is
 * the wrong default for a page whose whole purpose is finding a specific
 * event, not scrolling through all of history.
 */
@WebServlet(name = "AdminActivityLogServlet", urlPatterns = "/admin/activity-logs")
public class AdminActivityLogServlet extends HttpServlet {

    private static final int DEFAULT_LIMIT = 200;
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String roleParam = request.getParameter("role");
        String actionParam = request.getParameter("action");
        String fromParam = request.getParameter("from");
        String toParam = request.getParameter("to");

        List<ActivityLog> logs;
        if (isSet(fromParam) && isSet(toParam)) {
            logs = activityLogDAO.findByDateRange(
                    LocalDate.parse(fromParam).atStartOfDay(),
                    LocalDate.parse(toParam).atTime(LocalTime.MAX));
        } else if (isSet(roleParam)) {
            logs = activityLogDAO.findByUserRole(UserRole.valueOf(roleParam));
        } else if (isSet(actionParam)) {
            logs = activityLogDAO.findByAction(actionParam);
        } else {
            logs = activityLogDAO.findRecent(DEFAULT_LIMIT);
        }

        // The action dropdown's own options - distinct values actually
        // present in the log, not a hand-maintained list that would drift
        // from what logActivity() calls across the codebase actually pass.
        List<String> distinctActions = activityLogDAO.findRecent(1000).stream()
                .map(ActivityLog::getAction)
                .distinct()
                .sorted()
                .toList();

        request.setAttribute("logs", logs);
        request.setAttribute("distinctActions", distinctActions);
        request.setAttribute("selectedRole", roleParam);
        request.setAttribute("selectedAction", actionParam);
        request.setAttribute("fromDate", fromParam);
        request.setAttribute("toDate", toParam);
        request.setAttribute("resultLimited", logs.size() >= DEFAULT_LIMIT && !isSet(fromParam) && !isSet(roleParam) && !isSet(actionParam));

        request.getRequestDispatcher("/admin/activity-logs.jsp").forward(request, response);
    }

    private boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
