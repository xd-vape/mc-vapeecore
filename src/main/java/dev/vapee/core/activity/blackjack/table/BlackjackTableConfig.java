package dev.vapee.core.activity.blackjack.table;

import dev.vapee.core.activity.location.ActivityPosition;
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
import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;
import java.util.logging.Logger;

public final class BlackjackTableConfig {

    private static final String RESOURCE_NAME = "blackjack.yml";
    private static final int REPLACE_ATTEMPTS = 5;
    private static final long REPLACE_RETRY_DELAY_MILLIS = 25L;
    private static final byte[] DEFAULT_CONTENT = "tables: {}\n".getBytes(StandardCharsets.UTF_8);

    private final Path configFile;
    private final Logger logger;
    private final Supplier<InputStream> defaultResourceSupplier;
    private NavigableMap<String, BlackjackTableDraft> drafts = new TreeMap<>();

    public BlackjackTableConfig(JavaPlugin plugin) {
        JavaPlugin validatedPlugin = Objects.requireNonNull(plugin, "plugin");
        this.configFile = validatedPlugin.getDataFolder().toPath()
                .resolve(RESOURCE_NAME)
                .toAbsolutePath()
                .normalize();
        this.logger = validatedPlugin.getLogger();
        this.defaultResourceSupplier = () -> validatedPlugin.getResource(RESOURCE_NAME);
    }

    public BlackjackTableConfig(Path configFile, Logger logger) {
        this.configFile = Objects.requireNonNull(configFile, "configFile").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.defaultResourceSupplier = () -> new ByteArrayInputStream(DEFAULT_CONTENT);
    }

    public void initialize() {
        createDefaultFile();
        drafts = readDrafts();
    }

    public Optional<BlackjackTableDraft> getDraft(String id) {
        BlackjackTableDraft draft = drafts.get(id);
        return draft == null ? Optional.empty() : Optional.of(draft.copy());
    }

    public List<BlackjackTableDraft> getDrafts() {
        return drafts.values().stream().map(BlackjackTableDraft::copy).toList();
    }

    public boolean createDraft(String id) {
        if (!BlackjackTableDraft.isValidId(id) || drafts.containsKey(id)) {
            return false;
        }
        BlackjackTableDraft draft = new BlackjackTableDraft(id);
        NavigableMap<String, BlackjackTableDraft> updated = copyDrafts();
        updated.put(id, draft);
        persistAndApply(updated);
        return true;
    }

    public void saveDraft(BlackjackTableDraft draft) {
        BlackjackTableDraft validatedDraft = Objects.requireNonNull(draft, "draft").copy();
        if (!drafts.containsKey(validatedDraft.getId())) {
            throw new IllegalArgumentException("Unknown blackjack table '" + validatedDraft.getId() + "'");
        }
        NavigableMap<String, BlackjackTableDraft> updated = copyDrafts();
        updated.put(validatedDraft.getId(), validatedDraft);
        persistAndApply(updated);
    }

    public boolean deleteDraft(String id) {
        if (!drafts.containsKey(id)) {
            return false;
        }
        NavigableMap<String, BlackjackTableDraft> updated = copyDrafts();
        updated.remove(id);
        persistAndApply(updated);
        return true;
    }

    public Path getConfigFile() {
        return configFile;
    }

    private NavigableMap<String, BlackjackTableDraft> readDrafts() {
        YamlConfiguration configuration = loadConfiguration();
        NavigableMap<String, BlackjackTableDraft> loaded = new TreeMap<>();
        if (!configuration.contains("tables")) {
            return loaded;
        }
        ConfigurationSection tables = configuration.getConfigurationSection("tables");
        if (tables == null) {
            logger.warning("Invalid blackjack configuration at " + configFile
                    + ": 'tables' must be a YAML section. The file was left unchanged.");
            return loaded;
        }

        for (String id : tables.getKeys(false)) {
            if (!BlackjackTableDraft.isValidId(id)) {
                logger.warning("Skipping blackjack table '" + id + "': ID must match [a-z0-9_-]+.");
                continue;
            }
            ConfigurationSection section = tables.getConfigurationSection(id);
            if (section == null) {
                logger.warning("Skipping blackjack table '" + id + "': expected a YAML section.");
                continue;
            }
            try {
                loaded.put(id, readDraft(id, section));
            } catch (IllegalArgumentException exception) {
                logger.warning("Skipping blackjack table '" + id + "': " + exception.getMessage());
            }
        }
        return loaded;
    }

