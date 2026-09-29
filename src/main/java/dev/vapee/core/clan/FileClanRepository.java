package dev.vapee.core.clan;

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
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Strict UTF-8 YAML repository for a single complete clans snapshot. */
public final class FileClanRepository implements ClanRepository {
    public static final int SCHEMA_VERSION = 1;

    private final Path file;
    private final ClanFileMover mover;
    private final Logger logger = Logger.getLogger(FileClanRepository.class.getName());

    public FileClanRepository(Path file) {
        this(file, Files::move);
    }

    FileClanRepository(Path file, ClanFileMover mover) {
        this.file = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
        this.mover = Objects.requireNonNull(mover, "mover");
    }

    @Override
    public ClanSnapshot initialize() {
        Path parent = parent();
        createParent(parent);
        if (Files.notExists(file)) return ClanSnapshot.empty();
        if (!Files.isRegularFile(file)) throw corrupt("Clan path is not a regular file: " + file);
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Map<?, ?> root = map(readerYaml().load(reader), "document root");
            keys(root, Set.of("schema-version", "clans", "invites"), "document root");
            Object version = root.get("schema-version");
            if (!(version instanceof Byte || version instanceof Short || version instanceof Integer || version instanceof Long))
                throw corrupt("Missing or invalid clan schema-version in " + file);
            if (((Number) version).longValue() != SCHEMA_VERSION)
                throw corrupt("Unsupported clan schema-version " + version + " in " + file);
            return new ClanSnapshot(readClans(root), readInvites(root));
        } catch (IOException exception) {
            throw failure("load clan data", exception);
        } catch (ClanRepositoryException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ClanRepositoryException("Invalid clan data in " + file + ": " + exception.getMessage(), exception);
        }
    }

    @Override
    public void save(ClanSnapshot snapshot) {
        ClanSnapshot checked = new ClanSnapshot(Objects.requireNonNull(snapshot, "snapshot").clans(), snapshot.invites());
        Path parent = parent();
        createParent(parent);
        Path temporary = null;
        try {
            temporary = Files.createTempFile(parent, file.getFileName() + "-", ".tmp");
            LinkedHashMap<String, Object> root = new LinkedHashMap<>();
            root.put("schema-version", SCHEMA_VERSION);
            root.put("clans", writeClans(checked.clans()));
            root.put("invites", writeInvites(checked.invites()));
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                writerYaml().dump(root, writer);
            }
            replace(temporary);
            temporary = null;
        } catch (IOException | RuntimeException exception) {
            throw failure("save clan data", exception);
        } finally {
            cleanup(temporary);
        }
    }

    private List<Clan> readClans(Map<?, ?> root) {
        List<?> entries = list(root.get("clans"), "clans");
        ArrayList<Clan> clans = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            String location = "clans[" + index + "]";
            Map<?, ?> entry = map(entries.get(index), location);
            keys(entry, Set.of("id", "name", "tag", "created-at", "members"), location);
            List<?> rawMembers = list(entry.get("members"), location + ".members");
            ArrayList<ClanMember> members = new ArrayList<>(rawMembers.size());
            for (int memberIndex = 0; memberIndex < rawMembers.size(); memberIndex++) {
                String memberLocation = location + ".members[" + memberIndex + "]";
                Map<?, ?> member = map(rawMembers.get(memberIndex), memberLocation);
                keys(member, Set.of("player", "role", "joined-at"), memberLocation);
                String role = string(member, "role", memberLocation);
                ClanRole parsedRole;
                try {
                    parsedRole = ClanRole.valueOf(role);
                } catch (IllegalArgumentException exception) {
                    throw corrupt("Invalid role at " + memberLocation + " in " + file, exception);
                }
                members.add(new ClanMember(uuid(member, "player", memberLocation), parsedRole,
                        instant(member, "joined-at", memberLocation)));
            }
            clans.add(new Clan(uuid(entry, "id", location), string(entry, "name", location),
                    string(entry, "tag", location), instant(entry, "created-at", location), members));
        }
        return clans;
    }

    private List<ClanInvite> readInvites(Map<?, ?> root) {
        List<?> entries = list(root.get("invites"), "invites");
        ArrayList<ClanInvite> invites = new ArrayList<>(entries.size());
        for (int index = 0; index < entries.size(); index++) {
            String location = "invites[" + index + "]";
            Map<?, ?> entry = map(entries.get(index), location);
            keys(entry, Set.of("clan-id", "recipient", "created-at"), location);
            invites.add(new ClanInvite(uuid(entry, "clan-id", location),
                    uuid(entry, "recipient", location), instant(entry, "created-at", location)));
        }
        return invites;
    }

    private List<Map<String, Object>> writeClans(List<Clan> clans) {
        ArrayList<Map<String, Object>> output = new ArrayList<>(clans.size());
        for (Clan clan : clans) {
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("id", clan.id().toString());
            value.put("name", clan.name());
            value.put("tag", clan.tag());
            value.put("created-at", clan.createdAt().toEpochMilli());
            ArrayList<Map<String, Object>> members = new ArrayList<>(clan.members().size());
            for (ClanMember member : clan.members()) {
                LinkedHashMap<String, Object> memberValue = new LinkedHashMap<>();
                memberValue.put("player", member.playerId().toString());
                memberValue.put("role", member.role().name());
                memberValue.put("joined-at", member.joinedAt().toEpochMilli());
                members.add(memberValue);
            }
            value.put("members", members);
            output.add(value);
        }
        return output;
    }

    private List<Map<String, Object>> writeInvites(List<ClanInvite> invites) {
        ArrayList<Map<String, Object>> output = new ArrayList<>(invites.size());
        for (ClanInvite invite : invites) {
            LinkedHashMap<String, Object> value = new LinkedHashMap<>();
            value.put("clan-id", invite.clanId().toString());
            value.put("recipient", invite.recipient().toString());
            value.put("created-at", invite.createdAt().toEpochMilli());
            output.add(value);
        }
        return output;
    }

    private void replace(Path temporary) throws IOException {
        try {
            mover.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            logger.log(Level.FINE, "Atomic move unavailable for clan data; using replace fallback.", exception);
            mover.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void cleanup(Path temporary) {
        if (temporary == null) return;
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException exception) {
            logger.log(Level.WARNING, "Could not delete temporary clan file " + temporary, exception);
        }
    }

    private void createParent(Path parent) {
        try {
            Files.createDirectories(parent);
        } catch (IOException exception) {
            throw failure("create clan directory", exception);
        }
        if (!Files.isDirectory(parent)) throw corrupt("Clan parent path is not a directory: " + parent);
    }

    private Path parent() {
        Path parent = file.getParent();
        if (parent == null) throw corrupt("Clan file has no parent directory: " + file);
        return parent;
    }

    private Map<?, ?> map(Object value, String location) {
        if (!(value instanceof Map<?, ?> result)) throw corrupt("Expected map at " + location + " in " + file);
        return result;
    }

    private List<?> list(Object value, String location) {
        if (!(value instanceof List<?> result)) throw corrupt("Missing or invalid list at " + location + " in " + file);
        return result;
    }

    private void keys(Map<?, ?> value, Set<String> expected, String location) {
        if (!value.keySet().equals(expected))
            throw corrupt("Missing or unexpected keys at " + location + " in " + file + ": " + value.keySet());
    }

    private String string(Map<?, ?> value, String key, String location) {
        if (!(value.get(key) instanceof String text))
            throw corrupt("Missing or invalid '" + key + "' at " + location + " in " + file);
        return text;
    }

    private UUID uuid(Map<?, ?> value, String key, String location) {
        String text = string(value, key, location);
        try {
            UUID result = UUID.fromString(text);
            if (result.toString().equalsIgnoreCase(text)) return result;
        } catch (IllegalArgumentException ignored) {
            // A non-canonical UUID is invalid persistence data.
        }
        throw corrupt("Invalid UUID for '" + key + "' at " + location + " in " + file);
    }

    private Instant instant(Map<?, ?> value, String key, String location) {
        Object raw = value.get(key);
        if (!(raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long))
            throw corrupt("Invalid timestamp for '" + key + "' at " + location + " in " + file);
        try {
            return Instant.ofEpochMilli(((Number) raw).longValue());
        } catch (RuntimeException exception) {
            throw corrupt("Invalid timestamp for '" + key + "' at " + location + " in " + file, exception);
        }
    }

    private Yaml readerYaml() {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        return new Yaml(new SafeConstructor(options));
    }

    private Yaml writerYaml() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setSplitLines(false);
        return new Yaml(options);
    }

    private ClanRepositoryException corrupt(String message) { return new ClanRepositoryException(message); }
    private ClanRepositoryException corrupt(String message, Throwable cause) {
        return new ClanRepositoryException(message, cause);
    }
    private ClanRepositoryException failure(String action, Throwable cause) {
        return new ClanRepositoryException("Could not " + action + " at " + file, cause);
    }
}
