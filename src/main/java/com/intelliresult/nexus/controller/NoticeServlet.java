package com.intelliresult.nexus.controller;

import com.intelliresult.nexus.dao.NoticeDAO;
import com.intelliresult.nexus.dao.NoticeDAOImpl;
import com.intelliresult.nexus.entity.Notice;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.NoticePriority;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.BaseApplicationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.NoticeService;
import com.intelliresult.nexus.service.NotificationService;
import com.intelliresult.nexus.service.dto.NoticeRequest;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Same shape as ExamServlet/GradingRuleServlet (Phase 5e): one servlet,
 * flashError/flashSuccess session pattern, ValidationException re-renders
 * the form, BaseApplicationException flashes and redirects to the list.
 * expiryDate is the one field this controller - not NoticeService - owns
 * the format conversion for: the form submits a plain yyyy-MM-dd date
 * (matching every other date input in this codebase, per PHASE5C/5E's
 * &lt;input type="date"&gt; convention), converted here to end-of-day
 * (23:59:59) before NoticeRequest is built, so NoticeService only ever
 * deals in the LocalDateTime the entity and schema.sql's
 * chk_notices_expiry constraint actually use.
 */
@WebServlet(name = "NoticeServlet", urlPatterns = {
        "/admin/notices",
        "/admin/notices/new",
        "/admin/notices/edit",
        "/admin/notices/delete",
        "/admin/notices/restore",
        "/admin/notices/publish",
        "/admin/notices/unpublish"
})
public class NoticeServlet extends HttpServlet {

    private static final Logger LOGGER = LogManager.getLogger(NoticeServlet.class);
    private final NoticeService noticeService = new NoticeService();
    private final NoticeDAO noticeDAO = new NoticeDAOImpl();
    private final NotificationService notificationService = new NotificationService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String path = request.getServletPath();

