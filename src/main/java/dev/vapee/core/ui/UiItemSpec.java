package dev.vapee.core.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;
import java.util.Objects;

/** Immutable presentation data only; slots, targets and actions belong to the menu. */
public record UiItemSpec(Material material, Component name, List<Component> lore) {
    public UiItemSpec {
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(name, "name");
        lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
    }
}
