package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import java.time.LocalDateTime;

/**
 * Implements Section 27's recycle-bin pattern. deletedBy is a plain Long
 * (the raw users.id value), not a managed @ManyToOne relationship - this
 * field is written once at delete time and read only inside the recycle-bin
 * admin view, so a lazy association would add a relationship to manage on
 * every soft-deletable entity for a field that's rarely traversed. Contrast
 * with TeacherSubject.assignedBy (Phase 3 notes), which IS mapped as a full
 * relationship because assignment history is a primary, frequently-displayed
 * feature (Sec. 8), not an occasional audit lookup.
 */
@MappedSuperclass
public abstract class SoftDeletableEntity extends TimestampedEntity {

    @Column(name = "deleted", nullable = false)
    private boolean deleted = false;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public boolean isDeleted() {
        return deleted;
    }

    public Long getDeletedBy() {
        return deletedBy;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    /** Marks this record deleted (recycle bin), stamping who and when in one call so the three fields can never be set inconsistently with each other. */
    public void markDeleted(Long deletedByUserId) {
        this.deleted = true;
        this.deletedBy = deletedByUserId;
        this.deletedAt = LocalDateTime.now();
    }

    /** Restores a soft-deleted record (recycle bin "Restore" action). */
    public void restore() {
        this.deleted = false;
        this.deletedBy = null;
        this.deletedAt = null;
    }
}
