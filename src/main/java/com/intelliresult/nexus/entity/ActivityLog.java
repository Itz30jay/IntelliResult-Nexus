package com.intelliresult.nexus.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Sec. 26 audit trail - append-only, never updated, so no setters beyond
 * the constructor exist here at all. userId is nullable at the database
 * level to cover failed-login attempts against an email that never resolved
 * to a real account; this entity mirrors that with a plain nullable
 * @ManyToOne rather than requiring a User.
 */
@Entity
@Table(name = "activity_logs")
public class ActivityLog extends CreatedAtEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    protected ActivityLog() {
    }

    public ActivityLog(User user, String action, String details, String ipAddress) {
        this.user = user;
        this.action = action;
        this.details = details;
        this.ipAddress = ipAddress;
    }

    public User getUser() { return user; }
    public String getAction() { return action; }
    public String getDetails() { return details; }
    public String getIpAddress() { return ipAddress; }
}
