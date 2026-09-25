package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.ResultSummaryDAO;
import com.intelliresult.nexus.dao.ResultSummaryDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.entity.ResultSummary;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.User;
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
import java.util.Collections;
import java.util.List;

/**
 * {@code /student/marksheet} - Sec. 69's Marksheet nav item, added the same
 * way {@code student-head.jspf}'s own comment already commits every
 * remaining Student item to: by the phase that actually builds it. Lists
 * every examination this student can download an official PDF for.
 * "Downloadable" reuses {@link ResultSummary#isComplete()}, the exact same
 * gate {@code MarksheetService} enforces before it will actually build a
 * PDF - filtering here is a convenience (don't link to something that
 * would 409), not a security boundary; {@code MarksheetDownloadServlet}
 * and {@code MarksheetService} re-check independently regardless of what
 * this page shows.
 */
@WebServlet(name = "MarksheetServlet", urlPatterns = {"/student/marksheet"})
public class MarksheetServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(MarksheetServlet.class);

    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final ResultSummaryDAO resultSummaryDAO = new ResultSummaryDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentUser = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        Student student = studentDAO.findByUserId(currentUser.getId()).orElse(null);
        if (student == null) {
            LOGGER.error("User {} has role STUDENT but no matching Student profile.", currentUser.getEmail());
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        List<ResultSummary> eligible = new ArrayList<>();
        for (ResultSummary summary : resultSummaryDAO.findAllForStudent(student.getId())) {
            if (summary.isComplete()) {
                eligible.add(summary);
            }
        }
        // findAllForStudent orders oldest-first; reversed here to match
        // /student/results' own most-recent-exam-first convention.
        Collections.reverse(eligible);

        request.setAttribute("student", student);
        request.setAttribute("eligibleSummaries", eligible);
        request.getRequestDispatcher("/student/marksheet.jsp").forward(request, response);
    }
}
