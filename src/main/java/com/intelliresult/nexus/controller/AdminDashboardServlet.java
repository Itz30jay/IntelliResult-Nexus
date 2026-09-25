package com.intelliresult.nexus.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intelliresult.nexus.service.DashboardService;
import com.intelliresult.nexus.service.dto.DashboardStats;
import io.sentry.Sentry;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;

/**
 * Protected by AuthorizationFilter's /admin prefix restriction - only
 * reachable by an authenticated ADMIN, enforced by the filter chain before
 * this class ever runs (Sec. 2/68). Chart.js reads its data from plain JSON
 * embedded in the page rather than a separate AJAX endpoint - this project
 * doesn't have a JSON API layer yet, and building one just for two chart
 * datasets on one page would be premature; Jackson (already a pom.xml
 * dependency) serializes DashboardStats' two chart-relevant lists directly
 * into request attributes the JSP drops into an inline <script> tag.
 */
@WebServlet(name = "AdminDashboardServlet", urlPatterns = "/admin/dashboard")
public class AdminDashboardServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(AdminDashboardServlet.class);

    private final DashboardService dashboardService = new DashboardService();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            DashboardStats stats = dashboardService.loadStats();
            request.setAttribute("stats", stats);
            request.setAttribute("gradeDistributionJson", objectMapper.writeValueAsString(stats.gradeDistribution()));
            request.setAttribute("subjectPerformanceJson", objectMapper.writeValueAsString(stats.subjectPerformance()));
            request.getRequestDispatcher("/admin/dashboard.jsp").forward(request, response);
        } catch (RuntimeException e) {
            LOGGER.error("Failed to load admin dashboard", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "admin-dashboard"));
            throw e; // web.xml's Throwable error-page renders the friendly 500 page
        }
    }
}
