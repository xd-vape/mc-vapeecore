package dev.vapee.core.config;

import dev.vapee.core.chat.ChatModule;
import dev.vapee.core.chat.ChatService;
import dev.vapee.core.chat.config.ChatConfig;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.permission.LuckPermsService;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.rank.RankModule;
import dev.vapee.core.rank.RankService;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import dev.vapee.core.reload.ReloadResult;
import dev.vapee.core.reload.ReloadService;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.social.SocialService;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.track.TrackManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Real core/chat participants and RankService consumer; only Paper/LP I/O boundaries are fixtures. */
public final class ConfigReloadIntegrationHarness {
    private static final Expected START =
            new Expected(
                    "initial",
                    "Initial Server",
                    false,
                    7,
                    true,
                    "<gray><message></gray>",
                    ChatConfig.MetaFormat.LEGACY_AMPERSAND);
    private static final Expected NEXT =
            new Expected(
                    "promoted",
                    "Next Server",
                    true,
                    11,
                    false,
                    "<green><message></green>",
                    ChatConfig.MetaFormat.PLAIN);
    private static final Expected THIRD =
            new Expected(
                    "third",
                    "Third Server",
                    false,
                    13,
                    true,
                    "<aqua><message></aqua>",
                    ChatConfig.MetaFormat.MINI_MESSAGE);
    private static int checks;

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("vapeecore-config-reload-");
        try (var fixture = new Fixture(root)) {
            if (args.length == 0) fixture.frozenPlans();
            fixture.before = START;
            fixture.candidate = NEXT;
            fixture.write(NEXT);
            check(
                    fixture.reload(false).status() == ReloadResult.Status.SUCCESS,
                    "real two-participant reload succeeds");
            fixture.assertActive(NEXT, "successful reload");
            fixture.assertCalls("prepare core", "prepare chat", "apply core", "apply chat");

            fixture.before = NEXT;
            fixture.candidate = THIRD;
            fixture.write(THIRD);
            Files.writeString(root.resolve("chat.yml"), "enabled: [broken\n");
            check(
                    fixture.reload(false).status() == ReloadResult.Status.PREPARE_FAILED,
                    "real chat parse failure refuses reload");
            fixture.assertActive(NEXT, "prepare failure preserves prior state");
            fixture.assertCalls("prepare core", "prepare chat");
            fixture.write(THIRD);
            check(
                    fixture.reload(false).status() == ReloadResult.Status.SUCCESS,
                    "corrected real config retries successfully");
            fixture.assertActive(THIRD, "retry after parse failure");

            fixture.before = THIRD;
            fixture.candidate = START;
            fixture.write(START);
            Object configBefore = fixture.chatConfig.getState();
            Object runtimeBefore = fixture.chat.getChatService().getState();
            fixture.failChatApply = true;
            check(
                    fixture.reload(false).status() == ReloadResult.Status.APPLY_FAILED,
                    "failure after real second apply reported");
            fixture.assertActive(THIRD, "after second apply failure");
            check(
                    fixture.chatConfig.getState() == configBefore
                            && fixture.chat.getChatService().getState() == runtimeBefore,
                    "failed real participant restores exact previous config/runtime snapshots");
            fixture.assertCalls(
                    "prepare core",
                    "prepare chat",
                    "apply core",
                    "apply chat",
                    "rollback chat",
                    "rollback core");

            fixture.failChatApply = false;
            fixture.failTailApply = true;
            check(
                    fixture.reload(true).status() == ReloadResult.Status.APPLY_FAILED,
                    "later apply failure rolls back two real participants");
            fixture.assertActive(THIRD, "after third apply failure");
            fixture.assertCalls(
                    "prepare core",
                    "prepare chat",
                    "prepare tail",
                    "apply core",
                    "apply chat",
                    "apply tail",
                    "rollback tail",
                    "rollback chat",
                    "rollback core");
            fixture.failChatRollback = true;
            check(
                    fixture.reload(true).status() == ReloadResult.Status.ROLLBACK_INCOMPLETE,
                    "real-plan rollback exception is distinct");
            fixture.assertActive(THIRD, "remaining rollback continues after independent error");
            fixture.failChatRollback = false;
            fixture.failTailApply = false;
            check(
                    fixture.reload(false).status() == ReloadResult.Status.SUCCESS,
                    "retry after apply/rollback faults succeeds");
            fixture.assertActive(START, "final real state usable");
            fixture.warningFallback();
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.deleteIfExists(path);
            }
        }
        System.out.println("ConfigReloadIntegrationHarness passed " + checks + " checks.");
    }

    private record Expected(
            String track,
            String server,
            boolean debug,
            int friends,
            boolean chatEnabled,
            String format,
            ChatConfig.MetaFormat meta) {}

    private static final class Fixture implements AutoCloseable {
        final Path root;
        final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture();
        final ConfigService core;
        final ChatModule chat;
        final ChatConfig chatConfig;
        final RankService ranks;
        final List<String> calls = new ArrayList<>();
        final List<String> requestedTracks = new ArrayList<>();
        Expected before = START;
        Expected candidate = NEXT;
        boolean failChatApply;
        boolean failTailApply;
        boolean failChatRollback;

        Fixture(Path root) throws Exception {
            this.root = root;
            for (String name :
                    List.of(
                            "lobby.yml",
                            "private-messages.yml",
                            "presentation.yml",
                            "daily-quests.yml")) {
                try (var resource =
                        ConfigReloadIntegrationHarness.class.getResourceAsStream("/" + name)) {
                    Files.copy(resource, root.resolve(name));
                }
            }
            for (String name :
                    List.of(
                            "players/test.yml",
                            "friends.yml",
                            "clans.yml",
                            "warps.yml",
                            "moderation.yml",
                            "blackjack.yml",
                            "lang/en.yml",
                            "lang/de.yml",
                            "backups/config/previous.bak")) {
                Path file = root.resolve(name);
                Files.createDirectories(file.getParent());
                Files.writeString(file, "# operator-owned sentinel\ncustom: '007'\n");
            }
            ModuleRuntimeFixture.set(runtime.plugin, "dataFolder", root.toFile());
            core = new ConfigService(root.resolve("config.yml"), runtime.logger);
            Files.writeString(root.resolve("config.yml"), "");
            core.load();
            var trackManager =
                    ModuleRuntimeFixture.proxy(
                            TrackManager.class,
                            (method, arguments) -> {
                                if (!method.equals("getTrack"))
                                    throw new AssertionError("Unexpected LP boundary: " + method);
                                requestedTracks.add((String) arguments[0]);
                                return null;
                            });
            var lp =
                    ModuleRuntimeFixture.proxy(
                            LuckPerms.class,
                            (method, arguments) -> {
                                if (!method.equals("getTrackManager"))
                                    throw new AssertionError("Unexpected LP call: " + method);
                                return trackManager;
                            });
            var permissions = new LuckPermsService(lp);
            ranks = new RankService(core, permissions);
            check(
                    core.getRankTrack().equals("ranks")
                            && ranks.getPublicRanks().trackName().equals("ranks")
                            && requestedTracks.getLast().equals("ranks"),
                    "missing ranks.track default reaches actual RankService/LP boundary");
            write(START);
            core.load();
            chat =
                    new ChatModule(
                            runtime.plugin,
                            ModuleRuntimeFixture.dependency(
                                    PermissionModule.class,
                                    Map.of("luckPermsService", permissions)),
                            ModuleRuntimeFixture.dependency(
                                    RankModule.class, Map.of("rankService", ranks)),
                            ModuleRuntimeFixture.dependency(
                                    SocialModule.class,
                                    Map.of(
                                            "socialService",
                                            new SocialService(runtime.players, runtime.logger))),
                            runtime.messages);
            chat.enable();
            chatConfig = (ChatConfig) ModuleRuntimeFixture.get(chat, "chatConfig");
            assertActive(START, "real module startup");
        }

        void frozenPlans() throws Exception {
            write(NEXT);
            var oldChat = chatConfig.getState();
            var oldRuntime = chat.getChatService().getState();
            var corePlan = core.prepareReload();
            var chatPlan = chat.prepareReload();
            assertActive(START, "direct prepare does not publish");
            write(
                    THIRD); // An external edit after preparation must not mutate either prepared
                            // snapshot.
            var disk = snapshot();
            corePlan.apply();
            chatPlan.apply();
            assertActive(NEXT, "prepared snapshots survive subsequent disk edit");
            corePlan.rollback();
            chatPlan.rollback();
            assertActive(START, "direct rollback restores usable snapshots");
            check(
                    chatConfig.getState() == oldChat
                            && chat.getChatService().getState() == oldRuntime,
                    "immutable chat snapshots restored by identity");
            assertDisk(disk);
        }

        ReloadResult reload(boolean tail) throws Exception {
            calls.clear();
            var disk = snapshot();
            var participants =
                    new ArrayList<ReloadParticipant>(
                            List.of(observe(core, "core"), observe(chat, "chat")));
            if (tail)
                participants.add(
                        new ReloadParticipant() {
                            public String getReloadName() {
                                return "controlled-tail";
                            }

                            public ReloadPlan prepareReload() {
                                calls.add("prepare tail");
                                assertActive(before, "last prepare still sees old active state");
                                return ReloadPlan.of(
                                        () -> {
                                            calls.add("apply tail");
                                            assertActive(
                                                    candidate,
                                                    "real participants applied before tail");
                                            if (failTailApply)
                                                throw new IllegalStateException(
                                                        "controlled later apply failure");
                                        },
                                        () -> calls.add("rollback tail"));
                            }
                        });
            ReloadResult result = new ReloadService(runtime.logger, participants).reload();
            assertDisk(disk);
            return result;
        }

        ReloadParticipant observe(ReloadParticipant delegate, String name) {
            return new ReloadParticipant() {
                public String getReloadName() {
                    return delegate.getReloadName();
                }

                public ReloadPlan prepareReload() {
                    calls.add("prepare " + name);
                    assertActive(before, "before actual prepare");
                    ReloadPlan actual = delegate.prepareReload();
                    assertActive(before, "prepare does not publish");
                    return ReloadPlan.of(
                            () -> {
                                calls.add("apply " + name);
                                actual.apply();
                                if (name.equals("core"))
                                    assertCore(candidate, "complete core snapshot at apply");
                                else {
                                    assertActive(
                                            candidate, "complete feature config/runtime at apply");
                                    if (failChatApply)
                                        throw new IllegalStateException(
                                                "controlled failure after real chat apply");
                                }
                            },
                            () -> {
                                calls.add("rollback " + name);
                                actual.rollback();
                                if (name.equals("core")) assertCore(before, "core rollback");
                                else if (failChatRollback)
                                    throw new IllegalStateException(
                                            "controlled error after real chat rollback");
                            });
                }
            };
        }

        void warningFallback() throws Exception {
            var previous = chat.getChatService().getState();
            Files.writeString(root.resolve("chat.yml"), "enabled: true\nformat: '<green>broken'\n");
            var disk = snapshot();
            var plan = chat.prepareReload();
            check(
                    chat.getChatService().getState() == previous,
                    "invalid template fallback remains prepare-only");
            plan.apply();
            check(
                    chatConfig.getFormat().equals("<green>broken")
                            && chat.getChatService()
                                    .getState()
                                    .chatFormat()
                                    .equals(
                                            "<rank_name><dark_gray> \u00bb </dark_gray><white><message></white>"),
                    "invalid MiniMessage retains owning warning/fallback policy, not hard reload failure");
            plan.rollback();
            check(
                    chat.getChatService().getState() == previous,
                    "template fallback rollback restores previous runtime");
            assertDisk(disk);
        }

        void write(Expected value) throws Exception {
            Files.writeString(
                    root.resolve("config.yml"),
                    "config-version: 1\nserver: {name: '"
                            + value.server()
                            + "'}\nranks: {track: '  "
                            + value.track()
                            + "  '}\nsettings: {debug: "
                            + value.debug()
                            + "}\nfriends: {limits: {max-friends: "
                            + value.friends()
                            + "}}\nunknown: {keep: true}\n");
            Files.writeString(
                    root.resolve("chat.yml"),
                    "config-version: 1\nenabled: "
                            + value.chatEnabled()
                            + "\nformat: '"
                            + value.format()
                            + "'\nluckperms-meta: {format: '"
                            + value.meta()
                                    .name()
                                    .toLowerCase(java.util.Locale.ROOT)
                                    .replace('_', '-')
                            + "'}\n");
        }

        void assertCore(Expected expected, String reason) {
            check(core.getRankTrack().equals(expected.track()), reason + ": core track");
            check(
                    core.getServerName().equals(expected.server())
                            && core.isDebugEnabled() == expected.debug()
                            && core.getFriendLimits().maxFriends() == expected.friends(),
                    reason + ": complete core values");
            check(
                    ranks.getPublicRanks().trackName().equals(expected.track())
                            && requestedTracks.getLast().equals(expected.track()),
                    reason + ": actual owning rank consumer");
        }

        void assertActive(Expected expected, String reason) {
            assertCore(expected, reason);
            ChatService.RuntimeState state = chat.getChatService().getState();
            check(
                    chatConfig.isEnabled() == expected.chatEnabled()
                            && chatConfig.getFormat().equals(expected.format())
                            && chatConfig.getMetaFormat() == expected.meta(),
                    reason + ": feature config snapshot");
            check(
                    state.enabled() == expected.chatEnabled()
                            && state.chatFormat().equals(expected.format())
                            && state.metaFormat() == expected.meta(),
                    reason + ": feature usable runtime snapshot");
        }

        Map<Path, byte[]> snapshot() throws Exception {
            Map<Path, byte[]> snapshot = new LinkedHashMap<>();
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList())
                    snapshot.put(file, Files.readAllBytes(file));
            }
            return snapshot;
        }

        void assertDisk(Map<Path, byte[]> previous) throws Exception {
            var current = snapshot();
            check(
                    current.keySet().equals(previous.keySet()),
                    "reload creates/deletes no config/data/lang/backup files");
            for (var entry : previous.entrySet()) {
                check(
                        Arrays.equals(entry.getValue(), current.get(entry.getKey())),
                        "reload leaves exact file bytes: " + root.relativize(entry.getKey()));
            }
        }

        void assertCalls(String... expected) {
            check(calls.equals(List.of(expected)), "real participant phase order: " + calls);
        }

        @Override
        public void close() throws Exception {
            try {
                chat.disable();
            } finally {
                runtime.close();
            }
        }
    }

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
}
