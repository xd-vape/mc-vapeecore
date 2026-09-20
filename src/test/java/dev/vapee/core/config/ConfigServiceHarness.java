package dev.vapee.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class ConfigServiceHarness {

    private static int checks;

    private ConfigServiceHarness() {
    }

    public static void main(String[] args) throws IOException {
        Path directory = Files.createTempDirectory("vapeecore-config-harness-");
        Path configFile = directory.resolve("config.yml");
        CapturingHandler handler = new CapturingHandler();
        Logger logger = Logger.getLogger("ConfigServiceHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        logger.addHandler(handler);
        try {
            Files.writeString(configFile, "server:\n  name: Test\n");
            ConfigService service = new ConfigService(configFile, logger);
            service.load();
            check(service.getRankTrack().equals("ranks"),
                    "missing ranks.track uses the default track");

            Files.writeString(configFile, "ranks:\n  track: public-ranks\n");
            service.load();
            check(service.getRankTrack().equals("public-ranks"),
                    "configured ranks.track is loaded");

            String blankConfig = "ranks:\n  track: \"   \"\n";
            Files.writeString(configFile, blankConfig);
            handler.messages.clear();
            service.load();
            check(service.getRankTrack().equals("ranks")
                            && handler.messages.stream().anyMatch(message -> message.contains("ranks.track")),
                    "blank ranks.track warns and falls back");
            check(Files.readString(configFile).equals(blankConfig),
                    "invalid rank config is left unchanged");

            Files.writeString(configFile, "ranks:\n  track: 42\n");
            handler.messages.clear();
            service.load();
            check(service.getRankTrack().equals("ranks")
                            && handler.messages.stream().anyMatch(message -> message.contains("non-blank string")),
                    "wrong-type ranks.track warns and falls back");

            Files.writeString(configFile, "ranks:\n  track: first\n");
            service.load();
            Files.writeString(configFile, "ranks:\n  track: second\n");
            var plan = service.prepareReload();
            check(service.getRankTrack().equals("first"),
                    "prepared reload does not mutate the active rank track");
            plan.apply();
            check(service.getRankTrack().equals("second"),
                    "applied reload activates the new rank track without restart");
            plan.rollback();
            check(service.getRankTrack().equals("first"),
                    "reload rollback restores the prior rank track");
        } finally {
            Files.deleteIfExists(configFile);
            Files.deleteIfExists(directory);
        }

        System.out.println("ConfigServiceHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class CapturingHandler extends Handler {

        private final List<String> messages = new ArrayList<>();

        private CapturingHandler() {
            setLevel(Level.ALL);
        }

        @Override
        public void publish(LogRecord record) {
            messages.add(record.getMessage());
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
