package dev.vapee.core.activity.player;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record ActivityParticipant(UUID uniqueId, Instant joinedAt) {

    public ActivityParticipant {
        uniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        joinedAt = Objects.requireNonNull(joinedAt, "joinedAt");
    }
}
