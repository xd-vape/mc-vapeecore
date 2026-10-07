package dev.vapee.core.lobby.config;

import dev.vapee.core.lobby.item.LobbyItemDefinition;
import dev.vapee.core.lobby.item.LobbyItemDefinition.Appearance;
import dev.vapee.core.lobby.item.LobbyItemType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/** Only the three existing lobby item types are configurable; YAML cannot register actions. */
public final class LobbyItemsConfig {
    private static final MiniMessage TEXT = MiniMessage.miniMessage();
    private static final MiniMessage STRICT_TEXT = MiniMessage.builder().strict(true).build();
    private static final Map<LobbyItemType, LobbyItemDefinition> DEFAULTS = defaultsInternal();

    private LobbyItemsConfig() { }

    public static Map<LobbyItemType, LobbyItemDefinition> defaults() { return DEFAULTS; }

    public static Map<LobbyItemType, LobbyItemDefinition> read(YamlConfiguration yaml, Logger logger) {
        Map<LobbyItemType, LobbyItemDefinition> result = new EnumMap<>(LobbyItemType.class);
        Set<Integer> reserved = new HashSet<>();
        for (LobbyItemType type : LobbyItemType.values()) {
            var fallback = DEFAULTS.get(type);
            String path = "items." + type.getPersistentId();
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
            if (enabled) reserved.add(slot);
            Appearance normal = appearance(yaml, path, fallback.appearance(), logger);
            Appearance filtered = type == LobbyItemType.VISIBILITY
                    ? appearance(yaml, path + ".filtered", fallback.filteredAppearance(), logger) : normal;
            result.put(type, new LobbyItemDefinition(enabled, slot, normal, filtered));
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
            try { lore = lines.stream().map(line -> text((String) line, true)).toList(); }
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
        try { return text(value, true); }
        catch (RuntimeException invalid) { warn(logger, path, "invalid MiniMessage; using default"); return fallback; }
    }

    private static Component text(String value, boolean strict) {
        return (strict ? STRICT_TEXT : TEXT).deserialize(value).decoration(TextDecoration.ITALIC, false);
    }

    private static Map<LobbyItemType, LobbyItemDefinition> defaultsInternal() {
        var navigator = new Appearance(Material.COMPASS, text("<aqua>Warp Navigator</aqua>", false),
                List.of(text("<gray>Right-click to open the warp navigator.</gray>", false)), false);
        var visible = new Appearance(Material.LIME_DYE, text("<green>Players: Visible</green>", false),
                List.of(text("<gray>Right-click to use your visibility filters.</gray>", false)), false);
        var filtered = new Appearance(Material.GRAY_DYE, text("<gray>Players: Filtered</gray>", false),
                List.of(text("<gray>Right-click to show all lobby players.</gray>", false)), false);
        var settings = new Appearance(Material.COMPARATOR, text("<yellow>Settings</yellow>", false),
                List.of(text("<gray>Right-click to open your settings.</gray>", false)), false);
        return Map.of(
                LobbyItemType.NAVIGATOR, new LobbyItemDefinition(true, 0, navigator, navigator),
                LobbyItemType.VISIBILITY, new LobbyItemDefinition(true, 4, visible, filtered),
                LobbyItemType.SETTINGS, new LobbyItemDefinition(true, 8, settings, settings));
    }

    private static void warn(Logger logger, String path, String reason) {
        logger.warning("Invalid lobby setting '" + path + "': " + reason + ". File left unchanged.");
    }
}
