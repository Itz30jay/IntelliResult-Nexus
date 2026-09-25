package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDate;

/**
 * e.g. "2025-2026". isCurrent should have at most one true row across the
 * whole table at any time - a rule that depends on comparing this row
 * against every other row, so it's enforced by AcademicYearService (Phase 5),
 * not by any constraint expressible here (see PHASE2-DATABASE.md #9).
 */
@Entity
@Table(name = "academic_years")
public class AcademicYear extends TimestampedEntity {

    @Column(name = "label", nullable = false, length = 20, unique = true)
    private String label;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "is_current", nullable = false)
    private boolean current;

    protected AcademicYear() {
    }

    public AcademicYear(String label, LocalDate startDate, LocalDate endDate) {
        this.label = label;
        this.startDate = startDate;
        this.endDate = endDate;
        this.current = false;
    }

    public String getLabel() { return label; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public boolean isCurrent() { return current; }
    public void setCurrent(boolean current) { this.current = current; }
}
