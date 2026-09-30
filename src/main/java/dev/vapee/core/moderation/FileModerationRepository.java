package dev.vapee.core.moderation;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;

import java.io.IOException;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Strict schema-1 UTF-8 storage. No repair, migration or empty-state fallback on corruption. */
public final class FileModerationRepository implements ModerationRepository {
    public static final int SCHEMA_VERSION = 1;
    private static final Set<String> RECORD_KEYS = Set.of("id", "action", "target", "actor", "reason",
            "created-at", "expires-at", "revocation");
    private final Path file;
    private final Logger logger;
    private final ModerationFileMover fileMover;

    public FileModerationRepository(Path file, Logger logger) {
        this(file, logger, Files::move);
    }

    FileModerationRepository(Path file, Logger logger, ModerationFileMover fileMover) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.fileMover = Objects.requireNonNull(fileMover, "fileMover");
        if (this.file.getParent() == null) throw new IllegalArgumentException("Moderation file requires a parent");
    }

    @Override
    public ModerationSnapshot initialize() {
        if (Files.notExists(file)) return ModerationSnapshot.empty();
        if (!Files.isRegularFile(file)) throw invalid("Path is not a regular file", null);
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            Map<?, ?> root = map(new Yaml(new StrictConstructor(options)).load(reader),
                    Set.of("schema-version", "records"), "root");
            Object version = root.get("schema-version");
            if (!(version instanceof Byte || version instanceof Short || version instanceof Integer
                    || version instanceof Long) || ((Number) version).longValue() != SCHEMA_VERSION) {
                throw invalid("Unsupported or invalid schema-version", null);
            }
            if (!(root.get("records") instanceof List<?> entries)) throw invalid("records must be a list", null);
            ArrayList<ModerationRecord> records = new ArrayList<>();
            for (int index = 0; index < entries.size(); index++) {
                records.add(readRecord(entries.get(index), "records[" + index + "]"));
            }
            return new ModerationSnapshot(records);
        } catch (ModerationRepositoryException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw invalid("Could not load validated moderation data", exception);
        }
    }

    @Override
    public void save(ModerationSnapshot snapshot) {
        ModerationSnapshot validated = new ModerationSnapshot(Objects.requireNonNull(snapshot, "snapshot").records());
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), file.getFileName() + "-", ".tmp");
            LinkedHashMap<String, Object> root = new LinkedHashMap<>();
            root.put("schema-version", SCHEMA_VERSION);
            root.put("records", validated.records().stream().map(this::writeRecord).toList());
            DumperOptions options = new DumperOptions();
            options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
            options.setSplitLines(false);
            try (var writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                new Yaml(options).dump(root, writer);
            }
            try {
                fileMover.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                logger.log(Level.FINE, "Atomic moderation replace unavailable; using replace fallback.", exception);
                fileMover.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
            temporary = null;
        } catch (IOException | RuntimeException exception) {
            throw invalid("Could not save moderation data", exception);
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException | RuntimeException exception) {
                    logger.log(Level.WARNING, "Could not clean temporary moderation file " + temporary, exception);
                }
            }
        }
    }

    private ModerationRecord readRecord(Object value, String location) {
        Map<?, ?> entry = map(value, RECORD_KEYS, location);
        Optional<Instant> expiry = entry.get("expires-at") == null ? Optional.empty()
                : Optional.of(instant(entry.get("expires-at"), location + ".expires-at"));
        Optional<ModerationRevocation> revocation = entry.get("revocation") == null ? Optional.empty()
                : Optional.of(readRevocation(entry.get("revocation"), location + ".revocation"));
        return new ModerationRecord(uuid(entry.get("id"), location + ".id"),
                ModerationAction.valueOf(string(entry.get("action"), location + ".action")),
                uuid(entry.get("target"), location + ".target"),
                readActor(entry.get("actor"), location + ".actor"),
                string(entry.get("reason"), location + ".reason"),
                instant(entry.get("created-at"), location + ".created-at"), expiry, revocation);
    }

    private ModerationActor readActor(Object value, String location) {
        Map<?, ?> actor = map(value, Set.of("type", "player"), location);
        return new ModerationActor(ModerationActorType.valueOf(string(actor.get("type"), location + ".type")),
                actor.get("player") == null ? Optional.empty()
                        : Optional.of(uuid(actor.get("player"), location + ".player")));
    }

    private ModerationRevocation readRevocation(Object value, String location) {
        Map<?, ?> revocation = map(value, Set.of("actor", "revoked-at", "reason"), location);
        return new ModerationRevocation(readActor(revocation.get("actor"), location + ".actor"),
                instant(revocation.get("revoked-at"), location + ".revoked-at"),
                revocation.get("reason") == null ? Optional.empty()
                        : Optional.of(string(revocation.get("reason"), location + ".reason")));
    }

    private Map<String, Object> writeRecord(ModerationRecord record) {
        LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
        entry.put("id", record.id().toString());
        entry.put("action", record.action().name());
        entry.put("target", record.targetId().toString());
        entry.put("actor", writeActor(record.actor()));
        entry.put("reason", record.reason());
        // Canonical UTC strings preserve the entire Instant, including sub-millisecond boundaries.
        entry.put("created-at", record.createdAt().toString());
        entry.put("expires-at", record.expiresAt().map(Instant::toString).orElse(null));
        entry.put("revocation", record.revocation().map(this::writeRevocation).orElse(null));
        return entry;
    }

    private Map<String, Object> writeActor(ModerationActor actor) {
        LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
        entry.put("type", actor.type().name());
        entry.put("player", actor.playerId().map(UUID::toString).orElse(null));
        return entry;
    }

    private Map<String, Object> writeRevocation(ModerationRevocation revocation) {
        LinkedHashMap<String, Object> entry = new LinkedHashMap<>();
        entry.put("actor", writeActor(revocation.actor()));
        entry.put("revoked-at", revocation.revokedAt().toString());
        entry.put("reason", revocation.reason().orElse(null));
        return entry;
    }

    private Map<?, ?> map(Object value, Set<String> keys, String location) {
        if (!(value instanceof Map<?, ?> map) || !map.keySet().equals(keys)) {
            throw invalid("Expected exactly " + keys + " at " + location, null);
        }
        return map;
    }

    private String string(Object value, String location) {
        if (!(value instanceof String text)) throw invalid("Expected a string at " + location, null);
        return text;
    }

    private UUID uuid(Object value, String location) {
        String text = string(value, location);
        UUID id = UUID.fromString(text);
        if (!id.toString().equalsIgnoreCase(text)) throw invalid("Non-canonical UUID at " + location, null);
        return id;
    }

    private Instant instant(Object value, String location) {
        String text = string(value, location);
        Instant instant = Instant.parse(text);
        if (!instant.toString().equals(text)) throw invalid("Expected canonical UTC Instant at " + location, null);
        return instant;
    }

    private ModerationRepositoryException invalid(String message, Throwable cause) {
        return new ModerationRepositoryException(message + " at " + file + ".", cause);
    }

    private static final class StrictConstructor extends SafeConstructor {
        private StrictConstructor(LoaderOptions options) { super(options); }

        @Override protected void flattenMapping(MappingNode node) {
            rejectMerge(node);
            super.flattenMapping(node);
        }

        @Override protected void flattenMapping(MappingNode node, boolean forceStringKeys) {
            rejectMerge(node);
            super.flattenMapping(node, forceStringKeys);
        }

        private void rejectMerge(MappingNode node) {
            // SnakeYAML normally erases << before schema validation; it is not a schema-1 key.
            if (node.isMerged()) throw new IllegalArgumentException("YAML merge keys are not supported");
        }
    }
}
