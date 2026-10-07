package dev.vapee.core.lobby.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

/** Immutable, validated operator presentation. Item identity and actions are deliberately absent. */
public record LobbyItemDefinition(boolean enabled, int slot, Appearance appearance, Appearance filteredAppearance) {
    public LobbyItemDefinition {
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("Lobby slot must be in 0..8");
        Objects.requireNonNull(appearance);
        Objects.requireNonNull(filteredAppearance);
    }

    public record Appearance(Material material, Component name, List<Component> lore, boolean selfHead) {
        public Appearance {
            Objects.requireNonNull(material);
            Objects.requireNonNull(name);
            lore = List.copyOf(lore);
        }
    }
}
