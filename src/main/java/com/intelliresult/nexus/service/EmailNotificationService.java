package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.AppConfig;
import com.intelliresult.nexus.entity.Exam;
import com.intelliresult.nexus.entity.Notice;
import com.intelliresult.nexus.entity.RevaluationRequest;
import com.intelliresult.nexus.entity.User;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Properties;

/**
 * Sec. 24's own literally-named class - the only place in this codebase
 * that knows Jakarta Mail/Angus Mail exists (verified against that
 * library's own current FAQ examples via Context7, not assumed from
 * possibly-stale training data). {@code NotificationService} depends on
 * this class's three semantic methods, never on a {@code Session}/{@code
 * Transport}/{@code MimeMessage} directly - "do not tightly couple
 * business logic to the email provider" is satisfied by that boundary. No
 * separate {@code interface} sits above this class: every other service
 * in this project (UserService, MarksEntryService, ...) is a plain
 * concrete class, not an interface+impl pair - that pattern belongs to
 * the DAO layer here, and introducing it solely for this one class would
 * be a new convention this phase has no real need to establish.
 * <p>
 * Every failure - unreachable host, rejected credentials, timeout - is
 * caught and logged here, never propagated. This project's own demo
 * {@code mail.host} (application.properties) is a placeholder domain that
 * will never resolve, and Sec. 24's email channel is explicitly additive
 * to the always-reliable in-app {@code Notification} row {@code
 * NotificationService} writes first - a misconfigured or unreachable SMTP
 * server must never fail the business operation that triggered the
 * notification (publishing a result, resolving a re-evaluation).
 * <p>
 * Explicit 5-second connect/read/write timeouts are set on every {@link
 * Session} this class builds. Jakarta Mail's own defaults amount to "wait
 * for the operating system's TCP timeout", which against a host that
 * never responds at all can mean a minute or more - measured in this
 * class's own hardcoded constant, not sourced from {@link AppConfig}: this
 * is an operational safety margin no administrator would ever want to
 * tune, not a business value.
 */
public class EmailNotificationService {

    private static final Logger LOGGER = LogManager.getLogger(EmailNotificationService.class);
    private static final String TIMEOUT_MS = "5000";
    private static final String SIGNATURE = "\n\n- IntelliResult Nexus";

    public void sendResultPublished(User user, Exam exam) {
        sendSingle(user.getEmail(), "Result Published: " + exam.getName(),
                "Dear " + user.getFullName() + ",\n\n"
                        + "Your result for " + exam.getName() + " has been published. "
                        + "Log in to IntelliResult Nexus to view your marks, grade, and rank."
                        + SIGNATURE);
    }

    public void sendRevaluationDecision(User user, RevaluationRequest request, boolean approved) {
        String subjectName = request.getResult().getSubject().getSubjectName();
        String examName = request.getResult().getExam().getName();
        StringBuilder body = new StringBuilder("Dear " + user.getFullName() + ",\n\n"
                + "Your re-evaluation request for " + subjectName + " (" + examName + ") has been "
                + (approved ? "approved. Your marks have been updated accordingly." : "not approved.") + ".");
        if (request.getAdminRemark() != null && !request.getAdminRemark().isBlank()) {
            body.append("\n\nRemark: ").append(request.getAdminRemark());
        }
        if (request.getTeacherReport() != null && !request.getTeacherReport().isBlank()) {
            body.append("\n\nEvaluator's report: ").append(request.getTeacherReport());
        }
        body.append(SIGNATURE);
        sendSingle(user.getEmail(), "Re-evaluation " + (approved ? "Approved" : "Update"), body.toString());
    }

    /** Sent once a Registration submission is approved and the real account exists - the identifier line matters more here than in most emails, since it is the only place (besides the admin's own approval screen) the new login ID is ever surfaced to the person who will need to type it. */
    public void sendRegistrationApproved(User user, String loginIdentifier) {
        sendSingle(user.getEmail(), "Registration Approved - Welcome to IntelliResult Nexus",
                "Dear " + user.getFullName() + ",\n\n"
                        + "Your registration has been approved. You can now log in with:\n"
                        + "Login ID: " + loginIdentifier + "\n"
                        + "Password: the one you chose when registering."
                        + SIGNATURE);
    }

    /** No User exists for a rejected registration (RegistrationRequest.reject() never creates one - see its class Javadoc), so this takes a raw address rather than the User-based signature every other method here uses; a no-op if toEmail is null, since Registration's own field list never requires an email. */
    public void sendRegistrationRejected(String toEmail, String fullName, String remark) {
        if (toEmail == null || toEmail.isBlank()) {
            return;
        }
        StringBuilder body = new StringBuilder("Dear " + fullName + ",\n\n"
                + "Your registration request could not be approved.");
        if (remark != null && !remark.isBlank()) {
            body.append("\n\nReason: ").append(remark);
        }
        body.append("\n\nIf you believe this is a mistake, please contact the administration office.");
        body.append(SIGNATURE);
        sendSingle(toEmail, "Registration Not Approved", body.toString());
    }

