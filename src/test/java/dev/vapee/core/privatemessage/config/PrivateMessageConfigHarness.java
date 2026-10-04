package dev.vapee.core.privatemessage.config;

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

public final class PrivateMessageConfigHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-privatemessageconfig-");
        Path file = directory.resolve("private-messages.yml");
        List<String> warnings = new ArrayList<>();
        try {
            byte[] resource = "# resource bytes\r\nenabled: true\r\n".getBytes(StandardCharsets.UTF_8);
            PrivateMessageConfig config = new PrivateMessageConfig(file, logger(warnings), () -> new ByteArrayInputStream(resource));
            config.initialize();
            check(Arrays.equals(resource, Files.readAllBytes(file)), "PM default copies resource bytes");
            check(warnings.size() == 2, "missing PM format pair warns during initialize");
            var before = config.getState();
            for (String source : List.of("", "enabled: null\nformat: null\n",
                    "enabled: 7\nformat:\n  outgoing: 9\n  incoming: false\n",
                    "enabled: 'true'\nformat:\n  outgoing: '  '\n  incoming: ''\n",
                    "enabled: {}\nformat: []\n")) {
                Files.writeString(file, source); warnings.clear();
                var next = config.prepareReloadState();
                check(next.enabled() && next.outgoingFormat().equals(PrivateMessageConfig.DEFAULT_OUTGOING_FORMAT)
                        && next.incomingFormat().equals(PrivateMessageConfig.DEFAULT_INCOMING_FORMAT), "PM fallback values");
                List<String> expected = new ArrayList<>();
                if (!source.isEmpty() && !source.contains("null")) expected.add(
                        "Invalid private-message setting 'enabled' in " + file
                                + ": expected a boolean; using 'true'. The file was left unchanged.");
                for (String path : List.of("format.outgoing", "format.incoming")) expected.add(
                        "Invalid or missing private-message format '" + path + "' in " + file
                                + "; using the internal default. The file was left unchanged.");
                check(warnings.equals(expected), "PM missing/invalid/null format warnings remain exact");
                check(config.getState() == before && Files.readString(file).equals(source),
                        "PM prepare does not publish or rewrite");
                config.initialize();
                check(Files.readString(file).equals(source), "PM initialize preserves administrator input");
                config.applyState(before);
            }
            Files.writeString(file, "enabled: false\nformat:\n  outgoing: '  <message>  '\n  incoming: '<red>broken'\n");
            warnings.clear();
            var next = config.prepareReloadState();
            check(!next.enabled() && next.outgoingFormat().equals("  <message>  ")
                    && next.incomingFormat().equals("<red>broken") && warnings.isEmpty(),
                    "PM config retains untrimmed strings; template validation stays with service");
            config.applyState(next); check(config.getState() == next, "PM applies typed state");
            config.applyState(before); check(config.getState() == before, "PM restores exact previous state");
            Files.delete(file); warnings.clear();
            var missing = new PrivateMessageConfig(file, logger(warnings), () -> null);
            try { missing.initialize(); throw new AssertionError("missing resource accepted"); }
            catch (IllegalStateException failure) {
                check(failure.getMessage().equals("Default resource 'private-messages.yml' is missing from the plugin JAR.")
                        && warnings.equals(List.of(failure.getMessage())), "PM missing resource severe/fatal");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        System.out.println("PrivateMessageConfigHarness passed " + checks + " checks.");
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
