package dev.vapee.core.config;

import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Actual node evolution for a fixture-only 1 -> 2 revision, never a production version bump. */
public final class ConfigEvolutionExtensionHarness {
    private static final String TEST_EXTENSION_OPERATOR_FLAG = "example.additional-flag";
    private static final String DEFAULTS =
            """
            config-version: 2
            example:
              enabled: true
              additional-flag: false
              retained: default
              zero: 42
              blank: default
              empty-list: [default]
              list: [default]
              map: {added: 42}
              explicit-null: true
              conflict: {flag: true}
            """;
    private static final byte[] ORIGINAL =
            ("# Operator Gr\u00fc\u00dfe\r\nconfig-version: 1 # version note\r\n"
                            + "example:\r\n  enabled: false # intentional false\r\n  retained: '007'\r\n"
                            + "  zero: 0\r\n  blank: ''\r\n  empty-list: []\r\n  list: [custom, second]\r\n"
                            + "  map: {custom: value}\r\n  explicit-null: null\r\n  conflict: operator-scalar\r\n"
                            + "unknown: {nested: [one, null, 3]}\r\n")
                    .getBytes(StandardCharsets.UTF_8);
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("vapeecore-config-extension-");
        try {
            additiveRevision(root.resolve("revision"));
            versions(root.resolve("versions"));
            for (Fault fault : Fault.values()) failure(root.resolve(fault.name()), fault);
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
        System.out.println("ConfigEvolutionExtensionHarness passed " + checks + " checks.");
    }

    private static void additiveRevision(Path root) throws Exception {
        Files.createDirectories(root);
        Path file = root.resolve("config.yml");
        Files.write(file, ORIGINAL);
        var access = new Access(root, Fault.NONE);
        var manifest = new HashMap<>(Map.of("config.yml", 2));
        var evolution =
                new ConfigEvolution(
                        root,
                        logger(new ArrayList<>()),
                        name -> {
                            access.resourceReads++;
                            return new ByteArrayInputStream(
                                    DEFAULTS.getBytes(StandardCharsets.UTF_8));
                        },
                        access,
                        manifest);
        manifest.put("config.yml", 9);
        check(evolution.evolve("config.yml"), "fixture version 1 -> 2 applies");
        var yaml = yaml(file);
        check(
                yaml.get("config-version").equals(2),
                "supplied version manifest is defensively copied");
        var example = (Map<?, ?>) yaml.get("example");
        check(
                example.get("additional-flag").equals(false),
                TEST_EXTENSION_OPERATOR_FLAG + " receives false default");
        check(example.get("enabled").equals(false), "existing false preserved independently");
        check(
                example.get("zero").equals(0) && example.get("blank").equals(""),
                "operator zero/empty string preserved");
        check(
                example.get("retained").equals("007"),
                "quoted numeric-looking string retains type/value");
        check(
                example.get("empty-list").equals(List.of())
                        && example.get("list").equals(List.of("custom", "second")),
                "empty/existing lists not replaced or appended");
        check(
                example.containsKey("explicit-null") && example.get("explicit-null") == null,
                "explicit null preserved");
        check(
                example.get("conflict").equals("operator-scalar"),
                "type conflict remains operator owned");
        check(
                example.get("map").equals(Map.of("custom", "value", "added", 42)),
                "nested map extends without losing unknown key");
        check(
                yaml.get("unknown")
                        .equals(
                                new Yaml()
                                        .<Map<?, ?>>load(
                                                new String(ORIGINAL, StandardCharsets.UTF_8))
                                        .get("unknown")),
                "unknown nested subtree preserved");
        String candidate = Files.readString(file);
        check(
                candidate.contains("Operator Gr\u00fc\u00dfe")
                        && candidate.contains("version note")
                        && candidate.contains("intentional false"),
                "header/version/inline comments preserved");
        List<Path> backups = backups(root);
        check(
                backups.size() == 1
                        && backups.getFirst().getFileName().toString().contains(".v1-to-v2-"),
                "actual version 1 -> 2 backup transition");
        check(
                Arrays.equals(ORIGINAL, Files.readAllBytes(backups.getFirst())),
                "exact CRLF/UTF-8 original backup");
        byte[] first = Files.readAllBytes(file);
        check(!evolution.evolve("config.yml"), "second migration is skipped");
        check(
                Arrays.equals(first, Files.readAllBytes(file))
                        && backups(root).size() == 1
                        && access.writes == 1
                        && access.resourceReads == 1,
                "current version performs no candidate write/backup/default read");
        Path firstBackup = backups.getFirst();
        Files.write(file, ORIGINAL);
        check(
                evolution.evolve("config.yml") && backups(root).size() == 2,
                "reinstalled legacy gets unique backup");
        check(
                Arrays.equals(ORIGINAL, Files.readAllBytes(firstBackup)),
                "existing operator backup never overwritten");
    }

