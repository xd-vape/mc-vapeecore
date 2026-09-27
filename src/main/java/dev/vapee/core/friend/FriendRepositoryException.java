package dev.vapee.core.friend;

public final class FriendRepositoryException extends IllegalStateException {

    public FriendRepositoryException(String message) {
        super(message);
    }

    public FriendRepositoryException(String message, Throwable cause) {
        super(message, cause);
    }
}
