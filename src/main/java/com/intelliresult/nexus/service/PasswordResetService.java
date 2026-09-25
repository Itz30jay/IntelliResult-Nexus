package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.OtpCodeDAO;
import com.intelliresult.nexus.dao.OtpCodeDAOImpl;
import com.intelliresult.nexus.dao.PasswordResetRequestDAO;
import com.intelliresult.nexus.dao.PasswordResetRequestDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.OtpCode;
import com.intelliresult.nexus.entity.PasswordResetRequest;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.PasswordResetStatus;
import com.intelliresult.nexus.exception.AuthenticationException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.util.PasswordUtil;
import com.intelliresult.nexus.util.ValidationUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Sec. "Login Page - Forgot Password + OTP": request an OTP, verify it,
 * choose a new password - then, deliberately, stop short of applying it.
 * "The password-change request goes to Admin for final approval ... keep
 * Admin in the loop" is read literally: {@link #submitNewPassword} only
 * ever creates a PENDING {@link PasswordResetRequest} with the new password
 * already hashed; {@link #approve} is the one place {@code
 * User.passwordHash} actually changes. This mirrors RegistrationService's
 * identical "verify now, materialize on admin approval" shape.
 * <p>
 * Deliberately duplicates AuthenticationService's small
 * identifier-to-User resolution rather than exposing it as shared API -
 * two three-line lookups cost less to maintain than a shared helper class
 * would, the same reasoning every service's own private
 * {@code inTransaction} in this codebase already accepts.
 */
public class PasswordResetService {

    private static final int OTP_VALID_MINUTES = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserDAO userDAO = new UserDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final OtpCodeDAO otpCodeDAO = new OtpCodeDAOImpl();
    private final PasswordResetRequestDAO passwordResetRequestDAO = new PasswordResetRequestDAOImpl();
    private final EmailNotificationService emailNotificationService = new EmailNotificationService();
    private final NotificationService notificationService = new NotificationService();

    /**
     * Generates and emails a 6-digit OTP for the account matching
     * {@code identifier} (email/roll_no/employee_code, same resolution
     * order as login). Deliberately does not reveal whether the identifier
     * matched an account - the same enumeration defense
     * AuthenticationService.login already applies - so the caller always
     * sees "if that account exists and has an email on file, a code was
     * sent" regardless of which half is actually true. Returns silently
     * (there is nothing else to report) when the account has no email at
     * all; that is not an error, it is Registration's own optional-email
     * design working as intended, and this method is not the place to
     * explain that to an unauthenticated caller.
     */
    public void requestOtp(String identifier, String ipAddress) {
        Optional<User> userOpt = resolveUserByIdentifier(identifier);
        if (userOpt.isEmpty() || userOpt.get().getEmail() == null) {
            return;
        }
        User user = userOpt.get();

        String otp = String.format("%06d", RANDOM.nextInt(1_000_000));
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            OtpCode code = new OtpCode(user, PasswordUtil.hash(otp), LocalDateTime.now().plusMinutes(OTP_VALID_MINUTES), ipAddress);
            otpCodeDAO.save(code);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
        emailNotificationService.sendOtp(user, otp, OTP_VALID_MINUTES);
    }

    /**
     * @return the verified User, so the caller (ForgotPasswordServlet) can
     *         stamp a short-lived session flag for the password-entry step
     *         without asking for the OTP a second time.
     * @throws AuthenticationException if the identifier/OTP pair does not
     *         resolve to a currently-usable code (Sec. "OTP verification").
     */
    public User verifyOtp(String identifier, String otpCode) {
        User user = resolveUserByIdentifier(identifier)
                .orElseThrow(() -> new AuthenticationException("Invalid or expired code."));

        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            OtpCode code = otpCodeDAO.findMostRecentByUser(user.getId())
                    .filter(OtpCode::isUsable)
                    .orElseThrow(() -> new AuthenticationException("Invalid or expired code. Please request a new one."));

            if (!PasswordUtil.matches(otpCode, code.getOtpHash())) {
                code.registerFailedAttempt();
                otpCodeDAO.update(code);
                tx.commit();
                throw new AuthenticationException("Incorrect code. Please try again.");
            }

            code.markConsumed();
            otpCodeDAO.update(code);
            tx.commit();
            return user;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    /**
     * Stages the new password as a PENDING request rather than applying it
     * - {@code otpVerifiedAt} is passed in (stamped by the servlet the
     * moment {@link #verifyOtp} returned) rather than re-derived here, so
     * this method's own signature makes it impossible to call without that
     * step already having happened.
     */
    public PasswordResetRequest submitNewPassword(Long userId, String newPassword, LocalDateTime otpVerifiedAt) {
        if (!ValidationUtil.isValidPassword(newPassword)) {
            throw new ValidationException("Password must be at least 8 characters and include a letter and a number.",
                    Map.of("password", "Does not meet the requirements."));
        }
        User user = userDAO.findById(userId).orElseThrow(() -> new ResourceNotFoundException("User not found."));

        return inTransaction(() -> passwordResetRequestDAO.save(
                new PasswordResetRequest(user, PasswordUtil.hash(newPassword), otpVerifiedAt)));
    }

    public List<PasswordResetRequest> listPending() {
        return passwordResetRequestDAO.findByStatus(PasswordResetStatus.PENDING);
    }

    public List<PasswordResetRequest> listReviewed() {
        List<PasswordResetRequest> reviewed = passwordResetRequestDAO.findByStatus(PasswordResetStatus.APPROVED);
        reviewed.addAll(passwordResetRequestDAO.findByStatus(PasswordResetStatus.REJECTED));
        reviewed.sort((a, b) -> b.getReviewedAt().compareTo(a.getReviewedAt()));
        return reviewed;
    }

    /** The one place a Forgot-Password-initiated credential change actually reaches {@code User.passwordHash} - see this class's own Javadoc. */
    public void approve(Long requestId, Long adminId) {
        User targetUser = inTransaction(() -> {
            PasswordResetRequest request = passwordResetRequestDAO.findById(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Password reset request not found."));
            User admin = userDAO.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("Admin user not found."));

            request.approve(admin);
            passwordResetRequestDAO.update(request);

            User user = request.getUser();
            user.setPasswordHash(request.getNewPasswordHash());
            userDAO.update(user);
            return user;
        });
        notificationService.notifyPasswordResetDecision(targetUser, true);
    }

    public void reject(Long requestId, Long adminId) {
        User targetUser = inTransaction(() -> {
            PasswordResetRequest request = passwordResetRequestDAO.findById(requestId)
                    .orElseThrow(() -> new ResourceNotFoundException("Password reset request not found."));
            User admin = userDAO.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("Admin user not found."));

            request.reject(admin);
            passwordResetRequestDAO.update(request);
            return request.getUser();
        });
        notificationService.notifyPasswordResetDecision(targetUser, false);
    }

    /** Same three-step resolution order as AuthenticationService.login (roll_no, then employee_code, then email) - Forgot Password uses whichever identifier the account actually logs in with. */
    private Optional<User> resolveUserByIdentifier(String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String trimmed = identifier.trim().toUpperCase();
        Optional<User> byRollNo = studentDAO.findByRollNo(trimmed).map(com.intelliresult.nexus.entity.Student::getUser);
        if (byRollNo.isPresent()) {
            return byRollNo;
        }
        Optional<User> byEmployeeCode = teacherDAO.findByEmployeeCode(trimmed).map(com.intelliresult.nexus.entity.Teacher::getUser);
        if (byEmployeeCode.isPresent()) {
            return byEmployeeCode;
        }
        return userDAO.findByEmail(identifier.trim());
    }

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
}
