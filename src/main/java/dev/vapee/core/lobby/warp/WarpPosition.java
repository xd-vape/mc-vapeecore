package dev.vapee.core.lobby.warp;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

public record WarpPosition(
        String worldName,
        double x,
        double y,
        double z,
        float yaw,
        float pitch
) {

    public WarpPosition {
        worldName = Objects.requireNonNull(worldName, "worldName").trim();
        if (worldName.isEmpty()) {
            throw new IllegalArgumentException("worldName must not be blank");
        }
        requireFinite(x, "x");
        requireFinite(y, "y");
        requireFinite(z, "z");
        requireFinite(yaw, "yaw");
        requireFinite(pitch, "pitch");
    }

    public static WarpPosition fromLocation(Location location) {
        Location validatedLocation = Objects.requireNonNull(location, "location");
        World world = Objects.requireNonNull(validatedLocation.getWorld(), "location world");
        return new WarpPosition(
                world.getName(),
                validatedLocation.getX(),
                validatedLocation.getY(),
                validatedLocation.getZ(),
                validatedLocation.getYaw(),
                validatedLocation.getPitch()
        );
    }

    public Optional<Location> toLocation(Function<String, World> worldLookup) {
        World world = Objects.requireNonNull(worldLookup, "worldLookup").apply(worldName);
        return world == null ? Optional.empty() : Optional.of(new Location(world, x, y, z, yaw, pitch));
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
