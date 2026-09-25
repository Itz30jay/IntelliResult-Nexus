package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.ResultStatus;
import com.intelliresult.nexus.service.MarksEntryService;
import com.intelliresult.nexus.service.dto.MarksEntryGroup;
import com.intelliresult.nexus.service.dto.MarksEntryRow;
import com.intelliresult.nexus.util.AppConstants;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Same MarksEntryService.listGroupsForTeacher() computation the Marks
 * Entry picker (MarksEntryServlet) uses, filtered differently: Draft
 * Results wants groups with at least one DRAFT row still to finish;
 * Submitted Results wants groups with at least one row SUBMITTED or later,
 * a genuine history view (Sec. 69), not scoped to "currently open for
 * entry" the way the picker is - a result submitted for an exam that has
 * since moved to APPROVAL or beyond should still show here.
 * <p>
 * Upgrade: Submitted Results now also loads each group's individual
 * student rows (via the same loadGrid() the marks-entry grid itself uses),
 * filtered to submitted-or-later - Sec. "student disappears from pending
 * marks entry and appears in Submitted Results" was already true underneath
 * (Result.markSubmitted() does transition status correctly), but the page
 * only ever showed an aggregate "X of Y submitted" count, never the actual
 * students, so the requirement's own literal wording - a STUDENT visibly
 * appearing - wasn't really observable without an extra click into the
 * grid. Draft Results is untouched: its own aggregate-count view already
 * matches what an in-progress worklist needs.
 */
@WebServlet(name = "TeacherResultsServlet", urlPatterns = {"/teacher/draft-results", "/teacher/submitted-results"})
public class TeacherResultsServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(TeacherResultsServlet.class);
    private final MarksEntryService marksEntryService = new MarksEntryService();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Teacher teacher = teacherDAO.findByUserId(currentUser.getId()).orElse(null);
        if (teacher == null) {
            LOGGER.error("User {} has role TEACHER but no matching Teacher profile.", currentUser.getEmail());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        boolean isDraftView = request.getServletPath().endsWith("/draft-results");
        List<MarksEntryGroup> groups = marksEntryService.listGroupsForTeacher(teacher.getId()).stream()
                .filter(g -> isDraftView ? g.draftCount() > 0 : g.submittedOrLaterCount() > 0)
                .toList();

        request.setAttribute("groups", groups);
        if (!isDraftView) {
            request.setAttribute("groupRows", loadSubmittedRowsPerGroup(groups));
        }
        request.getRequestDispatcher(isDraftView ? "/teacher/draft-results.jsp" : "/teacher/submitted-results.jsp")
                .forward(request, response);
    }

    /**
     * One loadGrid() call per group (typically a handful for any one
     * teacher), filtered down to rows this page actually cares about -
     * reusing the exact same grid-loading logic the marks-entry screen
     * itself uses rather than a second, parallel query, so "what counts as
     * submitted" can never drift between the two screens.
     */
    private List<GroupWithStudents> loadSubmittedRowsPerGroup(List<MarksEntryGroup> groups) {
        List<GroupWithStudents> result = new ArrayList<>();
        for (MarksEntryGroup group : groups) {
            List<MarksEntryRow> submittedRows = marksEntryService.loadGrid(
                            group.exam().getId(), group.assignment().getSubject().getId(), group.assignment().getSection().getId())
                    .stream()
                    .filter(row -> row.hasExistingResult() && row.existingResult().getStatus() != ResultStatus.DRAFT)
                    .toList();
            result.add(new GroupWithStudents(group, submittedRows));
        }
        return result;
    }

    /** View-only pairing for the JSP - never touched by the Service layer, so it lives here rather than in service/dto alongside the real cross-layer contracts (same reasoning as TeacherAssignmentServlet's own SubjectOption/SectionOption). */
    public record GroupWithStudents(MarksEntryGroup group, List<MarksEntryRow> submittedRows) {
    }
}
