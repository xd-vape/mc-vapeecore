package dev.vapee.core.quest.daily;

import dev.vapee.core.config.ConfigFiles;
import dev.vapee.core.config.ConfigValues;
import dev.vapee.core.quest.QuestDefinition;
import dev.vapee.core.quest.QuestProgressKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.logging.Logger;

public final class DailyQuestConfig {

    public static final boolean DEFAULT_ENABLED = false;
    public static final int DEFAULT_QUESTS_PER_DAY = 4;
    public static final LocalTime DEFAULT_RESET_TIME = LocalTime.MIDNIGHT;
    public static final String DEFAULT_TIMEZONE = "system";
    private static final String RESOURCE_NAME = "daily-quests.yml";
    private static final int MAX_QUESTS_PER_DAY = 10_000;
    private static final DateTimeFormatter RESET_FORMAT = DateTimeFormatter
            .ofPattern("HH:mm", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private final JavaPlugin plugin;
    private final Path configFile;
    private final Logger logger;
    private State state = State.defaults();

    public DailyQuestConfig(JavaPlugin plugin) {
        this(Objects.requireNonNull(plugin, "plugin"),
                plugin.getDataFolder().toPath().resolve(RESOURCE_NAME), plugin.getLogger());
    }

    public DailyQuestConfig(Path configFile, Logger logger) {
        this(null, configFile, logger);
    }

    private DailyQuestConfig(JavaPlugin plugin, Path configFile, Logger logger) {
        this.plugin = plugin;
        this.configFile = Objects.requireNonNull(configFile, "configFile").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void initialize() {
        createDefaultFile();
        state = prepareReloadState();
    }

    public State prepareReloadState() {
        if (!Files.isRegularFile(configFile)) {
            throw new IllegalStateException("Required configuration file does not exist: " + configFile);
        }
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            String source = Files.readString(configFile);
            yaml.loadFromString(source);
            rejectSelectionNulls(source);
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load daily quest configuration at " + configFile, exception);
        }
        return parse(yaml);
    }

    public State getState() {
        return state;
    }

    public void applyState(State newState) {
        state = Objects.requireNonNull(newState, "newState");
    }

    public Path getConfigFile() {
        return configFile;
    }

    private State parse(YamlConfiguration yaml) {
        boolean enabled = readEnabled(yaml);
        int questsPerDay = readQuestsPerDay(yaml);
        LocalTime resetTime = readResetTime(yaml);
        String timezoneSetting = readTimezone(yaml);
        ZoneId zone = timezoneSetting.equals(DEFAULT_TIMEZONE)
                ? ZoneId.systemDefault() : ZoneId.of(timezoneSetting);
        List<QuestDefinition> definitions = readDefinitions(yaml);
        DailyQuestSelection selection = readSelection(yaml, definitions);
        if (enabled && definitions.isEmpty()) {
            logger.warning("Daily quests are enabled in " + configFile
                    + " but the catalog is empty; no assignments will be made.");
        } else if (enabled && definitions.size() < questsPerDay) {
            logger.warning("Daily quest catalog in " + configFile + " has " + definitions.size()
                    + " definition(s) for " + questsPerDay + " daily slots; fewer quests may be assigned.");
        }
        return new State(enabled, questsPerDay, resetTime, timezoneSetting, zone, definitions, selection,
                readFeedback(yaml));
    }

    private Feedback readFeedback(YamlConfiguration yaml) {
        if (yaml.contains("feedback") && !yaml.isConfigurationSection("feedback"))
            throw invalid("feedback", "expected a YAML section");
        var defaults = Feedback.DEFAULT;
        Object sound = yaml.get("feedback.sound", defaults.sound());
        if (!(sound instanceof String key) || org.bukkit.NamespacedKey.fromString(key) == null)
            throw invalid("feedback.sound", "expected a namespaced sound key");
        Object delay = yaml.get("feedback.open-delay-ticks", defaults.delayTicks());
        if (!(delay instanceof Integer ticks) || ticks < 1 || ticks > 100)
            throw invalid("feedback.open-delay-ticks", "expected a whole number from 1 through 100");
        return new Feedback(feedbackFlag(yaml, "feedback.sound-enabled", defaults.soundEnabled()),
                org.bukkit.NamespacedKey.fromString(key).toString(),
                feedbackFlag(yaml, "feedback.open-menu", defaults.openMenu()), ticks);
    }

    private boolean feedbackFlag(YamlConfiguration yaml, String path, boolean fallback) {
        Object value = yaml.get(path, fallback);
        if (!(value instanceof Boolean flag)) throw invalid(path, "expected a boolean");
        return flag;
    }

    private DailyQuestSelection readSelection(YamlConfiguration yaml, List<QuestDefinition> definitions) {
        if (yaml.contains("selection") && !yaml.isConfigurationSection("selection"))
            throw invalid("selection", "expected a YAML section");
        Map<String, DailyQuestSelection.Quest> metadata = new LinkedHashMap<>();
        for (QuestDefinition definition : definitions) {
            String path = "quests." + definition.id();
            ConfigurationSection entry = yaml.getConfigurationSection(path);
            boolean enabled = true;
            if (entry.contains("enabled")) {
                if (!(entry.get("enabled") instanceof Boolean flag))
                    throw invalid(path + ".enabled", "expected a boolean");
                enabled = flag;
            }
            metadata.put(definition.id(), new DailyQuestSelection.Quest(enabled,
                    optionalIdentifier(entry, path, "category"), optionalIdentifier(entry, path, "group")));
        }
        return new DailyQuestSelection(metadata, optionalLimit(yaml, "selection.max-per-category"),
                optionalLimit(yaml, "selection.max-per-group"));
    }

    private String optionalIdentifier(ConfigurationSection entry, String path, String key) {
        if (!entry.contains(key)) return null;
        String value = requiredString(entry, path, key);
        try { return DailyQuestSelection.requireIdentifier(value); }
        catch (IllegalArgumentException exception) { throw invalid(path + "." + key, exception.getMessage()); }
    }

    private Long optionalLimit(YamlConfiguration yaml, String path) {
        if (!yaml.contains(path)) return null;
        Object value = yaml.get(path);
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long number = ((Number) value).longValue();
            if (number > 0) return number;
        }
        throw invalid(path, "expected a positive whole number");
    }