    private BlackjackTableDraft readDraft(String id, ConfigurationSection section) {
        boolean enabled = readBoolean(section, "enabled", false);
        ActivityPosition pos1 = readOptionalPosition(section, "area.pos1", false);
        ActivityPosition pos2 = readOptionalPosition(section, "area.pos2", false);
        ActivityPosition dealer = readOptionalPosition(section, "dealer", true);
        BlackjackBlockPosition interaction = readOptionalBlockPosition(section, "interaction");
        BlackjackDisplayAnchor displayAnchor = readOptionalDisplayAnchor(section, "display");
        Collection<BlackjackSeat> seats = readSeats(section);
        return new BlackjackTableDraft(id, enabled, pos1, pos2, dealer, interaction, displayAnchor, seats);
    }

    private Collection<BlackjackSeat> readSeats(ConfigurationSection tableSection) {
        List<BlackjackSeat> seats = new ArrayList<>();
        if (!tableSection.contains("seats")) {
            return seats;
        }
        ConfigurationSection seatSection = tableSection.getConfigurationSection("seats");
        if (seatSection == null) {
            throw new IllegalArgumentException("'seats' must be a YAML section");
        }
        for (String key : seatSection.getKeys(false)) {
            int number;
            try {
                number = Integer.parseInt(key);
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException("seat key '" + key + "' is not a number");
            }
            String root = "seats." + key;
            ConfigurationSection definition = tableSection.getConfigurationSection(root);
            if (definition == null) {
                throw new IllegalArgumentException("seat " + key + " must be a YAML section");
            }
            boolean modern = definition.contains("position") || definition.contains("block");
            ActivityPosition position = readOptionalPosition(
                    tableSection,
                    modern ? root + ".position" : root,
                    true
            );
            if (position == null) throw new IllegalArgumentException("seat " + key + " is incomplete");
            BlackjackBlockPosition block = modern
                    ? readOptionalBlockPosition(tableSection, root + ".block")
                    : null;
            if (modern && block == null) {
                throw new IllegalArgumentException("seat " + key + " block is incomplete");
            }
            seats.add(new BlackjackSeat(number, position, block));
        }
        return seats;
    }

    private ActivityPosition readOptionalPosition(
            ConfigurationSection section,
            String path,
            boolean rotationRequired
    ) {
        if (!section.contains(path)) {
            return null;
        }
        ConfigurationSection position = section.getConfigurationSection(path);
        if (position == null) {
            throw new IllegalArgumentException("'" + path + "' must be a YAML section");
        }
        String world = readWorld(position, path);
        double x = readNumber(position, "x", path).doubleValue();
        double y = readNumber(position, "y", path).doubleValue();
        double z = readNumber(position, "z", path).doubleValue();
        float yaw = rotationRequired ? readNumber(position, "yaw", path).floatValue() : 0.0F;
        float pitch = rotationRequired ? readNumber(position, "pitch", path).floatValue() : 0.0F;
        return new ActivityPosition(world, x, y, z, yaw, pitch);
    }

