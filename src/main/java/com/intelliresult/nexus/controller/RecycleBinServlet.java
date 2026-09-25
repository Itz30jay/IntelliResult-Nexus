package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.DepartmentDAO;
import com.intelliresult.nexus.dao.DepartmentDAOImpl;
import com.intelliresult.nexus.dao.ExamDAO;
import com.intelliresult.nexus.dao.ExamDAOImpl;
import com.intelliresult.nexus.dao.NoticeDAO;
import com.intelliresult.nexus.dao.NoticeDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * New (upgrade pass): the sidebar's "Recycle Bin" link had no servlet or
 * JSP behind it at all - Sec. 27's "View deleted records" was only ever
 * implemented per-entity (each of Department/Course/Section/Subject/Exam/
 * Notice/User's own list page quietly loads its own findDeleted() - see
 * e.g. SectionServlet's deletedSections), never as the single unified view
 * the nav link promised. This aggregates all seven soft-deletable entity
 * types (User, Department, Course, Section, Subject, Exam, Notice) into
 * one list, with each row's Restore button posting straight to that
 * entity's OWN existing restore endpoint - no new restore logic, this is
 * purely a unified read view over capability that already existed piecemeal.
 * Result is the eighth entity SoftDeletableDAO covers, deliberately left
 * out here: nothing in the app ever exposes a way to soft-delete a Result
 * in the first place (no delete button exists anywhere on admin/results.jsp),
 * so there has never been a real "deleted Result" for this page to show,
 * and building that delete capability was not what this fix asked for.
 */
@WebServlet(name = "RecycleBinServlet", urlPatterns = "/admin/recycle-bin")
public class RecycleBinServlet extends HttpServlet {

    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();
    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final SubjectDAO subjectDAO = new SubjectDAOImpl();
    private final ExamDAO examDAO = new ExamDAOImpl();
    private final NoticeDAO noticeDAO = new NoticeDAOImpl();
    private final UserDAO userDAO = new UserDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Resolves deletedBy (a raw Long - see SoftDeletableEntity's own
        // Javadoc on why it isn't a managed relationship) to an admin's name
        // for display - built once per request rather than a lookup per
        // row, since only admins can delete anything in this system.
        Map<Long, String> adminNamesById = new HashMap<>();
        for (User admin : userDAO.findByRole(UserRole.ADMIN)) {
            adminNamesById.put(admin.getId(), admin.getFullName());
        }

        List<RecycleBinItem> items = new ArrayList<>();
        for (var d : departmentDAO.findDeleted()) {
            items.add(new RecycleBinItem("Department", d.getName() + " (" + d.getCode() + ")",
                    d.getDeletedAt(), adminNamesById.get(d.getDeletedBy()),
                    "/admin/academic-setup/departments/restore", d.getId(), null));
        }
        for (var c : courseDAO.findDeleted()) {
            items.add(new RecycleBinItem("Course", c.getName() + " (" + c.getCode() + ")",
                    c.getDeletedAt(), adminNamesById.get(c.getDeletedBy()),
                    "/admin/academic-setup/courses/restore", c.getId(), null));
        }
        for (var s : sectionDAO.findDeleted()) {
            items.add(new RecycleBinItem("Section", "Section " + s.getName() + " (Sem "
                    + s.getSemester().getSemesterNumber() + " " + s.getSemester().getCourse().getCode() + ")",
                    s.getDeletedAt(), adminNamesById.get(s.getDeletedBy()),
                    "/admin/academic-setup/sections/restore", s.getId(), null));
        }
        for (var sub : subjectDAO.findDeleted()) {
            items.add(new RecycleBinItem("Subject", sub.getSubjectCode() + " \u2014 " + sub.getSubjectName(),
                    sub.getDeletedAt(), adminNamesById.get(sub.getDeletedBy()),
                    "/admin/subjects/restore", sub.getId(), null));
        }
        for (var e : examDAO.findDeleted()) {
            items.add(new RecycleBinItem("Exam", e.getName(),
                    e.getDeletedAt(), adminNamesById.get(e.getDeletedBy()),
                    "/admin/exams/restore", e.getId(), null));
        }
        for (var n : noticeDAO.findDeleted()) {
            items.add(new RecycleBinItem("Notice", n.getTitle(),
                    n.getDeletedAt(), adminNamesById.get(n.getDeletedBy()),
                    "/admin/notices/restore", n.getId(), null));
        }
        for (var u : userDAO.findDeleted()) {
            // User's restore endpoint requires returnTo (see UserStatusServlet) -
            // routed back to Admins/Students/Teachers by role so the same
            // whitelist that servlet already enforces still applies.
            String returnTo = switch (u.getRole()) {
                case STUDENT -> "/admin/students";
                case TEACHER -> "/admin/teachers";
                case ADMIN -> "/admin/users";
            };
            items.add(new RecycleBinItem(u.getRole() + " Account", u.getFullName() + " (" + u.getEmail() + ")",
                    u.getDeletedAt(), adminNamesById.get(u.getDeletedBy()),
                    "/admin/users/restore", u.getId(), returnTo));
        }

        items.sort((a, b) -> {
            if (a.deletedAt() == null) return 1;
            if (b.deletedAt() == null) return -1;
            return b.deletedAt().compareTo(a.deletedAt());
        });

        request.setAttribute("items", items);
        readFlashError(request);
        request.getRequestDispatcher("/admin/recycle-bin.jsp").forward(request, response);
    }

    private void readFlashError(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute("flashError") != null) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, session.getAttribute("flashError"));
            session.removeAttribute("flashError");
        }
    }

    /** returnTo is null for every entity except User (see the switch above) - the JSP only renders that hidden field when it's present. */
    public record RecycleBinItem(String category, String label, LocalDateTime deletedAt, String deletedByName,
                                  String restoreUrl, Long id, String returnTo) {
    }
}
