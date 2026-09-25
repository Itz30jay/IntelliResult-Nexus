package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Append-only audit trail (Sec. 13) - extends BaseEntity directly (no
 * TimestampedEntity) because a row here is never updated after creation;
 * "updated_at" would be a meaningless field on an immutable record.
 * oldInternalMarks/newInternalMarks are additions beyond the spec's literal
 * field list, flagged in PHASE2-DATABASE.md #7. There is deliberately no
 * setter for anything but the constructor - a history row is written once
 * and read forever, so nothing in this class offers a way to mutate one
 * after the fact.
 */
@Entity
@Table(name = "result_history")
public class ResultHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "result_id", nullable = false)
    private Result result;

    @Column(name = "old_theory_marks", precision = 6, scale = 2)
    private BigDecimal oldTheoryMarks;
    @Column(name = "old_practical_marks", precision = 6, scale = 2)
    private BigDecimal oldPracticalMarks;
    @Column(name = "old_internal_marks", precision = 6, scale = 2)
    private BigDecimal oldInternalMarks;

    @Column(name = "new_theory_marks", precision = 6, scale = 2)
    private BigDecimal newTheoryMarks;
    @Column(name = "new_practical_marks", precision = 6, scale = 2)
    private BigDecimal newPracticalMarks;
    @Column(name = "new_internal_marks", precision = 6, scale = 2)
    private BigDecimal newInternalMarks;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by", nullable = false)
    private User changedBy;

    @Column(name = "change_reason", nullable = false, columnDefinition = "TEXT")
    private String changeReason;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    protected ResultHistory() {
    }

    public ResultHistory(Result result,
                          BigDecimal oldTheoryMarks, BigDecimal oldPracticalMarks, BigDecimal oldInternalMarks,
                          BigDecimal newTheoryMarks, BigDecimal newPracticalMarks, BigDecimal newInternalMarks,
                          User changedBy, String changeReason) {
        this.result = result;
        this.oldTheoryMarks = oldTheoryMarks;
        this.oldPracticalMarks = oldPracticalMarks;
        this.oldInternalMarks = oldInternalMarks;
        this.newTheoryMarks = newTheoryMarks;
        this.newPracticalMarks = newPracticalMarks;
        this.newInternalMarks = newInternalMarks;
        this.changedBy = changedBy;
        this.changeReason = changeReason;
    }

    public Result getResult() { return result; }
    public BigDecimal getOldTheoryMarks() { return oldTheoryMarks; }
    public BigDecimal getOldPracticalMarks() { return oldPracticalMarks; }
    public BigDecimal getOldInternalMarks() { return oldInternalMarks; }
    public BigDecimal getNewTheoryMarks() { return newTheoryMarks; }
    public BigDecimal getNewPracticalMarks() { return newPracticalMarks; }
    public BigDecimal getNewInternalMarks() { return newInternalMarks; }
    public User getChangedBy() { return changedBy; }
    public String getChangeReason() { return changeReason; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
