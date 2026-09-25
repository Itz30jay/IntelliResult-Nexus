package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.RegistrationService;
import com.intelliresult.nexus.service.dto.StudentRegistrationRequest;
import com.intelliresult.nexus.service.dto.TeacherRegistrationRequest;
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
 * The public Registration flow's entry point (Sec. "Registration +
 * Verification System") - listed in security.public.paths alongside
 * /login, since by definition nobody submitting this form has an account
 * yet. Both roles post to the same servlet with a "role" field rather than
 * two separate URLs, mirroring LoginServlet's single-endpoint shape; which
 * DTO/service method runs is decided here from that one field, same as
 * how UserFormServlet already branches on role for admin-created accounts.
 */
@WebServlet(name = "RegistrationServlet", urlPatterns = {"/register"})
public class RegistrationServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(RegistrationServlet.class);
    private final RegistrationService registrationService = new RegistrationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.getRequestDispatcher("/common/register.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String role = request.getParameter("role");
        try {
            if ("TEACHER".equals(role)) {
                registrationService.submitTeacherRegistration(new TeacherRegistrationRequest(
                        request.getParameter("fullName"), request.getParameter("employeeCode"),
                        request.getParameter("phone"), request.getParameter("email"), request.getParameter("password")));
            } else {
                registrationService.submitStudentRegistration(new StudentRegistrationRequest(
                        request.getParameter("fullName"), request.getParameter("rollNo"),
                        request.getParameter("phone"), request.getParameter("email"), request.getParameter("password")));
            }
            request.setAttribute("submitted", true);
            request.setAttribute("submittedRole", role);
            request.getRequestDispatcher("/common/register.jsp").forward(request, response);
        } catch (ValidationException e) {
            request.setAttribute("selectedRole", role);
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/common/register.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            LOGGER.info("Registration submission failed: {}", e.getMessage());
            request.setAttribute("selectedRole", role);
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.getRequestDispatcher("/common/register.jsp").forward(request, response);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error during registration submission", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "registration"));
            request.setAttribute("selectedRole", role);
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, "Something went wrong. Please try again.");
            request.getRequestDispatcher("/common/register.jsp").forward(request, response);
        }
    }
}
