package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.AcademicYearDAO;
import com.intelliresult.nexus.dao.AcademicYearDAOImpl;
import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.DepartmentDAO;
import com.intelliresult.nexus.dao.DepartmentDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SemesterDAO;
import com.intelliresult.nexus.dao.SemesterDAOImpl;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** A simple index with counts, not a DataTable of its own - each card links to its entity's real list page. */
@WebServlet(name = "AcademicSetupHubServlet", urlPatterns = "/admin/academic-setup")
public class AcademicSetupHubServlet extends HttpServlet {

    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();
    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final AcademicYearDAO academicYearDAO = new AcademicYearDAOImpl();
    private final SemesterDAO semesterDAO = new SemesterDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("departmentCount", departmentDAO.count());
        request.setAttribute("courseCount", courseDAO.count());
        request.setAttribute("academicYearCount", academicYearDAO.count());
        request.setAttribute("semesterCount", semesterDAO.count());
        request.setAttribute("sectionCount", sectionDAO.count());
        request.getRequestDispatcher("/admin/academic-setup.jsp").forward(request, response);
    }
}
