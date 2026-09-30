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
            hierarchy(configFile, handler, logger);
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

            Files.writeString(configFile, "server:\n  name: Legacy\n");
            service.load();
            check(service.getFriendLimits().maxFriends() == 100
                            && service.getFriendLimits().maxIncomingRequests() == 25
                            && service.getFriendLimits().maxOutgoingRequests() == 25,
                    "legacy config uses central friend limit defaults");
            Files.writeString(configFile, "friends:\n  limits:\n    max-friends: 5\n"
                    + "    max-incoming-requests: 3\n    max-outgoing-requests: 4\n");
            service.load();
            check(service.getFriendLimits().maxFriends() == 5
                            && service.getFriendLimits().maxIncomingRequests() == 3
                            && service.getFriendLimits().maxOutgoingRequests() == 4,
                    "custom friend limits load");
            Files.writeString(configFile, "friends:\n  limits:\n    max-friends: 1\n"
                    + "    max-incoming-requests: 2\n    max-outgoing-requests: 3\n");
            var friendPlan = service.prepareReload();
            check(service.getFriendLimits().maxFriends() == 5,
                    "friend limit reload preparation does not mutate current state");
            friendPlan.apply();
            check(service.getFriendLimits().maxFriends() == 1
                            && service.getFriendLimits().maxIncomingRequests() == 2,
                    "friend limits update on successful reload");
            friendPlan.rollback();
            check(service.getFriendLimits().maxFriends() == 5,
                    "friend limit rollback restores previous state");

            String invalidFriends = "friends:\n  limits:\n    max-friends: 0\n"
                    + "    max-incoming-requests: -1\n    max-outgoing-requests: nope\n";
            Files.writeString(configFile, invalidFriends);
            handler.messages.clear();
            service.load();
            check(service.getFriendLimits().maxFriends() == 100
                            && service.getFriendLimits().maxIncomingRequests() == 25
                            && service.getFriendLimits().maxOutgoingRequests() == 25,
                    "zero, negative, and wrong-type friend limits use defaults");
            check(handler.messages.stream().filter(message -> message.contains("friends.limits.")).count() == 3,
                    "each invalid friend limit warns");
            check(Files.readString(configFile).equals(invalidFriends),
                    "invalid friend limits never rewrite config.yml");
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

    private static void hierarchy(Path file, CapturingHandler handler, Logger logger) throws IOException {
        Files.writeString(file, "server:\n  name: Legacy\n");
        var service = new ConfigService(file, logger);
        service.load();
        check(service.getStaffHierarchyConfig().equals(dev.vapee.core.rank.staff.StaffHierarchyConfig.defaults()),
                "missing staff hierarchy uses safe defaults");
        String custom = "staff:\n  hierarchy:\n    protected-groups: [helper, ' ADMIN ', OWNER]\n";
        Files.writeString(file, custom);
        service.load();
        check(service.getStaffHierarchyConfig().protectedGroups().equals(List.of("helper", "admin", "owner")),
                "configured custom order normalized");
        var previous = service.getStaffHierarchyConfig();
        Files.writeString(file, "staff:\n  hierarchy:\n    protected-groups: [owner, admin]\n");
        var plan = service.prepareReload();
        check(service.getStaffHierarchyConfig() == previous, "prepare hierarchy has no mutation");
        plan.apply();
        check(service.getStaffHierarchyConfig().protectedGroups().equals(List.of("owner", "admin")), "apply hierarchy atomically");
        plan.rollback();
        check(service.getStaffHierarchyConfig() == previous, "rollback restores exact prior immutable snapshot");
        for (String invalid : List.of("[]", "admin", "42", "[admin, ADMIN]", "[admin, null]",
                "null", "['']", "['   ']", "['ad min']", "['admin\\n']")) {
            String source = "staff:\n  hierarchy:\n    protected-groups: " + invalid + "\n";
            // Use double quotes for YAML escape so the control-character case is actually a newline.
            if (invalid.equals("['admin\\n']")) source = "staff:\n  hierarchy:\n    protected-groups: [\"admin\\n\"]\n";
            Files.writeString(file, source);
            handler.messages.clear();
            service.load();
            check(service.getStaffHierarchyConfig().equals(dev.vapee.core.rank.staff.StaffHierarchyConfig.defaults()),
                    "invalid hierarchy safely falls back " + invalid);
            check(handler.messages.stream().anyMatch(message -> message.contains("staff.hierarchy.protected-groups")),
                    "invalid hierarchy warns " + invalid);
            check(Files.readString(file).equals(source), "invalid hierarchy never rewrites file " + invalid);
        }
        for (String source : List.of("staff: null\n", "staff: 42\n", "staff:\n  hierarchy: null\n", "staff:\n  hierarchy: []\n")) {
            Files.writeString(file, source); handler.messages.clear(); service.load();
            check(service.getStaffHierarchyConfig().equals(dev.vapee.core.rank.staff.StaffHierarchyConfig.defaults())
                    && !handler.messages.isEmpty(), "invalid hierarchy parent safe fallback/warning");
        }
        Files.writeString(file, "staff:\n  hierarchy:\n    protected-groups: []\n");
        var invalidPlan = service.prepareReload();
        invalidPlan.apply();
        check(service.getStaffHierarchyConfig().equals(dev.vapee.core.rank.staff.StaffHierarchyConfig.defaults()),
                "invalid reload never disables protection");
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
