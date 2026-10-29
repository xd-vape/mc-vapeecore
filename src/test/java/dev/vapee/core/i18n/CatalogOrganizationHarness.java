package dev.vapee.core.i18n;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class CatalogOrganizationHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        String flat = "clan.owner: 'Owner: '\nclan.owner.transferred: '<player> is owner'\ncustom.caption: 'Ö \\\\ text'\n";
        String nested = "# Operator comment\nclan:\n  owner: 'Owner: '\n  owner.transferred: '<player> is owner'\ncustom:\n  caption: 'Ö \\\\ text'\n";
        var before = parse(flat);
        var after = parse(nested);
        LanguageCatalogFiles.requireEquivalent(before, after);
        check(before.equals(after), "flat and mixed nested/dotted input resolve exactly alike");
        check(before.get("clan.owner").endsWith(" "), "intentional trailing string space survives");
        check(LanguageCatalogFiles.fingerprint(before).equals(LanguageCatalogFiles.fingerprint(after)), "fingerprint ignores YAML presentation/order");
        var multiline = parse("text: \"  <gold>Ö</gold>\\nLine 2\\t \\\\ end  \"\n");
        check(multiline.get("text").equals("  <gold>Ö</gold>\nLine 2\t \\ end  "), "escaped characters and multiline whitespace survive production reader");
        fails(() -> LanguageCatalogFiles.requireEquivalent(before, Map.of()), "missing keys fail exact comparison");
        fails(() -> LanguageCatalogFiles.requireEquivalent(before, Map.of("extra", "Extra")), "added keys fail exact comparison");
        fails(() -> LanguageCatalogFiles.requireEquivalent(Map.of("x", "<gold>X</gold>"), Map.of("x", "<aqua>X</aqua>")), "color-only change fails exact comparison");
        for (String invalid : List.of("x: One\nx: Two\n", "x.y: One\nx:\n  y: Two\n", "clan:\n  owner: Owner\n  owner:\n    transferred: Other\n", "x: [One]\n", "x: 42\n"))
            fails(() -> parse(invalid), "ambiguous/unsupported catalog rejected by production parser");
        fails(() -> TranslationCatalog.validateParity(Map.of("x", "Hi <player>"), Map.of("x", "Hallo"), true), "placeholder loss rejected");

        Path directory = Files.createTempDirectory("vapeecore-catalog-adoption-");
        try {
            Path live = directory.resolve("de.yml"), candidate = directory.resolve("de.candidate.yml"), snapshot = directory.resolve("before.snapshot");
            Path english = directory.resolve("en.yml"); Files.writeString(english, "custom: 'English original'\n");
            byte[] original = ("# Keep original comment\n" + flat).getBytes(StandardCharsets.UTF_8);
            Files.write(live, original); Files.writeString(candidate, nested, StandardCharsets.UTF_8);
            Files.writeString(snapshot, LanguageCatalogFiles.snapshot(multiline), StandardCharsets.UTF_8);
            check(LanguageCatalogFiles.readSnapshot(snapshot).equals(multiline), "snapshot round-trip preserves every string byte logically");
            check(LanguageCatalogFiles.adopt(live, candidate, false) == null, "preview does not create a backup");
            check(Arrays.equals(Files.readAllBytes(live), original), "preview leaves live bytes intact");
            try (var paths = Files.list(directory)) { check(paths.count() == 4, "preview creates no candidate/backup side effects"); }
            Path backup = LanguageCatalogFiles.adopt(live, candidate, true);
            check(Arrays.equals(Files.readAllBytes(backup), original), "apply makes exact original-byte backup including comments");
            check(Files.readString(live).equals(nested), "apply preserves candidate comments/UTF-8 and uses candidate bytes");
            check(LanguageCatalogFiles.read(live).get("custom.caption").equals(before.get("custom.caption")), "unknown custom key retained");
            Files.writeString(candidate, "clan.owner: Changed\n");
            fails(() -> LanguageCatalogFiles.adopt(live, candidate, true), "changed/missing custom values rejected before apply");
            check(Files.readString(live).equals(nested), "rejected apply leaves live intact");
            fails(() -> LanguageCatalogFiles.adopt(live, live, true), "same-file adoption rejected");
            Files.writeString(candidate, "x.y: One\nx:\n  y: Two\n");
            fails(() -> LanguageCatalogFiles.adopt(live, candidate, true), "duplicate flattened candidate rejected before write");
            byte[] invalidUtf8 = {(byte) 0xc3, (byte) 0x28}; Files.write(candidate, invalidUtf8);
            fails(() -> LanguageCatalogFiles.adopt(live, candidate, true), "invalid UTF-8 candidate rejected");
            // Languages are checked independently; a German customization never adopts English defaults.
            fails(() -> LanguageCatalogFiles.adopt(live, english, true), "English defaults cannot replace a customized German catalog");
            check(Files.readString(english).equals("custom: 'English original'\n") && Files.readString(live).equals(nested), "independent operator language values retained");
        } finally {
            try (var files = Files.list(directory)) { for (Path file : files.toList()) Files.delete(file); }
            Files.delete(directory);
        }

        Path resources = Path.of("src/main/resources/lang");
        var en = LanguageCatalogFiles.read(resources.resolve("en.yml"));
        var de = LanguageCatalogFiles.read(resources.resolve("de.yml"));
        TranslationCatalog.validateParity(en, de, true);
        check(!en.isEmpty() && en.keySet().equals(de.keySet()), "complete current EN/DE key/placeholder parity");
        for (String key : List.of("daily-quests.reset-title", "daily-quests.reset-countdown", "daily-quests.reset-description"))
            check(en.containsKey(key) && de.containsKey(key), "previous phase reset key retained: " + key);
        for (String key : List.of("clan.owner", "clan.role", "common.player-only", "common.profile-unavailable"))
            check(en.containsKey(key) && en.keySet().stream().anyMatch(other -> other.startsWith(key + ".")), "scalar and descendant remain separately addressable: " + key);
        var enLines = Files.readAllLines(resources.resolve("en.yml"));
        var deLines = Files.readAllLines(resources.resolve("de.yml"));
        check(shape(enLines).equals(shape(deLines)), "EN/DE share exact sections, comments, hierarchy and leaf ordering");
        for (var lines : List.of(enLines, deLines)) {
            check(lines.stream().noneMatch(line -> line.contains("\t")), "catalog indentation has no tabs");
            check(lines.stream().allMatch(line -> line.isBlank() || (line.length() - line.stripLeading().length()) % 2 == 0), "catalog indentation uses two spaces");
            check(lines.stream().allMatch(line -> line.isBlank() || line.length() - line.stripLeading().length() <= 6), "catalog nesting limited to three section levels");
        }
        System.out.println("CatalogOrganizationHarness passed " + checks + " checks.");
    }

    private static List<String> shape(List<String> lines) {
        return lines.stream().filter(line -> !line.startsWith("# VAPEECORE - "))
                .map(line -> line.contains(": \"") ? line.substring(0, line.indexOf(": \"") + 1) : line).toList();
    }
    private static Map<String, String> parse(String yaml) { return TranslationCatalog.read(new StringReader(yaml)); }
    @FunctionalInterface private interface Work { void run() throws Exception; }
    private static void fails(Work work, String label) throws Exception {
        try { work.run(); } catch (IllegalArgumentException | java.io.IOException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}