    private static void versions(Path root) throws Exception {
        Files.createDirectories(root);
        var absent = new Access(root, Fault.NONE);
        check(
                !evolution(root, absent).evolve("config.yml")
                        && !Files.exists(root.resolve("config.yml"))
                        && absent.resourceReads == 0,
                "new installation belongs to default loader");
        for (String version :
                List.of(
                        "3",
                        "99",
                        "2",
                        "-1",
                        "'2'",
                        "true",
                        "null",
                        "[]",
                        "999999999999999999999")) {
            Path scenario = root.resolve("case-" + version.hashCode());
            Files.createDirectories(scenario);
            Path file = scenario.resolve("config.yml");
            byte[] source =
                    ("config-version: " + version + "\nexample: {enabled: false}\n")
                            .getBytes(StandardCharsets.UTF_8);
            Files.write(file, source);
            var access = new Access(scenario, Fault.NONE);
            access.expectedOriginal = source;
            check(
                    !evolution(scenario, access).evolve("config.yml"),
                    "future/current/invalid version refused: " + version);
            check(
                    Arrays.equals(source, Files.readAllBytes(file))
                            && backups(scenario).isEmpty()
                            && access.writes == 0
                            && access.resourceReads == 0,
                    "refusal has exact byte/no-backup isolation: " + version);
        }
        Path unversioned = root.resolve("unversioned");
        Files.createDirectories(unversioned);
        Files.writeString(unversioned.resolve("config.yml"), "example: {enabled: false}\n");
        var access = new Access(unversioned, Fault.NONE);
        access.expectedOriginal = Files.readAllBytes(unversioned.resolve("config.yml"));
        check(
                evolution(unversioned, access).evolve("config.yml")
                        && backups(unversioned)
                                .getFirst()
                                .getFileName()
                                .toString()
                                .contains(".v0-to-v2-"),
                "separate unversioned -> 2 fixture transition");
    }

    private static void failure(Path root, Fault fault) throws Exception {
        Files.createDirectories(root);
        Path file = root.resolve("config.yml");
        Files.write(file, ORIGINAL);
        var logs = new ArrayList<LogRecord>();
        var access = new Access(root, fault);
        ConfigEvolution evolution =
                new ConfigEvolution(
                        root,
                        logger(logs),
                        name -> {
                            access.resourceReads++;
                            return new ByteArrayInputStream(
                                    DEFAULTS.getBytes(StandardCharsets.UTF_8));
                        },
                        access,
                        Map.of("config.yml", 2));
        if (fault == Fault.NONE || fault == Fault.FALLBACK_SUCCESS) {
            check(
                    evolution.evolve("config.yml") && yaml(file).get("config-version").equals(2),
                    "successful replacement " + fault);
            if (fault == Fault.FALLBACK_SUCCESS)
                check(access.fallback == 1, "actual fallback replacement executed");
        } else {
            Throwable failure =
                    expectFailure(
                            () -> evolution.evolve("config.yml"),
                            "injected failure propagates " + fault);
            if (fault == Fault.CONCURRENT) {
                check(
                        Files.readString(file).contains("concurrent edit") && access.atomic == 0,
                        "concurrent operator edit wins before replacement");
            } else if (fault == Fault.RESTORE_FAIL) {
                check(
                        failure.getCause() == access.failure
                                && Arrays.asList(access.failure.getSuppressed())
                                        .contains(access.restoreFailure),
                        "incomplete restoration retains primary and suppressed failure");
                check(
                        logs.stream()
                                .anyMatch(
                                        log ->
                                                log.getLevel() == Level.SEVERE
                                                        && log.getThrown() == access.restoreFailure
                                                        && log.getMessage()
                                                                .contains(
                                                                        backupsUnchecked(root)
                                                                                .getFirst()
                                                                                .toString())
                                                        && log.getMessage()
                                                                .contains(
                                                                        "Manual reconciliation required")),
                        "incomplete restoration names retained backup explicitly");
                check(
                        Files.readString(file).equals("partial destination"),
                        "incomplete recovery never pretends target restored");
            } else {
                check(
                        Arrays.equals(ORIGINAL, Files.readAllBytes(file)),
                        "failure preserves/restores original target " + fault);
            }
            if (fault == Fault.FALLBACK_FAIL)
                check(
                        access.restores == 1,
                        "damaged non-atomic destination restored through real copy");
        }
        check(
                backups(root).size() == 1
                        && Arrays.equals(ORIGINAL, Files.readAllBytes(backups(root).getFirst())),
                "exact backup survives success/failure " + fault);
        try (var paths = Files.list(root)) {
            check(
                    paths.noneMatch(path -> path.toString().endsWith(".tmp")),
                    "candidate cleaned " + fault);
        }
    }

