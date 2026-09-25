package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * The Students account-management page (Sec. "split Admin's Users into
 * separate Students and Teachers pages"). Upgrade: previously a read-only
 * roster with a comment pointing account actions at /admin/users - that
 * page is now Admin-accounts-only (see UserListServlet's own updated
 * Javadoc), so this page picked up the three account actions Sec. "Admin
 * can only: change password / disable-enable / soft-delete" allows,
 * reusing UserStatusServlet/UserPasswordResetServlet exactly as /admin/users
 * always did (same servlets, this page just also posts to them now with
 * returnTo=/admin/students).
 */
@WebServlet(name = "StudentListServlet", urlPatterns = "/admin/students")
public class StudentListServlet extends HttpServlet {

    private final StudentDAO studentDAO = new StudentDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("students", studentDAO.findAll());

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

        request.getRequestDispatcher("/admin/students.jsp").forward(request, response);
    }
}