    private BlackjackBlockPosition readOptionalBlockPosition(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            return null;
        }
        ConfigurationSection position = section.getConfigurationSection(path);
        if (position == null) {
            throw new IllegalArgumentException("'" + path + "' must be a YAML section");
        }
        return new BlackjackBlockPosition(
                readWorld(position, path),
                readInteger(position, "x", path),
                readInteger(position, "y", path),
                readInteger(position, "z", path)
        );
    }

    private BlackjackDisplayAnchor readOptionalDisplayAnchor(ConfigurationSection section, String path) {
        if (!section.contains(path)) {
            return null;
        }
        ConfigurationSection position = section.getConfigurationSection(path);
        if (position == null) {
            throw new IllegalArgumentException("'" + path + "' must be a YAML section");
        }
        return new BlackjackDisplayAnchor(
                readWorld(position, path),
                readNumber(position, "x", path).doubleValue(),
                readNumber(position, "y", path).doubleValue(),
                readNumber(position, "z", path).doubleValue(),
                readNumber(position, "yaw", path).floatValue()
        );
    }

    private String readWorld(ConfigurationSection section, String path) {
        Object value = section.get("world");
        if (!(value instanceof String world) || world.isBlank()) {
            throw new IllegalArgumentException("'" + path + ".world' must be a non-blank string");
        }
        return world;
    }

    private Number readNumber(ConfigurationSection section, String key, String path) {
        Object value = section.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException("'" + path + "." + key + "' must be numeric");
        }
        return number;
    }

    private int readInteger(ConfigurationSection section, String key, String path) {
        Number number = readNumber(section, key, path);
        double value = number.doubleValue();
        if (!Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("'" + path + "." + key + "' must be an integer");
        }
        return (int) value;
    }

    private boolean readBoolean(ConfigurationSection section, String path, boolean fallback) {
        if (!section.contains(path)) {
            return fallback;
        }
        Object value = section.get(path);
        if (!(value instanceof Boolean booleanValue)) {
            throw new IllegalArgumentException("'" + path + "' must be a boolean");
        }
        return booleanValue;
    }

    private void persistAndApply(NavigableMap<String, BlackjackTableDraft> updated) {
        save(updated);
        drafts = updated;
    }

    private NavigableMap<String, BlackjackTableDraft> copyDrafts() {
        NavigableMap<String, BlackjackTableDraft> copy = new TreeMap<>();
        drafts.forEach((id, draft) -> copy.put(id, draft.copy()));
        return copy;
    }

    private void save(Map<String, BlackjackTableDraft> values) {
        YamlConfiguration configuration = new YamlConfiguration();
        if (values.isEmpty()) {
            configuration.createSection("tables");
        }
        for (BlackjackTableDraft draft : values.values()) {
            String root = "tables." + draft.getId();
            configuration.set(root + ".enabled", draft.isEnabled());
            draft.getPos1().ifPresent(position -> writePosition(configuration, root + ".area.pos1", position, false));
            draft.getPos2().ifPresent(position -> writePosition(configuration, root + ".area.pos2", position, false));
            draft.getDealer().ifPresent(position -> writePosition(configuration, root + ".dealer", position, true));
            draft.getInteraction().ifPresent(position -> {
                configuration.set(root + ".interaction.world", position.worldName());
                configuration.set(root + ".interaction.x", position.x());
                configuration.set(root + ".interaction.y", position.y());
                configuration.set(root + ".interaction.z", position.z());
            });
            draft.getDisplayAnchor().ifPresent(anchor -> {
                configuration.set(root + ".display.world", anchor.worldName());
                configuration.set(root + ".display.x", anchor.x());
                configuration.set(root + ".display.y", anchor.y());
                configuration.set(root + ".display.z", anchor.z());
                configuration.set(root + ".display.yaw", anchor.yaw());
            });
            if (draft.getSeatDefinitions().isEmpty()) {
                configuration.createSection(root + ".seats");
            } else {
                draft.getSeatDefinitions().forEach((number, seat) -> {
                    String seatRoot = root + ".seats." + number;
                    if (seat.isModern()) {
                        BlackjackBlockPosition block = seat.block();
                        configuration.set(seatRoot + ".block.world", block.worldName());
                        configuration.set(seatRoot + ".block.x", block.x());
                        configuration.set(seatRoot + ".block.y", block.y());
                        configuration.set(seatRoot + ".block.z", block.z());
                        writePosition(configuration, seatRoot + ".position", seat.position(), true);
                    } else {
                        writePosition(configuration, seatRoot, seat.position(), true);
                    }
                });
            }
        }
        saveAtomically(configuration);
    }

    private void writePosition(
            YamlConfiguration configuration,
            String path,
            ActivityPosition position,
            boolean rotation
    ) {
        configuration.set(path + ".world", position.worldName());
        configuration.set(path + ".x", position.x());
        configuration.set(path + ".y", position.y());
        configuration.set(path + ".z", position.z());
        if (rotation) {
            configuration.set(path + ".yaw", position.yaw());
            configuration.set(path + ".pitch", position.pitch());
        }
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
            temporaryFile = Files.createTempFile(configFile.getParent(), "blackjack-", ".tmp");
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
                    logger.warning("Could not delete temporary blackjack configuration " + temporaryFile + ".");
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
            logger.warning("Could not delete copied temporary blackjack configuration " + temporaryFile + ".");
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
