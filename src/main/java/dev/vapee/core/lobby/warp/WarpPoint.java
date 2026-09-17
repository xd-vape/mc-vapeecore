package dev.vapee.core.lobby.warp;

import org.bukkit.Material;

import java.util.Objects;
import java.util.regex.Pattern;

public record WarpPoint(String id, String displayName, Material icon, WarpPosition position) {

    private static final Pattern ID_PATTERN = Pattern.compile("[a-z0-9_-]+");

    public WarpPoint {
        if (!isValidId(id)) {
            throw new IllegalArgumentException("id must match [a-z0-9_-]+");
        }
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        if (displayName.isEmpty()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        icon = Objects.requireNonNull(icon, "icon");
        if (!isDisplayableItem(icon)) {
            throw new IllegalArgumentException("icon must be an item material");
        }
        position = Objects.requireNonNull(position, "position");
    }

    public static boolean isValidId(String id) {
        return id != null && ID_PATTERN.matcher(id).matches();
    }

    public static boolean isDisplayableItem(Material material) {
        if (material == null) {
            return false;
        }
        try {
            return !material.isAir() && material.isItem();
        } catch (IllegalStateException | LinkageError unavailableRegistry) {
            return material != Material.AIR
                    && material != Material.CAVE_AIR
                    && material != Material.VOID_AIR;
        }
    }
}
