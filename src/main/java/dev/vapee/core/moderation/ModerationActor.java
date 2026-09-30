package dev.vapee.core.moderation;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Actor identity, never a persisted display name or a Bukkit player reference. */
public record ModerationActor(ModerationActorType type, Optional<UUID> playerId) {
    public ModerationActor {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(playerId, "playerId");
        if ((type == ModerationActorType.PLAYER) != playerId.isPresent()) {
            throw new IllegalArgumentException("PLAYER requires a UUID; CONSOLE must not have one");
        }
    }

    public static ModerationActor player(UUID id) {
        return new ModerationActor(ModerationActorType.PLAYER, Optional.of(id));
    }

    public static ModerationActor console() {
        return new ModerationActor(ModerationActorType.CONSOLE, Optional.empty());
    }
}
