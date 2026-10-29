package dev.vapee.core.lobby.experience.navigator;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.HashSet;
import java.util.UUID;

/** An explicit inclusive cuboid, using the same world-name convention as local warps. */
public record NavigatorRegion(String world, double minX, double minY, double minZ,
                              double maxX, double maxY, double maxZ) {
    public NavigatorRegion {
        if (world == null || world.isBlank()) throw new IllegalArgumentException("Count world is required");
        for (double value : new double[]{minX, minY, minZ, maxX, maxY, maxZ}) {
            if (!Double.isFinite(value)) throw new IllegalArgumentException("Count bounds must be finite");
        }
        double x = minX, y = minY, z = minZ;
        minX = Math.min(x, maxX); minY = Math.min(y, maxY); minZ = Math.min(z, maxZ);
        maxX = Math.max(x, maxX); maxY = Math.max(y, maxY); maxZ = Math.max(z, maxZ);
    }

    public boolean contains(Location location) {
        return location != null && location.getWorld() != null && world.equals(location.getWorld().getName())
                && location.getX() >= minX && location.getX() <= maxX
                && location.getY() >= minY && location.getY() <= maxY
                && location.getZ() >= minZ && location.getZ() <= maxZ;
    }

    public long count(Player viewer, Collection<? extends Player> online) {
        long count = 0;
        var seen = new HashSet<UUID>();
        for (Player target : online) {
            if (target == null || !target.isOnline() || !seen.add(target.getUniqueId())
                    || target.getWorld() == null || !world.equals(target.getWorld().getName())
                    || !viewer.canSee(target) || target.isInvisible()
                    || target.getMetadata("vanished").stream().anyMatch(value -> value.asBoolean())
                    || target.getMetadata("vanish").stream().anyMatch(value -> value.asBoolean())) continue;
            if (contains(target.getLocation())) count++;
        }
        return count;
    }

    static int stackAmount(long count, int itemLimit) {
        if (count < 0) throw new IllegalArgumentException("Negative player count");
        return (int) Math.max(1L, Math.min(count, Math.min(64, Math.max(1, itemLimit))));
    }
}
