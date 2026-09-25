package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.AcademicYear;

import java.util.Optional;

public class AcademicYearDAOImpl extends AbstractDAO<AcademicYear, Long> implements AcademicYearDAO {

    public AcademicYearDAOImpl() {
        super(AcademicYear.class);
    }

    @Override
    public Optional<AcademicYear> findByLabel(String label) {
        return namedQuery("FROM AcademicYear WHERE label = :label")
                .setParameter("label", label)
                .uniqueResultOptional();
    }

    @Override
    public Optional<AcademicYear> findCurrent() {
        return namedQuery("FROM AcademicYear WHERE current = true").uniqueResultOptional();
    }
}
