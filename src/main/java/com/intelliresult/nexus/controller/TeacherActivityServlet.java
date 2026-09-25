package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** My Activity (Sec. 69) - the teacher's own ActivityLog rows, already ordered newest-first at the DAO level (confirmed in Phase 6a's reconnaissance). Unpaginated for now: a single teacher's own history is small enough that DataTables' client-side paging on the full set is adequate - the same call every other small, individually-scoped list in this codebase has made. */
@WebServlet(name = "TeacherActivityServlet", urlPatterns = {"/teacher/activity"})
public class TeacherActivityServlet extends HttpServlet {

    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        request.setAttribute("logs", activityLogDAO.findByUser(currentUser.getId()));
        request.getRequestDispatcher("/teacher/activity.jsp").forward(request, response);
    }
}
