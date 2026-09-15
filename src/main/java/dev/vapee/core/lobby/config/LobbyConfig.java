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
import java.util.logging.Logger;

public final class LobbyConfig {

    private static final String RESOURCE_NAME = "lobby.yml";

    private final JavaPlugin plugin;
    private final Logger logger;
    private final Path configFile;

    private volatile State state = State.defaults();

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
        state = readState(false);
    }

    public State prepareReloadState() {
        return readState(true);
    }

    public State getState() {
        return state;
    }

    public void applyState(State newState) {
        state = Objects.requireNonNull(newState, "newState");
    }

    public Optional<LobbySpawn> getSpawn() {
        return state.spawn();
    }

    public void saveSpawn(LobbySpawn spawn) {
        LobbySpawn validatedSpawn = Objects.requireNonNull(spawn, "spawn");
        YamlConfiguration currentConfiguration = loadConfiguration();
        currentConfiguration.set("spawn.world", validatedSpawn.worldName());
        currentConfiguration.set("spawn.x", validatedSpawn.x());
        currentConfiguration.set("spawn.y", validatedSpawn.y());
        currentConfiguration.set("spawn.z", validatedSpawn.z());
        currentConfiguration.set("spawn.yaw", validatedSpawn.yaw());
        currentConfiguration.set("spawn.pitch", validatedSpawn.pitch());
        save(currentConfiguration);
        state = state.withSpawn(validatedSpawn);
    }

    public boolean isTeleportOnJoinEnabled() {
        return state.teleportOnJoin();
    }

    public boolean isTeleportOnRespawnEnabled() {
        return state.teleportOnRespawn();
    }

    public boolean isVoidRescueEnabled() {
        return state.voidRescue();
    }

    public boolean isDamageProtectionEnabled() {
        return state.damageProtection();
    }

    public boolean isHungerProtectionEnabled() {
        return state.hungerProtection();
    }

    public boolean isBlockBreakProtectionEnabled() {
        return state.blockBreakProtection();
    }

    public boolean isBlockPlaceProtectionEnabled() {
        return state.blockPlaceProtection();
    }

    public boolean isItemDropProtectionEnabled() {
        return state.itemDropProtection();
    }

    public boolean isItemPickupProtectionEnabled() {
        return state.itemPickupProtection();
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

    private State readState(boolean strictSpawnValidation) {
        YamlConfiguration configuration = loadConfiguration();
        return new State(
                readSpawn(configuration, strictSpawnValidation),
                readBoolean(configuration, "teleport.on-join"),
                readBoolean(configuration, "teleport.on-respawn"),
                readBoolean(configuration, "void-rescue"),
                readBoolean(configuration, "protection.damage"),
                readBoolean(configuration, "protection.hunger"),
                readBoolean(configuration, "protection.block-break"),
                readBoolean(configuration, "protection.block-place"),
                readBoolean(configuration, "protection.item-drop"),
                readBoolean(configuration, "protection.item-pickup")
        );
    }

    private Optional<LobbySpawn> readSpawn(
            YamlConfiguration configuration,
            boolean strictValidation
    ) {
        if (!configuration.contains("spawn")) {
            return Optional.empty();
        }
        if (!configuration.isConfigurationSection("spawn")) {
            return invalidSpawn("'spawn' must be a YAML section", strictValidation);
        }

        Object worldValue = configuration.get("spawn.world");
        if (!(worldValue instanceof String worldName) || worldName.isBlank()) {
            return invalidSpawn("missing or blank 'spawn.world'", strictValidation);
        }

        Object xValue = configuration.get("spawn.x");
        Object yValue = configuration.get("spawn.y");
        Object zValue = configuration.get("spawn.z");
        Object yawValue = configuration.get("spawn.yaw");
        Object pitchValue = configuration.get("spawn.pitch");
        if (!(xValue instanceof Number x)
                || !(yValue instanceof Number y)
                || !(zValue instanceof Number z)
                || !(yawValue instanceof Number yaw)
                || !(pitchValue instanceof Number pitch)) {
            return invalidSpawn(
                    "spawn coordinates and rotation must all be numeric",
                    strictValidation
            );
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
            return invalidSpawn(exception.getMessage(), strictValidation);
        }
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
            throw configFailure("load lobby configuration", exception);
        }
    }

    private void save(YamlConfiguration configuration) {
        try {
            configuration.save(configFile.toFile());
        } catch (IOException exception) {
            throw configFailure("save lobby configuration", exception);
        }
    }

    private boolean readBoolean(YamlConfiguration configuration, String path) {
        if (!configuration.contains(path)) {
            return true;
        }

        Object value = configuration.get(path);
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }

        logger.warning("Invalid lobby setting '" + path + "' in " + configFile
                + ": expected a boolean; using 'true'. The file was left unchanged."
        );
        return true;
    }

    private Optional<LobbySpawn> invalidSpawn(String reason, boolean strictValidation) {
        String message = "Invalid lobby spawn in " + configFile + ": " + reason + ".";
        if (strictValidation) {
            throw new IllegalArgumentException(message);
        }

        logger.warning(message + " The file was left unchanged.");
        return Optional.empty();
    }

    private IllegalStateException configFailure(String operation, Throwable cause) {
        String message = "Could not " + operation + " at " + configFile + ".";
        return new IllegalStateException(message, cause);
    }

    public record State(
            Optional<LobbySpawn> spawn,
            boolean teleportOnJoin,
            boolean teleportOnRespawn,
            boolean voidRescue,
            boolean damageProtection,
            boolean hungerProtection,
            boolean blockBreakProtection,
            boolean blockPlaceProtection,
            boolean itemDropProtection,
            boolean itemPickupProtection
    ) {

        public State {
            spawn = Objects.requireNonNull(spawn, "spawn");
        }

        private static State defaults() {
            return new State(
                    Optional.empty(),
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true
            );
        }

        private State withSpawn(LobbySpawn newSpawn) {
            return new State(
                    Optional.of(Objects.requireNonNull(newSpawn, "newSpawn")),
                    teleportOnJoin,
                    teleportOnRespawn,
                    voidRescue,
                    damageProtection,
                    hungerProtection,
                    blockBreakProtection,
                    blockPlaceProtection,
                    itemDropProtection,
                    itemPickupProtection
            );
        }
    }
}
