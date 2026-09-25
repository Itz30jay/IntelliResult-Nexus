package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.ActivityLogDAO;
import com.intelliresult.nexus.dao.ActivityLogDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.ActivityLog;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.exception.AuthenticationException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.util.PasswordUtil;
import com.intelliresult.nexus.util.ValidationUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.Optional;

/**
 * Owns every rule about "is this login attempt valid" and "may this user
 * change their password this way" - the two pieces of Sec. 3's Authentication
 * System that involve real business logic, as opposed to session/cookie
 * mechanics (which belong to the filters and LoginServlet/LogoutServlet
 * instead, since those are Servlet-API concerns this Service deliberately
 * stays free of, for testability and layering).
 * Every public method here explicitly demarcates its own transaction - see
 * HibernateSessionFilter's note on why session lifecycle and transaction
 * lifecycle are different concerns in this project.
 * <p>
 * Upgrade: login() takes a single "identifier" rather than an email
 * specifically - Registration makes roll_no/employee_code the login ID
 * (Sec. "Registration + Verification System"), but existing/admin-created
 * accounts keep working exactly as before by email, so the field is resolved
 * as roll_no, then employee_code, then email, in that order, rather than
 * replacing email lookup outright. There is no separate "is this account
 * verified" check anywhere in this class: a row in `users` only ever exists
 * once RegistrationService has approved it (or an admin created it directly)
 * - see RegistrationRequest's class Javadoc - so "the account exists" and
 * "the account is approved" are the same fact by construction, not two
 * checks that could disagree.
 */
public class AuthenticationService {

    private final UserDAO userDAO = new UserDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final ActivityLogDAO activityLogDAO = new ActivityLogDAOImpl();

    /**
     * Verifies credentials and returns the authenticated User, or throws
     * AuthenticationException. Deliberately uses the SAME generic message
     * ("Invalid login ID or password") whether the identifier doesn't
     * resolve to any account or the password is wrong - a standard defense
     * against user enumeration (telling an attacker "that ID isn't
     * registered" is itself a leak). A disabled account gets its own
     * distinct message since that's genuinely more helpful for a legitimate
     * user and doesn't weaken the enumeration defense (reaching that branch
     * already required a valid, correctly-guessed identifier/password pair).
     */
    public User login(String identifier, String plainTextPassword, String ipAddress) {
        if (ValidationUtil.isBlank(identifier) || ValidationUtil.isBlank(plainTextPassword)) {
            throw new ValidationException("Login ID and password are required.");
        }

        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            User user = resolveUserByIdentifier(identifier.trim()).orElse(null);

            if (user == null || !PasswordUtil.matches(plainTextPassword, user.getPasswordHash())) {
                logActivity(null, "LOGIN_FAILURE", "Failed login attempt for identifier: " + identifier, ipAddress);
                tx.commit();
                throw new AuthenticationException("Invalid login ID or password.");
            }

            if (!user.isActive()) {
                logActivity(user, "LOGIN_FAILURE", "Login attempt on disabled account", ipAddress);
                tx.commit();
                throw new AuthenticationException("This account has been disabled. Contact your administrator.");
            }

            user.recordLogin();
            userDAO.update(user);
            logActivity(user, "LOGIN_SUCCESS", "User logged in", ipAddress);
            tx.commit();
            return user;
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    /** roll_no and employee_code are both stored upper-cased (see RegistrationService/UserService) - upper-casing the lookup here too means a student typing their own roll number in lowercase still matches, without needing a case-insensitive DB collation to carry that guarantee. */
    private Optional<User> resolveUserByIdentifier(String identifier) {
        Optional<User> byRollNo = studentDAO.findByRollNo(identifier.toUpperCase()).map(com.intelliresult.nexus.entity.Student::getUser);
        if (byRollNo.isPresent()) {
            return byRollNo;
        }
        Optional<User> byEmployeeCode = teacherDAO.findByEmployeeCode(identifier.toUpperCase()).map(com.intelliresult.nexus.entity.Teacher::getUser);
        if (byEmployeeCode.isPresent()) {
            return byEmployeeCode;
        }
        return userDAO.findByEmail(identifier);
    }

    public void logout(User user, String ipAddress) {
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            logActivity(user, "LOGOUT", "User logged out", ipAddress);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    /**
     * Requires the current password to be re-entered correctly even though
     * the user is already authenticated - a session being valid proves "this
     * request came from an authenticated browser," not "the person at the
     * keyboard right now is the account owner" (a shared/unlocked device is
     * exactly the gap this closes). newPassword is validated against the
     * same ValidationUtil.isValidPassword() policy every account creation
     * path uses, so there is exactly one definition of "strong enough."
     */
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        if (!ValidationUtil.isValidPassword(newPassword)) {
            throw new ValidationException(
                    "New password must be at least 8 characters and include a letter and a digit.");
        }

        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            User user = userDAO.findById(userId)
                    .orElseThrow(() -> new AuthenticationException("Session user no longer exists."));

            if (!PasswordUtil.matches(currentPassword, user.getPasswordHash())) {
                throw new AuthenticationException("Current password is incorrect.");
            }

            user.setPasswordHash(PasswordUtil.hash(newPassword));
            userDAO.update(user);
            logActivity(user, "PASSWORD_CHANGED", "User changed their own password", null);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }

    private void logActivity(User user, String action, String details, String ipAddress) {
        activityLogDAO.save(new ActivityLog(user, action, details, ipAddress));
    }
}
