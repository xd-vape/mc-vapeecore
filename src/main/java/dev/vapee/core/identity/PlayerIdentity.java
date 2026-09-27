package dev.vapee.core.identity;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record PlayerIdentity(UUID uniqueId, String name, Instant firstJoin, Instant lastJoin) {

    public PlayerIdentity {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(firstJoin, "firstJoin");
        Objects.requireNonNull(lastJoin, "lastJoin");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (lastJoin.isBefore(firstJoin)) {
            throw new IllegalArgumentException("lastJoin must not precede firstJoin");
        }
    }
}
