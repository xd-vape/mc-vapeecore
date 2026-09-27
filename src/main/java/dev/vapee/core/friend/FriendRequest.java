package dev.vapee.core.friend;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An immutable, directed friend request. */
public record FriendRequest(UUID sender, UUID recipient, Instant createdAt) {

    public FriendRequest {
        Objects.requireNonNull(sender, "sender");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(createdAt, "createdAt");
        if (sender.equals(recipient)) {
            throw new IllegalArgumentException("A friend request cannot target its sender");
        }
    }
}
