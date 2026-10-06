package dev.vapee.core.presentation.config;

import dev.vapee.core.config.ConfigFiles;
import dev.vapee.core.config.ConfigValues;
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
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class PresentationConfig {

    public static final long DEFAULT_UPDATE_INTERVAL_TICKS = 20L;
    public static final String DEFAULT_SCOREBOARD_TITLE = "<aqua><bold><server></bold></aqua>";
    public static final String DEFAULT_TABLIST_NAME_FORMAT = "<rank_name><clan_tag_display>";
    public static final String DEFAULT_NAMETAG_PREFIX = "";
    public static final String DEFAULT_NAMETAG_SUFFIX = "<clan_tag_display>";
    public static final String DEFAULT_CLAN_TAG_FORMAT = " <dark_gray>[</dark_gray><gray><clan_tag></gray><dark_gray>]</dark_gray>";

    private static final String RESOURCE_NAME = "presentation.yml";
    private static final int MAX_SCOREBOARD_LINES = 15;
    private static final List<String> DEFAULT_SCOREBOARD_LINES = List.of(
            "<gray>Coins:</gray>",
            "<gold><coins></gold> ",
            "",
            "<gray>Rank:</gray>",
            "<rank>",
            "",
            "<gray>Playtime:</gray>",
            "<blue><playtime></blue>"
    );
    private static final List<String> DEFAULT_TABLIST_HEADER = List.of(
            "<aqua><bold><server></bold></aqua>",
            "<gray>The place to meet new friends, hang out and play games together!</gray>"
    );
    private static final List<String> DEFAULT_TABLIST_FOOTER = List.of(
            "<gray>you dont need to pay for extra features!</gray>"
    );

    private final Supplier<InputStream> defaultResourceSupplier;
    private final Logger logger;
    private final Path configFile;

    private volatile State state = State.defaults();

    public PresentationConfig(JavaPlugin plugin) {
        this(Objects.requireNonNull(plugin, "plugin").getDataFolder().toPath().resolve(RESOURCE_NAME),
                plugin.getLogger(), () -> plugin.getResource(RESOURCE_NAME));
    }

    PresentationConfig(Path configFile, Logger logger, Supplier<InputStream> defaultResourceSupplier) {
        this.configFile = Objects.requireNonNull(configFile, "configFile").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.defaultResourceSupplier = Objects.requireNonNull(defaultResourceSupplier, "defaultResourceSupplier");
    }

    public void initialize() {
        createDefaultFile();
        state = readState();
    }

    public State prepareReloadState() {
        return readState();
    }

    public State getState() {
        return state;
    }

    public void applyState(State newState) {
        state = Objects.requireNonNull(newState, "newState");
    }

    private State readState() {
        if (!Files.isRegularFile(configFile)) {
            throw new IllegalStateException("Required configuration file does not exist: " + configFile);
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(configFile.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            throw configFailure("load presentation configuration", exception);
        }

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
        return new State(
                readBoolean(configuration, "enabled", true),
                readUpdateInterval(configuration),
                readMetaFormat(configuration),
                readBoolean(configuration, "scoreboard.enabled", true),
                readBoolean(configuration, "scoreboard.lobby-only", true),
                readString(configuration, "scoreboard.title", DEFAULT_SCOREBOARD_TITLE),
                loadedScoreboardLines,
                readBoolean(configuration, "tablist.enabled", true),
                readString(configuration, "tablist.name-format", DEFAULT_TABLIST_NAME_FORMAT),
                readStringList(configuration, "tablist.header", DEFAULT_TABLIST_HEADER),
                readStringList(configuration, "tablist.footer", DEFAULT_TABLIST_FOOTER),
                readBoolean(configuration, "nametag.enabled", true),
                readBoolean(configuration, "nametag.lobby-only", true),
                readString(configuration, "nametag.prefix", DEFAULT_NAMETAG_PREFIX),
                readString(configuration, "nametag.suffix", DEFAULT_NAMETAG_SUFFIX),
                readString(configuration, "clan-tag-format", DEFAULT_CLAN_TAG_FORMAT)
        );
    }

    public boolean isEnabled() {
        return state.enabled();
    }

    public long getUpdateIntervalTicks() {
        return state.updateIntervalTicks();
    }

    public MetaFormat getMetaFormat() {
        return state.metaFormat();
    }

    public boolean isScoreboardEnabled() {
        return state.scoreboardEnabled();
    }

    public boolean isScoreboardLobbyOnly() {
        return state.scoreboardLobbyOnly();
    }

    public String getScoreboardTitle() {
        return state.scoreboardTitle();
    }

    public List<String> getScoreboardLines() {
        return state.scoreboardLines();
    }

    public boolean isTablistEnabled() {
        return state.tablistEnabled();
    }

    public String getTablistNameFormat() {
        return state.tablistNameFormat();
    }

    public List<String> getTablistHeader() {
        return state.tablistHeader();
    }

    public List<String> getTablistFooter() {
        return state.tablistFooter();
    }

    public Path getConfigFile() {
        return configFile;
    }

    public boolean isNametagEnabled() { return state.nametagEnabled(); }

    public boolean isNametagLobbyOnly() { return state.nametagLobbyOnly(); }

    private boolean readBoolean(YamlConfiguration configuration, String path, boolean defaultValue) {
        return ConfigValues.readBoolean(configuration, path, defaultValue,
                () -> warnInvalidValue(path, "a boolean", defaultValue));
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
        return ConfigValues.readString(configuration, path, defaultValue,
                () -> warnInvalidValue(path, "a string", defaultValue));
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
            if (ConfigFiles.copyDefault(configFile, defaultResourceSupplier, () -> {
                String message = "Default resource '" + RESOURCE_NAME + "' is missing from the plugin JAR.";
                logger.severe(message);
                return new IllegalStateException(message);
            })) {
                logger.info("Created default presentation configuration at " + configFile + ".");
            }
        } catch (IOException exception) {
            throw configFailure("create default presentation configuration", exception);
        }
    }

    private IllegalStateException configFailure(String operation, Throwable cause) {
        String message = "Could not " + operation + " at " + configFile + ".";
        return new IllegalStateException(message, cause);
    }

    public record State(
            boolean enabled,
            long updateIntervalTicks,
            MetaFormat metaFormat,
            boolean scoreboardEnabled,
            boolean scoreboardLobbyOnly,
            String scoreboardTitle,
            List<String> scoreboardLines,
            boolean tablistEnabled,
            String tablistNameFormat,
            List<String> tablistHeader,
            List<String> tablistFooter,
            boolean nametagEnabled,
            boolean nametagLobbyOnly,
            String nametagPrefix,
            String nametagSuffix,
            String clanTagFormat
    ) {

        public State {
            if (updateIntervalTicks <= 0L) {
                throw new IllegalArgumentException("Presentation update interval must be positive");
            }
            Objects.requireNonNull(metaFormat, "metaFormat");
            Objects.requireNonNull(scoreboardTitle, "scoreboardTitle");
            scoreboardLines = List.copyOf(Objects.requireNonNull(scoreboardLines, "scoreboardLines"));
            Objects.requireNonNull(tablistNameFormat, "tablistNameFormat");
            tablistHeader = List.copyOf(Objects.requireNonNull(tablistHeader, "tablistHeader"));
            tablistFooter = List.copyOf(Objects.requireNonNull(tablistFooter, "tablistFooter"));
            Objects.requireNonNull(nametagPrefix, "nametagPrefix");
            Objects.requireNonNull(nametagSuffix, "nametagSuffix");
            Objects.requireNonNull(clanTagFormat, "clanTagFormat");
        }

        /** Existing callers constructing a sidebar/tab-only state retain that explicit scope. */
        public State(boolean enabled, long interval, MetaFormat metaFormat, boolean scoreboardEnabled,
                     boolean lobbyOnly, String title, List<String> lines, boolean tablistEnabled,
                     String name, List<String> header, List<String> footer) {
            this(enabled, interval, metaFormat, scoreboardEnabled, lobbyOnly, title, lines,
                    tablistEnabled, name, header, footer, false, true,
                    DEFAULT_NAMETAG_PREFIX, DEFAULT_NAMETAG_SUFFIX, DEFAULT_CLAN_TAG_FORMAT);
        }

        private static State defaults() {
            return new State(
                    true,
                    DEFAULT_UPDATE_INTERVAL_TICKS,
                    MetaFormat.LEGACY_AMPERSAND,
                    true,
                    true,
                    DEFAULT_SCOREBOARD_TITLE,
                    DEFAULT_SCOREBOARD_LINES,
                    true,
                    DEFAULT_TABLIST_NAME_FORMAT,
                    DEFAULT_TABLIST_HEADER,
                    DEFAULT_TABLIST_FOOTER,
                    true,
                    true,
                    DEFAULT_NAMETAG_PREFIX,
                    DEFAULT_NAMETAG_SUFFIX,
                    DEFAULT_CLAN_TAG_FORMAT
            );
        }
    }

    public enum MetaFormat {
        LEGACY_AMPERSAND,
        MINI_MESSAGE,
        PLAIN
    }
}