    private enum Fault {
        NONE,
        WRITE,
        CORRUPT,
        ATOMIC,
        FALLBACK_SUCCESS,
        FALLBACK_FAIL,
        RESTORE_FAIL,
        CONCURRENT
    }

    private static final class Access extends ConfigEvolution.FileAccess {
        final Path root;
        final Fault fault;
        final IOException failure = new IOException("injected evolution I/O failure");
        final IOException restoreFailure = new IOException("injected restore failure");
        byte[] expectedOriginal = ORIGINAL;
        int writes;
        int atomic;
        int fallback;
        int restores;
        int resourceReads;

        Access(Path root, Fault fault) {
            this.root = root;
            this.fault = fault;
        }

        @Override
        void write(Path temporary, byte[] candidate) throws IOException {
            writes++;
            var backups = backups(root);
            check(
                    !backups.isEmpty()
                            && Arrays.equals(
                                    expectedOriginal, Files.readAllBytes(backups.getLast())),
                    "exact backup exists before candidate write");
            if (fault == Fault.WRITE) throw failure;
            super.write(temporary, fault == Fault.CORRUPT ? new byte[] {1, 2, 3} : candidate);
            if (fault == Fault.CONCURRENT)
                Files.writeString(
                        root.resolve("config.yml"), "# concurrent edit\nconfig-version: 1\n");
        }

        @Override
        void replace(Path temporary, Path target, boolean atomically) throws IOException {
            if (atomically) {
                atomic++;
                if (fault == Fault.ATOMIC) throw failure;
                if (List.of(Fault.FALLBACK_SUCCESS, Fault.FALLBACK_FAIL, Fault.RESTORE_FAIL)
                        .contains(fault)) {
                    throw new AtomicMoveNotSupportedException(
                            temporary.toString(), target.toString(), "fixture unsupported");
                }
            } else {
                fallback++;
                if (fault == Fault.FALLBACK_FAIL || fault == Fault.RESTORE_FAIL) {
                    Files.writeString(target, "partial destination");
                    throw failure;
                }
            }
            super.replace(temporary, target, atomically);
        }

        @Override
        void restore(Path backup, Path target) throws IOException {
            restores++;
            if (fault == Fault.RESTORE_FAIL) throw restoreFailure;
            super.restore(backup, target);
        }
    }

    private static ConfigEvolution evolution(Path root, Access access) {
        return new ConfigEvolution(
                root,
                logger(new ArrayList<>()),
                name -> {
                    access.resourceReads++;
                    return new ByteArrayInputStream(DEFAULTS.getBytes(StandardCharsets.UTF_8));
                },
                access,
                Map.of("config.yml", 2));
    }

    private static Map<?, ?> yaml(Path file) throws IOException {
        return new Yaml().load(Files.readString(file));
    }

    private static List<Path> backups(Path root) throws IOException {
        Path directory = root.resolve("backups/config");
        if (!Files.exists(directory)) return List.of();
        try (var paths = Files.list(directory)) {
            return paths.sorted().toList();
        }
    }

    private static List<Path> backupsUnchecked(Path root) {
        try {
            return backups(root);
        } catch (IOException failure) {
            throw new AssertionError(failure);
        }
    }

    private static Logger logger(List<LogRecord> logs) {
        var logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        logs.add(record);
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                });
        return logger;
    }

    private static Throwable expectFailure(Runnable action, String reason) {
        try {
            action.run();
            throw new AssertionError(reason);
        } catch (IllegalStateException expected) {
            check(true, reason);
            return expected;
        }
    }

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
}
