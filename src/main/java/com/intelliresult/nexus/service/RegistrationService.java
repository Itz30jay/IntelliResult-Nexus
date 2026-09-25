package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.CourseDAO;
import com.intelliresult.nexus.dao.CourseDAOImpl;
import com.intelliresult.nexus.dao.DepartmentDAO;
import com.intelliresult.nexus.dao.DepartmentDAOImpl;
import com.intelliresult.nexus.dao.RegistrationRequestDAO;
import com.intelliresult.nexus.dao.RegistrationRequestDAOImpl;
import com.intelliresult.nexus.dao.SectionDAO;
import com.intelliresult.nexus.dao.SectionDAOImpl;
import com.intelliresult.nexus.dao.StudentDAO;
import com.intelliresult.nexus.dao.StudentDAOImpl;
import com.intelliresult.nexus.dao.TeacherDAO;
import com.intelliresult.nexus.dao.TeacherDAOImpl;
import com.intelliresult.nexus.dao.UserDAO;
import com.intelliresult.nexus.dao.UserDAOImpl;
import com.intelliresult.nexus.entity.Course;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.RegistrationRequest;
import com.intelliresult.nexus.entity.Section;
import com.intelliresult.nexus.entity.Student;
import com.intelliresult.nexus.entity.Teacher;
import com.intelliresult.nexus.entity.User;
import com.intelliresult.nexus.entity.enums.RegistrationStatus;
import com.intelliresult.nexus.entity.enums.UserRole;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.StudentRegistrationRequest;
import com.intelliresult.nexus.service.dto.TeacherRegistrationRequest;
import com.intelliresult.nexus.util.PasswordUtil;
import com.intelliresult.nexus.util.ValidationUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns the public Registration form and the admin verification queue it
 * feeds. Deliberately does NOT touch users/students/teachers until an admin
 * approves - see RegistrationRequest's own class Javadoc for why a
 * dedicated staging table exists at all rather than a PENDING status on
 * User directly. Every uniqueness check here (identifier availability)
 * considers both the live Student/Teacher tables AND any other still-PENDING
 * request, so two people can never simultaneously have unresolved claims on
 * the same roll number/employee code, and a request cannot be approved into
 * an identifier some other request or account has since claimed.
 * <p>
 * approveStudent/approveTeacher intentionally do not call
 * UserService.createUser(): that method requires an email
 * (UserService.validateCommonFields) and generates a temporary password for
 * an admin to relay - both wrong for this path, where the registrant has
 * no required email and already chose their own password at submission
 * time. Duplicating the small amount of object-construction this needs is
 * clearer than bending UserService's admin-creation contract to also serve
 * a meaningfully different caller.
 */
public class RegistrationService {

    private final RegistrationRequestDAO registrationRequestDAO = new RegistrationRequestDAOImpl();
    private final UserDAO userDAO = new UserDAOImpl();
    private final StudentDAO studentDAO = new StudentDAOImpl();
    private final TeacherDAO teacherDAO = new TeacherDAOImpl();
    private final CourseDAO courseDAO = new CourseDAOImpl();
    private final SectionDAO sectionDAO = new SectionDAOImpl();
    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();
    private final NotificationService notificationService = new NotificationService();

    // ---------------------------------------------------------------- submission

