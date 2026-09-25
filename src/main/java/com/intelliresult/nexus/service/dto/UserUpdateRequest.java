package com.intelliresult.nexus.service.dto;

/**
 * No role field here on purpose - see UserService's note on why role is
 * immutable once a user is created.
 * Upgrade: rollNo/courseId/sectionId/employeeCode/departmentId/designation
 * removed - UserService.updateUser() now only ever operates on an ADMIN-role
 * account (Student/Teacher accounts have no edit screen anywhere in the new
 * design), so carrying academic-profile fields this method can never read
 * would just be unused surface area.
 */
public record UserUpdateRequest(
        Long userId,
        String email,
        String fullName,
        String phone
) {
}
