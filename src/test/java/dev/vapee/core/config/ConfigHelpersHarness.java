package dev.vapee.core.config;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class ConfigHelpersHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        resources();
        values();
        System.out.println("ConfigHelpersHarness passed " + checks + " checks.");
    }

    private static void resources() throws Exception {
        Path root = Files.createTempDirectory("vapeecore-config-files-");
        try {
            Path file = root.resolve("nested/config.yml");
            byte[] bytes = "# retain comments\r\nname: 'value'\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            AtomicInteger opens = new AtomicInteger(), closes = new AtomicInteger();
            java.util.function.Supplier<InputStream> supplier = () -> {
                opens.incrementAndGet();
                return new ByteArrayInputStream(bytes) {
                    @Override public void close() throws IOException { closes.incrementAndGet(); super.close(); }
                };
            };
            var missing = new IllegalStateException("caller resource context");
            check(ConfigFiles.copyDefault(file, supplier, () -> missing), "missing file created with parents");
            check(Arrays.equals(bytes, Files.readAllBytes(file)), "resource copied byte-for-byte");
            check(opens.get() == 1 && closes.get() == 1, "stream opened and closed once");
            Files.writeString(file, "administrator edits\n");
            check(!ConfigFiles.copyDefault(file, supplier, () -> missing), "existing file left alone");
            check(Files.readString(file).equals("administrator edits\n") && opens.get() == 1,
                    "existing file neither rewritten nor resource opened");
            Path absent = root.resolve("missing.yml");
            try {
                ConfigFiles.copyDefault(absent, () -> null, () -> missing);
                throw new AssertionError("missing resource accepted");
            } catch (IllegalStateException failure) {
                check(failure == missing && Files.notExists(absent), "caller missing-resource failure preserved");
            }
            IOException copyFailure = new IOException("injected read failure");
            AtomicInteger failedCloses = new AtomicInteger();
            try {
                ConfigFiles.copyDefault(root.resolve("broken.yml"), () -> new InputStream() {
                    @Override public int read() throws IOException { throw copyFailure; }
                    @Override public void close() { failedCloses.incrementAndGet(); }
                }, () -> missing);
                throw new AssertionError("copy failure accepted");
            } catch (IOException failure) {
                check(failure == copyFailure && failedCloses.get() == 1,
                        "copy IOException surfaced and failed stream closed");
            }
            Path parentFile = root.resolve("not-directory");
            Files.writeString(parentFile, "keep");
            try {
                ConfigFiles.copyDefault(parentFile.resolve("config.yml"), supplier, () -> missing);
                throw new AssertionError("invalid parent accepted");
            } catch (IOException expected) {
                check(Files.readString(parentFile).equals("keep"), "directory failure preserves existing parent");
            }
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
    }

    private static void values() throws Exception {
        YamlConfiguration config = new YamlConfiguration();
        AtomicInteger warnings = new AtomicInteger();
        Runnable warn = warnings::incrementAndGet;
        check(ConfigValues.readBoolean(config, "flag", true, warn)
                && !ConfigValues.readBoolean(config, "flag", false, warn) && warnings.get() == 0,
                "missing booleans preserve both caller fallbacks silently");
        for (boolean value : List.of(false, true)) {
            config.set("flag", value);
            check(ConfigValues.readBoolean(config, "flag", !value, warn) == value && warnings.get() == 0,
                    "boolean type accepted without coercion");
        }
        for (Object value : List.of("true", 1, List.of(), java.util.Map.of("nested", true))) {
            config.set("flag", value); warnings.set(0);
            check(ConfigValues.readBoolean(config, "flag", true, warn) && warnings.get() == 1,
                    "invalid boolean warns exactly once");
        }
        warnings.set(0);
        check(ConfigValues.readString(config, "text", "fallback", warn).equals("fallback")
                && warnings.get() == 0, "missing string silently falls back");
        for (String value : List.of("", "   ", " spaced ")) {
            config.set("text", value);
            check(ConfigValues.readString(config, "text", "fallback", warn).equals(value)
                    && warnings.get() == 0, "string reader preserves blank and whitespace");
        }
        for (Object value : List.of(false, 7, List.of(), java.util.Map.of("key", "value"))) {
            config.set("text", value); warnings.set(0);
            check(ConfigValues.readString(config, "text", "fallback", warn).equals("fallback")
                    && warnings.get() == 1, "wrong string type warns once");
        }
        for (Object value : Arrays.asList(null, "", "  ", 42, false, List.of())) {
            warnings.set(0);
            check(ConfigValues.nonBlankString(value, "fallback", warn).equals("fallback")
                    && warnings.get() == 1, "raw null/blank/wrong nonblank string warns once");
        }
        warnings.set(0);
        check(ConfigValues.nonBlankString(" keep spaces ", "fallback", warn).equals(" keep spaces ")
                && warnings.get() == 0, "nonblank reader never trims");
        config.loadFromString("flag: null\ntext: null\n");
        check(!config.contains("flag") && !config.contains("text"),
                "Bukkit's scalar YAML null/missing behavior remains explicit");
        check(ConfigValues.readBoolean(config, "flag", true, warn)
                && ConfigValues.readString(config, "text", "fallback", warn).equals("fallback")
                && warnings.get() == 0, "shared optional readers preserve Bukkit null behavior");
        config.set("staff.hierarchy.protected-groups", false);
        check(config.contains("staff.hierarchy.protected-groups")
                && config.get("staff.hierarchy.protected-groups").equals(false),
                "helpers do not alter caller-retained security null sentinel");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
