package dev.vapee.core.activity.location;

import org.bukkit.Location;
import org.bukkit.World;

import java.util.Objects;

public record ActivityArea(
        String worldName,
        double minX,
        double minY,
        double minZ,
        double maxX,
        double maxY,
        double maxZ
) {

    public ActivityArea {
        worldName = requireWorldName(worldName);
        requireFinite(minX, "minX");
        requireFinite(minY, "minY");
        requireFinite(minZ, "minZ");
        requireFinite(maxX, "maxX");
        requireFinite(maxY, "maxY");
        requireFinite(maxZ, "maxZ");

        double normalizedMinX = Math.min(minX, maxX);
        double normalizedMinY = Math.min(minY, maxY);
        double normalizedMinZ = Math.min(minZ, maxZ);
        double normalizedMaxX = Math.max(minX, maxX);
        double normalizedMaxY = Math.max(minY, maxY);
        double normalizedMaxZ = Math.max(minZ, maxZ);

        minX = normalizedMinX;
        minY = normalizedMinY;
        minZ = normalizedMinZ;
        maxX = normalizedMaxX;
        maxY = normalizedMaxY;
        maxZ = normalizedMaxZ;
    }

    public boolean contains(Location location) {
        if (location == null) {
            return false;
        }

        World world = location.getWorld();
        return world != null
                && worldName.equals(world.getName())
                && containsCoordinates(location.getX(), location.getY(), location.getZ());
    }

    public boolean contains(ActivityPosition position) {
        return position != null
                && worldName.equals(position.worldName())
                && containsCoordinates(position.x(), position.y(), position.z());
    }

    private boolean containsCoordinates(double x, double y, double z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
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
}
