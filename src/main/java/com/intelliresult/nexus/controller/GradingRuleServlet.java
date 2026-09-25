package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.AcademicYearDAO;
import com.intelliresult.nexus.dao.AcademicYearDAOImpl;
import com.intelliresult.nexus.dao.GradingRuleDAO;
import com.intelliresult.nexus.dao.GradingRuleDAOImpl;
import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.GradingRule;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.GradingRuleService;
import com.intelliresult.nexus.service.dto.GradingRuleRequest;
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
import java.math.BigDecimal;
import java.util.List;

/**
 * The list is scoped to one academic year at a time (a "years" selector at
 * the top of grading-rules.jsp switches it via a plain GET ?academicYearId=
 * - no form, no state to lose) rather than showing every rule for every year
 * in one flat table: a grading scale is inherently a per-year concept (Sec.
 * 12), so "which year am I looking at" is the first, unavoidable question
 * for this screen, the same way AcademicSetupHubServlet treats AcademicYear
 * as a distinct top-level concept rather than a column to filter within a
 * combined view.
 */
@WebServlet(name = "GradingRuleServlet", urlPatterns = {
        "/admin/grading-rules",
        "/admin/grading-rules/new",
        "/admin/grading-rules/edit",
        "/admin/grading-rules/deactivate",
        "/admin/grading-rules/activate"
})
public class GradingRuleServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(GradingRuleServlet.class);
    private final GradingRuleService gradingRuleService = new GradingRuleService();
    private final GradingRuleDAO gradingRuleDAO = new GradingRuleDAOImpl();
    private final AcademicYearDAO academicYearDAO = new AcademicYearDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();
        List<AcademicYear> allYears = academicYearDAO.findAll();

        if (path.endsWith("/new")) {
            request.setAttribute("academicYears", allYears);
            request.getRequestDispatcher("/admin/grading-rule-form.jsp").forward(request, response);
            return;
        }
        if (path.endsWith("/edit")) {
            GradingRule rule = gradingRuleDAO.findById(Long.valueOf(request.getParameter("id"))).orElse(null);
            if (rule == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("editingRule", rule);
            request.getRequestDispatcher("/admin/grading-rule-form.jsp").forward(request, response);
            return;
        }

        AcademicYear selectedYear = resolveSelectedYear(request, allYears);
        request.setAttribute("academicYears", allYears);
        request.setAttribute("selectedYear", selectedYear);
        request.setAttribute("rules", selectedYear == null ? List.of() : gradingRuleService.findAllForYear(selectedYear.getId()));
        readFlashMessages(request);
        request.getRequestDispatcher("/admin/grading-rules.jsp").forward(request, response);
    }

    /** Explicit ?academicYearId= wins; otherwise the year marked current (AcademicSetupService.setCurrentAcademicYear); otherwise the first year that exists, so the page never opens on an arbitrary/empty selection while any year exists; {@code null} only when there truly are no academic years yet (Sec. 56 empty state, not an error). */
    private AcademicYear resolveSelectedYear(HttpServletRequest request, List<AcademicYear> allYears) {
        String requested = request.getParameter("academicYearId");
        if (requested != null && !requested.isBlank()) {
            Long id = Long.valueOf(requested);
            return allYears.stream().filter(y -> y.getId().equals(id)).findFirst().orElse(null);
        }
        return academicYearDAO.findCurrent().orElse(allYears.isEmpty() ? null : allYears.get(0));
    }

    private void readFlashMessages(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return;
        }
        if (session.getAttribute("flashError") != null) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, session.getAttribute("flashError"));
            session.removeAttribute("flashError");
        }
        if (session.getAttribute("flashSuccess") != null) {
            request.setAttribute("successMessage", session.getAttribute("flashSuccess"));
            session.removeAttribute("flashSuccess");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();

        try {
            Long ruleYearId = null;
            if (path.endsWith("/deactivate")) {
                GradingRule rule = requireRule(request);
                ruleYearId = rule.getAcademicYear().getId();
                gradingRuleService.deactivate(rule.getId());
                flashSuccessAndRedirect(request, response, "Grading rule deactivated.", ruleYearId);
            } else if (path.endsWith("/activate")) {
                GradingRule rule = requireRule(request);
                ruleYearId = rule.getAcademicYear().getId();
                gradingRuleService.activate(rule.getId());
                flashSuccessAndRedirect(request, response, "Grading rule reactivated.", ruleYearId);
            } else {
                String idParam = request.getParameter("id");
                GradingRuleRequest ruleRequest = parseRequest(request);
                if (idParam == null || idParam.isBlank()) {
                    gradingRuleService.createRule(ruleRequest);
                    flashSuccessAndRedirect(request, response, "Grading rule added.", ruleRequest.academicYearId());
                } else {
                    gradingRuleService.updateRule(Long.valueOf(idParam), ruleRequest);
                    flashSuccessAndRedirect(request, response, "Grading rule updated.", ruleRequest.academicYearId());
                }
            }
        } catch (ValidationException e) {
            request.setAttribute("academicYears", academicYearDAO.findAll());
            String idParam = request.getParameter("id");
            if (idParam != null && !idParam.isBlank()) {
                gradingRuleDAO.findById(Long.valueOf(idParam)).ifPresent(r -> request.setAttribute("editingRule", r));
            }
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/admin/grading-rule-form.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            LOGGER.info("Grading rule operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/grading-rules");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in grading rule management", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "grading-rules"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/grading-rules");
        }
    }

    private GradingRule requireRule(HttpServletRequest request) {
        return gradingRuleDAO.findById(Long.valueOf(request.getParameter("id")))
                .orElseThrow(() -> new ResourceNotFoundException("Grading rule not found."));
    }

    private void flashSuccessAndRedirect(HttpServletRequest request, HttpServletResponse response, String message, Long academicYearId)
            throws IOException {
        request.getSession().setAttribute("flashSuccess", message);
        String suffix = academicYearId == null ? "" : "?academicYearId=" + academicYearId;
        response.sendRedirect(request.getContextPath() + "/admin/grading-rules" + suffix);
    }

    private GradingRuleRequest parseRequest(HttpServletRequest request) {
        Long academicYearId = null;
        String yearParam = request.getParameter("academicYearId");
        if (yearParam != null && !yearParam.isBlank()) {
            academicYearId = Long.valueOf(yearParam);
        }
        return new GradingRuleRequest(academicYearId, parseDecimal(request.getParameter("minPercentage")),
                parseDecimal(request.getParameter("maxPercentage")), request.getParameter("grade"),
                parseDecimal(request.getParameter("gradePoint")));
    }

    private BigDecimal parseDecimal(String value) {
        return (value == null || value.isBlank()) ? null : new BigDecimal(value);
    }
}
