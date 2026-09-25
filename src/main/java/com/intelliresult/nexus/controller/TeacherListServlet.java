package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/** The Teachers account-management page - same upgrade and reasoning as StudentListServlet. */
@WebServlet(name = "TeacherListServlet", urlPatterns = "/admin/teachers")
public class TeacherListServlet extends HttpServlet {

    private final TeacherDAO teacherDAO = new TeacherDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("teachers", teacherDAO.findAll());

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

        request.getRequestDispatcher("/admin/teachers.jsp").forward(request, response);
    }
}
