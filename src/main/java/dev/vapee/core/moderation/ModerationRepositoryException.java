package dev.vapee.core.moderation;

public final class ModerationRepositoryException extends RuntimeException {
    public ModerationRepositoryException(String message) { super(message); }
    public ModerationRepositoryException(String message, Throwable cause) { super(message, cause); }
}
