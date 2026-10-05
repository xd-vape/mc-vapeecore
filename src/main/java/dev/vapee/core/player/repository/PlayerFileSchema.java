package dev.vapee.core.player.repository;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.ConfigurationSection;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Local player schema. Open containers preserve siblings; active assignments are wholly owned. */
final class PlayerFileSchema {
    static final String VERSION_KEY = "schema-version";
    static final int LEGACY_VERSION = 0;
    static final int CURRENT_VERSION = 1;

    private static final List<String> OWNED_FIELDS = List.of(
            "name", "first-join", "last-join",
            "settings.scoreboard", "settings.sounds", "settings.private-messages",
            "settings.friend-requests", "settings.friend-presence-notifications", "settings.lobby-players-visible",
            "settings.visibility.show-friends", "settings.visibility.show-staff",
            "settings.visibility.show-added-users", "settings.visibility.show-game-participants",
            "settings.visibility.added-players", "economy.coins", "social.ignored",
            "rewards.online.processed-playtime-ticks", "quests.daily.cycle-id", "quests.active");

    private PlayerFileSchema() { }

    static Map<Object, Object> read(String content) {
        Object value = reader().load(content);
        if (value == null) return new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> map)) throw new IllegalStateException("Player YAML root must be a map");
        return new LinkedHashMap<>(map);
    }

    static int version(Map<Object, Object> source) {
        if (!source.containsKey(VERSION_KEY)) return LEGACY_VERSION;
        Object value = source.get(VERSION_KEY);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw new IllegalStateException("Invalid player schema-version: expected a non-negative integer");
        }
        long version = ((Number) value).longValue();
        if (version > CURRENT_VERSION) {
            throw new IllegalStateException("Future player schema-version " + version
                    + " exceeds supported " + CURRENT_VERSION + "; load and mutation refused");
        }
        if (version < LEGACY_VERSION) throw new IllegalStateException("Negative player schema-version");
        return (int) version;
    }

    static void migrate(Map<Object, Object> source) {
        int version = version(source);
        while (version < CURRENT_VERSION) {
            switch (version) {
                case LEGACY_VERSION -> {
                    // v0 already has today's domain fields; v1 adds the explicit version contract.
                    source.put(VERSION_KEY, CURRENT_VERSION);
                    version = CURRENT_VERSION;
                }
                default -> throw new IllegalStateException("No player migration from version " + version);
            }
        }
    }

    static String overlay(Map<Object, Object> source, YamlConfiguration canonical) {
        migrate(source);
        Map<Object, Object> known = read(canonical.saveToString());
        for (String path : OWNED_FIELDS) {
            copyOwnedField(source, known, path.split("\\."), 0);
        }
        source.put(VERSION_KEY, CURRENT_VERSION);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setSplitLines(false);
        return new Yaml(options).dump(source);
    }

    static void validateOwnedValues(YamlConfiguration written, YamlConfiguration canonical) {
        for (String path : OWNED_FIELDS) {
            if (!Objects.equals(value(written.get(path)), value(canonical.get(path)))) {
                throw new IllegalStateException("Written player field differs from canonical aggregate: " + path);
            }
        }
    }

    private static Object value(Object value) {
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            return ((Number) value).longValue(); // YAML chooses the smallest integral Java type on read.
        }
        if (!(value instanceof ConfigurationSection section)) return value;
        Map<String, Object> fields = new LinkedHashMap<>();
        section.getValues(true).forEach((key, entry) -> {
            if (!(entry instanceof ConfigurationSection)) fields.put(key, value(entry));
        });
        return fields;
    }

    private static void copyOwnedField(Map<Object, Object> target, Map<Object, Object> known,
                                       String[] path, int index) {
        String key = path[index];
        if (index == path.length - 1) {
            if (known.containsKey(key)) target.put(key, known.get(key));
            else target.remove(key);
            return;
        }
        // Copy only this explicit owned path, never recursively merge arbitrary unknown nodes.
        // Copy-on-path also prevents a YAML alias from mutating an unknown sibling indirectly.
        Map<Object, Object> child = mapCopy(target.get(key));
        copyOwnedField(child, mapCopy(known.get(key)), path, index + 1);
        if (child.isEmpty()) target.remove(key);
        else target.put(key, child);
    }

    private static Map<Object, Object> mapCopy(Object value) {
        return value instanceof Map<?, ?> map ? new LinkedHashMap<>(map) : new LinkedHashMap<>();
    }

    private static Yaml reader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }
}
