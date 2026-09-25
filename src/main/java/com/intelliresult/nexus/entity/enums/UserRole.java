package com.intelliresult.nexus.entity.enums;

/** Mirrors users.chk_users_role in schema.sql exactly - the three roles from Sec. 2. Stored as its name() string (@Enumerated(EnumType.STRING) on User.role), matching the VARCHAR+CHECK column rather than a native MySQL ENUM, so the allowed set stays defined in exactly one place if it's ever extended. */
public enum UserRole {
    ADMIN,
    TEACHER,
    STUDENT
}
