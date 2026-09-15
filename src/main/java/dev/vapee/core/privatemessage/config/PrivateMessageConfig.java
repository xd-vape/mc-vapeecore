package dev.vapee.core.privatemessage.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

public final class PrivateMessageConfig {

    public static final String DEFAULT_OUTGOING_FORMAT =
            "<dark_gray>[</dark_gray><gray>You</gray><dark_gray> → </dark_gray>"
                    + "<recipient><dark_gray>]</dark_gray> <white><message></white>";
    public static final String DEFAULT_INCOMING_FORMAT =
            "<dark_gray>[</dark_gray><sender><dark_gray> → </dark_gray><gray>You</gray>"
                    + "<dark_gray>]</dark_gray> <white><message></white>";

    private static final String RESOURCE_NAME = "private-messages.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path configFile;

    private volatile State state = State.defaults();

    public PrivateMessageConfig(JavaPlugin plugin) {
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
            throw configFailure("load private-message configuration", exception);
        }

        return new State(
                readEnabled(configuration),
                readFormat(configuration, "format.outgoing", DEFAULT_OUTGOING_FORMAT),
                readFormat(configuration, "format.incoming", DEFAULT_INCOMING_FORMAT)
        );
    }

    private boolean readEnabled(YamlConfiguration configuration) {
        if (!configuration.contains("enabled")) {
            return true;
        }

        Object value = configuration.get("enabled");
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        logger.warning("Invalid private-message setting 'enabled' in " + configFile
                + ": expected a boolean; using 'true'. The file was left unchanged."
        );
        return true;
    }

    private String readFormat(YamlConfiguration configuration, String path, String fallback) {
        if (!configuration.contains(path)) {
            warnInvalidFormat(path);
            return fallback;
        }

        Object value = configuration.get(path);
        if (value instanceof String stringValue && !stringValue.isBlank()) {
            return stringValue;
        }

        warnInvalidFormat(path);
        return fallback;
    }

    private void warnInvalidFormat(String path) {
        logger.warning("Invalid or missing private-message format '" + path + "' in " + configFile
                + "; using the internal default. The file was left unchanged."
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
            logger.info("Created default private-message configuration at " + configFile + ".");
        } catch (IOException exception) {
            throw configFailure("create default private-message configuration", exception);
        }
    }

    private IllegalStateException configFailure(String operation, Throwable cause) {
        return new IllegalStateException("Could not " + operation + " at " + configFile + ".", cause);
    }

    public record State(boolean enabled, String outgoingFormat, String incomingFormat) {

        public State {
            Objects.requireNonNull(outgoingFormat, "outgoingFormat");
            Objects.requireNonNull(incomingFormat, "incomingFormat");
        }

        private static State defaults() {
            return new State(true, DEFAULT_OUTGOING_FORMAT, DEFAULT_INCOMING_FORMAT);
        }
    }
}
