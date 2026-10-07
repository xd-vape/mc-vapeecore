package dev.vapee.core.config;

import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Exercises the production node merge and actual disk writes, including recoverable I/O failures. */
public final class ConfigEvolutionHarness {
    private static int checks;
    private static final String DEFAULTS = """
            # Operator schema
            config-version: 1
            enabled: true
            section:
              kept: default
              added: 42
              nested:
                flag: true
            list: [default, second]
            new-list: [one, two]
            """;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("vapeecore-config-evolution-");
        try {
            testMerge(root.resolve("merge"));
            testVersions(root.resolve("versions"));
            testFailures(root.resolve("failures"));
            testAllowlistAndResources(root.resolve("resources"));
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
        System.out.println("ConfigEvolutionHarness passed " + checks + " checks.");
    }

    private static void testMerge(Path root) throws Exception {
        Files.createDirectories(root);
        String original = """
                # My operator header
                enabled: false # my inline comment
                section:
                  # Keep my custom nested value
                  kept: operator
                  nested:
                    custom: extra
                list: [custom]
                unknown-null: null
                unknown-list: []
                unknown-map: {}
                unknown-string: '007'
                """;
        Path file = root.resolve("chat.yml");
        Files.writeString(file, original);
        var evolution = evolution(root, DEFAULTS, new ConfigEvolution.FileAccess());
        check(evolution.evolve("chat.yml"), "legacy config migrated");
        Map<?, ?> actual = yaml(file);
        check(actual.get("config-version").equals(1), "version advanced");
        check(actual.get("enabled").equals(false), "existing false preserved");
        Map<?, ?> section = (Map<?, ?>) actual.get("section");
        check(section.get("kept").equals("operator"), "custom string preserved");
        check(section.get("added").equals(42), "missing nested default added");
        Map<?, ?> nested = (Map<?, ?>) section.get("nested");
        check(nested.get("flag").equals(true) && nested.get("custom").equals("extra"), "recursive merge preserves unknown nested key");
        check(actual.get("list").equals(List.of("custom")), "existing list not appended or replaced");
        check(actual.get("new-list").equals(List.of("one", "two")), "missing list copied atomically");
        check(actual.containsKey("unknown-null") && actual.get("unknown-null") == null, "unknown null retained");
        check(actual.get("unknown-list").equals(List.of()) && actual.get("unknown-map").equals(Map.of()), "empty unknown collection values retained");
        check(actual.get("unknown-string").equals("007"), "quoted scalar type retained");
        String saved = Files.readString(file);
        for (String comment : List.of("My operator header", "my inline comment", "Keep my custom nested value")) {
            check(saved.contains(comment), "operator comment survives: " + comment);
        }
        List<Path> backups = backups(root);
        check(backups.size() == 1 && Arrays.equals(Files.readAllBytes(backups.getFirst()), original.getBytes(StandardCharsets.UTF_8)), "one exact-byte pre-migration backup");
        byte[] first = Files.readAllBytes(file);
        check(!evolution.evolve("chat.yml"), "second migration is skipped");
        check(Arrays.equals(first, Files.readAllBytes(file)) && backups(root).size() == 1, "idempotent bytes and backup count");
        // An explicit legacy file installed again gets a distinct backup, never overwrites the first receipt.
        Files.writeString(file, "enabled: false\n");
        check(evolution.evolve("chat.yml") && backups(root).size() == 2, "unique second legacy backup");
        check(Arrays.equals(Files.readAllBytes(backups.getFirst()), original.getBytes(StandardCharsets.UTF_8)), "older backup untouched");

        Files.writeString(file, "section: operator-scalar\nlist: []\nenabled: 'wrong-type'\n");
        check(evolution.evolve("chat.yml"), "type-conflicting legacy file still evolves missing peers");
        actual = yaml(file);
        check(actual.get("section").equals("operator-scalar") && actual.get("enabled").equals("wrong-type"), "type conflict never overwrites operator values");
        check(actual.get("list").equals(List.of()), "empty existing list preserved");
    }

