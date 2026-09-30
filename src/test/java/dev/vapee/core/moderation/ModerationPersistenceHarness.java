package dev.vapee.core.moderation;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

public final class ModerationPersistenceHarness {
    private static int checks;
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00.123456789Z");
    private static final ModerationActor CONSOLE = ModerationActor.console();

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-moderation-persistence-");
        try {
            roundTrip(directory);
            corruption(directory);
            atomicWrites(directory);
            System.out.println("ModerationPersistenceHarness passed " + checks + " checks.");
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path path : files.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void roundTrip(Path directory) throws IOException {
        Path file = directory.resolve("missing").resolve("moderation.yml");
        FileModerationRepository repo = new FileModerationRepository(file, logger());
        check(repo.initialize().equals(ModerationSnapshot.empty()), "missing file empty");
        check(Files.notExists(file) && Files.notExists(file.getParent()), "initialize entirely read-only");
        List<ModerationRecord> records = new ArrayList<>();
        long nextId = 1;
        for (ModerationAction action : ModerationAction.values()) {
            for (ModerationActor actor : List.of(CONSOLE, ModerationActor.player(id(80)))) {
                records.add(new ModerationRecord(id(nextId++), action, id(90), actor,
                        "Café 😀 <red>literal</red> &a # text: value", NOW, Optional.empty(), Optional.empty()));
                if (action.supportsActiveState()) {
                    records.add(new ModerationRecord(id(nextId++), action, id(90), actor, "Temporary", NOW,
                            Optional.of(NOW.plusNanos(1)), Optional.empty()));
                    for (ModerationActor revoker : List.of(CONSOLE, ModerationActor.player(id(81)))) {
                        records.add(new ModerationRecord(id(nextId++), action, id(90), actor, "Revoked", NOW,
                                Optional.of(NOW.plusSeconds(3)), Optional.of(new ModerationRevocation(revoker,
                                NOW.plusNanos(2), Optional.of("Appel accepté 😀")))));
                        records.add(new ModerationRecord(id(nextId++), action, id(90), actor, "Permanent revoked", NOW,
                                Optional.empty(), Optional.of(new ModerationRevocation(revoker, NOW, Optional.empty()))));
                    }
                }
            }
        }
        // The whole Instant domain is lossless, not only contemporary epoch-millisecond values.
        records.add(new ModerationRecord(id(nextId), ModerationAction.WARNING, id(91), CONSOLE, "Ancient",
                Instant.MIN, Optional.empty(), Optional.empty()));
        Collections.reverse(records);
        ModerationSnapshot snapshot = new ModerationSnapshot(records);
        repo.save(snapshot);
        check(Files.isRegularFile(file), "first successful save creates file");
        String yaml = Files.readString(file, StandardCharsets.UTF_8);
        check(yaml.contains("schema-version: 1") && yaml.contains("Café 😀")
                && yaml.contains("<red>literal</red>"), "schema UTF-8 and literal text");
        check(yaml.contains("2026-09-30T12:00:00.123456790Z"), "nanosecond expiry serialized losslessly");
        check(!yaml.contains("name:") && !yaml.contains("rank:") && !yaml.contains("ip:"), "UUID only, no identity/IP duplication");
        ModerationSnapshot loaded = repo.initialize();
        check(loaded.equals(snapshot), "all actions, actors, temporary/permanent/revoked metadata roundtrip");
        for (int index = 0; index < loaded.records().size(); index++) {
            check(loaded.records().get(index).equals(snapshot.records().get(index)), "exact record roundtrip " + index);
        }
        Collections.reverse(records);
        repo.save(new ModerationSnapshot(records));
        check(Files.readString(file).equals(yaml), "deterministic exact YAML independent of input order");
        check(noTemps(file), "normal save leaves no temp");
        repo.save(ModerationSnapshot.empty());
        check(repo.initialize().equals(ModerationSnapshot.empty()), "explicit empty save roundtrip");
    }

    @SuppressWarnings("unchecked")
    private static void corruption(Path directory) throws IOException {
        Path file = directory.resolve("corrupt.yml");
        FileModerationRepository repo = new FileModerationRepository(file, logger());
        ModerationRecord sample = new ModerationRecord(id(1), ModerationAction.MUTE, id(90),
                ModerationActor.player(id(80)), "Reason", NOW, Optional.of(NOW.plusSeconds(10)),
                Optional.of(new ModerationRevocation(ModerationActor.player(id(81)), NOW.plusSeconds(1), Optional.of("Appeal"))));
        repo.save(new ModerationSnapshot(List.of(sample)));
        String valid = Files.readString(file);
        for (String yaml : List.of("", "[]", "schema-version: 1\nrecords: [", valid.substring(0, valid.length() / 2),
                valid + "schema-version: 1\n", valid.replace("action: MUTE", "action: MUTE\n  action: BAN"),
                "!!java/object:java.lang.ProcessBuilder {}", valid + "---\nschema-version: 1\nrecords: []\n",
                "schema-version: 1\nrecords: &records [*records]\n",
                "<<: {schema-version: 1, records: []}\n",
                valid.replace("    type: PLAYER", "    <<: {type: PLAYER}"),
                valid.replace("    type: PLAYER", "    type: PLAYER\n    type: CONSOLE"),
                valid.replace("    revoked-at:", "    revoked-at: '2026-09-30T12:00:01.123456789Z'\n    revoked-at:"))) {
            corrupt(file, yaml, "invalid/truncated/duplicate/tag/multidocument YAML");
        }
        // Missing and unknown keys at every nested level, not only root.
        for (int depth = 0; depth < 5; depth++) {
            Map<String, Object> root = tree(valid);
            Map<String, Object> selected = at(root, depth);
            for (String key : new ArrayList<>(selected.keySet())) {
                Map<String, Object> changed = tree(valid);
                at(changed, depth).remove(key);
                corrupt(file, new Yaml().dump(changed), "missing depth " + depth + " key " + key);
            }
            selected.put("extra", "unexpected");
            corrupt(file, new Yaml().dump(root), "unknown key at depth " + depth);
            root = tree(valid);
            ((Map<Object, Object>) (Map<?, ?>) at(root, depth)).put(1, "non-string-key");
            corrupt(file, new Yaml().dump(root), "non-string key at depth " + depth);
        }
        for (Object value : List.of(2, "1", 1.0, true)) altered(file, valid, 0, "schema-version", value, "invalid schema");
        altered(file, valid, 0, "schema-version", null, "null schema");
        for (Object value : List.of("bad", 1, true, Map.of())) altered(file, valid, 0, "records", value, "invalid records");
        altered(file, valid, 0, "records", null, "null records");
        altered(file, valid, 0, "records", List.of("bad"), "non-map record");
        altered(file, valid, 0, "records", Arrays.asList((Object) null), "null record");
        Map<String, Object> duplicates = tree(valid);
        ((List<Object>) duplicates.get("records")).add(((List<Object>) duplicates.get("records")).getFirst());
        corrupt(file, new Yaml().dump(duplicates), "duplicate record ID");
        for (String key : List.of("id", "target", "action", "reason", "created-at")) {
            for (Object value : List.of(1, true, List.of(), Map.of())) altered(file, valid, 1, key, value, "wrong field type " + key);
            altered(file, valid, 1, key, null, "null required field " + key);
        }
        for (String key : List.of("id", "target")) {
            for (String value : List.of("not-a-uuid", "1-1-1-1-1", id(1) + " ", " " + id(1))) {
                altered(file, valid, 1, key, value, "noncanonical UUID " + key);
            }
        }
        altered(file, valid, 1, "action", "FREEZE", "unknown action");
        altered(file, valid, 1, "action", "mute", "wrong action case");
        for (String reason : List.of("", " ", "\nReason", "a\tb", "a\u0085b", "a\u2028b", "😀".repeat(257))) {
            altered(file, valid, 1, "reason", reason, "invalid persisted reason");
            altered(file, valid, 3, "reason", reason, "invalid revoke reason");
        }
        for (int depth : List.of(2, 4)) {
            altered(file, valid, depth, "type", "SYSTEM", "unknown actor type");
            altered(file, valid, depth, "type", 1, "wrong actor type");
            altered(file, valid, depth, "player", null, "PLAYER missing UUID");
            altered(file, valid, depth, "player", "1-1-1-1-1", "actor UUID noncanonical");
            altered(file, valid, depth, "player", 3, "actor UUID wrong type");
            altered(file, valid, depth, "type", "CONSOLE", "CONSOLE with UUID");
        }
        for (String key : List.of("actor", "revocation")) {
            for (Object value : List.of(1, "bad", List.of())) altered(file, valid, 1, key, value, "wrong nested map " + key);
        }
        altered(file, valid, 1, "actor", null, "null actor");
        altered(file, valid, 3, "actor", null, "null revoker");
        for (String key : List.of("created-at", "expires-at")) {
            for (Object value : List.of(1, 1.5, true, "bad", "2026-09-30T13:00:00+01:00", "2026-09-30",
                    "2026-02-30T12:00:00Z", "2026-09-30T12:00:00.1234567890Z")) {
                altered(file, valid, 1, key, value, "invalid timestamp " + key);
            }
        }
        for (Object value : List.of(1, true, "bad", "2026-02-30T12:00:00Z")) {
            altered(file, valid, 3, "revoked-at", value, "invalid revoked timestamp");
        }
        altered(file, valid, 3, "revoked-at", null, "null revoke time");
        altered(file, valid, 3, "reason", 1, "revoke reason wrong type");
        altered(file, valid, 1, "expires-at", NOW.toString(), "equal expiry");
        altered(file, valid, 1, "expires-at", NOW.minusNanos(1).toString(), "past expiry");
        altered(file, valid, 3, "revoked-at", NOW.minusNanos(1).toString(), "revoke before creation");
        for (String action : List.of("WARNING", "KICK")) {
            Map<String, Object> root = tree(valid);
            at(root, 1).put("action", action);
            at(root, 1).put("revocation", null);
            corrupt(file, new Yaml().dump(root), action + " with expiry");
            root = tree(valid);
            at(root, 1).put("action", action);
            at(root, 1).put("expires-at", null);
            corrupt(file, new Yaml().dump(root), action + " with revoke");
        }
        // Upper-case UUID input is canonical ignoring case and normalizes on the next save.
        UUID uppercase = UUID.fromString("00000000-0000-0000-0000-000000abcdef");
        Files.writeString(file, valid.replace(id(80).toString(), uppercase.toString().toUpperCase(java.util.Locale.ROOT)));
        check(repo.initialize().records().getFirst().actor().playerId().orElseThrow().equals(uppercase),
                "uppercase canonical actor UUID accepted");
        corrupt(file, "schema-version: 9\nrecords: []\n", "corrupt service constructor");
        boolean failed = false;
        try { new ModerationService(repo, Clock.fixed(NOW, ZoneOffset.UTC), UUID::randomUUID); }
        catch (ModerationRepositoryException expected) { failed = true; }
        check(failed, "constructor cannot publish empty state on corrupt load");
        Files.delete(file);
        Files.createDirectory(file);
        expectFailure(repo::initialize, "directory instead of data file");
        Files.delete(file);
    }

    private static void atomicWrites(Path directory) throws IOException {
        Path file = directory.resolve("atomic").resolve("moderation.yml").toAbsolutePath().normalize();
        ModerationSnapshot first = new ModerationSnapshot(List.of(new ModerationRecord(id(1), ModerationAction.WARNING,
                id(90), CONSOLE, "First", NOW, Optional.empty(), Optional.empty())));
        ModerationSnapshot next = new ModerationSnapshot(List.of(new ModerationRecord(id(2), ModerationAction.KICK,
                id(90), CONSOLE, "Next", NOW, Optional.empty(), Optional.empty())));
        int[] moves = {0};
        FileModerationRepository normal = new FileModerationRepository(file, logger(), (source, target, options) -> {
            moves[0]++;
            check(source.getParent().equals(target.getParent()) && Files.isRegularFile(source), "sibling temporary file");
            check(List.of(options).contains(StandardCopyOption.ATOMIC_MOVE)
                    && List.of(options).contains(StandardCopyOption.REPLACE_EXISTING), "atomic replace requested");
            check(new FileModerationRepository(source, logger()).initialize().equals(first), "closed complete candidate readable before move");
            Files.move(source, target, options);
        });
        normal.save(first);
        check(moves[0] == 1 && normal.initialize().equals(first), "successful atomic replace");
        FileModerationRepository fallback = new FileModerationRepository(file, logger(), (source, target, options) -> {
            moves[0]++;
            if (List.of(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "injected");
            }
            check(List.of(options).equals(List.of(StandardCopyOption.REPLACE_EXISTING)), "controlled fallback options");
            Files.move(source, target, options);
        });
        fallback.save(next);
        check(moves[0] == 3 && fallback.initialize().equals(next) && noTemps(file), "fallback replaces and cleans up");
        for (int failureMode = 0; failureMode < 3; failureMode++) {
            int mode = failureMode;
            byte[] before = Files.readAllBytes(file);
            FileModerationRepository failed = new FileModerationRepository(file, logger(), (source, target, options) -> {
                if (mode == 1 && List.of(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                    throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "injected");
                }
                if (mode == 2) throw new IllegalStateException("injected runtime mover failure");
                throw new IOException("injected move failure");
            });
            expectFailure(() -> failed.save(first), "failed replacement mode " + mode);
            check(Arrays.equals(before, Files.readAllBytes(file)), "failed move preserves exact previous bytes " + mode);
            check(fallback.initialize().equals(next), "old file still valid/readable " + mode);
            check(noTemps(file), "failed move cleans temp " + mode);
        }
        Path parentFile = directory.resolve("not-directory");
        Files.writeString(parentFile, "marker");
        expectFailure(() -> new FileModerationRepository(parentFile.resolve("moderation.yml"), logger()).save(first),
                "invalid parent");
        check(Files.readString(parentFile).equals("marker"), "invalid parent unchanged");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> tree(String yaml) {
        return (Map<String, Object>) new Yaml(new SafeConstructor(new LoaderOptions())).load(yaml);
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> at(Map<String, Object> root, int depth) {
        Map<String, Object> record = (Map<String, Object>) ((List<?>) root.get("records")).getFirst();
        return switch (depth) {
            case 0 -> root;
            case 1 -> record;
            case 2 -> (Map<String, Object>) record.get("actor");
            case 3 -> (Map<String, Object>) record.get("revocation");
            case 4 -> (Map<String, Object>) ((Map<?, ?>) record.get("revocation")).get("actor");
            default -> throw new AssertionError();
        };
    }
    private static void altered(Path file, String valid, int depth, String key, Object value, String message) throws IOException {
        Map<String, Object> root = tree(valid);
        at(root, depth).put(key, value);
        corrupt(file, new Yaml().dump(root), message);
    }
    private static void corrupt(Path file, String yaml, String message) throws IOException {
        Files.writeString(file, yaml, StandardCharsets.UTF_8);
        byte[] before = Files.readAllBytes(file);
        expectFailure(() -> new FileModerationRepository(file, logger()).initialize(), message);
        check(Arrays.equals(before, Files.readAllBytes(file)), "load leaves corrupt input byte-identical: " + message);
    }
    private static void expectFailure(Runnable operation, String message) {
        boolean failed = false;
        try { operation.run(); }
        catch (ModerationRepositoryException expected) {
            failed = true;
            check(expected.getMessage().contains("moderation") || expected.getMessage().contains("corrupt.yml"),
                    "repository exception has file context");
        }
        check(failed, message);
    }
    private static boolean noTemps(Path file) throws IOException {
        try (var files = Files.list(file.getParent())) {
            return files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp"));
        }
    }
    private static Logger logger() {
        Logger logger = Logger.getLogger("ModerationPersistenceHarness-" + UUID.randomUUID());
        logger.setUseParentHandlers(false);
        return logger;
    }
    private static UUID id(long value) { return new UUID(0, value); }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
