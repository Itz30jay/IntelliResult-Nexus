package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.service.NotificationService;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Sec. 69's Notifications page for the two roles that have one (Admin,
 * Student - Sec. 69's own Teacher nav list has no Notifications item).
 * One class under both role prefixes rather than two near-identical
 * servlets: "show my notifications" and "mark mine as read" have no
 * role-specific behavior at all, only a role-specific URL prefix -
 * {@code AuthorizationFilter} already enforces that an admin can't reach
 * {@code /student/notifications} or vice versa purely from the path,
 * independent of which servlet class handles it (the same reasoning
 * {@code MarksheetServlet}, Phase 12, used for its own admin+student
 * split, applied here to justify the opposite conclusion: there, the two
 * roles needed different authorization scopes for the SAME resource id;
 * here, "my own notifications" is already scoped by session on both
 * sides, so there is nothing left to differ).
 */
@WebServlet(name = "NotificationServlet", urlPatterns = {
        "/admin/notifications", "/admin/notifications/read", "/admin/notifications/read-all",
        "/student/notifications", "/student/notifications/read", "/student/notifications/read-all"
})
public class NotificationServlet extends HttpServlet {

    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        request.setAttribute("notifications", notificationService.getNotifications(currentUser.getId()));
        String viewPath = currentUser.getRole() == UserRole.ADMIN
                ? "/admin/notifications.jsp"
                : "/student/notifications.jsp";
        request.getRequestDispatcher(viewPath).forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        String path = request.getServletPath();
        String redirectBase = currentUser.getRole() == UserRole.ADMIN ? "/admin/notifications" : "/student/notifications";

        try {
            if (path.endsWith("/read-all")) {
                notificationService.markAllRead(currentUser.getId());
            } else if (path.endsWith("/read")) {
                Long notificationId = Long.valueOf(request.getParameter("notificationId"));
                notificationService.markRead(notificationId, currentUser.getId());
            }
        } catch (BaseApplicationException e) {
            request.getSession().setAttribute("flashError", e.getMessage());
        }
        response.sendRedirect(request.getContextPath() + redirectBase);
    }
}