    private static void testVersions(Path root) throws Exception {
        Files.createDirectories(root);
        Path file = root.resolve("config.yml");
        var evolution = evolution(root, DEFAULTS, new ConfigEvolution.FileAccess());
        check(!evolution.evolve("config.yml") && !Files.exists(file), "new installation is left to owning default loader");
        for (String version : List.of("2", "999999999999999999999", "-1", "'1'", "true", "null")) {
            String source = "# version operator comment\nconfig-version: " + version + "\nenabled: false\n";
            Files.writeString(file, source);
            check(!evolution.evolve("config.yml"), "future/invalid version skipped " + version);
            check(Files.readString(file).equals(source) && backups(root).isEmpty(), "future/invalid file exact bytes and no backup " + version);
        }
        Files.writeString(file, "config-version: 1 # keep version note\nenabled: false\n");
        check(!evolution.evolve("config.yml") && !yaml(file).containsKey("section"), "supported version no startup rewrite even with deleted keys");
        Files.writeString(file, "config-version: 0 # keep version note\nenabled: false\n");
        check(evolution.evolve("config.yml") && Files.readString(file).contains("keep version note"), "explicit legacy version comment retained");
        Files.writeString(file, "enabled: [broken\n");
        byte[] invalid = Files.readAllBytes(file);
        int count = backups(root).size();
        expectFailure(() -> evolution.evolve("config.yml"), "invalid YAML rejected");
        check(Arrays.equals(invalid, Files.readAllBytes(file)) && backups(root).size() == count, "invalid input causes no backup/write");
        Files.writeString(file, "enabled: false\nenabled: true\n");
        expectFailure(() -> evolution.evolve("config.yml"), "duplicate keys rejected without lossy rewrite");
        Files.writeString(file, "unknown: &loop\n  again: *loop\n");
        String recursive = Files.readString(file);
        expectFailure(() -> evolution.evolve("config.yml"), "recursive alias rejected safely");
        check(Files.readString(file).equals(recursive), "recursive alias input remains recoverable");
        Files.writeString(file, "unknown: &base {label: custom}\ncopy: *base\n");
        check(evolution.evolve("config.yml"), "ordinary aliases are preserved by node serialization");
        check(yaml(file).get("unknown").equals(yaml(file).get("copy")), "ordinary alias values retained");
    }

    private static void testFailures(Path root) throws Exception {
        Files.createDirectories(root);
        Path file = root.resolve("chat.yml");
        byte[] original = "enabled: false\n".getBytes(StandardCharsets.UTF_8);
        Files.write(file, original);
        var writeFailure = new ConfigEvolution.FileAccess() {
            @Override void write(Path target, byte[] content) throws IOException { throw new IOException("injected write failure"); }
        };
        expectFailure(() -> evolution(root, DEFAULTS, writeFailure).evolve("chat.yml"), "candidate write failure propagates");
        check(Arrays.equals(original, Files.readAllBytes(file)), "failed write leaves original intact");
        check(backups(root).size() == 1 && Arrays.equals(original, Files.readAllBytes(backups(root).getFirst())), "backup recoverable after failed write");
        check(noTemporary(root), "failed candidate cleaned up");
        var corruptFallback = new ConfigEvolution.FileAccess() {
            @Override void replace(Path temporary, Path target, boolean atomic) throws IOException {
                if (atomic) throw new AtomicMoveNotSupportedException(temporary.toString(), target.toString(), "injected");
                Files.writeString(target, "partial");
                throw new IOException("injected non-atomic failure");
            }
        };
        expectFailure(() -> evolution(root, DEFAULTS, corruptFallback).evolve("chat.yml"), "failed fallback propagates");
        check(Arrays.equals(original, Files.readAllBytes(file)) && backups(root).size() == 2, "failed fallback restores exact original and keeps backups");
        check(noTemporary(root), "fallback temporary cleaned up");
        var nonAtomic = new ConfigEvolution.FileAccess() {
            @Override void replace(Path temporary, Path target, boolean atomic) throws IOException {
                if (atomic) throw new AtomicMoveNotSupportedException(temporary.toString(), target.toString(), "injected");
                super.replace(temporary, target, false);
            }
        };
        check(evolution(root, DEFAULTS, nonAtomic).evolve("chat.yml") && yaml(file).get("enabled").equals(false), "recoverable fallback success uses production merge");
        Files.write(file, original);
        var changed = new ConfigEvolution.FileAccess() {
            @Override void write(Path temporary, byte[] content) throws IOException {
                super.write(temporary, content);
                Files.writeString(file, "enabled: true # concurrent operator edit\n");
            }
        };
        expectFailure(() -> evolution(root, DEFAULTS, changed).evolve("chat.yml"), "concurrent edit rejected");
        check(Files.readString(file).contains("concurrent operator edit"), "concurrent operator edit not clobbered");
    }

