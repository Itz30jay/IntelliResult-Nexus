package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.GradingRule;

import java.util.List;

public interface GradingRuleDAO extends GenericDAO<GradingRule, Long> {
    /**
     * Every active rule for a year, in ascending order. This is the one
     * method GradingService (Phase 5/7) needs for BOTH its jobs: overlap
     * validation before saving a new/edited rule, and turning a computed
     * percentage into a grade by iterating this list and calling each
     * GradingRule.covers(pct) - deliberately not a
     * findGradeForPercentage(...) DAO method, since "which rule matches this
     * percentage" is calculation logic that belongs with
     * ResultCalculationService (Sec. 11), not baked into a query.
     */
    List<GradingRule> findActiveByAcademicYear(Long academicYearId);
}
