package dev.vapee.core.seat;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record SeatAssignment(
        SeatKey key,
        SeatType type,
        UUID playerId,
        UUID worldId,
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        UUID seatEntityId
) {

    public SeatAssignment {
        key = Objects.requireNonNull(key, "key");
        type = Objects.requireNonNull(type, "type");
        playerId = Objects.requireNonNull(playerId, "playerId");
        worldId = Objects.requireNonNull(worldId, "worldId");
        worldName = requireText(worldName, "worldName");
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");
    }

    public SeatAssignment withSeatEntity(UUID entityId) {
        return new SeatAssignment(
                key, type, playerId, worldId, worldName, x, y, z, yaw, pitch,
                Objects.requireNonNull(entityId, "entityId")
        );
    }

    public Optional<UUID> seatEntity() {
        return Optional.ofNullable(seatEntityId);
    }

    public boolean isMounted() {
        return seatEntityId != null;
    }

    private static String requireText(String value, String name) {
        String text = Objects.requireNonNull(value, name).trim();
        if (text.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return text;
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite");
        }
    }
}
