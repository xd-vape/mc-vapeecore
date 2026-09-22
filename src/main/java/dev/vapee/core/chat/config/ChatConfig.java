package dev.vapee.core.chat.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Logger;

public final class ChatConfig {

    public static final String DEFAULT_FORMAT =
            "<rank_name><dark_gray> » </dark_gray><white><message></white>";

    private static final String RESOURCE_NAME = "chat.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path configFile;

    private volatile State state = State.defaults();

    public ChatConfig(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.configFile = plugin.getDataFolder().toPath()
                .resolve(RESOURCE_NAME)
                .toAbsolutePath()
                .normalize();
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

    public boolean isEnabled() {
        return state.enabled();
    }

    public String getFormat() {
        return state.format();
    }

    public MetaFormat getMetaFormat() {
        return state.metaFormat();
    }

    public Path getConfigFile() {
        return configFile;
    }

    private State readState() {
        if (!Files.isRegularFile(configFile)) {
            throw new IllegalStateException("Required configuration file does not exist: " + configFile);
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(configFile.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            throw configFailure("load chat configuration", exception);
        }

        return new State(
                readBoolean(configuration, "enabled", true),
                readFormat(configuration),
                readMetaFormat(configuration)
        );
    }

    private boolean readBoolean(YamlConfiguration configuration, String path, boolean defaultValue) {
        if (!configuration.contains(path)) {
            return defaultValue;
        }

        Object value = configuration.get(path);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        logger.warning("Invalid chat setting '" + path + "' in " + configFile
                + ": expected a boolean; using '" + defaultValue + "'. The file was left unchanged."
        );
        return defaultValue;
    }

    private String readFormat(YamlConfiguration configuration) {
        if (!configuration.contains("format")) {
            return DEFAULT_FORMAT;
        }

        Object value = configuration.get("format");
        if (value instanceof String configuredFormat && !configuredFormat.isBlank()) {
            return configuredFormat;
        }

        logger.warning("Invalid 'format' in " + configFile
                + ": expected a non-blank string; using the internal default. The file was left unchanged."
        );
        return DEFAULT_FORMAT;
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
            logger.info("Created default chat configuration at " + configFile + ".");
        } catch (IOException exception) {
            throw configFailure("create default chat configuration", exception);
        }
    }

    private IllegalStateException configFailure(String operation, Throwable cause) {
        String message = "Could not " + operation + " at " + configFile + ".";
        return new IllegalStateException(message, cause);
    }

    public record State(boolean enabled, String format, MetaFormat metaFormat) {

        public State {
            Objects.requireNonNull(format, "format");
            Objects.requireNonNull(metaFormat, "metaFormat");
        }

        private static State defaults() {
            return new State(true, DEFAULT_FORMAT, MetaFormat.LEGACY_AMPERSAND);
        }
    }

    public enum MetaFormat {
        LEGACY_AMPERSAND,
        MINI_MESSAGE,
        PLAIN
    }
}
