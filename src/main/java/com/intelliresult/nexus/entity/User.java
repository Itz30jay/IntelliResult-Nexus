package com.intelliresult.nexus.entity;

import com.intelliresult.nexus.entity.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * Single identity/auth table for all three roles - see PHASE2-DATABASE.md #5
 * for why there is no separate admin_profiles table. passwordHash is never
 * touched here beyond storage/retrieval: hashing itself is PasswordUtil's
 * job (Phase 4), keeping "how a password becomes a hash" out of the entity
 * layer entirely.
 */
@Entity
@Table(name = "users")
public class User extends SoftDeletableEntity {

    // Upgrade: nullable (was NOT NULL). Self-registered Students/Teachers
    // log in with roll_no/employee_code, not email, and Registration does
    // not collect one - see RegistrationService. Still unique when present:
    // MySQL/Hibernate both treat multiple NULLs as distinct under a UNIQUE
    // constraint, so any number of accounts can have no email at once.
    @Column(name = "email", length = 150, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "phone", length = 20)
    private String phone;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    protected User() {
    }

    public User(String email, String passwordHash, String fullName, UserRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
        this.role = role;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    public UserRole getRole() { return role; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void recordLogin() { this.lastLoginAt = LocalDateTime.now(); }
}
