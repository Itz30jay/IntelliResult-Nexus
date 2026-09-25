package com.intelliresult.nexus.entity;

import com.intelliresult.nexus.entity.converter.NoticeAudienceConverter;
import com.intelliresult.nexus.entity.enums.NoticePriority;
import com.intelliresult.nexus.entity.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;
import java.util.Set;

/** Sec. 25. audience uses NoticeAudienceConverter to bridge the native MySQL SET column - see that class for why. "archive" in the spec's own language for removing a notice maps directly onto the standard soft-delete pattern (markDeleted/restore, inherited from SoftDeletableEntity). */
@Entity
@Table(name = "notices")
public class Notice extends SoftDeletableEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = NoticeAudienceConverter.class)
    @Column(name = "audience", nullable = false)
    private Set<UserRole> audience;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private NoticePriority priority = NoticePriority.NORMAL;

    @Column(name = "is_published", nullable = false)
    private boolean published = false;

    @Column(name = "published_date")
    private LocalDateTime publishedDate;

    @Column(name = "expiry_date")
    private LocalDateTime expiryDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by", nullable = false)
    private User createdBy;

    protected Notice() {
    }

    public Notice(String title, String content, Set<UserRole> audience, User createdBy) {
        this.title = title;
        this.content = content;
        this.audience = audience;
        this.createdBy = createdBy;
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public Set<UserRole> getAudience() { return audience; }
    public void setAudience(Set<UserRole> audience) { this.audience = audience; }
    public NoticePriority getPriority() { return priority; }
    public void setPriority(NoticePriority priority) { this.priority = priority; }
    public boolean isPublished() { return published; }
    public LocalDateTime getPublishedDate() { return publishedDate; }
    public LocalDateTime getExpiryDate() { return expiryDate; }
    public void setExpiryDate(LocalDateTime expiryDate) { this.expiryDate = expiryDate; }
    public User getCreatedBy() { return createdBy; }

    public void publish() {
        this.published = true;
        this.publishedDate = LocalDateTime.now();
    }

    public void unpublish() {
        this.published = false;
    }
}
