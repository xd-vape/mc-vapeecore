package dev.vapee.core.friend;

import java.util.Objects;
import java.util.UUID;

/**
 * A stable, unordered UUID pair. UUID text order is the persistence contract;
 * hash iteration order must never influence the stored representation.
 */
public record FriendPair(UUID playerA, UUID playerB) implements Comparable<FriendPair> {

    public FriendPair {
        Objects.requireNonNull(playerA, "playerA");
        Objects.requireNonNull(playerB, "playerB");
        if (playerA.equals(playerB)) {
            throw new IllegalArgumentException("A friend pair requires two different players");
        }
        if (compareUuid(playerA, playerB) > 0) {
            UUID swap = playerA;
            playerA = playerB;
            playerB = swap;
        }
    }

    public static FriendPair of(UUID first, UUID second) {
        return new FriendPair(first, second);
    }

    public boolean contains(UUID player) {
        Objects.requireNonNull(player, "player");
        return playerA.equals(player) || playerB.equals(player);
    }

    public UUID other(UUID player) {
        Objects.requireNonNull(player, "player");
        if (playerA.equals(player)) {
            return playerB;
        }
        if (playerB.equals(player)) {
            return playerA;
        }
        throw new IllegalArgumentException("Player is not part of this friend pair");
    }

    @Override
    public int compareTo(FriendPair other) {
        int first = compareUuid(playerA, other.playerA);
        return first != 0 ? first : compareUuid(playerB, other.playerB);
    }

    static int compareUuid(UUID first, UUID second) {
        return first.toString().compareTo(second.toString());
    }
}