    public RegistrationRequest submitStudentRegistration(StudentRegistrationRequest req) {
        Map<String, String> errors = validateCommonSubmissionFields(req.fullName(), req.phone(), req.email(), req.password());
        String rollNo = req.rollNo() == null ? null : req.rollNo().trim().toUpperCase();
        if (ValidationUtil.isBlank(rollNo)) {
            errors.put("rollNo", "Roll number is required.");
        } else if (studentDAO.existsByRollNo(rollNo) || registrationRequestDAO.existsPendingByIdentifier(rollNo, UserRole.STUDENT)) {
            errors.put("rollNo", "An account or pending registration already uses this roll number.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        return inTransaction(() -> registrationRequestDAO.save(new RegistrationRequest(
                UserRole.STUDENT, req.fullName().trim(), rollNo, req.phone().trim(),
                blankToNull(req.email()), PasswordUtil.hash(req.password()))));
    }

    public RegistrationRequest submitTeacherRegistration(TeacherRegistrationRequest req) {
        Map<String, String> errors = validateCommonSubmissionFields(req.fullName(), req.phone(), req.email(), req.password());
        String employeeCode = req.employeeCode() == null ? null : req.employeeCode().trim().toUpperCase();
        if (ValidationUtil.isBlank(employeeCode)) {
            errors.put("employeeCode", "Employee ID is required.");
        } else if (teacherDAO.existsByEmployeeCode(employeeCode) || registrationRequestDAO.existsPendingByIdentifier(employeeCode, UserRole.TEACHER)) {
            errors.put("employeeCode", "An account or pending registration already uses this employee ID.");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        return inTransaction(() -> registrationRequestDAO.save(new RegistrationRequest(
                UserRole.TEACHER, req.fullName().trim(), employeeCode, req.phone().trim(),
                blankToNull(req.email()), PasswordUtil.hash(req.password()))));
    }

    private Map<String, String> validateCommonSubmissionFields(String fullName, String phone, String email, String password) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (ValidationUtil.isBlank(fullName)) {
            errors.put("fullName", "Full name is required.");
        }
        if (ValidationUtil.isBlank(phone)) {
            errors.put("phone", "Phone number is required.");
        }
        if (ValidationUtil.isNotBlank(email) && !ValidationUtil.isValidEmail(email)) {
            errors.put("email", "Enter a valid email address, or leave it blank.");
        }
        if (!ValidationUtil.isValidPassword(password)) {
            errors.put("password", "Password must be at least 8 characters and include a letter and a number.");
        }
        return errors;
    }

    private String blankToNull(String value) {
        return ValidationUtil.isBlank(value) ? null : value.trim();
    }

    // ---------------------------------------------------------------- admin verification queue

    public List<RegistrationRequest> listPending() {
        return registrationRequestDAO.findByStatus(RegistrationStatus.PENDING);
    }

    public List<RegistrationRequest> listReviewed() {
        List<RegistrationRequest> reviewed = registrationRequestDAO.findByStatus(RegistrationStatus.APPROVED);
        reviewed.addAll(registrationRequestDAO.findByStatus(RegistrationStatus.REJECTED));
        reviewed.sort((a, b) -> b.getReviewedAt().compareTo(a.getReviewedAt()));
        return reviewed;
    }

    /**
     * Approval both verifies the identity claim AND supplies the academic
     * placement (course + optional section) Registration itself never
     * asked for - one admin action does the work a registrar's office
     * would otherwise do in two steps.
     * <p>
     * Re-checks identifier availability here, not just at submission time -
     * submitStudentRegistration's own check runs when the request is
     * created, but two people can submit for the same roll number close
     * enough together that both pass that check before either is approved
     * (students.roll_no's own UNIQUE constraint is the final backstop
     * either way, but catching it here first gives a specific "someone
     * already holds this roll number" message instead of a raw constraint-
     * violation surfacing as a generic error).
     */
    public User approveStudent(Long requestId, Long courseId, Long sectionId, Long adminId) {
        RegistrationRequest request = loadPendingOrThrow(requestId, UserRole.STUDENT);
        if (studentDAO.existsByRollNo(request.getIdentifier())) {
            throw new BusinessRuleException("Roll number " + request.getIdentifier()
                    + " is already in use by another account - this request cannot be approved as-is.");
        }
        Course course = courseDAO.findById(courseId)
                .orElseThrow(() -> new ResourceNotFoundException("Selected course not found."));
        Section section = null;
        if (sectionId != null) {
            section = sectionDAO.findById(sectionId)
                    .orElseThrow(() -> new ResourceNotFoundException("Selected section not found."));
        }
        Section finalSection = section;

        User user = inTransaction(() -> {
            User newUser = buildAndSaveUser(request, UserRole.STUDENT);
            Student student = new Student(newUser, request.getIdentifier(), course);
            student.setCurrentSection(finalSection);
            studentDAO.save(student);
            request.approve(newUser.getId(), userDAO.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("Admin user not found.")));
            registrationRequestDAO.update(request);
            return newUser;
        });
        notificationService.notifyRegistrationApproved(user, request.getIdentifier());
        return user;
    }

    /** Mirrors approveStudent for the Teacher path, including its defensive re-check - department is required (teachers.department_id is NOT NULL), designation is optional exactly as it already is on the admin-creation path (UserService.createTeacherProfile). */
    public User approveTeacher(Long requestId, Long departmentId, String designation, Long adminId) {
        RegistrationRequest request = loadPendingOrThrow(requestId, UserRole.TEACHER);
        if (teacherDAO.existsByEmployeeCode(request.getIdentifier())) {
            throw new BusinessRuleException("Employee ID " + request.getIdentifier()
                    + " is already in use by another account - this request cannot be approved as-is.");
        }
        Department department = departmentDAO.findById(departmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Selected department not found."));

        User user = inTransaction(() -> {
            User newUser = buildAndSaveUser(request, UserRole.TEACHER);
            Teacher teacher = new Teacher(newUser, request.getIdentifier(), department);
            teacher.setDesignation(ValidationUtil.isBlank(designation) ? null : designation.trim());
            teacherDAO.save(teacher);
            request.approve(newUser.getId(), userDAO.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("Admin user not found.")));
            registrationRequestDAO.update(request);
            return newUser;
        });
        notificationService.notifyRegistrationApproved(user, request.getIdentifier());
        return user;
    }

    public void reject(Long requestId, Long adminId, String remark) {
        RegistrationRequest request = registrationRequestDAO.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration request not found."));
        if (ValidationUtil.isBlank(remark)) {
            throw new ValidationException("Please provide a reason for rejecting this request.",
                    Map.of("remark", "Required."));
        }
        inTransaction(() -> {
            request.reject(remark.trim(), userDAO.findById(adminId).orElseThrow(() -> new ResourceNotFoundException("Admin user not found.")));
            registrationRequestDAO.update(request);
            return null;
        });
        notificationService.notifyRegistrationRejected(request.getEmail(), request.getFullName(), remark.trim());
    }

    private RegistrationRequest loadPendingOrThrow(Long requestId, UserRole expectedRole) {
        RegistrationRequest request = registrationRequestDAO.findById(requestId)
                .orElseThrow(() -> new ResourceNotFoundException("Registration request not found."));
        if (request.getStatus() != RegistrationStatus.PENDING) {
            throw new BusinessRuleException("This request was already " + request.getStatus() + ".");
        }
        if (request.getRole() != expectedRole) {
            throw new BusinessRuleException("This request is not a " + expectedRole + " registration.");
        }
        return request;
    }

    /** The password hash and (optional) email travel across unchanged from the original submission - the registrant already chose both; approval only ever adds the academic placement, never re-collects credentials. */
    private User buildAndSaveUser(RegistrationRequest request, UserRole role) {
        User user = new User(request.getEmail(), request.getPasswordHash(), request.getFullName(), role);
        user.setPhone(request.getPhone());
        userDAO.save(user);
        return user;
    }

    // ---------------------------------------------------------------- transaction helper

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
            if (tx.isActive()) {
                tx.rollback();
            }
            throw e;
        }
    }
}
