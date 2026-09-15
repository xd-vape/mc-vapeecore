package dev.vapee.core.config;

import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.logging.Logger;

public final class ConfigService implements ReloadParticipant {

    private static final String RESOURCE_NAME = "config.yml";
    private static final String DEFAULT_SERVER_NAME = "Vapee Community";
    private static final String DEFAULT_MESSAGE_PREFIX = "<gray>[<aqua>VapeeCore</aqua>]</gray> ";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path configFile;

    private volatile CoreConfigState state = CoreConfigState.defaults();

    public ConfigService(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.logger = plugin.getLogger();
        this.configFile = plugin.getDataFolder().toPath()
                .resolve(RESOURCE_NAME)
                .toAbsolutePath()
                .normalize();
    }

    public void load() {
        plugin.saveDefaultConfig();
        state = readState();
    }

    @Override
    public String getReloadName() {
        return RESOURCE_NAME;
    }

    @Override
    public ReloadPlan prepareReload() {
        CoreConfigState previousState = state;
        CoreConfigState preparedState = readState();
        return ReloadPlan.of(
                () -> state = preparedState,
                () -> state = previousState
        );
    }

    public String getServerName() {
        return state.serverName();
    }

    public String getMessagePrefix() {
        return state.messagePrefix();
    }

    public boolean isDebugEnabled() {
        return state.debugEnabled();
    }

    private CoreConfigState readState() {
        YamlConfiguration configuration = loadConfiguration();
        return new CoreConfigState(
                readString(configuration, "server.name", DEFAULT_SERVER_NAME),
                readString(configuration, "messages.prefix", DEFAULT_MESSAGE_PREFIX),
                readBoolean(configuration, "settings.debug", false)
        );
    }

    private YamlConfiguration loadConfiguration() {
        if (!Files.isRegularFile(configFile)) {
            throw new IllegalStateException("Required configuration file does not exist: " + configFile);
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(configFile.toFile());
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            throw new IllegalStateException("Could not load configuration at " + configFile + ".", exception);
        }
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

    private void warnInvalidValue(String path, String expected, Object fallback) {
        logger.warning("Invalid core setting '" + path + "' in " + configFile
                + ": expected " + expected + "; using '" + fallback
                + "'. The file was left unchanged."
        );
    }

    private record CoreConfigState(String serverName, String messagePrefix, boolean debugEnabled) {

        private CoreConfigState {
            Objects.requireNonNull(serverName, "serverName");
            Objects.requireNonNull(messagePrefix, "messagePrefix");
        }

        private static CoreConfigState defaults() {
            return new CoreConfigState(DEFAULT_SERVER_NAME, DEFAULT_MESSAGE_PREFIX, false);
        }
    }
}
