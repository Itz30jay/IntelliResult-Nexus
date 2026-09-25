package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * Sec. 12's dynamic grading engine - grades and boundaries are pure data,
 * never hard-coded in Java (GradeUtil in Phase 7 looks these rows up, it
 * does not define them). isActive rather than the generic
 * deleted/deletedBy/deletedAt triple: a rule that already graded a published
 * result must stay queryable by name, not soft-deleted and hidden the way a
 * mistakenly-created department would be. Overlap validation across rows for
 * the same academic year is a Service-layer rule (GradingService, Phase 5) -
 * see PHASE2-DATABASE.md #9 for why no constraint here can express it.
 */
@Entity
@Table(name = "grading_rules")
public class GradingRule extends TimestampedEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "academic_year_id", nullable = false)
    private AcademicYear academicYear;

    @Column(name = "min_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercentage;

    @Column(name = "max_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPercentage;

    @Column(name = "grade", nullable = false, length = 10)
    private String grade;

    @Column(name = "grade_point", nullable = false, precision = 4, scale = 2)
    private BigDecimal gradePoint;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    protected GradingRule() {
    }

    public GradingRule(AcademicYear academicYear, BigDecimal minPercentage, BigDecimal maxPercentage,
                        String grade, BigDecimal gradePoint) {
        this.academicYear = academicYear;
        this.minPercentage = minPercentage;
        this.maxPercentage = maxPercentage;
        this.grade = grade;
        this.gradePoint = gradePoint;
    }

    public AcademicYear getAcademicYear() { return academicYear; }
    public BigDecimal getMinPercentage() { return minPercentage; }
    public void setMinPercentage(BigDecimal minPercentage) { this.minPercentage = minPercentage; }
    public BigDecimal getMaxPercentage() { return maxPercentage; }
    public void setMaxPercentage(BigDecimal maxPercentage) { this.maxPercentage = maxPercentage; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public BigDecimal getGradePoint() { return gradePoint; }
    public void setGradePoint(BigDecimal gradePoint) { this.gradePoint = gradePoint; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }

    /** Whether pct falls in [min, max] inclusive - the one piece of query-independent logic worth keeping on the entity itself, since it's a pure function of this row's own two boundary fields and every caller (GradingService, reports) needs the exact same inclusive-boundary semantics schema.sql's CHECK constraint already assumes. */
    public boolean covers(BigDecimal pct) {
        return pct.compareTo(minPercentage) >= 0 && pct.compareTo(maxPercentage) <= 0;
    }
}
