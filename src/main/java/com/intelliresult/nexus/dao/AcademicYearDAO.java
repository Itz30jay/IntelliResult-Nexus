package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.AcademicYear;

import java.util.Optional;

public interface AcademicYearDAO extends GenericDAO<AcademicYear, Long> {
    Optional<AcademicYear> findByLabel(String label);

    /** The single is_current=true row, if one has been set - AcademicYearService (Phase 5) is what guarantees there's ever at most one (PHASE2-DATABASE.md #9), so this legitimately returns empty if none has been marked current yet. */
    Optional<AcademicYear> findCurrent();
}
