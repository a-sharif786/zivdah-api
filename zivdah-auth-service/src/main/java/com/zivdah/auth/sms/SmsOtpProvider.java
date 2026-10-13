package com.zivdah.auth.sms;

import reactor.core.publisher.Mono;

// An SMS gateway that generates and verifies OTPs itself (the OTP never passes through us).
// Selected by otp.sms.provider: "authkey" (real SMS via authkey.io) or "static" (fixed 123456, local dev only).
// Failures the user can act on are OtpException with a 4xx status; provider/config problems are
// OtpException 503 (details only in the server log).
public interface SmsOtpProvider {

    // Sends an OTP to the mobile number; emits the provider's reference for it (to verify against).
    Mono<String> send(String mobile);

    Mono<OtpCheckResult> verify(String mobile, String reference, String otp);

    enum OtpCheckResult {
        VALID,
        INVALID,
        // expired, already used, or unknown reference — the user must request a new OTP
        EXPIRED
    }
}
