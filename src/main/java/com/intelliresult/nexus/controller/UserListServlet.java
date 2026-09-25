package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * Protected by AuthorizationFilter's /admin prefix (ADMIN role only).
 * findByRole(ADMIN) (not findAll()) - Students and Teachers now have their
 * own dedicated pages (/admin/students, /admin/teachers) with the three
 * account actions Sec. "Admin can only: change password / disable-enable /
 * soft-delete" allows, so this page's remaining purpose is exclusively
 * managing other Admin accounts.
 * DataTables handles search/sort/pagination client-side (Sec. 6/44) - the
 * current data volume doesn't yet warrant server-side processing; see
 * PHASE5B decisions doc for when that trade-off would flip.
 */
@WebServlet(name = "UserListServlet", urlPatterns = "/admin/users")
public class UserListServlet extends HttpServlet {

    private final UserDAO userDAO = new UserDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("users", userDAO.findByRole(UserRole.ADMIN));

        // One-time flash display of a just-generated temporary password
        // (account creation or admin-initiated reset) - read and
        // immediately cleared so a page refresh can never show it twice.
        HttpSession session = request.getSession(false);
        if (session != null && session.getAttribute("flashTempPassword") != null) {
            request.setAttribute("flashTempPassword", session.getAttribute("flashTempPassword"));
            request.setAttribute("flashTempPasswordFor", session.getAttribute("flashTempPasswordFor"));
            session.removeAttribute("flashTempPassword");
            session.removeAttribute("flashTempPasswordFor");
        }
        if (session != null && session.getAttribute("flashError") != null) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, session.getAttribute("flashError"));
            session.removeAttribute("flashError");
        }

        request.getRequestDispatcher("/admin/users.jsp").forward(request, response);
    }
}
