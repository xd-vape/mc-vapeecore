package dev.vapee.core.lobby.item;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;

import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.Locale;
import dev.vapee.core.i18n.LanguageResolver;

/** Immutable, validated operator presentation. Item identity and actions are deliberately absent. */
public record LobbyItemDefinition(boolean enabled, int slot, Appearance appearance, Appearance filteredAppearance,
                                  Map<String, Appearance> variants) {
    public LobbyItemDefinition {
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("Lobby slot must be in 0..8");
        Objects.requireNonNull(appearance);
        Objects.requireNonNull(filteredAppearance);
        variants = Map.copyOf(variants);
    }

    public LobbyItemDefinition(boolean enabled, int slot, Appearance appearance, Appearance filteredAppearance) {
        this(enabled, slot, appearance, filteredAppearance, Map.of());
    }

    public Appearance variant(String id) { return variants.getOrDefault(id, appearance); }

    public record Appearance(Material material, Component name, List<Component> lore, boolean selfHead, Map<String, Component> localizedNames, Map<String, List<Component>> localizedLore) {
        public Appearance {
            Objects.requireNonNull(material);
            Objects.requireNonNull(name);
            lore = List.copyOf(lore); localizedNames = Map.copyOf(localizedNames);
            var lines = new java.util.LinkedHashMap<String, List<Component>>();
            localizedLore.forEach((language, values) -> lines.put(language, List.copyOf(values)));
            localizedLore = Map.copyOf(lines);
            if (!localizedNames.containsKey("en") || !localizedLore.containsKey("en")) throw new IllegalArgumentException("Appearance needs English fallback");
        }
        public Appearance(Material material, Component name, List<Component> lore, boolean selfHead) {
            this(material, name, lore, selfHead, Map.of("en", name), Map.of("en", lore));
        }
        public Component name(Locale locale) { return localizedNames.getOrDefault(LanguageResolver.resolve(locale).code(), localizedNames.get("en")); }
        public List<Component> lore(Locale locale) { return localizedLore.getOrDefault(LanguageResolver.resolve(locale).code(), localizedLore.get("en")); }
    }
}
