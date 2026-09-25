package com.intelliresult.nexus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliresult.nexus.dao.DepartmentDAO;
import com.intelliresult.nexus.dao.DepartmentDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.dao.TeacherSubjectDAO;
import com.intelliresult.nexus.dao.TeacherSubjectDAOImpl;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.entity.TeacherSubject;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.service.TeacherAssignmentService;
import com.intelliresult.nexus.util.AppConstants;
import io.sentry.Sentry;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.time.LocalTime;
import java.util.List;

/**
 * Assign form is inline on the list page; still branches on path for
 * assign/unassign/update-timing POSTs, same pattern as every other Phase 5
 * controller.
 * <p>
 * Upgrade: the assign form's field order is now Teacher -&gt; Semester
 * (number 1-8) -&gt; Department -&gt; Subject -&gt; Section -&gt; Start/End
 * time. Semester-number and Department narrow the Subject list client-side
 * (subjectOptionsJson) rather than via a server round-trip per selection -
 * at the data volumes an academic-setup admin form deals with, shipping the
 * full option set once and filtering in JS is simpler and faster than
 * wiring a fetch-per-dropdown endpoint for what is, structurally, a
 * three-field cascade. Section is not one of the fields the admin
 * explicitly asked for, but teacher_subjects.section_id is NOT NULL - it
 * has to be chosen from somewhere - so it appears once a Subject is picked,
 * filtered to sections belonging to that exact Subject's own semester
 * (sectionOptionsJson), which is the only way to keep "which students does
 * this assignment apply to" and "which subject" from disagreeing about
 * which concrete Semester they mean (a semester NUMBER like "3" can be
 * shared by several actual Semester rows - different courses, different
 * academic years).
 */
@WebServlet(name = "TeacherAssignmentServlet", urlPatterns = {
        "/admin/teacher-assignments", "/admin/teacher-assignments/assign",
        "/admin/teacher-assignments/unassign", "/admin/teacher-assignments/update-timing"
})
public class TeacherAssignmentServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(TeacherAssignmentServlet.class);
    private final TeacherAssignmentService assignmentService = new TeacherAssignmentService();
    private final TeacherSubjectDAO teacherSubjectDAO = new TeacherSubjectDAOImpl();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final SubjectDAO subjectDAO = new SubjectDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("teachers", teacherDAO.findAll());
        request.setAttribute("departments", departmentDAO.findAll());

        List<Subject> subjects = subjectDAO.findAll();
        List<Section> sections = sectionDAO.findAll();
        request.setAttribute("subjectOptionsJson", objectMapper.writeValueAsString(subjects.stream()
                .map(this::toSubjectOption).toList()));
        request.setAttribute("sectionOptionsJson", objectMapper.writeValueAsString(sections.stream()
                .map(this::toSectionOption).toList()));

        // No dedicated "all assignments" DAO query - built here from
        // findAll() + a Java filter. Fine at this data volume; worth a real
        // query only if a dedicated report needs it beyond this admin view.
        List<TeacherSubject> all = teacherSubjectDAO.findAll();
        request.setAttribute("activeAssignments", all.stream().filter(TeacherSubject::isActive).toList());
        request.setAttribute("endedAssignments", all.stream().filter(a -> !a.isActive()).toList());
        readFlashError(request);

        request.getRequestDispatcher("/admin/teacher-assignments.jsp").forward(request, response);
    }

    /** contextLabel disambiguates two Subjects that share a semester NUMBER but belong to different courses/years (e.g. two "Semester 3"s) - shown in the option text once the JS cascade has already filtered by number, so an admin picking among a short filtered list can still tell them apart. */
    private SubjectOption toSubjectOption(Subject s) {
        String contextLabel = s.getSemester().getCourse().getCode() + " " + s.getSemester().getAcademicYear().getLabel();
        return new SubjectOption(s.getId(), s.getSubjectCode(), s.getSubjectName(),
                s.getSemester().getSemesterNumber(), s.getDepartment().getId(), s.getSemester().getId(), contextLabel);
    }

    private SectionOption toSectionOption(Section s) {
        return new SectionOption(s.getId(), s.getName(), s.getSemester().getId());
    }

    private void readFlashError(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session != null && session.getAttribute("flashError") != null) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, session.getAttribute("flashError"));
            session.removeAttribute("flashError");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);

        try {
            if (path.endsWith("/assign")) {
                assignmentService.assign(
                        Long.valueOf(request.getParameter("teacherId")),
                        Long.valueOf(request.getParameter("subjectId")),
                        Long.valueOf(request.getParameter("sectionId")),
                        parseTime(request.getParameter("classStartTime")),
                        parseTime(request.getParameter("classEndTime")),
                        currentAdmin.getId());
            } else if (path.endsWith("/update-timing")) {
                assignmentService.updateTiming(
                        Long.valueOf(request.getParameter("id")),
                        parseTime(request.getParameter("classStartTime")),
                        parseTime(request.getParameter("classEndTime")));
            } else {
                assignmentService.unassign(Long.valueOf(request.getParameter("id")), currentAdmin.getId());
            }
            response.sendRedirect(request.getContextPath() + "/admin/teacher-assignments");

        } catch (BaseApplicationException e) {
            LOGGER.info("Teacher assignment operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/teacher-assignments");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in teacher assignment", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "teacher-assignment"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/teacher-assignments");
        }
    }

    private LocalTime parseTime(String value) {
        return (value == null || value.isBlank()) ? null : LocalTime.parse(value);
    }

    /** View-only shaping for the client-side cascade - never touched by the Service layer, so these live here rather than in service/dto alongside the real cross-layer contracts. */
    private record SubjectOption(Long id, String code, String name, int semesterNumber, Long departmentId,
                                  Long semesterId, String contextLabel) {
    }

    private record SectionOption(Long id, String name, Long semesterId) {
    }
}
