package com.zivdah.auth.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

// Mobile OTP failures with their own status: 400 invalid/expired OTP, 429 resend cooldown or
// provider number limit, 503 SMS provider unavailable. Mapped as-is by GlobalExceptionHandler.
@Getter
public class OtpException extends RuntimeException {

    private final HttpStatus status;

    public OtpException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
}