    /** Forgot Password's OTP - the code itself is the entire message, so brevity and a visible expiry window matter more here than in any other email this class sends. */
    public void sendOtp(User user, String otpCode, int validMinutes) {
        sendSingle(user.getEmail(), "Your IntelliResult Nexus verification code",
                "Dear " + user.getFullName() + ",\n\n"
                        + "Your one-time password (OTP) is: " + otpCode + "\n"
                        + "It is valid for " + validMinutes + " minutes. Do not share this code with anyone."
                        + SIGNATURE);
    }

    /** The final step of Forgot Password (Sec. "keep Admin in the loop") - tells the user their OTP-verified password change request has been decided, mirroring sendRegistrationApproved/Rejected's split for the same reason (a rejected reset changes nothing, so the message is deliberately reassuring, not alarming). */
    public void sendPasswordResetDecision(User user, boolean approved) {
        sendSingle(user.getEmail(), "Password Reset " + (approved ? "Approved" : "Not Approved"),
                "Dear " + user.getFullName() + ",\n\n"
                        + (approved
                                ? "Your password has been changed. You can now log in with your new password."
                                : "Your password change request was not approved. Your existing password remains unchanged. "
                                        + "Please contact the administration office if you still need to reset it.")
                        + SIGNATURE);
    }

    /** One connection, many messages - Sec. 23's "important administrative notices" can reach every user of a role, and this project's own demo mail host being an unreachable placeholder should cost one connection failure for the whole batch, not one per recipient. */
    public void sendNoticePublished(List<User> recipients, Notice notice) {
        if (recipients.isEmpty()) {
            return;
        }
        String subject = "Notice: " + notice.getTitle();
        String body = notice.getContent() + SIGNATURE;
        sendBulk(recipients, subject, body);
    }

    private void sendSingle(String toEmail, String subject, String body) {
        try {
            Session session = buildSession();
            MimeMessage message = buildMessage(session, toEmail, subject, body);
            Transport.send(message, AppConfig.getString("mail.username", ""), AppConfig.getString("mail.password", ""));
        } catch (MessagingException | RuntimeException e) {
            LOGGER.warn("Email to {} could not be sent - the in-app notification was still created. Cause: {}",
                    toEmail, e.getMessage());
        }
    }

    private void sendBulk(List<User> recipients, String subject, String body) {
        Session session = buildSession();
        Transport transport = null;
        try {
            transport = session.getTransport("smtp");
            transport.connect(
                    AppConfig.getString("mail.host", ""),
                    AppConfig.getInt("mail.port", 587),
                    AppConfig.getString("mail.username", ""),
                    AppConfig.getString("mail.password", ""));
            for (User recipient : recipients) {
                try {
                    MimeMessage message = buildMessage(session, recipient.getEmail(), subject, body);
                    transport.sendMessage(message, message.getAllRecipients());
                } catch (MessagingException e) {
                    LOGGER.warn("Notice email to {} could not be sent. Cause: {}", recipient.getEmail(), e.getMessage());
                }
            }
        } catch (MessagingException | RuntimeException e) {
            LOGGER.warn("Could not connect to the mail server to send {} notice email(s) - "
                    + "in-app notifications were still created. Cause: {}", recipients.size(), e.getMessage());
        } finally {
            if (transport != null) {
                try {
                    transport.close();
                } catch (MessagingException ignored) {
                    // The connection was already in a failed state; a second log line adds nothing.
                }
            }
        }
    }

    private Session buildSession() {
        Properties props = new Properties();
        props.setProperty("mail.smtp.host", AppConfig.getString("mail.host", ""));
        props.setProperty("mail.smtp.port", String.valueOf(AppConfig.getInt("mail.port", 587)));
        props.setProperty("mail.smtp.starttls.enable", "true");
        props.setProperty("mail.smtp.connectiontimeout", TIMEOUT_MS);
        props.setProperty("mail.smtp.timeout", TIMEOUT_MS);
        props.setProperty("mail.smtp.writetimeout", TIMEOUT_MS);
        return Session.getInstance(props);
    }

    private MimeMessage buildMessage(Session session, String toEmail, String subject, String body) throws MessagingException {
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(AppConfig.getString("mail.username", "noreply@example.edu")));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail));
        message.setSubject(subject);
        message.setText(body);
        return message;
    }
}
