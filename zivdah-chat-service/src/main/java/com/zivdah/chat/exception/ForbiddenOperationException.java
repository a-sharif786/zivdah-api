package com.zivdah.chat.exception;

// Thrown whenever a caller-supplied conversation/order id doesn't belong to the JWT-derived
// caller (e.g. conversation.customerId != current user) — never trust a client-supplied id.
// Mapped to HTTP 403 by GlobalExceptionHandler.
public class ForbiddenOperationException extends RuntimeException {
    public ForbiddenOperationException(String message) {
        super(message);
    }
}
