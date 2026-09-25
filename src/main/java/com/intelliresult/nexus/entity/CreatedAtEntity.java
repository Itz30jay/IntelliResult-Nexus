package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * For tables that record when a row was created but never mark it as later
 * modified - notifications and activity_logs, which are written once and
 * read many times, never updated. @CreationTimestamp lets Hibernate set this
 * on insert instead of the application having to remember to.
 */
@MappedSuperclass
public abstract class CreatedAtEntity extends BaseEntity {

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
