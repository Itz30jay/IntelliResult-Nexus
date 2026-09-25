package com.intelliresult.nexus.service.dto;

/**
 * Input for RegistrationService.submitStudentRegistration() - the public
 * Registration form's exact field list for the Student path (full name,
 * roll number, phone, password) plus an optional recovery email not on the
 * literal spec but needed for Forgot Password's OTP later; nothing academic
 * (course/section) is collected here on purpose - see RegistrationRequest's
 * own class Javadoc.
 */
public record StudentRegistrationRequest(
        String fullName,
        String rollNo,
        String phone,
        String email,
        String password
) {
}
