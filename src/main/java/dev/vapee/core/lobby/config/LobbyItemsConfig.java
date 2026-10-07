package dev.vapee.core.lobby.config;

import dev.vapee.core.lobby.item.LobbyItemDefinition;
import dev.vapee.core.lobby.item.LobbyItemDefinition.Appearance;
import dev.vapee.core.lobby.item.LobbyItemRegistry;
import dev.vapee.core.lobby.item.LobbyItemRegistrations;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Parses presentation only for explicitly registered Java features. YAML cannot register behavior. */
public final class LobbyItemsConfig {
    private static final MiniMessage TEXT = MiniMessage.miniMessage();

    private LobbyItemsConfig() { }

    public static Map<String, LobbyItemDefinition> defaults() { return defaults(LobbyItemRegistrations.inactive()); }
    public static Map<String, LobbyItemDefinition> defaults(LobbyItemRegistry registry) {
        Map<String, LobbyItemDefinition> result = new LinkedHashMap<>();
        registry.entries().forEach((id, entry) -> result.put(id, entry.defaults()));
        return Map.copyOf(result);
    }
    public static Map<String, LobbyItemDefinition> read(YamlConfiguration yaml, Logger logger) {
        return read(yaml, logger, LobbyItemRegistrations.inactive());
    }
    public static Map<String, LobbyItemDefinition> read(YamlConfiguration yaml, Logger logger, LobbyItemRegistry registry) {
        Map<String, LobbyItemDefinition> result = new LinkedHashMap<>();
        var configured = yaml.getConfigurationSection("items");
        if (configured != null) {
            for (String id : configured.getKeys(false)) {
                if (registry.resolve(id).isEmpty()) warn(logger, "items." + id, "unregistered Java feature; ignored (no item or action)");
            }
        } else if (yaml.contains("items")) warn(logger, "items", "expected a section; using registered defaults");
        Set<Integer> reserved = new HashSet<>();
        for (var entry : registry.entries().values()) {
            var fallback = entry.defaults();
            String path = "items." + entry.id();
            // An explicit items section owns its entries; removing an ID must not resurrect its Java fallback.
            if (configured != null && !configured.contains(entry.id())) {
                result.put(entry.id(), new LobbyItemDefinition(false, fallback.slot(),
                        fallback.appearance(), fallback.filteredAppearance()));
                continue;
            }
            if (yaml.contains(path) && !yaml.isConfigurationSection(path)) {
                warn(logger, path, "expected a section; using defaults");
            }
            Object enabledValue = yaml.get(path + ".enabled");
            boolean enabled = enabledValue instanceof Boolean flag ? flag : fallback.enabled();
            if (enabledValue != null && !(enabledValue instanceof Boolean)) warn(logger, path + ".enabled", "expected boolean; using default");
            Object slotValue = yaml.get(path + ".slot");
            int slot = fallback.slot();
            if (slotValue instanceof Integer number && number >= 0 && number <= 8) slot = number;
            else if (slotValue != null) warn(logger, path + ".slot", "expected integer 0..8; using default");
            if (enabled && reserved.contains(slot)) {
                warn(logger, path + ".slot", "duplicate enabled slot " + slot + "; choosing a free hotbar slot");
                slot = fallback.slot();
                if (reserved.contains(slot)) {
                    for (int candidate = 0; candidate <= 8; candidate++) {
                        if (!reserved.contains(candidate)) { slot = candidate; break; }
                    }
                }
            }
            if (enabled && reserved.contains(slot)) {
                warn(logger, path + ".slot", "no free hotbar slot; item disabled");
                enabled = false;
            }
            if (enabled) reserved.add(slot);
            Appearance normal = appearance(yaml, path, fallback.appearance(), logger);
            Appearance filtered = !fallback.filteredAppearance().equals(fallback.appearance())
                    ? appearance(yaml, path + ".filtered", fallback.filteredAppearance(), logger) : normal;
            result.put(entry.id(), new LobbyItemDefinition(enabled, slot, normal, filtered));
        }
        return Map.copyOf(result);
    }

    private static Appearance appearance(YamlConfiguration yaml, String path, Appearance fallback, Logger logger) {
        Material material = fallback.material();
        Object materialValue = yaml.get(path + ".material");
        if (materialValue != null) {
            Material parsed = materialValue instanceof String name ? Material.matchMaterial(name) : null;
            if (parsed != null && parsed.isItem() && !parsed.isAir()) material = parsed;
            else warn(logger, path + ".material", "expected non-air item material; using " + material);
        }
        Component name = fallback.name();
        Object nameValue = yaml.get(path + ".name");
        if (nameValue instanceof String text) name = parse(text, name, path + ".name", logger);
        else if (nameValue != null) warn(logger, path + ".name", "expected MiniMessage string; using default");
        List<Component> lore = fallback.lore();
        Object loreValue = yaml.get(path + ".lore");
        if (loreValue instanceof List<?> lines && lines.stream().allMatch(String.class::isInstance)) {
            try { lore = lines.stream().map(line -> text((String) line)).toList(); }
            catch (RuntimeException malformed) { warn(logger, path + ".lore", "invalid MiniMessage; using default"); }
        } else if (loreValue != null) warn(logger, path + ".lore", "expected list of MiniMessage strings; using default");
        boolean selfHead = fallback.selfHead();
        Object headValue = yaml.get(path + ".head-owner");
        if (headValue instanceof String owner && (owner.equalsIgnoreCase("self") || owner.equalsIgnoreCase("none"))) {
            selfHead = owner.equalsIgnoreCase("self");
        } else if (headValue != null) warn(logger, path + ".head-owner", "expected self or none; using default");
        return new Appearance(material, name, lore, selfHead);
    }

    private static Component parse(String value, Component fallback, String path, Logger logger) {
        try { return text(value); }
        catch (RuntimeException invalid) { warn(logger, path, "invalid MiniMessage; using default"); return fallback; }
    }

    private static Component text(String value) {
        // Standard MiniMessage supports implicit style closing, including the operator examples.
        return TEXT.deserialize(value).decoration(TextDecoration.ITALIC, false);
    }

    private static void warn(Logger logger, String path, String reason) {
        logger.warning("Invalid lobby setting '" + path + "': " + reason + ". File left unchanged.");
    }
}
