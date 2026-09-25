package com.intelliresult.nexus.service;

import com.intelliresult.nexus.config.HibernateUtil;
import com.intelliresult.nexus.dao.AcademicYearDAO;
import com.intelliresult.nexus.dao.AcademicYearDAOImpl;
import com.intelliresult.nexus.dao.GradingRuleDAO;
import com.intelliresult.nexus.dao.GradingRuleDAOImpl;
import com.intelliresult.nexus.entity.AcademicYear;
import com.intelliresult.nexus.entity.GradingRule;
import com.intelliresult.nexus.exception.BusinessRuleException;
import com.intelliresult.nexus.exception.ResourceNotFoundException;
import com.intelliresult.nexus.exception.ValidationException;
import com.intelliresult.nexus.service.dto.GradingRuleRequest;
import com.intelliresult.nexus.util.ValidationUtil;
import org.hibernate.Session;
import org.hibernate.Transaction;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns Sec. 12's Dynamic Grading Engine. Two rules are enforced here that
 * cannot live in schema.sql (see grading_rules' table comment and
 * PHASE2-DATABASE.md #9): percentage ranges for the same academic year must
 * not overlap, and - a deliberate addition beyond the spec's literal text,
 * flagged here the same way ResultHistory's internal-marks columns or
 * RevaluationRequest's resolved_by were - the same grade name (e.g. "A+")
 * must not appear twice for one academic year while both rows are active.
 * A grading table only means anything if it partitions 0-100 into distinct,
 * unambiguously-named bands; allowing "A+" to silently exist at two
 * non-overlapping ranges in the same year is a data-quality bug the spec's
 * literal "validate overlapping percentage ranges" wording doesn't
 * explicitly name but "prevent invalid configurations" clearly intends.
 * Both checks only ever compare against OTHER *active* rules - a deactivated
 * rule is out of force (GradingRuleDAO.findActiveByAcademicYear already
 * excludes it from the only query GradeUtil/ResultCalculationService will
 * ever run in Phase 7), so it cannot meaningfully "conflict" with anything.
 */
public class GradingRuleService {

    private final GradingRuleDAO gradingRuleDAO = new GradingRuleDAOImpl();
    private final AcademicYearDAO academicYearDAO = new AcademicYearDAOImpl();

    /** Every rule (active and inactive) for one academic year, ascending by range - what the management screen shows; GradingRuleDAO.findActiveByAcademicYear() is the narrower query GradeUtil will use in Phase 7 to actually grade a result. No dedicated DAO query for "all rules, one year": grading tables run to single digits or low tens of rows, the same "fine at this data volume" reasoning SectionService's name-collision check documents. */
    public List<GradingRule> findAllForYear(Long academicYearId) {
        return gradingRuleDAO.findAll().stream()
                .filter(r -> r.getAcademicYear().getId().equals(academicYearId))
                .sorted(Comparator.comparing(GradingRule::getMinPercentage))
                .toList();
    }

    public GradingRule createRule(GradingRuleRequest req) {
        AcademicYear year = academicYearDAO.findById(req.academicYearId())
                .orElseThrow(() -> new ResourceNotFoundException("Academic year not found."));

        Map<String, String> errors = validateFields(req);
        checkOverlapAndDuplicateName(req, null, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        return inTransaction(() -> {
            GradingRule rule = new GradingRule(year, req.minPercentage(), req.maxPercentage(),
                    req.grade().trim().toUpperCase(), req.gradePoint());
            gradingRuleDAO.save(rule);
            return rule;
        });
    }

    /** academicYearId in the request is accepted but not applied - the rule's year is fixed at creation, same "identity settles once created" reasoning as Exam's semester/examType (see ExamService's class Javadoc). The edit form shows the year as read-only text rather than a resubmittable dropdown, precisely so the field being ignored is never a surprise. */
    public GradingRule updateRule(Long id, GradingRuleRequest req) {
        GradingRule rule = gradingRuleDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Grading rule not found."));

        Map<String, String> errors = validateFields(req);
        checkOverlapAndDuplicateName(req, id, errors);
        if (!errors.isEmpty()) {
            throw new ValidationException("Please correct the highlighted fields.", errors);
        }

        return inTransaction(() -> {
            rule.setMinPercentage(req.minPercentage());
            rule.setMaxPercentage(req.maxPercentage());
            rule.setGrade(req.grade().trim().toUpperCase());
            rule.setGradePoint(req.gradePoint());
            gradingRuleDAO.update(rule);
            return rule;
        });
    }

    public void deactivate(Long id) {
        GradingRule rule = gradingRuleDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Grading rule not found."));
        runInTransaction(() -> rule.setActive(false));
    }

    /** Re-validates overlap/duplicate-name before reactivating: while this rule sat inactive, a new rule could legitimately have been created over the same range, and reactivating this one would silently recreate the exact ambiguity Sec. 12 exists to prevent. */
    public void activate(Long id) {
        GradingRule rule = gradingRuleDAO.findById(id).orElseThrow(() -> new ResourceNotFoundException("Grading rule not found."));

        Map<String, String> errors = new LinkedHashMap<>();
        checkOverlapAndDuplicateName(
                new GradingRuleRequest(rule.getAcademicYear().getId(), rule.getMinPercentage(),
                        rule.getMaxPercentage(), rule.getGrade(), rule.getGradePoint()),
                id, errors);
        if (!errors.isEmpty()) {
            throw new BusinessRuleException("Reactivating " + rule.getGrade()
                    + " would conflict with a rule that became active while this one was off: " + errors.values().iterator().next());
        }

        runInTransaction(() -> rule.setActive(true));
    }

    private Map<String, String> validateFields(GradingRuleRequest req) {
        Map<String, String> errors = new LinkedHashMap<>();

        if (ValidationUtil.isBlank(req.grade())) {
            errors.put("grade", "Grade name is required (e.g. A+, B, F).");
        }
        BigDecimal min = req.minPercentage();
        BigDecimal max = req.maxPercentage();
        if (min == null) {
            errors.put("minPercentage", "Minimum percentage is required.");
        } else if (min.compareTo(BigDecimal.ZERO) < 0) {
            errors.put("minPercentage", "Minimum percentage cannot be below 0.");
        }
        if (max == null) {
            errors.put("maxPercentage", "Maximum percentage is required.");
        } else if (max.compareTo(new BigDecimal("100")) > 0) {
            errors.put("maxPercentage", "Maximum percentage cannot exceed 100.");
        }
        if (min != null && max != null && min.compareTo(max) > 0) {
            errors.put("maxPercentage", "Maximum percentage must be greater than or equal to the minimum.");
        }
        if (req.gradePoint() == null || req.gradePoint().compareTo(BigDecimal.ZERO) < 0) {
            errors.put("gradePoint", "Enter a grade point of 0 or more.");
        }

        return errors;
    }

    /** Both multi-row checks in one pass over the same active-rules query, since they share the same "which other rows matter" data source. Adds at most one error per concern so the two potential problems (range overlap, duplicate name) are never conflated into one confusing message. */
    private void checkOverlapAndDuplicateName(GradingRuleRequest req, Long excludeRuleId, Map<String, String> errors) {
        if (req.academicYearId() == null || req.minPercentage() == null || req.maxPercentage() == null || ValidationUtil.isBlank(req.grade())) {
            return; // field-level errors already cover this; nothing meaningful to cross-check yet
        }
        String normalizedGrade = req.grade().trim().toUpperCase();
        List<GradingRule> siblings = gradingRuleDAO.findActiveByAcademicYear(req.academicYearId()).stream()
                .filter(r -> excludeRuleId == null || !r.getId().equals(excludeRuleId))
                .toList();

        for (GradingRule sibling : siblings) {
            boolean rangesOverlap = req.minPercentage().compareTo(sibling.getMaxPercentage()) <= 0
                    && req.maxPercentage().compareTo(sibling.getMinPercentage()) >= 0;
            if (rangesOverlap && !errors.containsKey("minPercentage")) {
                errors.put("minPercentage", "This range overlaps " + sibling.getGrade() + " ("
                        + sibling.getMinPercentage() + "-" + sibling.getMaxPercentage() + "%) for the same academic year.");
            }
            if (sibling.getGrade().equalsIgnoreCase(normalizedGrade) && !errors.containsKey("grade")) {
                errors.put("grade", "Grade \"" + normalizedGrade + "\" is already active for this academic year.");
            }
        }
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
