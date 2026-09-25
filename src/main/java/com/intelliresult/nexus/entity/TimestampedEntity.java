package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Adds an auto-maintained updated_at on top of CreatedAtEntity, for tables
 * whose rows genuinely get edited after creation (academic setup records,
 * users, exams, results). Tables that are append-only (result_history) or
 * have their own domain-specific timestamps instead (teacher_subjects'
 * assigned_at/unassigned_at) deliberately do NOT extend this - a generic
 * "updated_at" would be meaningless noise on a row that's either never
 * updated or already has a more precise timestamp for what actually happened.
 */
@MappedSuperclass
public abstract class TimestampedEntity extends CreatedAtEntity {

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
