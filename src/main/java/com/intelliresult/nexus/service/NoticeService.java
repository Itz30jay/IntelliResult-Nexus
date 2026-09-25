package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.NoticeDAO;
import com.intelliresult.nexus.dao.NoticeDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.Notice;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.NoticeRequest;
import com.intelliresult.nexus.util.ValidationUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns Sec. 25's Notice Board. Unlike Exam (see ExamService's class
 * Javadoc), full editing - including audience and priority - stays open
 * regardless of published state: a notice isn't an audit-critical academic
 * record the way an exam's identity is, and Sec. 25 lists "Edit" and
 * "Publish/Unpublish" as parallel, independent actions rather than a
 * lock-on-publish sequence. The one place published state does gate
 * something is the expiry date, and that's schema.sql's own
 * chk_notices_expiry constraint, not a design choice made here.
 */
public class NoticeService {

    private final NoticeDAO noticeDAO = new NoticeDAOImpl();
    private final UserDAO userDAO = new UserDAOImpl();

    public Notice createNotice(NoticeRequest req, Long createdByUserId) {
        User creator = userDAO.findById(createdByUserId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found."));

        Map<String, String> errors = validate(req, null);
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        return inTransaction(() -> {
            Notice notice = new Notice(req.title().trim(), req.content().trim(), req.audience(), creator);
            notice.setPriority(req.priority());
            notice.setExpiryDate(req.expiryDate());
            noticeDAO.save(notice);
            return notice;
        });
    }

    public Notice updateNotice(Long id, NoticeRequest req) {
        Notice notice = noticeDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notice not found."));

        Map<String, String> errors = validate(req, notice);
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        return inTransaction(() -> {
            notice.setTitle(req.title().trim());
            notice.setContent(req.content().trim());
            notice.setAudience(req.audience());
            notice.setPriority(req.priority());
            notice.setExpiryDate(req.expiryDate());
            noticeDAO.update(notice);
            return notice;
        });
    }

    /** Rejects publishing a notice whose expiry has already passed - the one case schema.sql's chk_notices_expiry would otherwise reject at the database, surfaced here as a clear message (Sec. 45) instead of a raw constraint-violation error. */
    public Notice publish(Long id) {
        Notice notice = noticeDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notice not found."));
        if (notice.getExpiryDate() != null && !notice.getExpiryDate().isAfter(LocalDateTime.now())) {
            throw new BusinessRuleException("This notice's expiry date has already passed - update the expiry date before publishing.");
        }
        return inTransaction(() -> {
            notice.publish();
            noticeDAO.update(notice);
            return notice;
        });
    }

    public Notice unpublish(Long id) {
        Notice notice = noticeDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notice not found."));
        return inTransaction(() -> {
            notice.unpublish();
            noticeDAO.update(notice);
            return notice;
        });
    }

    public void softDelete(Long id, Long adminId) {
        Notice notice = noticeDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notice not found."));
        runInTransaction(() -> notice.markDeleted(adminId));
    }

    public void restore(Long id) {
        Notice notice = noticeDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Notice not found."));
        runInTransaction(notice::restore);
    }

    private Map<String, String> validate(NoticeRequest req, Notice existing) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (ValidationUtil.isBlank(req.title())) {
            errors.put("title", "Title is required.");
        } else if (req.title().trim().length() > 200) {
            errors.put("title", "Title must be 200 characters or fewer.");
        }
        if (ValidationUtil.isBlank(req.content())) {
            errors.put("content", "Content is required.");
        }
        if (req.audience() == null || req.audience().isEmpty()) {
            errors.put("audience", "Select at least one audience.");
        }
        if (req.priority() == null) {
            errors.put("priority", "Select a priority.");
        }
        // chk_notices_expiry (schema.sql): once a notice has ever been
        // published, its expiry can never sit on or before that publish
        // moment. Only reachable on update - a brand-new notice has no
        // publishedDate yet, so the second OR clause of that constraint
        // always covers it regardless of what expiry is chosen.
        if (existing != null && existing.getPublishedDate() != null && req.expiryDate() != null
                && !req.expiryDate().isAfter(existing.getPublishedDate())) {
            errors.put("expiryDate", "Expiry date must be after this notice's publish date ("
                    + existing.getPublishedDate().toLocalDate() + ").");
        }

        return errors;
    }

    // ---------------------------------------------------------------- transaction helpers

    private interface TransactionalWork<T> {
        T run();
    }

    private <T> T inTransaction(TransactionalWork<T> work) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            T result = work.run();
            tx.commit();
            return result;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    private void runInTransaction(Runnable work) {
        inTransaction(() -> {
            work.run();
            return null;
        });
    }
}
