package dev.vapee.core.lobby.warp;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class WarpConfig {

    private static final String RESOURCE_NAME = "warps.yml";
    private static final int REPLACE_ATTEMPTS = 5;
    private static final long REPLACE_RETRY_DELAY_MILLIS = 25L;
    private static final byte[] DEFAULT_CONTENT = "warps: {}\n".getBytes(StandardCharsets.UTF_8);

    private final Path configFile;
    private final Logger logger;
    private final Supplier<InputStream> defaultResourceSupplier;

    public WarpConfig(JavaPlugin plugin) {
        JavaPlugin validatedPlugin = Objects.requireNonNull(plugin, "plugin");
        this.configFile = validatedPlugin.getDataFolder().toPath()
                .resolve(RESOURCE_NAME)
                .toAbsolutePath()
                .normalize();
        this.logger = validatedPlugin.getLogger();
        this.defaultResourceSupplier = () -> validatedPlugin.getResource(RESOURCE_NAME);
    }

    public WarpConfig(Path configFile, Logger logger) {
        this.configFile = Objects.requireNonNull(configFile, "configFile").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.defaultResourceSupplier = () -> new ByteArrayInputStream(DEFAULT_CONTENT);
    }

    public NavigableMap<String, WarpPoint> initialize() {
        createDefaultFile();
        return loadWarps();
    }

    public NavigableMap<String, WarpPoint> loadWarps() {
        YamlConfiguration configuration = loadConfiguration();
        NavigableMap<String, WarpPoint> warps = new TreeMap<>();
        if (!configuration.contains("warps")) {
            return warps;
        }
        ConfigurationSection root = configuration.getConfigurationSection("warps");
        if (root == null) {
            logger.warning("Invalid warp configuration at " + configFile
                    + ": 'warps' must be a YAML section. The file was left unchanged.");
            return warps;
        }

        for (String id : root.getKeys(false)) {
            if (!WarpPoint.isValidId(id)) {
                logger.warning("Skipping warp '" + id + "': ID must match [a-z0-9_-]+.");
                continue;
            }
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                logger.warning("Skipping warp '" + id + "': expected a YAML section.");
                continue;
            }
            try {
                warps.put(id, readWarp(id, section));
            } catch (IllegalArgumentException exception) {
                logger.warning("Skipping warp '" + id + "': " + exception.getMessage()
                        + ". The file was left unchanged.");
            }
        }
        return warps;
    }

    public void saveWarps(Collection<WarpPoint> warps) {
        YamlConfiguration configuration = new YamlConfiguration();
        Collection<WarpPoint> validatedWarps = Objects.requireNonNull(warps, "warps");
        if (validatedWarps.isEmpty()) {
            configuration.createSection("warps");
        }
        for (WarpPoint warp : validatedWarps) {
            String root = "warps." + warp.id();
            configuration.set(root + ".display-name", warp.displayName());
            configuration.set(root + ".icon", warp.icon().name());
            configuration.set(root + ".location.world", warp.position().worldName());
            configuration.set(root + ".location.x", warp.position().x());
            configuration.set(root + ".location.y", warp.position().y());
            configuration.set(root + ".location.z", warp.position().z());
            configuration.set(root + ".location.yaw", warp.position().yaw());
            configuration.set(root + ".location.pitch", warp.position().pitch());
        }
        saveAtomically(configuration);
    }

    public Path getConfigFile() {
        return configFile;
    }

    private WarpPoint readWarp(String id, ConfigurationSection section) {
        Object displayNameValue = section.get("display-name");
        if (!(displayNameValue instanceof String displayName) || displayName.isBlank()) {
            throw new IllegalArgumentException("display-name must be a non-blank string");
        }
        Object iconValue = section.get("icon");
        if (!(iconValue instanceof String iconName)) {
            throw new IllegalArgumentException("icon must be a material name");
        }
        Material icon = Material.matchMaterial(iconName);
        if (!WarpPoint.isDisplayableItem(icon)) {
            throw new IllegalArgumentException("icon is not a valid item material");
        }
        ConfigurationSection location = section.getConfigurationSection("location");
        if (location == null) {
            throw new IllegalArgumentException("location must be a YAML section");
        }
        Object worldValue = location.get("world");
        if (!(worldValue instanceof String world) || world.isBlank()) {
            throw new IllegalArgumentException("location.world must be a non-blank string");
        }
        WarpPosition position = new WarpPosition(
                world,
                number(location, "x").doubleValue(),
                number(location, "y").doubleValue(),
                number(location, "z").doubleValue(),
                number(location, "yaw").floatValue(),
                number(location, "pitch").floatValue()
        );
        return new WarpPoint(id, displayName, icon, position);
    }

    private Number number(ConfigurationSection section, String path) {
        Object value = section.get(path);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("location." + path + " must be numeric");
        }
        return number;
    }

    private void createDefaultFile() {
        try {
            Files.createDirectories(configFile.getParent());
            if (Files.exists(configFile)) {
                return;
            }
            try (InputStream input = defaultResourceSupplier.get()) {
                if (input == null) {
                    throw new IllegalStateException("Default resource '" + RESOURCE_NAME + "' is missing");
                }
                Files.copy(input, configFile);
            }
        } catch (IOException exception) {
            throw failure("create default configuration", exception);
        }
    }

    private YamlConfiguration loadConfiguration() {
        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(configFile.toFile());
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            throw failure("load configuration", exception);
        }
    }

    private void saveAtomically(YamlConfiguration configuration) {
        Path temporaryFile = null;
        try {
            Files.createDirectories(configFile.getParent());
            temporaryFile = Files.createTempFile(configFile.getParent(), "warps-", ".tmp");
            configuration.save(temporaryFile.toFile());
            replaceConfiguration(temporaryFile);
            temporaryFile = null;
        } catch (IOException exception) {
            throw failure("save configuration", exception);
        } finally {
            if (temporaryFile != null) {
                try {
                    Files.deleteIfExists(temporaryFile);
                } catch (IOException exception) {
                    logger.warning("Could not delete temporary warp configuration " + temporaryFile + ".");
                }
            }
        }
    }

    private void replaceConfiguration(Path temporaryFile) throws IOException {
        IOException atomicFailure;
        try {
            Files.move(
                    temporaryFile,
                    configFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
            return;
        } catch (IOException exception) {
            atomicFailure = exception;
        }

        IOException replaceFailure = null;
        for (int attempt = 1; attempt <= REPLACE_ATTEMPTS; attempt++) {
            try {
                Files.move(temporaryFile, configFile, StandardCopyOption.REPLACE_EXISTING);
                return;
            } catch (IOException exception) {
                replaceFailure = exception;
                if (attempt < REPLACE_ATTEMPTS) {
                    waitBeforeReplaceRetry(attempt, atomicFailure, replaceFailure);
                }
            }
        }

        try {
            Files.copy(temporaryFile, configFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            exception.addSuppressed(atomicFailure);
            if (replaceFailure != null) {
                exception.addSuppressed(replaceFailure);
            }
            throw exception;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            logger.warning("Could not delete copied temporary warp configuration " + temporaryFile + ".");
        }
    }

    private void waitBeforeReplaceRetry(int attempt, IOException atomicFailure, IOException replaceFailure)
            throws IOException {
        try {
            Thread.sleep(REPLACE_RETRY_DELAY_MILLIS * attempt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            IOException interrupted = new IOException("Interrupted while retrying configuration replacement", exception);
            interrupted.addSuppressed(atomicFailure);
            interrupted.addSuppressed(replaceFailure);
            throw interrupted;
        }
    }

    private IllegalStateException failure(String operation, Throwable cause) {
        return new IllegalStateException("Could not " + operation + " at " + configFile + ".", cause);
    }
}
