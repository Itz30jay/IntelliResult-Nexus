package com.intelliresult.nexus.service.dto;

/**
 * Input for RegistrationService.submitTeacherRegistration() - the public
 * Registration form's exact field list for the Teacher path (full name,
 * employee ID, phone, password) plus an optional recovery email, mirroring
 * StudentRegistrationRequest. See RegistrationRequest's own class Javadoc
 * for why department/designation are deliberately absent here.
 */
public record TeacherRegistrationRequest(
        String fullName,
        String employeeCode,
        String phone,
        String email,
        String password
) {
}
