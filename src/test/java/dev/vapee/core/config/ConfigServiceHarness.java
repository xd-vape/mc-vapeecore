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

            Files.writeString(configFile, "server:\n  name: Legacy\n");
            service.load();
            var defaults = service.getOnlineRewardConfig();
            check(defaults.enabled(), "missing online-rewards.enabled uses true");
            check(defaults.intervalMinutes() == 60L && defaults.intervalTicks() == 72_000L,
                    "missing online reward interval uses sixty safely converted minutes");
            check(defaults.coins() == 250L, "missing online reward coins uses 250");
            check(defaults.messageEnabled(), "missing online reward message enabled uses true");
            check(defaults.messageFormat().contains("<coins>")
                            && defaults.messageFormat().contains("<minutes>"),
                    "missing online reward message uses the internal MiniMessage fallback");

            String customOnlineRewards = """
                    online-rewards:
                      enabled: false
                      interval-minutes: 30
                      coins: 500
                      message:
                        enabled: false
                        format: "<aqua><coins>|<minutes>|<intervals>|<balance></aqua>"
                    """;
            Files.writeString(configFile, customOnlineRewards);
            service.load();
            var custom = service.getOnlineRewardConfig();
            check(!custom.enabled() && !custom.messageEnabled(),
                    "custom online reward enable flags are loaded");
            check(custom.intervalMinutes() == 30L && custom.intervalTicks() == 36_000L,
                    "custom online reward interval is loaded and converted to ticks");
            check(custom.coins() == 500L,
                    "custom online reward coin amount is loaded");
            check(custom.messageFormat().equals("<aqua><coins>|<minutes>|<intervals>|<balance></aqua>"),
                    "custom online reward MiniMessage format is loaded");

            Files.writeString(configFile, "online-rewards:\n  interval-minutes: 60\n  coins: 250\n");
            service.load();
            Files.writeString(configFile, "online-rewards:\n  interval-minutes: 30\n  coins: 500\n");
            var onlinePlan = service.prepareReload();
            check(service.getOnlineRewardConfig().intervalMinutes() == 60L
                            && service.getOnlineRewardConfig().coins() == 250L,
                    "prepared reload does not mutate active online reward settings");
            onlinePlan.apply();
            check(service.getOnlineRewardConfig().intervalMinutes() == 30L
                            && service.getOnlineRewardConfig().coins() == 500L,
                    "applied reload activates online interval and coin settings");
            onlinePlan.rollback();
            check(service.getOnlineRewardConfig().intervalMinutes() == 60L
                            && service.getOnlineRewardConfig().coins() == 250L,
                    "reload rollback restores prior online reward settings");

            String invalidNumbers = "online-rewards:\n  interval-minutes: 0\n  coins: -4\n";
            Files.writeString(configFile, invalidNumbers);
            handler.messages.clear();
            service.load();
            check(service.getOnlineRewardConfig().intervalMinutes() == 60L
                            && service.getOnlineRewardConfig().coins() == 250L,
                    "non-positive online reward values fall back to defaults");
            check(handler.messages.stream().anyMatch(message -> message.contains("interval-minutes"))
                            && handler.messages.stream().anyMatch(message -> message.contains("online-rewards.coins")),
                    "invalid interval and coins each produce a warning");
            check(Files.readString(configFile).equals(invalidNumbers),
                    "invalid online reward numbers leave config.yml unchanged");

            Files.writeString(
                    configFile,
                    "online-rewards:\n  interval-minutes: " + Long.MAX_VALUE + "\n"
            );
            handler.messages.clear();
            service.load();
            check(service.getOnlineRewardConfig().intervalMinutes() == 60L
                            && handler.messages.stream().anyMatch(message -> message.contains("safely converts")),
                    "overflowing minute-to-tick conversion warns and uses the default interval");

            String invalidMessage = """
                    online-rewards:
                      message:
                        format: "<green>broken"
                    """;
            Files.writeString(configFile, invalidMessage);
            handler.messages.clear();
            service.load();
            check(service.getOnlineRewardConfig().messageFormat().equals(
                            dev.vapee.core.onlinereward.OnlineRewardConfig.DEFAULT_MESSAGE_FORMAT
                    ) && handler.messages.stream().anyMatch(message -> message.contains("message.format")),
                    "invalid online reward MiniMessage warns and uses the safe fallback");
            check(Files.readString(configFile).equals(invalidMessage),
                    "invalid online reward MiniMessage leaves config.yml unchanged");
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
