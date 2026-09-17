package dev.vapee.core.lobby.warp;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

public final class WarpService {

    public static final Material DEFAULT_ICON = Material.ENDER_PEARL;

    private final WarpConfig warpConfig;
    private final Function<String, World> worldLookup;
    private NavigableMap<String, WarpPoint> warps;

    public WarpService(JavaPlugin plugin, WarpConfig warpConfig) {
        this(
                warpConfig,
                Objects.requireNonNull(plugin, "plugin").getServer()::getWorld,
                warpConfig.initialize()
        );
    }

    public WarpService(
            WarpConfig warpConfig,
            Function<String, World> worldLookup,
            NavigableMap<String, WarpPoint> initialWarps
    ) {
        this.warpConfig = Objects.requireNonNull(warpConfig, "warpConfig");
        this.worldLookup = Objects.requireNonNull(worldLookup, "worldLookup");
        this.warps = new TreeMap<>(Objects.requireNonNull(initialWarps, "initialWarps"));
    }

    public Optional<WarpPoint> getWarp(String id) {
        return Optional.ofNullable(warps.get(normalizeId(id)));
    }

    public List<WarpPoint> getWarps() {
        return List.copyOf(warps.values());
    }

    public boolean hasWarp(String id) {
        return warps.containsKey(normalizeId(id));
    }

    public WarpResult setWarp(String id, Location location) {
        String normalizedId = normalizeId(id);
        if (!WarpPoint.isValidId(normalizedId)) {
            return WarpResult.INVALID_ID;
        }
        WarpPosition position;
        try {
            position = WarpPosition.fromLocation(location);
        } catch (IllegalArgumentException | NullPointerException exception) {
            return WarpResult.WORLD_NOT_LOADED;
        }
        WarpPoint existing = warps.get(normalizedId);
        WarpPoint updated = existing == null
                ? new WarpPoint(normalizedId, defaultDisplayName(normalizedId), DEFAULT_ICON, position)
                : new WarpPoint(normalizedId, existing.displayName(), existing.icon(), position);
        persistReplacement(normalizedId, updated);
        return WarpResult.SUCCESS;
    }

    public WarpResult removeWarp(String id) {
        String normalizedId = normalizeId(id);
        if (!warps.containsKey(normalizedId)) {
            return WarpResult.NOT_FOUND;
        }
        NavigableMap<String, WarpPoint> updated = new TreeMap<>(warps);
        updated.remove(normalizedId);
        persist(updated);
        return WarpResult.SUCCESS;
    }

    public WarpResult setDisplayName(String id, String displayName) {
        String normalizedId = normalizeId(id);
        WarpPoint existing = warps.get(normalizedId);
        if (existing == null) {
            return WarpResult.NOT_FOUND;
        }
        String value = displayName == null ? "" : displayName.trim();
        if (value.isEmpty()) {
            return WarpResult.INVALID_NAME;
        }
        persistReplacement(normalizedId, new WarpPoint(
                existing.id(), value, existing.icon(), existing.position()
        ));
        return WarpResult.SUCCESS;
    }

    public WarpResult setIcon(String id, Material material) {
        String normalizedId = normalizeId(id);
        WarpPoint existing = warps.get(normalizedId);
        if (existing == null) {
            return WarpResult.NOT_FOUND;
        }
        if (!WarpPoint.isDisplayableItem(material)) {
            return WarpResult.INVALID_ICON;
        }
        persistReplacement(normalizedId, new WarpPoint(
                existing.id(), existing.displayName(), material, existing.position()
        ));
        return WarpResult.SUCCESS;
    }

    public WarpResult teleport(Player player, String id) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        WarpPoint warp = warps.get(normalizeId(id));
        if (warp == null) {
            return WarpResult.NOT_FOUND;
        }
        if (!validatedPlayer.isOnline()) {
            return WarpResult.PLAYER_OFFLINE;
        }
        Optional<Location> target = warp.position().toLocation(worldLookup);
        if (target.isEmpty()) {
            return WarpResult.WORLD_NOT_LOADED;
        }
        if (!validatedPlayer.teleport(target.get(), PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            return WarpResult.TELEPORT_FAILED;
        }
        validatedPlayer.setFallDistance(0.0F);
        validatedPlayer.setVelocity(new Vector());
        return WarpResult.SUCCESS;
    }

    public void clear() {
        warps.clear();
    }

    private void persistReplacement(String id, WarpPoint replacement) {
        NavigableMap<String, WarpPoint> updated = new TreeMap<>(warps);
        updated.put(id, replacement);
        persist(updated);
    }

    private void persist(NavigableMap<String, WarpPoint> updated) {
        warpConfig.saveWarps(new ArrayList<>(updated.values()));
        warps = updated;
    }

    private String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    private String defaultDisplayName(String id) {
        String[] words = id.split("[_-]+");
        List<String> displayWords = new ArrayList<>();
        for (String word : words) {
            if (!word.isEmpty()) {
                displayWords.add(Character.toUpperCase(word.charAt(0)) + word.substring(1));
            }
        }
        return displayWords.isEmpty() ? id : String.join(" ", displayWords);
    }
}
