package dev.vapee.core.presentation.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PresentationConfig {

    public static final long DEFAULT_UPDATE_INTERVAL_TICKS = 20L;
    public static final String DEFAULT_SCOREBOARD_TITLE = "<aqua><bold><server></bold></aqua>";
    public static final String DEFAULT_TABLIST_NAME_FORMAT = "<prefix><name><suffix>";

    private static final String RESOURCE_NAME = "presentation.yml";
    private static final int MAX_SCOREBOARD_LINES = 15;
    private static final List<String> DEFAULT_SCOREBOARD_LINES = List.of(
            "",
            "<gray>Rank:</gray> <white><group></white>",
            "<gray>Coins:</gray> <gold><coins></gold>",
            "",
            "<gray>Online:</gray> <white><online>/<max_players></white>",
            ""
    );
    private static final List<String> DEFAULT_TABLIST_HEADER = List.of(
            "<aqua><bold><server></bold></aqua>",
            "<gray>Welcome <white><name></white></gray>"
    );
    private static final List<String> DEFAULT_TABLIST_FOOTER = List.of(
            "<gray>Online: <white><online>/<max_players></white></gray>",
            "<gray>Coins: <gold><coins></gold></gray>"
    );

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path configFile;

    private boolean enabled = true;
    private long updateIntervalTicks = DEFAULT_UPDATE_INTERVAL_TICKS;
    private MetaFormat metaFormat = MetaFormat.LEGACY_AMPERSAND;
    private boolean scoreboardEnabled = true;
    private boolean scoreboardLobbyOnly = true;
    private String scoreboardTitle = DEFAULT_SCOREBOARD_TITLE;
    private List<String> scoreboardLines = DEFAULT_SCOREBOARD_LINES;
    private boolean tablistEnabled = true;
    private String tablistNameFormat = DEFAULT_TABLIST_NAME_FORMAT;
    private List<String> tablistHeader = DEFAULT_TABLIST_HEADER;
    private List<String> tablistFooter = DEFAULT_TABLIST_FOOTER;

    public PresentationConfig(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.configFile = plugin.getDataFolder().toPath()
                .resolve(RESOURCE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    public void initialize() {
        createDefaultFile();
        reload();
    }

    public void reload() {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(configFile.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            throw configFailure("load presentation configuration", exception);
        }

        enabled = readBoolean(configuration, "enabled", true);
        updateIntervalTicks = readUpdateInterval(configuration);
        metaFormat = readMetaFormat(configuration);
        scoreboardEnabled = readBoolean(configuration, "scoreboard.enabled", true);
        scoreboardLobbyOnly = readBoolean(configuration, "scoreboard.lobby-only", true);
        scoreboardTitle = readString(configuration, "scoreboard.title", DEFAULT_SCOREBOARD_TITLE);

        List<String> loadedScoreboardLines = readStringList(
                configuration,
                "scoreboard.lines",
                DEFAULT_SCOREBOARD_LINES
        );
        if (loadedScoreboardLines.size() > MAX_SCOREBOARD_LINES) {
            logger.warning("Presentation setting 'scoreboard.lines' in " + configFile
                    + " contains " + loadedScoreboardLines.size() + " lines; only the first "
                    + MAX_SCOREBOARD_LINES + " will be used. The file was left unchanged."
            );
            loadedScoreboardLines = loadedScoreboardLines.subList(0, MAX_SCOREBOARD_LINES);
        }
        scoreboardLines = List.copyOf(loadedScoreboardLines);

        tablistEnabled = readBoolean(configuration, "tablist.enabled", true);
        tablistNameFormat = readString(
                configuration,
                "tablist.name-format",
                DEFAULT_TABLIST_NAME_FORMAT
        );
        tablistHeader = List.copyOf(readStringList(
                configuration,
                "tablist.header",
                DEFAULT_TABLIST_HEADER
        ));
        tablistFooter = List.copyOf(readStringList(
                configuration,
                "tablist.footer",
                DEFAULT_TABLIST_FOOTER
        ));
    }

    public boolean isEnabled() {
        return enabled;
    }

    public long getUpdateIntervalTicks() {
        return updateIntervalTicks;
    }

    public MetaFormat getMetaFormat() {
        return metaFormat;
    }

    public boolean isScoreboardEnabled() {
        return scoreboardEnabled;
    }

    public boolean isScoreboardLobbyOnly() {
        return scoreboardLobbyOnly;
    }

    public String getScoreboardTitle() {
        return scoreboardTitle;
    }

    public List<String> getScoreboardLines() {
        return scoreboardLines;
    }

    public boolean isTablistEnabled() {
        return tablistEnabled;
    }

    public String getTablistNameFormat() {
        return tablistNameFormat;
    }

    public List<String> getTablistHeader() {
        return tablistHeader;
    }

    public List<String> getTablistFooter() {
        return tablistFooter;
    }

    public Path getConfigFile() {
        return configFile;
    }

    private boolean readBoolean(YamlConfiguration configuration, String path, boolean defaultValue) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }

        Object value = configuration.get(path);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        warnInvalidValue(path, "a boolean", defaultValue);
        return defaultValue;
    }

    private long readUpdateInterval(YamlConfiguration configuration) {
        if (!configuration.contains("update-interval-ticks")) {
            return DEFAULT_UPDATE_INTERVAL_TICKS;
        }

        Object value = configuration.get("update-interval-ticks");
        if (value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long) {
            long interval = ((Number) value).longValue();
            if (interval > 0L) {
                return interval;
            }
        }

        warnInvalidValue("update-interval-ticks", "a positive whole number", DEFAULT_UPDATE_INTERVAL_TICKS);
        return DEFAULT_UPDATE_INTERVAL_TICKS;
    }

    private String readString(YamlConfiguration configuration, String path, String defaultValue) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }

        Object value = configuration.get(path);
        if (value instanceof String stringValue) {
            return stringValue;
        }

        warnInvalidValue(path, "a string", defaultValue);
        return defaultValue;
    }

    private List<String> readStringList(
            YamlConfiguration configuration,
            String path,
            List<String> defaultValue
    ) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }

        Object value = configuration.get(path);
        if (!(value instanceof List<?> values)) {
            warnInvalidValue(path, "a string list", "the internal defaults");
            return defaultValue;
        }

        List<String> strings = new ArrayList<>(values.size());
        for (int index = 0; index < values.size(); index++) {
            Object entry = values.get(index);
            if (entry instanceof String stringEntry) {
                strings.add(stringEntry);
                continue;
            }

            logger.warning("Invalid presentation template '" + path + "[" + index + "]' in "
                    + configFile + ": expected a string; using an empty line. The file was left unchanged."
            );
            strings.add("");
        }
        return strings;
    }

    private MetaFormat readMetaFormat(YamlConfiguration configuration) {
        Object value = configuration.get("luckperms-meta.format", "legacy-ampersand");
        if (value instanceof String configuredFormat) {
            return switch (configuredFormat.trim().toLowerCase(Locale.ROOT)) {
                case "legacy-ampersand" -> MetaFormat.LEGACY_AMPERSAND;
                case "mini-message" -> MetaFormat.MINI_MESSAGE;
                case "plain" -> MetaFormat.PLAIN;
                default -> invalidMetaFormat(configuredFormat);
            };
        }
        return invalidMetaFormat(String.valueOf(value));
    }

    private MetaFormat invalidMetaFormat(String configuredFormat) {
        logger.warning("Unknown LuckPerms meta format '" + configuredFormat + "' in " + configFile
                + "; using 'legacy-ampersand'. The file was left unchanged."
        );
        return MetaFormat.LEGACY_AMPERSAND;
    }

    private void warnInvalidValue(String path, String expected, Object fallback) {
        logger.warning("Invalid presentation setting '" + path + "' in " + configFile
                + ": expected " + expected + "; using '" + fallback
                + "'. The file was left unchanged."
        );
    }

    private void createDefaultFile() {
        try {
            Files.createDirectories(configFile.getParent());
            if (Files.exists(configFile)) {
                return;
            }

            try (InputStream resource = plugin.getResource(RESOURCE_NAME)) {
                if (resource == null) {
                    String message = "Default resource '" + RESOURCE_NAME + "' is missing from the plugin JAR.";
                    logger.severe(message);
                    throw new IllegalStateException(message);
                }
                Files.copy(resource, configFile);
            }
            logger.info("Created default presentation configuration at " + configFile + ".");
        } catch (IOException exception) {
            throw configFailure("create default presentation configuration", exception);
        }
    }

    private IllegalStateException configFailure(String operation, Throwable cause) {
        String message = "Could not " + operation + " at " + configFile + ".";
        logger.log(Level.SEVERE, message, cause);
        return new IllegalStateException(message, cause);
    }

    public enum MetaFormat {
        LEGACY_AMPERSAND,
        MINI_MESSAGE,
        PLAIN
    }
}
