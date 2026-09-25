package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.UserCreateRequest;
import com.intelliresult.nexus.service.dto.UserCreationResult;
import com.intelliresult.nexus.service.dto.UserUpdateRequest;
import com.intelliresult.nexus.util.PasswordUtil;
import com.intelliresult.nexus.util.ValidationUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sec. 6's User Management, minus bulk import/export - those are Excel
 * operations that belong with Phase 13's dedicated ExcelUtil/POI
 * infrastructure, not duplicated here ahead of it.
 * Role is deliberately immutable once a user is created - there is no
 * changeRole() method. "Change role" (Sec. 6) is real, but a general
 * implementation has to answer what happens to a Student's existing
 * Results or a Teacher's existing TeacherSubject assignments when their
 * role changes out from under that data, and neither this project nor the
 * spec defines that migration. Rather than build a version that only
 * "works" for the case where no dependent data exists yet (a check that's
 * easy to get subtly wrong and dangerous to get wrong at all - orphaned
 * academic records are exactly the kind of thing Sec. 47/48 exist to
 * prevent), role is fixed at creation. Correcting a genuine mistake means
 * disabling the wrong-role account and creating the correct one.
 * <p>
 * Upgrade: this class now only ever creates/edits ADMIN-role accounts.
 * Student/Teacher accounts come exclusively from the public Registration +
 * admin verification flow (see RegistrationService) - createStudentProfile/
 * createTeacherProfile/updateStudentProfile/updateTeacherProfile and their
 * Course/Department/Section/Student/Teacher DAO dependencies were removed
 * from this class entirely rather than left unreachable, since nothing here
 * can invoke them anymore. setActive/resetPassword/softDelete/restore remain
 * genuinely role-agnostic (Sec. "Admin can only: change password /
 * disable-enable / soft-delete-remove" applies identically to every role),
 * so they are untouched and are exactly what the new Students/Teachers pages
 * call for those three actions.
 */
public class UserService {

    private final UserDAO userDAO = new UserDAOImpl();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    /** Upgrade: restricted to {@code UserRole.ADMIN}. Student/Teacher accounts no longer come from this admin panel - see RegistrationService, the only remaining path that creates either. */
    public UserCreationResult createUser(UserCreateRequest req, Long createdByAdminId) {
        if (req.role() != UserRole.ADMIN) {
            throw new ValidationException("New Student and Teacher accounts are created through the public " +
                    "Registration and admin verification flow, not here.", Map.of("role", "Only Admin accounts can be created here."));
        }
        validateCommonFields(req.email(), req.fullName());
        if (userDAO.existsByEmail(req.email())) {
            throw new ValidationException("A user with this email already exists.",
                    Map.of("email", "Already in use."));
        }

        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            String temporaryPassword = PasswordUtil.generateTemporaryPassword();
            User user = new User(req.email(), PasswordUtil.hash(temporaryPassword), req.fullName(), req.role());
            user.setPhone(req.phone());
            userDAO.save(user);

            logActivity(createdByAdminId, "USER_CREATED", "Created Admin account for " + req.email());
            tx.commit();
            return new UserCreationResult(user, temporaryPassword);
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    /** Upgrade: like createUser, only ever operates on an ADMIN-role account now - see this class's own Javadoc update. A Student/Teacher's profile has no edit screen anywhere in the new design (Sec. "Admin can only: change password / disable-enable / soft-delete"); correcting a genuine mistake in their academic placement is Academic Setup's concern, not this form's. */
    public User updateUser(UserUpdateRequest req, Long updatedByAdminId) {
        validateCommonFields(req.email(), req.fullName());

        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            User user = userDAO.findById(req.userId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found."));
            if (user.getRole() != UserRole.ADMIN) {
                throw new BusinessRuleException("Only Admin accounts can be edited here.");
            }

            if (!user.getEmail().equalsIgnoreCase(req.email()) && userDAO.existsByEmail(req.email())) {
                throw new ValidationException("A user with this email already exists.",
                        Map.of("email", "Already in use."));
            }
            user.setEmail(req.email());
            user.setFullName(req.fullName());
            user.setPhone(req.phone());

            logActivity(updatedByAdminId, "USER_UPDATED", "Updated account for " + user.getEmail());
            tx.commit();
            return user;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    public void setActive(Long userId, boolean active, Long adminId) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            User user = userDAO.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found."));
            if (user.getRole() == UserRole.ADMIN && !active && userDAO.countByRole(UserRole.ADMIN) <= 1) {
                throw new BusinessRuleException("Cannot disable the only remaining admin account.");
            }
            user.setActive(active);
            logActivity(adminId, active ? "USER_ACTIVATED" : "USER_DISABLED", "Account: " + user.getEmail());
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    /** Returns the new plaintext temporary password - shown to the admin exactly once by the caller, never logged or persisted anywhere in plaintext. */
    public String resetPassword(Long userId, Long adminId) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            User user = userDAO.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found."));
            String temporaryPassword = PasswordUtil.generateTemporaryPassword();
            user.setPasswordHash(PasswordUtil.hash(temporaryPassword));
            logActivity(adminId, "PASSWORD_RESET_BY_ADMIN", "Reset password for: " + user.getEmail());
            tx.commit();
            return temporaryPassword;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    /** Soft-deletes (Sec. "Soft-delete/Remove user") via the same SoftDeletableEntity.markDeleted() every other deletable entity in this codebase uses - User already extends it and users.deleted/deleted_by/deleted_at have existed in the schema since Phase 2, this is simply the first controller path to actually call it. A deleted user is excluded from login (AuthenticationService's lookups all filter deleted rows via their DAOs) without losing their historical Results/marks, which a hard delete would cascade-destroy. */
    public void softDelete(Long userId, Long adminId) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            User user = userDAO.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found."));
            if (user.getRole() == UserRole.ADMIN && userDAO.countByRole(UserRole.ADMIN) <= 1) {
                throw new BusinessRuleException("Cannot remove the only remaining admin account.");
            }
            user.markDeleted(adminId);
            logActivity(adminId, "USER_DELETED", "Removed account: " + user.getEmail());
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    public void restore(Long userId, Long adminId) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            User user = userDAO.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found."));
            user.restore();
            logActivity(adminId, "USER_RESTORED", "Restored account: " + user.getEmail());
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    private void validateCommonFields(String email, String fullName) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (!ValidationUtil.isValidEmail(email)) {
            fieldErrors.put("email", "Enter a valid email address.");
        }
        if (ValidationUtil.isBlank(fullName)) {
            fieldErrors.put("fullName", "Full name is required.");
        }
        if (!fieldErrors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", fieldErrors);
        }
    }

    private void logActivity(Long adminId, String action, String details) {
        User admin = adminId == null ? null : userDAO.findById(adminId).orElse(null);
        activityLogDAO.save(new ActivityLog(admin, action, details, null));
    }
}
