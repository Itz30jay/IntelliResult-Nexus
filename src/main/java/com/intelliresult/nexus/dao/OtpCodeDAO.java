package com.intelliresult.nexus.dao;

import com.intelliresult.nexus.entity.OtpCode;

import java.util.Optional;

public interface OtpCodeDAO extends GenericDAO<OtpCode, Long> {
    /** The code PasswordResetService.verifyOtp checks against - the most recently requested one for this user, since requesting a new OTP should always supersede an older still-unexpired one rather than leaving multiple simultaneously "valid" codes a user could be confused by. */
    Optional<OtpCode> findMostRecentByUser(Long userId);
}
