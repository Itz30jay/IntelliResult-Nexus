package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.GradingRule;

import java.util.List;

public class GradingRuleDAOImpl extends AbstractDAO<GradingRule, Long> implements GradingRuleDAO {

    public GradingRuleDAOImpl() {
        super(GradingRule.class);
    }

    @Override
    public List<GradingRule> findActiveByAcademicYear(Long academicYearId) {
        return namedQuery("FROM GradingRule WHERE academicYear.id = :yearId AND active = true ORDER BY minPercentage")
                .setParameter("yearId", academicYearId)
                .getResultList();
    }
}
