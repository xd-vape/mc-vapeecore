package dev.vapee.core.clan;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ClanMember(UUID playerId, ClanRole role, Instant joinedAt) {
    public ClanMember {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
