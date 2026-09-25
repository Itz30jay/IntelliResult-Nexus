package com.intelliresult.nexus.service.dto;

import com.intelliresult.nexus.entity.enums.UserRole;

/**
 * Input for UserService.createUser(), which now only ever accepts
 * {@code UserRole.ADMIN} - see that method's own Javadoc.
 * Upgrade: rollNo/courseId/sectionId/employeeCode/departmentId/designation
 * removed along with the STUDENT/TEACHER creation path they served; role is
 * kept (rather than assumed ADMIN implicitly) so UserService's own check
 * remains a real, meaningful guard against a caller passing anything else,
 * not just documentation of an assumption.
 */
public record UserCreateRequest(
        UserRole role,
        String email,
        String fullName,
        String phone
) {
}
