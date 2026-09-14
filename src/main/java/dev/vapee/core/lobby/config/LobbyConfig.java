package dev.vapee.core.lobby.config;

import dev.vapee.core.lobby.LobbySpawn;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LobbyConfig {

    private static final String RESOURCE_NAME = "lobby.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path configFile;

    private YamlConfiguration configuration;

    public LobbyConfig(JavaPlugin plugin) {
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
        YamlConfiguration loadedConfiguration = new YamlConfiguration();
        try {
            loadedConfiguration.load(configFile.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            throw configFailure("load lobby configuration", exception);
        }
        configuration = loadedConfiguration;
    }

    public Optional<LobbySpawn> getSpawn() {
        YamlConfiguration currentConfiguration = getConfiguration();
        if (!currentConfiguration.contains("spawn")) {
            return Optional.empty();
        }
        if (!currentConfiguration.isConfigurationSection("spawn")) {
            return invalidSpawn("'spawn' must be a YAML section");
        }

        Object worldValue = currentConfiguration.get("spawn.world");
        if (!(worldValue instanceof String worldName) || worldName.isBlank()) {
            return invalidSpawn("missing or blank 'spawn.world'");
        }

        Object xValue = currentConfiguration.get("spawn.x");
        Object yValue = currentConfiguration.get("spawn.y");
        Object zValue = currentConfiguration.get("spawn.z");
        Object yawValue = currentConfiguration.get("spawn.yaw");
        Object pitchValue = currentConfiguration.get("spawn.pitch");
        if (!(xValue instanceof Number x)
                || !(yValue instanceof Number y)
                || !(zValue instanceof Number z)
                || !(yawValue instanceof Number yaw)
                || !(pitchValue instanceof Number pitch)) {
            return invalidSpawn("spawn coordinates and rotation must all be numeric");
        }

        try {
            return Optional.of(new LobbySpawn(
                    worldName,
                    x.doubleValue(),
                    y.doubleValue(),
                    z.doubleValue(),
                    yaw.floatValue(),
                    pitch.floatValue()
            ));
        } catch (IllegalArgumentException exception) {
            return invalidSpawn(exception.getMessage());
        }
    }

    public void saveSpawn(LobbySpawn spawn) {
        LobbySpawn validatedSpawn = Objects.requireNonNull(spawn, "spawn");
        YamlConfiguration currentConfiguration = getConfiguration();
        currentConfiguration.set("spawn.world", validatedSpawn.worldName());
        currentConfiguration.set("spawn.x", validatedSpawn.x());
        currentConfiguration.set("spawn.y", validatedSpawn.y());
        currentConfiguration.set("spawn.z", validatedSpawn.z());
        currentConfiguration.set("spawn.yaw", validatedSpawn.yaw());
        currentConfiguration.set("spawn.pitch", validatedSpawn.pitch());
        save();
    }

    public boolean isTeleportOnJoinEnabled() {
        return getBoolean("teleport.on-join");
    }

    public boolean isTeleportOnRespawnEnabled() {
        return getBoolean("teleport.on-respawn");
    }

    public boolean isVoidRescueEnabled() {
        return getBoolean("void-rescue");
    }

    public boolean isDamageProtectionEnabled() {
        return getBoolean("protection.damage");
    }

    public boolean isHungerProtectionEnabled() {
        return getBoolean("protection.hunger");
    }

    public boolean isBlockBreakProtectionEnabled() {
        return getBoolean("protection.block-break");
    }

    public boolean isBlockPlaceProtectionEnabled() {
        return getBoolean("protection.block-place");
    }

    public boolean isItemDropProtectionEnabled() {
        return getBoolean("protection.item-drop");
    }

    public boolean isItemPickupProtectionEnabled() {
        return getBoolean("protection.item-pickup");
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
            logger.info("Created default lobby configuration at " + configFile + ".");
        } catch (IOException exception) {
            throw configFailure("create default lobby configuration", exception);
        }
    }

    private void save() {
        try {
            getConfiguration().save(configFile.toFile());
        } catch (IOException exception) {
            throw configFailure("save lobby configuration", exception);
        }
    }

    private boolean getBoolean(String path) {
        return getConfiguration().getBoolean(path, true);
    }

    private YamlConfiguration getConfiguration() {
        return Objects.requireNonNull(configuration, "LobbyConfig is not initialized");
    }

    private Optional<LobbySpawn> invalidSpawn(String reason) {
        logger.warning("Invalid lobby spawn in " + configFile + ": " + reason
                + ". The file was left unchanged."
        );
        return Optional.empty();
    }

    private IllegalStateException configFailure(String operation, Throwable cause) {
        String message = "Could not " + operation + " at " + configFile + ".";
        logger.log(Level.SEVERE, message, cause);
        return new IllegalStateException(message, cause);
    }
}
