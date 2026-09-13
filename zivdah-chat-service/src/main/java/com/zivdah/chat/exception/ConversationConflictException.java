package com.zivdah.chat.exception;

// Thrown when POST /conversations/{id}/accept finds 0 rows affected by the atomic
// "WHERE status='WAITING'" update — another agent already claimed it (or it was closed/reset) in
// the meantime. Mapped to HTTP 409 by GlobalExceptionHandler.
public class ConversationConflictException extends RuntimeException {
    public ConversationConflictException(String message) {
        super(message);
    }
}