    private void rejectSelectionNulls(String source) {
        // Like ConfigService's strict settings: Bukkit erases explicit nulls, which are not missing legacy fields.
        var root = new org.yaml.snakeyaml.Yaml().compose(new java.io.StringReader(source));
        var selection = child(root, "selection");
        rejectNull(selection, "selection");
        for (String key : List.of("max-per-category", "max-per-group")) rejectNull(child(selection, key), "selection." + key);
        if (child(root, "quests") instanceof org.yaml.snakeyaml.nodes.MappingNode quests) {
            for (var entry : quests.getValue()) {
                if (entry.getKeyNode() instanceof org.yaml.snakeyaml.nodes.ScalarNode id) {
                    for (String key : List.of("enabled", "category", "group"))
                        rejectNull(child(entry.getValueNode(), key), "quests." + id.getValue() + "." + key);
                }
            }
        }
    }

    private static org.yaml.snakeyaml.nodes.Node child(org.yaml.snakeyaml.nodes.Node node, String key) {
        if (!(node instanceof org.yaml.snakeyaml.nodes.MappingNode mapping)) return null;
        return mapping.getValue().stream().filter(tuple -> tuple.getKeyNode() instanceof org.yaml.snakeyaml.nodes.ScalarNode scalar
                && scalar.getValue().equals(key)).map(org.yaml.snakeyaml.nodes.NodeTuple::getValueNode).findFirst().orElse(null);
    }

    private void rejectNull(org.yaml.snakeyaml.nodes.Node node, String path) {
        if (node != null && node.getTag().equals(org.yaml.snakeyaml.nodes.Tag.NULL))
            throw invalid(path, "explicit null is not a valid selection setting; omit the optional field instead");
    }

    private boolean readEnabled(YamlConfiguration yaml) {
        return ConfigValues.readBoolean(yaml, "enabled", DEFAULT_ENABLED,
                () -> warn("enabled", "a boolean", DEFAULT_ENABLED));
    }

    private int readQuestsPerDay(YamlConfiguration yaml) {
        if (!yaml.contains("quests-per-day")) return DEFAULT_QUESTS_PER_DAY;
        Object value = yaml.get("quests-per-day");
        if (value instanceof Number number && (value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long)) {
            long count = number.longValue();
            if (count > 0L && count <= MAX_QUESTS_PER_DAY) return (int) count;
        }
        warn("quests-per-day", "a positive whole number up to " + MAX_QUESTS_PER_DAY,
                DEFAULT_QUESTS_PER_DAY);
        return DEFAULT_QUESTS_PER_DAY;
    }

    private LocalTime readResetTime(YamlConfiguration yaml) {
        if (!yaml.contains("reset.time")) return DEFAULT_RESET_TIME;
        Object value = yaml.get("reset.time");
        if (value instanceof String stringValue) {
            try {
                return LocalTime.parse(stringValue, RESET_FORMAT);
            } catch (DateTimeException ignored) {
                // The configured text is invalid; retain the safe default below.
            }
        }
        warn("reset.time", "HH:mm", "00:00");
        return DEFAULT_RESET_TIME;
    }

    private String readTimezone(YamlConfiguration yaml) {
        if (!yaml.contains("reset.timezone")) return DEFAULT_TIMEZONE;
        Object value = yaml.get("reset.timezone");
        if (value instanceof String stringValue) {
            if (DEFAULT_TIMEZONE.equals(stringValue)) return DEFAULT_TIMEZONE;
            try {
                ZoneId.of(stringValue);
                return stringValue;
            } catch (DateTimeException ignored) {
                // The configured zone is invalid; retain the system default below.
            }
        }
        warn("reset.timezone", "system or an IANA zone ID", DEFAULT_TIMEZONE);
        return DEFAULT_TIMEZONE;
    }