        if (path.endsWith("/new")) {
            request.setAttribute("priorities", NoticePriority.values());
            request.getRequestDispatcher("/admin/notice-form.jsp").forward(request, response);
            return;
        }
        if (path.endsWith("/edit")) {
            Notice notice = noticeDAO.findById(Long.valueOf(request.getParameter("id"))).orElse(null);
            if (notice == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            request.setAttribute("editingNotice", notice);
            request.setAttribute("priorities", NoticePriority.values());
            // Computed here, not left to the JSP, because Set<UserRole>.contains(Object)
            // erases to an Object parameter - EL has no target type to coerce a
            // String literal toward, so ${notice.audience.contains('ADMIN')} would
            // silently always evaluate false (UserRole.ADMIN.equals("ADMIN") is
            // false; enums never equal a String). Comparing UserRole to UserRole
            // here, in real Java, sidesteps that entirely.
            request.setAttribute("audienceHasAdmin", notice.getAudience().contains(UserRole.ADMIN));
            request.setAttribute("audienceHasTeacher", notice.getAudience().contains(UserRole.TEACHER));
            request.setAttribute("audienceHasStudent", notice.getAudience().contains(UserRole.STUDENT));
            // Also computed here rather than as ${editingNotice.expiryDate.toLocalDate()}
            // in the JSP: expiryDate is legitimately null for a no-expiry
            // notice (one of the three seeded rows is exactly this case),
            // and chained EL method calls on a null base aren't a risk worth
            // taking untested - an empty string is what an empty <input
            // type="date"> needs regardless.
            request.setAttribute("expiryDateValue",
                    notice.getExpiryDate() == null ? "" : notice.getExpiryDate().toLocalDate().toString());
            request.getRequestDispatcher("/admin/notice-form.jsp").forward(request, response);
            return;
        }

        request.setAttribute("notices", noticeDAO.findAll());
        request.setAttribute("deletedNotices", noticeDAO.findDeleted());
        readFlashMessages(request);
        request.getRequestDispatcher("/admin/notices.jsp").forward(request, response);
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
        User currentAdmin = (User) request.getAttribute(AppConstants.REQUEST_ATTR_CURRENT_USER);

        try {
            if (path.endsWith("/delete")) {
                noticeService.softDelete(parseId(request), currentAdmin.getId());
                flashSuccessAndRedirect(request, response, "Notice moved to the recycle bin.");
            } else if (path.endsWith("/restore")) {
                noticeService.restore(parseId(request));
                flashSuccessAndRedirect(request, response, "Notice restored.");
            } else if (path.endsWith("/publish")) {
                Notice published = noticeService.publish(parseId(request));
                try {
                    notificationService.notifyNoticePublished(published);
                } catch (RuntimeException e) {
                    // Sec. 23's audience fan-out must never mask that the
                    // notice itself already published successfully above.
                    LOGGER.error("Could not notify the audience for notice #{}.", published.getId(), e);
                }
                flashSuccessAndRedirect(request, response, "Notice published.");
            } else if (path.endsWith("/unpublish")) {
                noticeService.unpublish(parseId(request));
                flashSuccessAndRedirect(request, response, "Notice unpublished.");
            } else {
                String idParam = request.getParameter("id");
                NoticeRequest noticeRequest = parseNoticeRequest(request);
                if (idParam == null || idParam.isBlank()) {
                    noticeService.createNotice(noticeRequest, currentAdmin.getId());
                    flashSuccessAndRedirect(request, response, "Notice created.");
                } else {
                    noticeService.updateNotice(Long.valueOf(idParam), noticeRequest);
                    flashSuccessAndRedirect(request, response, "Notice updated.");
                }
            }
        } catch (ValidationException e) {
            request.setAttribute("priorities", NoticePriority.values());
            String idParam = request.getParameter("id");
            if (idParam != null && !idParam.isBlank()) {
                noticeDAO.findById(Long.valueOf(idParam)).ifPresent(n -> request.setAttribute("editingNotice", n));
            }
            // Reflects what was just submitted, not the notice's prior saved
            // state - same reasoning as every other field re-showing
            // ${param.xxx} rather than the entity's old value after a failed
            // validation. See the /edit branch above for why this is done
            // in Java rather than left to an EL Set.contains() call.
            String[] submittedAudience = request.getParameterValues("audience");
            Set<String> submitted = submittedAudience == null ? Set.of() : Set.of(submittedAudience);
            request.setAttribute("audienceHasAdmin", submitted.contains("ADMIN"));
            request.setAttribute("audienceHasTeacher", submitted.contains("TEACHER"));
            request.setAttribute("audienceHasStudent", submitted.contains("STUDENT"));
            request.setAttribute(AppConstants.REQUEST_ATTR_ERROR_MESSAGE, e.getMessage());
            request.setAttribute("fieldErrors", e.getFieldErrors());
            request.getRequestDispatcher("/admin/notice-form.jsp").forward(request, response);
        } catch (BaseApplicationException e) {
            LOGGER.info("Notice operation failed: {}", e.getMessage());
            request.getSession().setAttribute("flashError", e.getMessage());
            response.sendRedirect(request.getContextPath() + "/admin/notices");
        } catch (RuntimeException e) {
            LOGGER.error("Unexpected error in notice management", e);
            Sentry.captureException(e, scope -> scope.setTag("module", "notice-board"));
            request.getSession().setAttribute("flashError", "Something went wrong. Please try again.");
            response.sendRedirect(request.getContextPath() + "/admin/notices");
        }
    }

    private void flashSuccessAndRedirect(HttpServletRequest request, HttpServletResponse response, String message)
            throws IOException {
        request.getSession().setAttribute("flashSuccess", message);
        response.sendRedirect(request.getContextPath() + "/admin/notices");
    }

    private Long parseId(HttpServletRequest request) {
        return Long.valueOf(request.getParameter("id"));
    }

    private NoticeRequest parseNoticeRequest(HttpServletRequest request) {
        Set<UserRole> audience = new LinkedHashSet<>();
        String[] audienceParams = request.getParameterValues("audience");
        if (audienceParams != null) {
            for (String value : audienceParams) {
                audience.add(UserRole.valueOf(value));
            }
        }
        NoticePriority priority = null;
        String priorityParam = request.getParameter("priority");
        if (priorityParam != null && !priorityParam.isBlank()) {
            priority = NoticePriority.valueOf(priorityParam);
        }
        return new NoticeRequest(request.getParameter("title"), request.getParameter("content"), audience, priority,
                parseExpiryEndOfDay(request.getParameter("expiryDate")));
    }

    /** A date-only picker (see class Javadoc) turned into "valid through the end of that day," so an admin picking today's date gets a notice that's actually visible for the rest of today rather than one that reads as already-expired the moment it's saved. */
    private LocalDateTime parseExpiryEndOfDay(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value).atTime(23, 59, 59);
    }
}
