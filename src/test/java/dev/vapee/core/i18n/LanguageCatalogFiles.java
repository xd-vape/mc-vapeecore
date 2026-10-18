package dev.vapee.core.i18n;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.Arrays;

/** Operator tooling only: all YAML interpretation belongs to the production catalog reader. */
public final class LanguageCatalogFiles {
    private LanguageCatalogFiles() { }

    public static Map<String, String> read(Path file) throws IOException {
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return TranslationCatalog.read(reader);
        }
    }

    // Sorted Base64(key), TAB, Base64(value), LF: lossless even for escapes/newlines/spaces.
    public static String snapshot(Map<String, String> values) {
        var encoder = Base64.getEncoder();
        var output = new StringBuilder();
        new TreeMap<>(values).forEach((key, value) -> output
                .append(encoder.encodeToString(key.getBytes(StandardCharsets.UTF_8))).append('\t')
                .append(encoder.encodeToString(value.getBytes(StandardCharsets.UTF_8))).append('\n'));
        return output.toString();
    }

    public static String fingerprint(Map<String, String> values) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(snapshot(values).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    public static Map<String, String> readSnapshot(Path file) throws IOException {
        var values = new TreeMap<String, String>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            var parts = line.split("\t", -1);
            if (parts.length != 2) throw new IllegalArgumentException("Invalid catalog snapshot");
            var decoder = Base64.getDecoder();
            String key = new String(decoder.decode(parts[0]), StandardCharsets.UTF_8);
            String value = new String(decoder.decode(parts[1]), StandardCharsets.UTF_8);
            if (values.putIfAbsent(key, value) != null) throw new IllegalArgumentException("Duplicate snapshot key");
        }
        return Map.copyOf(values);
    }

    public static void requireEquivalent(Map<String, String> before, Map<String, String> after) {
        long missing = before.keySet().stream().filter(key -> !after.containsKey(key)).count();
        long added = after.keySet().stream().filter(key -> !before.containsKey(key)).count();
        long changed = before.entrySet().stream().filter(entry -> after.containsKey(entry.getKey())
                && !entry.getValue().equals(after.get(entry.getKey()))).count();
        if (missing != 0 || added != 0 || changed != 0)
            throw new IllegalArgumentException("Catalog differs: missing=" + missing + ", added=" + added + ", changed=" + changed);
    }

    /** No YAML writer: copies the operator's validated candidate bytes, including its comments. */
    public static Path adopt(Path live, Path candidate, boolean apply) throws IOException {
        if (Files.isSameFile(live, candidate)) throw new IllegalArgumentException("Use a separate candidate file");
        byte[] original = Files.readAllBytes(live), proposed = Files.readAllBytes(candidate);
        requireEquivalent(readBytes(original), readBytes(proposed));
        if (!apply) return null;
        Path parent = live.toAbsolutePath().getParent();
        Path temporary = Files.createTempFile(parent, ".language-candidate-", ".tmp");
        Path backup = null;
        try {
            Files.write(temporary, proposed);
            if (!Arrays.equals(original, Files.readAllBytes(live))) throw new IOException("Live catalog changed during validation");
            backup = Files.createTempFile(parent, live.getFileName() + ".before-organization-", ".bak");
            Files.write(backup, original);
            if (!Arrays.equals(original, Files.readAllBytes(backup))) throw new IOException("Backup verification failed");
            if (!Arrays.equals(original, Files.readAllBytes(live))) throw new IOException("Live catalog changed before adoption");
            // Fail closed if this filesystem cannot perform an atomic replacement. No copy fallback.
            Files.move(temporary, live, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            return backup;
        } catch (IOException exception) {
            throw new IOException("Catalog adoption failed; backup=" + backup, exception);
        } finally { Files.deleteIfExists(temporary); }
    }

    private static Map<String, String> readBytes(byte[] bytes) throws IOException {
        try (var reader = new InputStreamReader(new ByteArrayInputStream(bytes), StandardCharsets.UTF_8.newDecoder())) {
            return TranslationCatalog.read(reader);
        }
    }

    public static void main(String[] args) throws IOException {
        if (args.length != 3) throw new IllegalArgumentException(
                "Usage: snapshot <catalog> <new-snapshot> | compare <snapshot> <catalog> | equivalent <before-yaml> <after-yaml> | parity <en-yaml> <de-yaml> | preview|adopt <live-yaml> <candidate-yaml>");
        Path first = Path.of(args[1]), second = Path.of(args[2]);
        Map<String, String> values;
        switch (args[0]) {
            case "snapshot" -> {
                values = read(first);
                Files.writeString(second, snapshot(values), StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            }
            case "compare" -> {
                values = read(second);
                requireEquivalent(readSnapshot(first), values);
            }
            case "equivalent" -> {
                values = read(second);
                requireEquivalent(read(first), values);
            }
            case "parity" -> {
                values = read(second);
                TranslationCatalog.validateParity(read(first), values, true);
            }
            case "preview", "adopt" -> {
                var backup = adopt(first, second, args[0].equals("adopt"));
                values = read(args[0].equals("adopt") ? first : second);
                System.out.println(backup == null ? "Preview only; live file unchanged" : "Exact-byte backup: " + backup);
            }
            default -> throw new IllegalArgumentException("Unknown catalog validation mode");
        }
        System.out.println("Keys=" + values.size() + " SHA256=" + fingerprint(values));
    }
}
