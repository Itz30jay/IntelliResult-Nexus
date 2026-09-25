package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** Sec. 23 - exact spec field list, nothing added. Extends CreatedAtEntity (created_at only, no updated_at) since a notification is written once and only ever has is_read flipped, which doesn't warrant a generic "last modified" timestamp of its own. */
@Entity
@Table(name = "notifications")
public class Notification extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "is_read", nullable = false)
    private boolean read = false;

    protected Notification() {
    }

    public Notification(User user, String title, String message) {
        this.user = user;
        this.title = title;
        this.message = message;
    }

    public User getUser() { return user; }
    public String getTitle() { return title; }
    public String getMessage() { return message; }
    public boolean isRead() { return read; }
    public void markRead() { this.read = true; }
}