    private static void testAllowlistAndResources(Path root) throws Exception {
        Files.createDirectories(root);
        var evolution = new ConfigEvolution(root, logger(), name -> ConfigEvolutionHarness.class.getResourceAsStream("/" + name));
        check(ConfigEvolution.MANAGED_CONFIGS.size() == 6, "exact six operator config allowlist");
        for (String name : ConfigEvolution.MANAGED_CONFIGS.keySet()) Files.writeString(root.resolve(name), "# operator custom\noperator-extra: null\n");
        Map<String, byte[]> excluded = new java.util.LinkedHashMap<>();
        for (String name : List.of("warps.yml", "blackjack.yml", "players/a.yml", "friends.yml", "clans.yml", "moderation.yml")) {
            Path file = root.resolve(name); Files.createDirectories(file.getParent());
            byte[] bytes = "runtime: preserved\n".getBytes(StandardCharsets.UTF_8); Files.write(file, bytes); excluded.put(name, bytes);
            try { evolution.evolve(name); throw new AssertionError("persistence accepted " + name); }
            catch (IllegalArgumentException expected) { checks++; }
        }
        evolution.evolveAll();
        for (String name : ConfigEvolution.MANAGED_CONFIGS.keySet()) {
            Map<?, ?> config = yaml(root.resolve(name));
            check(config.get("config-version").equals(ConfigEvolution.MANAGED_CONFIGS.get(name)) && config.containsKey("operator-extra"), "actual resource merged without dropping unknown null " + name);
        }
        for (var entry : excluded.entrySet()) check(Arrays.equals(entry.getValue(), Files.readAllBytes(root.resolve(entry.getKey()))), "persistence byte parity " + entry.getKey());
        check(backups(root).size() == 6, "only operator configs generate backups");
        Map<String, byte[]> first = new java.util.HashMap<>();
        for (String name : ConfigEvolution.MANAGED_CONFIGS.keySet()) first.put(name, Files.readAllBytes(root.resolve(name)));
        evolution.evolveAll();
        for (var entry : first.entrySet()) check(Arrays.equals(entry.getValue(), Files.readAllBytes(root.resolve(entry.getKey()))), "second evolveAll exact byte parity " + entry.getKey());
        check(backups(root).size() == 6, "second evolveAll no extra backups");
    }

    private static ConfigEvolution evolution(Path root, String defaults, ConfigEvolution.FileAccess files) {
        return new ConfigEvolution(root, logger(), name -> new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8)), files);
    }
    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() { public void publish(LogRecord record) { } public void flush() { } public void close() { } });
        return logger;
    }
    private static Map<?, ?> yaml(Path file) throws IOException { return new Yaml().load(Files.readString(file)); }
    private static List<Path> backups(Path root) throws IOException {
        Path directory = root.resolve("backups/config");
        if (!Files.exists(directory)) return List.of();
        try (var paths = Files.list(directory)) { return paths.sorted().toList(); }
    }
    private static boolean noTemporary(Path root) throws IOException {
        try (var paths = Files.list(root)) { return paths.noneMatch(path -> path.toString().endsWith(".tmp")); }
    }
    private static void expectFailure(Runnable task, String label) {
        try { task.run(); throw new AssertionError(label); }
        catch (IllegalStateException expected) { checks++; }
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
