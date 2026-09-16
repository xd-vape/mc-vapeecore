package dev.vapee.core.activity.location;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;

public record ActivityPosition(
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    public ActivityPosition {
        worldName = requireWorldName(worldName);
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");
    }

    public Optional<Location> toLocation(Server server) {
        World world = Objects.requireNonNull(server, "server").getWorld(worldName);
        if (world == null) {
            return Optional.empty();
        }
        return Optional.of(new Location(world, x, y, z, yaw, pitch));
    }

    private static String requireWorldName(String worldName) {
        String value = Objects.requireNonNull(worldName, "worldName").trim();
        if (value.isEmpty()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
        return value;
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
