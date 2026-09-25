package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.UserService;
import com.intelliresult.nexus.service.dto.UserCreateRequest;
import com.intelliresult.nexus.service.dto.UserCreationResult;
import com.intelliresult.nexus.service.dto.UserUpdateRequest;
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
 * One servlet for both create and edit, distinguished by whether an "id"
 * parameter is present - not two separate classes, since the two flows
 * share almost everything (same validation shape, same redirect) and
 * duplicating that would be the actual DRY violation, not this file's dual
 * purpose.
 * Upgrade: this form now only ever creates/edits ADMIN-role accounts (Sec.
 * "Admin can no longer create new teachers or students") - UserService
 * itself enforces this too (defense in depth), but rejecting a non-ADMIN
 * role here, before ever calling the service, means the dropdown data this
 * class used to load for Student/Teacher fields (courses/departments/
 * sections) simply isn't needed by this file anymore.
 */
@WebServlet(name = "UserFormServlet", urlPatterns = {"/admin/users/new", "/admin/users/edit"})
public class UserFormServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(UserFormServlet.class);

    private final UserService userService = new UserService();
    private final UserDAO userDAO = new UserDAOImpl();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String idParam = request.getParameter("id");
        if (idParam != null) {
            User user = userDAO.findById(Long.valueOf(idParam)).orElse(null);
            if (user == null || user.getRole() != UserRole.ADMIN) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("editingUser", user);
        }

        request.getRequestDispatcher("/admin/user-form.jsp").forward(request, response);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);
        String idParam = request.getParameter("id");

        try {
            if (idParam == null) {
                UserCreateRequest req = new UserCreateRequest(
                        UserRole.ADMIN,
                        request.getParameter("email"),
                        request.getParameter("fullName"),
                        request.getParameter("phone")
                );
                UserCreationResult result = userService.createUser(req, currentAdmin.getId());
                request.getSession().setAttribute("flashTempPassword", result.temporaryPassword());
                request.getSession().setAttribute("flashTempPasswordFor", result.user().getEmail());
            } else {
                UserUpdateRequest req = new UserUpdateRequest(
                        Long.valueOf(idParam),
                        request.getParameter("email"),
                        request.getParameter("fullName"),
                        request.getParameter("phone")
                );
                userService.updateUser(req, currentAdmin.getId());
            }
            response.sendRedirect(request.getContextPath() + "/admin/users");

        } catch (ValidationException e) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            if (idParam != null) {
                userDAO.findById(Long.valueOf(idParam)).ifPresent(u -> request.setAttribute("editingUser", u));
            }
            request.getRequestDispatcher("/admin/user-form.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            if (idParam != null) {
                userDAO.findById(Long.valueOf(idParam)).ifPresent(u -> request.setAttribute("editingUser", u));
            }
            request.getRequestDispatcher("/admin/user-form.jsp").forward(request, response);
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error saving user", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "user-management"));
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, "Something went wrong. Please try again.");
            request.getRequestDispatcher("/admin/user-form.jsp").forward(request, response);
        }
    }
}
