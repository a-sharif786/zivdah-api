package com.zivdah.chat.exception;

// e.g. registering a userId that's already on the support_agents roster. Mapped to 409, same as
// ConversationConflictException but not conversation-specific, so kept as its own type.
public class DuplicateResourceException extends RuntimeException {
    public DuplicateResourceException(String message) {
        super(message);
    }
}
