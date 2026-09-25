package com.zivdah.auth.exception;

// Mapped to 401 (not the generic RuntimeException -> 400) by GlobalExceptionHandler, so
// clients can tell "refresh failed, log out" apart from ordinary validation errors.
public class InvalidRefreshTokenException extends RuntimeException {
    public InvalidRefreshTokenException(String message) {
        super(message);
    }
}
