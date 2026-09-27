package dev.vapee.core.friend;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An immutable, symmetric friendship stored in canonical UUID order. */
public record Friendship(UUID playerA, UUID playerB, Instant createdAt) {

    public Friendship {
        Objects.requireNonNull(playerA, "playerA");
        Objects.requireNonNull(playerB, "playerB");
        Objects.requireNonNull(createdAt, "createdAt");
        FriendPair pair = FriendPair.of(playerA, playerB);
        playerA = pair.playerA();
        playerB = pair.playerB();
    }

    public FriendPair pair() {
        return FriendPair.of(playerA, playerB);
    }
}
