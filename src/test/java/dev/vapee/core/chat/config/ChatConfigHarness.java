package dev.vapee.core.chat.config;

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

public final class ChatConfigHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-chatconfig-");
        Path file = directory.resolve("chat.yml");
        List<String> warnings = new ArrayList<>();
        try {
            byte[] resource = "# resource bytes\r\nenabled: true\r\n".getBytes(StandardCharsets.UTF_8);
            ChatConfig config = new ChatConfig(file, logger(warnings), () -> new ByteArrayInputStream(resource));
            config.initialize();
            check(Arrays.equals(resource, Files.readAllBytes(file)), "default file copies exact bundled bytes");
            check(config.isEnabled() && config.getFormat().equals(ChatConfig.DEFAULT_FORMAT)
                    && warnings.isEmpty(), "missing chat format is silent");
            var before = config.getState();
            for (String source : List.of("", "enabled: null\nformat: null\n",
                    "enabled: 7\nformat: 9\n", "enabled: 'true'\nformat: '  '\n",
                    "enabled: {}\nformat: []\n")) {
                Files.writeString(file, source); warnings.clear();
                var next = config.prepareReloadState();
                int count = source.isEmpty() || source.contains("null") ? 0 : 2;
                check(next.enabled() && next.format().equals(ChatConfig.DEFAULT_FORMAT), "chat scalar fallback");
                check(warnings.size() == count, "chat missing/null silent; invalid fields warn individually");
                if (count == 2) check(warnings.equals(List.of(
                        "Invalid chat setting 'enabled' in " + file + ": expected a boolean; using 'true'. The file was left unchanged.",
                        "Invalid 'format' in " + file + ": expected a non-blank string; using the internal default. The file was left unchanged."
                )), "chat exact warning text");
                check(config.getState() == before && Files.readString(file).equals(source),
                        "chat prepare neither publishes nor rewrites");
                config.initialize();
                check(Files.readString(file).equals(source), "chat initialize never overwrites existing input");
                config.applyState(before);
            }
            Files.writeString(file, "enabled: false\nformat: '  <message>  '\nluckperms-meta:\n  format: ' PLAIN '\n");
            warnings.clear();
            var custom = config.prepareReloadState();
            check(!custom.enabled() && custom.format().equals("  <message>  ")
                    && custom.metaFormat() == ChatConfig.MetaFormat.PLAIN && warnings.isEmpty(),
                    "chat accepted values retain whitespace and local meta normalization");
            config.applyState(custom);
            check(config.getState() == custom, "chat apply uses typed prepared state");
            config.applyState(before);
            check(config.getState() == before, "chat prior snapshot can be restored");
            Files.writeString(file, "luckperms-meta:\n  format: unsupported\n");
            warnings.clear();
            check(config.prepareReloadState().metaFormat() == ChatConfig.MetaFormat.LEGACY_AMPERSAND
                    && warnings.size() == 1, "chat invalid enum retains local warning/fallback");
            Files.delete(file); warnings.clear();
            ChatConfig missing = new ChatConfig(file, logger(warnings), () -> null);
            try { missing.initialize(); throw new AssertionError("missing resource accepted"); }
            catch (IllegalStateException failure) {
                check(failure.getMessage().equals("Default resource 'chat.yml' is missing from the plugin JAR.")
                        && warnings.equals(List.of(failure.getMessage())), "chat missing resource remains severe/fatal");
            }
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
        System.out.println("ChatConfigHarness passed " + checks + " checks.");
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
