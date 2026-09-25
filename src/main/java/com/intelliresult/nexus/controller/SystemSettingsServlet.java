package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.SystemSettingsService;
import com.intelliresult.nexus.service.dto.SystemSettingsRequest;
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

/**
 * Shaped differently from every other admin controller on purpose:
 * {@link SystemSettingsService} has no create/delete/list, only
 * getSettings()/updateSettings() (see that service's own Javadoc for why),
 * so this servlet has no /new, /edit, or /delete route either - just GET to
 * show the one record and POST to update it, mirroring the single-record
 * shape of the domain it fronts rather than forcing the usual list+form
 * pair onto something that was never a list.
 */
@WebServlet(name = "SystemSettingsServlet", urlPatterns = {"/admin/settings"})
public class SystemSettingsServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(SystemSettingsServlet.class);
    private final SystemSettingsService systemSettingsService = new SystemSettingsService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("settings", systemSettingsService.getSettings());
        readFlashMessages(request);
        request.getRequestDispatcher("/admin/settings.jsp").forward(request, response);
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
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);

        try {
            SystemSettingsRequest req = new SystemSettingsRequest(
                    request.getParameter("institutionName"), request.getParameter("institutionAddress"),
                    request.getParameter("institutionLogoPath"), request.getParameter("signatoryName"),
                    request.getParameter("signatoryDesignation"));
            systemSettingsService.updateSettings(req, currentAdmin.getId());
            request.getSession().setAttribute("flashSuccess", "Settings saved.");
            response.sendRedirect(request.getContextPath() + "/admin/settings");
        } catch (ValidationException e) {
            request.setAttribute("settings", systemSettingsService.getSettings());
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/admin/settings.jsp").forward(request, response);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error updating system settings", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "system-settings"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/settings");
        }
    }
}
