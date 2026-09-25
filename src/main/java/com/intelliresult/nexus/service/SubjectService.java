package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.DepartmentDAO;
import com.intelliresult.nexus.dao.DepartmentDAOImpl;
import com.intelliresult.nexus.dao.SemesterDAO;
import com.intelliresult.nexus.dao.SemesterDAOImpl;
import com.intelliresult.nexus.dao.SubjectDAO;
import com.intelliresult.nexus.dao.SubjectDAOImpl;
import com.intelliresult.nexus.entity.Department;
import com.intelliresult.nexus.entity.Semester;
import com.intelliresult.nexus.entity.Subject;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.SubjectComponentInput;
import com.intelliresult.nexus.service.dto.SubjectRequest;
import com.intelliresult.nexus.util.ValidationUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Owns Sec. 7's Subject Type/Credits/Maximum Marks/Passing Marks rules.
 * schema.sql's CHECK constraints already guarantee "if a component is
 * enabled, its marks aren't null" and "at least one component is enabled" -
 * this service adds the one rule the database can't express on its own:
 * passing marks must not exceed max marks for any enabled component. That's
 * a cross-column comparison within a single row, which MySQL 8 CHECK
 * constraints CAN technically express (unlike the multi-row rules noted in
 * PHASE2-DATABASE.md #9) - deliberately kept in the Service layer anyway,
 * since Sec. 37 assigns validation logic here, and schema.sql's existing
 * constraints already establish the pattern of checking component
 * completeness at the DB level while checking value sensibility in Java.
 */
public class SubjectService {

    private final SubjectDAO subjectDAO = new SubjectDAOImpl();
    private final SemesterDAO semesterDAO = new SemesterDAOImpl();
    private final DepartmentDAO departmentDAO = new DepartmentDAOImpl();

    public Subject createSubject(SubjectRequest req) {
        Semester semester = semesterDAO.findById(req.semesterId())
                .orElseThrow(() -> new ResourceNotFoundException("Semester not found."));
        Department department = departmentDAO.findById(req.departmentId())
                .orElseThrow(() -> new ResourceNotFoundException("Department not found."));

        Map<String, String> errors = validate(req, null);
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            Subject subject = new Subject(semester, department, req.subjectCode().trim().toUpperCase(), req.subjectName().trim());
            subject.setCredits(req.credits());
            applyComponents(subject, req);
            subjectDAO.save(subject);
            tx.commit();
            return subject;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    public Subject updateSubject(Long id, SubjectRequest req) {
        Subject subject = subjectDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject not found."));
        Map<String, String> errors = validate(req, subject.getSubjectCode());
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            subject.setSubjectCode(req.subjectCode().trim().toUpperCase());
            subject.setSubjectName(req.subjectName().trim());
            subject.setCredits(req.credits());
            applyComponents(subject, req);
            subjectDAO.update(subject);
            tx.commit();
            return subject;
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    private void applyComponents(Subject subject, SubjectRequest req) {
        if (req.theory() != null) {
            subject.setTheoryComponent(req.theory().maxMarks(), req.theory().passingMarks());
        } else {
            subject.clearTheoryComponent();
        }
        if (req.practical() != null) {
            subject.setPracticalComponent(req.practical().maxMarks(), req.practical().passingMarks());
        } else {
            subject.clearPracticalComponent();
        }
        if (req.internal() != null) {
            subject.setInternalComponent(req.internal().maxMarks(), req.internal().passingMarks());
        } else {
            subject.clearInternalComponent();
        }
    }

    public void softDelete(Long id, Long adminId) {
        Subject subject = subjectDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject not found."));
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            subject.markDeleted(adminId);
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    public void restore(Long id) {
        Subject subject = subjectDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject not found."));
        Session session = HibernateUtil.getCurrentSession();
        Transaction tx = session.beginTransaction();
        try {
            subject.restore();
            tx.commit();
        } catch (RuntimeException e) {
            if (tx.isActive()) tx.rollback();
            throw e;
        }
    }

    private Map<String, String> validate(SubjectRequest req, String currentCode) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (ValidationUtil.isBlank(req.subjectName())) {
            errors.put("subjectName", "Subject name is required.");
        }
        String normalizedCode = ValidationUtil.isBlank(req.subjectCode()) ? "" : req.subjectCode().trim().toUpperCase();
        if (normalizedCode.isEmpty()) {
            errors.put("subjectCode", "Subject code is required.");
        } else if (!normalizedCode.equals(currentCode) && subjectDAO.findByCode(normalizedCode).isPresent()) {
            errors.put("subjectCode", "This subject code is already in use.");
        }

        if (req.theory() == null && req.practical() == null && req.internal() == null) {
            errors.put("components", "Enable at least one of theory, practical, or internal.");
        }
        validateComponent(req.theory(), "theory", errors);
        validateComponent(req.practical(), "practical", errors);
        validateComponent(req.internal(), "internal", errors);

        return errors;
    }

    private void validateComponent(SubjectComponentInput component, String label, Map<String, String> errors) {
        if (component == null) {
            return;
        }
        BigDecimal max = component.maxMarks();
        BigDecimal pass = component.passingMarks();
        if (max == null || max.compareTo(BigDecimal.ZERO) <= 0) {
            errors.put(label + "MaxMarks", "Enter a maximum marks value greater than 0.");
        }
        if (pass == null || pass.compareTo(BigDecimal.ZERO) < 0) {
            errors.put(label + "PassingMarks", "Enter a passing marks value of 0 or more.");
        }
        if (max != null && pass != null && pass.compareTo(max) > 0) {
            errors.put(label + "PassingMarks", "Passing marks cannot exceed maximum marks.");
        }
    }
}
