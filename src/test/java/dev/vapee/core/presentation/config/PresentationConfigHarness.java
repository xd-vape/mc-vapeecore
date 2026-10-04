package dev.vapee.core.presentation.config;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class PresentationConfigHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-presentationconfig-");
        Path file = directory.resolve("presentation.yml");
        List<String> warnings = new ArrayList<>();
        try {
            byte[] resource = "# resource bytes\r\nenabled: true\r\n".getBytes(StandardCharsets.UTF_8);
            PresentationConfig config = new PresentationConfig(file, logger(warnings), () -> new ByteArrayInputStream(resource));
            config.initialize();
            check(Arrays.equals(resource, Files.readAllBytes(file)), "presentation default resource bytes");
            var before = config.getState();
            check(warnings.isEmpty(), "presentation missing optional fields silent");
            for (String source : List.of("", "enabled: null\nscoreboard:\n  title: null\n",
                    "enabled: 7\nscoreboard:\n  title: 9\n",
                    "enabled: 'true'\nscoreboard:\n  title: []\n")) {
                Files.writeString(file, source); warnings.clear();
                var next = config.prepareReloadState();
                check(next.enabled() && next.scoreboardTitle().equals(PresentationConfig.DEFAULT_SCOREBOARD_TITLE),
                        "presentation optional scalar fallback");
                int count = source.isEmpty() || source.contains("null") ? 0 : 2;
                check(warnings.size() == count, "presentation missing/null silent; wrong type warns");
                if (count == 2) check(warnings.equals(List.of(
                        "Invalid presentation setting 'enabled' in " + file + ": expected a boolean; using 'true'. The file was left unchanged.",
                        "Invalid presentation setting 'scoreboard.title' in " + file + ": expected a string; using '"
                                + PresentationConfig.DEFAULT_SCOREBOARD_TITLE + "'. The file was left unchanged."
                )), "presentation warning text unchanged");
                check(config.getState() == before && Files.readString(file).equals(source), "prepare immutable/no rewrite");
                config.initialize(); check(Files.readString(file).equals(source), "initialize no rewrite");
                config.applyState(before);
            }
            Files.writeString(file, "enabled: false\nscoreboard:\n  title: ''\n  lines: [first, 42, '', false]\n"
                    + "tablist:\n  name-format: '  '\n  header: []\n  footer: [last]\nupdate-interval-ticks: 1\n");
            warnings.clear();
            var next = config.prepareReloadState();
            check(!next.enabled() && next.scoreboardTitle().isEmpty() && next.tablistNameFormat().equals("  "),
                    "presentation blank strings remain valid");
            check(next.scoreboardLines().equals(List.of("first", "", "", "")) && next.tablistHeader().isEmpty()
                    && next.tablistFooter().equals(List.of("last")) && warnings.size() == 2,
                    "presentation list element recovery/empty lists remain local");
            check(next.updateIntervalTicks() == 1, "positive whole interval accepted");
            config.applyState(next); check(config.getState() == next, "presentation typed apply");
            config.applyState(before); check(config.getState() == before, "presentation prior state restored");
            for (String interval : List.of("0", "-1", "1.5", "'20'")) {
                String source = "update-interval-ticks: " + interval + "\n";
                Files.writeString(file, source); warnings.clear();
                check(config.prepareReloadState().updateIntervalTicks() == 20 && warnings.size() == 1
                        && Files.readString(file).equals(source), "invalid interval fallback/warning/no rewrite");
            }
            String lines = "scoreboard:\n  lines:\n" + "    - line\n".repeat(17);
            Files.writeString(file, lines); warnings.clear();
            check(config.prepareReloadState().scoreboardLines().size() == 15 && warnings.size() == 1
                    && Files.readString(file).equals(lines), "line cap retained without rewriting");
            Files.writeString(file, "scoreboard:\n  lines: 9\nluckperms-meta:\n  format: unknown\n");
            warnings.clear();
            var invalid = config.prepareReloadState();
            check(invalid.scoreboardLines().equals(before.scoreboardLines())
                    && invalid.metaFormat() == PresentationConfig.MetaFormat.LEGACY_AMPERSAND
                    && warnings.size() == 2, "invalid whole list and enum remain feature-owned");
            Files.delete(file); warnings.clear();
            var missing = new PresentationConfig(file, logger(warnings), () -> null);
            try { missing.initialize(); throw new AssertionError("missing resource accepted"); }
            catch (IllegalStateException failure) {
                check(failure.getMessage().equals("Default resource 'presentation.yml' is missing from the plugin JAR.")
                        && warnings.equals(List.of(failure.getMessage())), "presentation missing resource severe/fatal");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        System.out.println("PresentationConfigHarness passed " + checks + " checks.");
    }

    private static Logger logger(List<String> warnings) {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()) warnings.add(record.getMessage());
            }
            public void flush() { }
            public void close() { }
        });
        return logger;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
