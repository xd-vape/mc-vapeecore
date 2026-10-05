package dev.vapee.core.lobby.warp;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.Tag;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.CopyOption;
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
    private static final byte[] DEFAULT_CONTENT = "warps: {}\n".getBytes(StandardCharsets.UTF_8);

    private final Path configFile;
    private final Logger logger;
    private final Supplier<InputStream> defaultResourceSupplier;
    private final ReplacementIO replacementIO;

    public WarpConfig(JavaPlugin plugin) {
        JavaPlugin validatedPlugin = Objects.requireNonNull(plugin, "plugin");
        this.configFile = validatedPlugin.getDataFolder().toPath()
                .resolve(RESOURCE_NAME)
                .toAbsolutePath()
                .normalize();
        this.logger = validatedPlugin.getLogger();
        this.defaultResourceSupplier = () -> validatedPlugin.getResource(RESOURCE_NAME);
        this.replacementIO = new ReplacementIO();
    }

    public WarpConfig(Path configFile, Logger logger) {
        this(configFile, logger, new ReplacementIO());
    }

    WarpConfig(Path configFile, Logger logger, ReplacementIO replacementIO) {
        this.configFile = Objects.requireNonNull(configFile, "configFile").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.defaultResourceSupplier = () -> new ByteArrayInputStream(DEFAULT_CONTENT);
        this.replacementIO = Objects.requireNonNull(replacementIO, "replacementIO");
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
            configuration.set(root + ".navigator.visible", warp.navigation().visible());
            configuration.set(root + ".navigator.order", warp.navigation().order());
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
        return new WarpPoint(id, displayName, icon, position, readNavigation(id, section));
    }

    private WarpNavigation readNavigation(String id, ConfigurationSection section) {
        if (!section.contains("navigator")) {
            return WarpNavigation.DEFAULT;
        }
        ConfigurationSection navigator = section.getConfigurationSection("navigator");
        if (navigator == null) {
            warnNavigation(id, "navigator", "a YAML section", "visible=true, order=0");
            return WarpNavigation.DEFAULT;
        }

        boolean visible = true;
        if (navigator.contains("visible")) {
            if (navigator.get("visible") instanceof Boolean value) {
                visible = value;
            } else {
                warnNavigation(id, "navigator.visible", "a boolean", "true");
            }
        }
        int order = 0;
        if (navigator.contains("order")) {
            Object value = navigator.get("order");
            if ((value instanceof Integer || value instanceof Long)
                    && ((Number) value).longValue() >= 0
                    && ((Number) value).longValue() <= Integer.MAX_VALUE) {
                order = ((Number) value).intValue();
            } else {
                warnNavigation(id, "navigator.order", "an integer in 0..2147483647", "0");
            }
        }
        return new WarpNavigation(visible, order);
    }

    private void warnNavigation(String id, String key, String expected, String fallback) {
        logger.warning("Invalid warp '" + id + "' setting '" + key + "' in " + configFile
                + ": expected " + expected + "; using " + fallback + ". The file was left unchanged.");
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
            String source = Files.readString(configFile, StandardCharsets.UTF_8);
            configuration.loadFromString(source);
            retainExplicitNavigationNulls(configuration, source);
            return configuration;
        } catch (IOException | InvalidConfigurationException exception) {
            throw failure("load configuration", exception);
        }
    }

    private void retainExplicitNavigationNulls(YamlConfiguration configuration, String source) {
        // Bukkit drops explicit YAML nulls. Keep them distinct from missing legacy metadata
        // so the normal optional-field parser also warns for an explicitly invalid null.
        Node root = child(new Yaml().compose(new StringReader(source)), "warps");
        if (!(root instanceof MappingNode warps)) {
            return;
        }
        for (var entry : warps.getValue()) {
            if (!(entry.getKeyNode() instanceof ScalarNode id) || !WarpPoint.isValidId(id.getValue())) {
                continue;
            }
            String path = "warps." + id.getValue() + ".navigator";
            Node navigator = child(entry.getValueNode(), "navigator");
            if (navigator == null) {
                continue;
            }
            if (Tag.NULL.equals(navigator.getTag())) {
                configuration.set(path, "explicit YAML null");
                continue;
            }
            for (String key : java.util.List.of("visible", "order")) {
                Node value = child(navigator, key);
                if (value != null && Tag.NULL.equals(value.getTag())) {
                    configuration.set(path + "." + key, "explicit YAML null");
                }
            }
        }
    }

    private Node child(Node node, String key) {
        if (!(node instanceof MappingNode mapping)) {
            return null;
        }
        return mapping.getValue().stream()
                .filter(entry -> entry.getKeyNode() instanceof ScalarNode scalar && scalar.getValue().equals(key))
                .map(entry -> entry.getValueNode()).findFirst().orElse(null);
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
            replacementIO.move(
                    temporaryFile,
                    configFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
            return;
        } catch (IOException exception) {
            atomicFailure = exception;
        }

        // Preserve the supported fallbacks, with one attempt per stage and no caller-thread backoff.
        IOException replaceFailure;
        try {
            replacementIO.move(temporaryFile, configFile, StandardCopyOption.REPLACE_EXISTING);
            return;
        } catch (IOException exception) {
            replaceFailure = exception;
        }

        try {
            replacementIO.copy(temporaryFile, configFile);
        } catch (IOException exception) {
            exception.addSuppressed(atomicFailure);
            exception.addSuppressed(replaceFailure);
            throw exception;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            logger.warning("Could not delete copied temporary warp configuration " + temporaryFile + ".");
        }
    }

    static class ReplacementIO {
        void move(Path source, Path target, CopyOption... options) throws IOException {
            Files.move(source, target, options);
        }

        void copy(Path source, Path target) throws IOException {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private IllegalStateException failure(String operation, Throwable cause) {
        return new IllegalStateException("Could not " + operation + " at " + configFile + ".", cause);
    }
}
