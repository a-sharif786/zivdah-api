package com.zivdah.auth.exception;

// Mapped to 409 by GlobalExceptionHandler (e.g. releasing a platform/version pair that already exists).
public class ResourceConflictException extends RuntimeException {
    public ResourceConflictException(String message) {
        super(message);
    }
}