    private List<QuestDefinition> readDefinitions(YamlConfiguration yaml) {
        if (!yaml.contains("quests")) return List.of();
        ConfigurationSection section = yaml.getConfigurationSection("quests");
        if (section == null) throw invalid("quests", "expected a YAML section");
        List<QuestDefinition> definitions = new ArrayList<>();
        for (String id : section.getKeys(false).stream().sorted().toList()) {
            String path = "quests." + id;
            ConfigurationSection entry = section.getConfigurationSection(id);
            if (entry == null) throw invalid(path, "expected a YAML section");
            try {
                definitions.add(new QuestDefinition(
                        id,
                        requiredLocalizedText(entry, path, "name"),
                        requiredLocalizedText(entry, path, "description"),
                        QuestProgressKey.of(requiredString(entry, path, "progress-key")),
                        requiredPositiveLong(entry, path, "target"),
                        requiredPositiveLong(entry, path, "reward-coins")
                ));
            } catch (IllegalArgumentException exception) {
                throw invalid(path, exception.getMessage());
            }
        }
        return List.copyOf(definitions);
    }

    private dev.vapee.core.i18n.LocalizedText requiredLocalizedText(ConfigurationSection entry, String path, String key) {
        Object value = entry.get(key);
        if (value instanceof ConfigurationSection section) value = section.getValues(false);
        try { return dev.vapee.core.i18n.LocalizedText.parse(value); }
        catch (IllegalArgumentException exception) { throw invalid(path + "." + key, exception.getMessage()); }
    }

    private String requiredString(ConfigurationSection entry, String path, String key) {
        Object value = entry.get(key);
        if (!(value instanceof String stringValue)) throw invalid(path + "." + key, "expected a string");
        return stringValue;
    }

    private long requiredPositiveLong(ConfigurationSection entry, String path, String key) {
        Object value = entry.get(key);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw invalid(path + "." + key, "expected a positive whole number");
        }
        long number = ((Number) value).longValue();
        if (number <= 0L) throw invalid(path + "." + key, "must be positive");
        return number;
    }

    private IllegalArgumentException invalid(String path, String reason) {
        return new IllegalArgumentException("Invalid daily quest setting '" + path + "' in "
                + configFile + ": " + reason + ". The file was left unchanged.");
    }

    private void warn(String path, String expected, Object fallback) {
        logger.warning("Invalid daily quest setting '" + path + "' in " + configFile
                + ": expected " + expected + "; using '" + fallback + "'. The file was left unchanged.");
    }

    private void createDefaultFile() {
        if (plugin == null || Files.exists(configFile)) return;
        try {
            if (ConfigFiles.copyDefault(configFile, () -> plugin.getResource(RESOURCE_NAME),
                    () -> new IllegalStateException("Missing resource " + RESOURCE_NAME))) {
                logger.info("Created default daily quest configuration at " + configFile + ".");
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Could not create default daily quest configuration at "
                    + configFile, exception);
        }
    }

    public record Feedback(boolean soundEnabled, String sound, boolean openMenu, int delayTicks) {
        static final Feedback DEFAULT = new Feedback(true, "minecraft:ui.toast.challenge_complete", false, 10);
    }

    public record State(boolean enabled, int questsPerDay, LocalTime resetTime,
                        String timezoneSetting, ZoneId zone, List<QuestDefinition> definitions,
                        DailyQuestSelection selection, Feedback feedback) {

        public State(boolean enabled, int questsPerDay, LocalTime resetTime,
                     String timezoneSetting, ZoneId zone, List<QuestDefinition> definitions) {
            this(enabled, questsPerDay, resetTime, timezoneSetting, zone, definitions, DailyQuestSelection.unrestricted());
        }

        public State(boolean enabled, int questsPerDay, LocalTime resetTime,
                     String timezoneSetting, ZoneId zone, List<QuestDefinition> definitions, DailyQuestSelection selection) {
            this(enabled, questsPerDay, resetTime, timezoneSetting, zone, definitions, selection, Feedback.DEFAULT);
        }

        public State {
            if (questsPerDay <= 0) throw new IllegalArgumentException("questsPerDay must be positive");
            Objects.requireNonNull(resetTime, "resetTime");
            Objects.requireNonNull(timezoneSetting, "timezoneSetting");
            Objects.requireNonNull(zone, "zone");
            definitions = List.copyOf(Objects.requireNonNull(definitions, "definitions"));
            Objects.requireNonNull(selection, "selection");
            Objects.requireNonNull(feedback, "feedback");
        }

        public static State defaults() {
            return new State(DEFAULT_ENABLED, DEFAULT_QUESTS_PER_DAY, DEFAULT_RESET_TIME,
                    DEFAULT_TIMEZONE, ZoneId.systemDefault(), List.of());
        }
    }
}
