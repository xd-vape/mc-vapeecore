package dev.vapee.core.player.repository;

import dev.vapee.core.player.CorePlayer;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FilePlayerRepository implements PlayerRepository {

    private final Path playersDirectory;
    private final Logger logger;

    public FilePlayerRepository(Path playersDirectory, Logger logger) {
        this.playersDirectory = Objects.requireNonNull(playersDirectory, "playersDirectory")
                .toAbsolutePath()
                .normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void initialize() {
        try {
            Files.createDirectories(playersDirectory);
        } catch (IOException exception) {
            throw storageFailure("initialize player storage", playersDirectory, exception);
        }

        if (!Files.isDirectory(playersDirectory)) {
            throw invalidStorage("Player storage path is not a directory: " + playersDirectory);
        }

        logger.info("Player storage ready at " + playersDirectory + ".");
    }

    @Override
    public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
        Path playerFile = getPlayerFile(uniqueId);
        if (Files.notExists(playerFile)) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(playerFile)) {
            throw invalidPlayerFile(playerFile, "Path is not a regular file");
        }

        YamlConfiguration configuration = new YamlConfiguration();
        try {
            configuration.load(playerFile.toFile());
        } catch (IOException | InvalidConfigurationException exception) {
            throw storageFailure("load player data", playerFile, exception);
        }

        return Optional.of(readPlayer(uniqueId, playerFile, configuration));
    }

    @Override
    public void save(CorePlayer player) {
        Objects.requireNonNull(player, "player");
        ensureInitialized();

        Path playerFile = getPlayerFile(player.getUniqueId());
        Path temporaryFile = null;

        try {
            temporaryFile = Files.createTempFile(
                    playersDirectory,
                    player.getUniqueId() + "-",
                    ".tmp"
            );

            YamlConfiguration configuration = new YamlConfiguration();
            configuration.set("name", player.getName());
            configuration.set("first-join", player.getFirstJoin().toEpochMilli());
            configuration.set("last-join", player.getLastJoin().toEpochMilli());
            configuration.save(temporaryFile.toFile());

            replacePlayerFile(temporaryFile, playerFile);
            temporaryFile = null;
        } catch (IOException | RuntimeException exception) {
            throw storageFailure("save player data", playerFile, exception);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    @Override
    public boolean exists(UUID uniqueId) {
        return Files.isRegularFile(getPlayerFile(uniqueId));
    }

    private CorePlayer readPlayer(UUID uniqueId, Path playerFile, YamlConfiguration configuration) {
        String name = configuration.getString("name");
        if (name == null || name.isBlank()) {
            throw invalidPlayerFile(playerFile, "Missing or blank 'name'");
        }

        long firstJoinMillis = readEpochMillis(configuration, playerFile, "first-join");
        long lastJoinMillis = readEpochMillis(configuration, playerFile, "last-join");

        try {
            return new CorePlayer(
                    uniqueId,
                    name,
                    Instant.ofEpochMilli(firstJoinMillis),
                    Instant.ofEpochMilli(lastJoinMillis)
            );
        } catch (RuntimeException exception) {
            throw invalidPlayerFile(playerFile, "Invalid player values", exception);
        }
    }

    private long readEpochMillis(YamlConfiguration configuration, Path playerFile, String key) {
        Object value = configuration.get(key);
        if (!(value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long)) {
            throw invalidPlayerFile(playerFile, "Missing or invalid '" + key + "'");
        }
        return ((Number) value).longValue();
    }

    private void replacePlayerFile(Path temporaryFile, Path playerFile) throws IOException {
        try {
            Files.move(
                    temporaryFile,
                    playerFile,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(temporaryFile, playerFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }

        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not delete temporary player file " + temporaryFile + ".", exception);
        }
    }

    private void ensureInitialized() {
        if (!Files.isDirectory(playersDirectory)) {
            initialize();
        }
    }

    private Path getPlayerFile(UUID uniqueId) {
        UUID validatedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        return playersDirectory.resolve(validatedUniqueId + ".yml");
    }

    private IllegalStateException invalidStorage(String message) {
        logger.severe(message);
        return new IllegalStateException(message);
    }

    private IllegalStateException invalidPlayerFile(Path playerFile, String reason) {
        return invalidPlayerFile(playerFile, reason, null);
    }

    private IllegalStateException invalidPlayerFile(Path playerFile, String reason, Throwable cause) {
        String message = "Invalid player file " + playerFile + ": " + reason + ". The file was left unchanged.";
        if (cause == null) {
            logger.severe(message);
        } else {
            logger.log(Level.SEVERE, message, cause);
        }
        return new IllegalStateException(message, cause);
    }

    private IllegalStateException storageFailure(String operation, Path path, Throwable cause) {
        String message = "Could not " + operation + " at " + path + ".";
        logger.log(Level.SEVERE, message, cause);
        return new IllegalStateException(message, cause);
    }
}
