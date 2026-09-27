package dev.vapee.core.friend;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** YAML repository for the complete, central friends state. */
public final class FileFriendRepository implements FriendRepository {

    public static final int SCHEMA_VERSION = 1;

    private final Path file;
    private final Logger logger;
    private final FriendFileMover fileMover;

    public FileFriendRepository(Path file, Logger logger) {
        this(file, logger, Files::move);
    }

    FileFriendRepository(Path file, Logger logger, FriendFileMover fileMover) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.fileMover = Objects.requireNonNull(fileMover, "fileMover");
    }

    @Override
    public FriendSnapshot initialize() {
        Path parent = requireParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw storageFailure("initialize friends directory", parent, exception);
        }
        if (!Files.isDirectory(parent)) {
            throw invalidData("Friends parent path is not a directory: " + parent);
        }
        if (Files.notExists(file)) {
            return FriendSnapshot.empty();
        }
        if (!Files.isRegularFile(file)) {
            throw invalidData("Friends path is not a regular file: " + file);
        }

        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Object loaded = yamlReader().load(reader);
            Map<?, ?> root = readMap(loaded, "document root");
            validateSchema(root);
            List<Friendship> friendships = readFriendships(root);
            List<FriendRequest> requests = readRequests(root);
            return new FriendSnapshot(friendships, requests);
        } catch (IOException exception) {
            throw storageFailure("load friends data", file, exception);
        } catch (FriendRepositoryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidData("Invalid friends data in " + file, exception);
        }
    }

    @Override
    public void save(FriendSnapshot snapshot) {
        FriendSnapshot validated = new FriendSnapshot(
                Objects.requireNonNull(snapshot, "snapshot").friendships(),
                snapshot.requests()
        );
        Path parent = requireParent();
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw storageFailure("initialize friends directory", parent, exception);
        }
        if (!Files.isDirectory(parent)) {
            throw invalidData("Friends parent path is not a directory: " + parent);
        }

        Path temporaryFile = null;
        try {
            temporaryFile = Files.createTempFile(parent, file.getFileName() + "-", ".tmp");
            LinkedHashMap<String, Object> root = new LinkedHashMap<>();
            root.put("schema-version", SCHEMA_VERSION);
            root.put("friendships", writeFriendships(validated.friendships()));
            root.put("requests", writeRequests(validated.requests()));
            try (Writer writer = Files.newBufferedWriter(temporaryFile, StandardCharsets.UTF_8)) {
                yamlWriter().dump(root, writer);
            }
            replaceFile(temporaryFile);
            temporaryFile = null;
        } catch (IOException | RuntimeException exception) {
            throw storageFailure("save friends data", file, exception);
        } finally {
            deleteTemporaryFile(temporaryFile);
        }
    }

    private void validateSchema(Map<?, ?> root) {
        Object value = root.get("schema-version");
        if (!(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long)) {
            throw invalidData("Missing or invalid 'schema-version' in " + file);
        }
        long version = ((Number) value).longValue();
        if (version != SCHEMA_VERSION) {
            throw invalidData("Unsupported friends schema-version " + version + " in " + file);
        }
    }

    private List<Friendship> readFriendships(Map<?, ?> root) {
        List<?> entries = readRequiredList(root, "friendships");
        ArrayList<Friendship> friendships = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            Map<?, ?> entry = readMap(entries.get(index), "friendships[" + index + "]");
            UUID playerA = readUuid(entry, "player-a", "friendships[" + index + "]");
            UUID playerB = readUuid(entry, "player-b", "friendships[" + index + "]");
            Instant createdAt = readInstant(entry, "created-at", "friendships[" + index + "]");
            friendships.add(new Friendship(playerA, playerB, createdAt));
        }
        return friendships;
    }

    private List<FriendRequest> readRequests(Map<?, ?> root) {
        List<?> entries = readRequiredList(root, "requests");
        ArrayList<FriendRequest> requests = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            Map<?, ?> entry = readMap(entries.get(index), "requests[" + index + "]");
            UUID sender = readUuid(entry, "sender", "requests[" + index + "]");
            UUID recipient = readUuid(entry, "recipient", "requests[" + index + "]");
            Instant createdAt = readInstant(entry, "created-at", "requests[" + index + "]");
            requests.add(new FriendRequest(sender, recipient, createdAt));
        }
        return requests;
    }

    private List<?> readRequiredList(Map<?, ?> root, String key) {
        Object values = root.get(key);
        if (!(values instanceof List<?> list)) {
            throw invalidData("Missing or invalid '" + key + "' list in " + file);
        }
        return list;
    }

    private Map<?, ?> readMap(Object value, String location) {
        if (!(value instanceof Map<?, ?> map)) {
            throw invalidData("Expected a map at " + location + " in " + file);
        }
        return map;
    }

    private Yaml yamlReader() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    private Yaml yamlWriter() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        return new Yaml(options);
    }

    private UUID readUuid(Map<?, ?> entry, String key, String location) {
        Object value = entry.get(key);
        if (!(value instanceof String text)) {
            throw invalidData("Missing or invalid '" + key + "' at " + location + " in " + file);
        }
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException exception) {
            throw invalidData("Invalid UUID for '" + key + "' at " + location + " in " + file, exception);
        }
    }

    private Instant readInstant(Map<?, ?> entry, String key, String location) {
        Object value = entry.get(key);
        if (!(value instanceof Byte || value instanceof Short
                || value instanceof Integer || value instanceof Long)) {
            throw invalidData("Missing or invalid '" + key + "' at " + location + " in " + file);
        }
        try {
            return Instant.ofEpochMilli(((Number) value).longValue());
        } catch (RuntimeException exception) {
            throw invalidData("Invalid timestamp for '" + key + "' at " + location + " in " + file, exception);
        }
    }

    private List<Map<String, Object>> writeFriendships(List<Friendship> friendships) {
        ArrayList<Map<String, Object>> values = new ArrayList<>(friendships.size());
        for (Friendship friendship : friendships) {
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("player-a", friendship.playerA().toString());
            value.put("player-b", friendship.playerB().toString());
            value.put("created-at", friendship.createdAt().toEpochMilli());
            values.add(value);
        }
        return values;
    }

    private List<Map<String, Object>> writeRequests(List<FriendRequest> requests) {
        ArrayList<Map<String, Object>> values = new ArrayList<>(requests.size());
        for (FriendRequest request : requests) {
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("sender", request.sender().toString());
            value.put("recipient", request.recipient().toString());
            value.put("created-at", request.createdAt().toEpochMilli());
            values.add(value);
        }
        return values;
    }

    private void replaceFile(Path temporaryFile) throws IOException {
        try {
            fileMover.move(
                    temporaryFile,
                    file,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
            );
        } catch (AtomicMoveNotSupportedException exception) {
            logger.log(Level.FINE, "Atomic move unavailable for friends data; using replace fallback.", exception);
            fileMover.move(temporaryFile, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void deleteTemporaryFile(Path temporaryFile) {
        if (temporaryFile == null) {
            return;
        }
        try {
            Files.deleteIfExists(temporaryFile);
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not delete temporary friends file " + temporaryFile + ".", exception);
        }
    }

    private Path requireParent() {
        Path parent = file.getParent();
        if (parent == null) {
            throw invalidData("Friends file has no parent directory: " + file);
        }
        return parent;
    }

    private FriendRepositoryException storageFailure(String action, Path path, Throwable cause) {
        return new FriendRepositoryException("Could not " + action + " at " + path, cause);
    }

    private FriendRepositoryException invalidData(String message) {
        return new FriendRepositoryException(message);
    }

    private FriendRepositoryException invalidData(String message, Throwable cause) {
        return new FriendRepositoryException(message, cause);
    }
}
