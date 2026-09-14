package dev.vapee.core.lobby;

import java.util.Objects;

public record LobbySpawn(
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    public LobbySpawn {
        worldName = Objects.requireNonNull(worldName, "worldName").trim();
        if (worldName.isEmpty()) {
            throw new IllegalArgumentException("Lobby spawn world name must not be blank");
        }
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("Lobby spawn coordinates must be finite");
        }
        if (!Float.isFinite(yaw) || !Float.isFinite(pitch)) {
            throw new IllegalArgumentException("Lobby spawn rotation must be finite");
        }
    }
}
