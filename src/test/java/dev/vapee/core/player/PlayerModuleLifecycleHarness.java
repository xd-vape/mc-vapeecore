package dev.vapee.core.player;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.persistence.SafeFileWriter;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.player.settings.VisibilityMode;
import dev.vapee.core.quest.daily.DailyQuestCycleId;
import dev.vapee.core.reward.RewardService;
import dev.vapee.core.reward.RewardSource;

import net.kyori.adventure.text.Component;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/** Real Player owners, real per-profile commits and real HandlerLists; only external effects fail. */
public final class PlayerModuleLifecycleHarness {
    private static int checks;
    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);
    private static final UUID OTHER = new UUID(0, 9);

    public static void main(String[] args) throws Exception {
        List<String> cases =
                args.length == 0
                        ? List.of(
                                "normal",
                                "storage",
                                "registration-before",
                                "registration-after",
                                "stages",
                                "duplicate",
                                "reentrant",
                                "startup-cleanup",
                                "unregister",
                                "combined",
                                "multi-0",
                                "multi-1",
                                "multi-2",
                                "multi-3",
                                "recovery",
                                "stale",
                                "join-quit",
                                "public-save",
                                "diagnostic",
                                "manager",
                                "aggregate")
                        : List.of(args);
        for (String scenario : cases) {
            switch (scenario) {
                case "normal" -> normal();
                case "storage" -> storage();
                case "registration-before" -> registration(false);
                case "registration-after" -> registration(true);
                case "stages" -> stages();
                case "duplicate" -> duplicate();
                case "reentrant" -> reentrant();
                case "startup-cleanup" -> startupCleanup();
                case "unregister" -> unregister(false);
                case "combined" -> unregister(true);
                case "multi-0", "multi-1", "multi-2", "multi-3" -> multi(scenario.charAt(6) - '0');
                case "recovery" -> recovery();
                case "stale" -> stale();
                case "join-quit" -> joinQuit();
                case "public-save" -> publicSave();
                case "diagnostic" -> diagnostic();
                case "manager" -> manager();
                case "aggregate" -> aggregate();
                default -> throw new AssertionError(scenario);
            }
        }
        System.out.println("PlayerModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static void normal() throws Exception {
        try (Fixture f = new Fixture()) {
            unavailable(f.module);
            check(!Files.exists(f.directory()), "constructor has no storage effects");
            f.module.enable();
            PlayerService service = f.module.getPlayerService();
            check(Files.isDirectory(f.directory()), "missing directory initialized");
            check(f.module.getPlayerSettingsService() != null, "settings runtime available");
            check(
                    f.runtime.handlerCount() == 2 && f.runtime.registered.size() == 1,
                    "exactly one listener with join and quit handlers");
            check(
                    Arrays.stream(PlayerJoinEvent.getHandlerList().getRegisteredListeners())
                                    .filter(h -> h.getPlugin() == f.runtime.plugin)
                                    .count()
                            == 1,
                    "one join handler");
            check(
                    Arrays.stream(PlayerQuitEvent.getHandlerList().getRegisteredListeners())
                                    .filter(h -> h.getPlugin() == f.runtime.plugin)
                                    .count()
                            == 1,
                    "one quit handler");
            f.load(A, "A").updateName("A-shutdown");
            f.module.disable();
            check(f.read(A).getName().equals("A-shutdown"), "normal shutdown saves loaded profile");
            check(service.getLoadedPlayers().isEmpty(), "successful shutdown releases cache");
            released(f);
            Map<String, String> durable = f.snapshot();
            f.module.disable();
            check(f.snapshot().equals(durable), "repeated successful disable has no file effects");
            f.module.enable();
            check(f.module.getPlayerService() != service, "fresh runtime after successful cleanup");
            check(
                    f.module
                            .getPlayerService()
                            .loadPlayer(A, "Rejoined")
                            .getSettings()
                            .isSoundsEnabled(),
                    "fresh runtime can read durable profile");
            f.module.disable();
            released(f);
        }
    }

    private static void storage() throws Exception {
        try (Fixture f = new Fixture()) {
            Files.writeString(f.directory(), "not a directory");
            failure(f.module::enable);
            released(f);
            check(
                    Files.readString(f.directory()).equals("not a directory"),
                    "invalid storage path untouched");
            Files.delete(f.directory()); // Disposable invalid-path fixture only.
            f.module.enable();
            f.module.disable();
            released(f);
        }
    }

    private static void registration(boolean afterEffect) throws Exception {
        try (Fixture f = new Fixture()) {
            RuntimeException original = new IllegalStateException("registration boundary");
            f.registration =
                    listener -> {
                        unavailable(f.module);
                        listener.onPlayerJoin(join(f.player(A, "DuringStart")));
                        check(
                                !Files.exists(f.file(A)),
                                "incomplete startup listener has no authority");
                        if (afterEffect)
                            f.originalManager.registerEvents(listener, f.runtime.plugin);
                        throw original;
                    };
            check(failure(f.module::enable) == original, "original startup exception identity");
            check(f.runtime.handlerCount() == 0, "listener rollback leaves no registered handler");
            unavailable(f.module);
            f.module.disable();
            check(f.runtime.handlerCount() == 0, "failed startup cleanup before fixture teardown");
            f.registration =
                    listener -> f.originalManager.registerEvents(listener, f.runtime.plugin);
            f.module.enable();
            check(f.runtime.handlerCount() == 2, "registration retry has no duplicate handlers");
            f.module.disable();
        }
    }

    private static void stages() throws Exception {
        for (PlayerModule.EnableStage selected : PlayerModule.EnableStage.values()) {
            try (Fixture f = new Fixture()) {
                RuntimeException original = new IllegalStateException("stage " + selected);
                f.module =
                        f.module(
                                stage -> {
                                    unavailable(f.module);
                                    if (stage == selected) throw original;
                                });
                check(
                        failure(f.module::enable) == original,
                        "checkpoint startup preserves original identity");
                released(f);
                f.module = f.module(stage -> {});
                f.module.enable();
                f.module.disable();
            }
        }
    }

    private static void duplicate() throws Exception {
        try (Fixture f = new Fixture()) {
            f.module.enable();
            PlayerService first = f.module.getPlayerService();
            Object settings = f.module.getPlayerSettingsService();
            RuntimeException refused = null;
            try {
                f.module.enable();
            } catch (RuntimeException e) {
                refused = e;
            }
            check(
                    refused != null
                            && f.runtime.handlerCount() == 2
                            && f.module.getPlayerService() == first
                            && f.module.getPlayerSettingsService() == settings,
                    "duplicate enable preserves existing runtime without duplicate resources");
            f.module.disable();
        }
    }

    private static void reentrant() throws Exception {
        try (Fixture f = new Fixture()) {
            boolean[] attempted = {false};
            f.module =
                    f.module(
                            stage -> {
                                if (stage == PlayerModule.EnableStage.SERVICES_CREATED
                                        && !attempted[0]) {
                                    attempted[0] = true;
                                    failure(f.module::enable);
                                    failure(f.module::disable);
                                }
                            });
            f.module.enable();
            check(
                    attempted[0]
                            && f.runtime.registered.size() == 1
                            && f.runtime.handlerCount() == 2,
                    "reentrant enable installs no second runtime");
            f.module.disable();
        }
    }

    private static void startupCleanup() throws Exception {
        try (Fixture f = new Fixture()) {
            RuntimeException original = new IllegalStateException("after real registration");
            RefusingHandlers[] held = new RefusingHandlers[1];
            f.module =
                    f.module(
                            stage -> {
                                if (stage == PlayerModule.EnableStage.LISTENER_REGISTERED) {
                                    held[0] = f.refuse(false);
                                    throw original;
                                }
                            });
            RuntimeException error = failure(f.module::enable);
            check(
                    error == original && contains(error, held[0].refusal),
                    "startup cleanup attaches independent cause");
            check(f.runtime.handlerCount() > 0, "refused startup cleanup represented honestly");
            unavailable(f.module);
            failure(f.module::enable);
            f.listener().onPlayerJoin(join(f.player(A, "Abandoned")));
            check(!Files.exists(f.file(A)), "abandoned listener cannot create profile");
            held[0].refused = false;
            f.module.disable();
            released(f);
        }
    }

    private static void multi(int mask) throws Exception {
        try (Fixture f = new Fixture()) {
            f.module.enable();
            PlayerService service = f.module.getPlayerService();
            Map<UUID, String> originals = new LinkedHashMap<>();
            for (UUID id : List.of(A, B, C)) {
                f.load(id, "Original" + id.getLeastSignificantBits())
                        .updateName("Unsaved" + id.getLeastSignificantBits());
                originals.put(id, Files.readString(f.file(id)));
            }
            check(
                    service.getLoadedPlayers().stream()
                            .map(CorePlayer::getUniqueId)
                            .toList()
                            .equals(List.of(A, B, C)),
                    "deterministic failure before later independent saves");
            Files.writeString(f.file(OTHER), "schema-version: 99\noperator: untouched\n");
            String unrelated = Files.readString(f.file(OTHER));
            List<UUID> failed = new ArrayList<>();
            for (UUID id : List.of(A, B)) {
                if ((mask & (id.equals(A) ? 1 : 2)) != 0) {
                    failed.add(id);
                    f.future(id);
                }
            }
            RuntimeException error = null;
            try {
                f.module.disable();
            } catch (RuntimeException e) {
                error = e;
            }
            check(
                    (error == null) == failed.isEmpty(),
                    "incomplete shutdown is reported to module caller");
            unavailable(f.module);
            check(f.runtime.handlerCount() == 0, "profile failure cannot retain released listener");
            for (UUID id : List.of(A, B, C)) {
                if (failed.contains(id)) {
                    check(
                            service.getPlayer(id).isPresent()
                                    && service.getPlayer(id)
                                            .orElseThrow()
                                            .getName()
                                            .equals("Unsaved" + id.getLeastSignificantBits()),
                            "failed profile retains original unsaved state");
                    check(
                            Files.readString(f.file(id))
                                    .equals(
                                            originals
                                                    .get(id)
                                                    .replace(
                                                            "schema-version: 1",
                                                            "schema-version: 99")),
                            "future schema source is never overwritten");
                } else {
                    check(
                            f.read(id).getName().equals("Unsaved" + id.getLeastSignificantBits()),
                            "independent later profile is durably saved");
                }
            }
            if (!failed.isEmpty()) {
                PlayerService.SaveFailures report = saveFailures(error);
                check(
                        report != null
                                && report.failures().keySet().equals(java.util.Set.copyOf(failed)),
                        "exact failed UUIDs remain associated with causes");
                for (UUID id : failed) {
                    RuntimeException cause = report.failures().get(id);
                    check(
                            cause.getCause() != null
                                    && containsText(cause, "Future player schema-version"),
                            "root schema cause remains accessible");
                    check(
                            f.logs.stream()
                                    .anyMatch(
                                            log ->
                                                    log.getLevel() == Level.SEVERE
                                                            && log.getMessage()
                                                                    .contains(
                                                                            "INCOMPLETE PLAYER SHUTDOWN")
                                                            && log.getMessage()
                                                                    .contains(id.toString())
                                                            && log.getMessage()
                                                                    .contains("JVM exit")),
                            "high severity UUID and shutdown limitation diagnostic");
                }
                Map<String, String> beforeRetry = f.snapshot();
                RuntimeException refused = null;
                try {
                    f.module.enable();
                } catch (RuntimeException e) {
                    refused = e;
                }
                check(
                        refused != null
                                && f.snapshot().equals(beforeRetry)
                                && f.runtime.handlerCount() == 0,
                        "unsafe re-enable is blocked without replacing pending data");
                failure(f.module::disable);
                check(
                        f.snapshot().equals(beforeRetry),
                        "unresolved retry preserves all evidence and successful profiles");
                // Model deliberate operator reconciliation of a disposable future-version conflict.
                for (UUID id : failed) Files.writeString(f.file(id), originals.get(id));
                f.module.disable();
                for (UUID id : failed) {
                    check(
                            f.read(id).getName().equals("Unsaved" + id.getLeastSignificantBits()),
                            "retry commits retained unsaved values rather than stale disk");
                }
                check(
                        service.getLoadedPlayers().isEmpty(),
                        "successful retry releases retained cache");
                f.module.enable();
                check(
                        f.module.getPlayerService() != service,
                        "resolved retry permits fresh runtime");
                f.module.disable();
            }
            check(
                    Files.readString(f.file(OTHER)).equals(unrelated),
                    "unrelated profile remains byte exact");
            check(f.snapshot().size() == 4, "no extra persistent files or manufactured backups");
            released(f);
        }
    }

    private static void unregister(boolean failSave) throws Exception {
        for (boolean afterEffect : List.of(false, true)) {
            try (Fixture f = new Fixture()) {
                f.module.enable();
                PlayerService service = f.module.getPlayerService();
                f.load(A, "Original").updateName("SavedDespiteUnregister");
                f.load(B, "OriginalB").updateName("RetainedB");
                String originalB = Files.readString(f.file(B));
                if (failSave) f.future(B);
                RefusingHandlers held = f.refuse(afterEffect);
                RuntimeException error = failure(f.module::disable);
                check(
                        f.read(A).getName().equals("SavedDespiteUnregister"),
                        "unregister failure cannot skip persistence");
                check(contains(error, held.refusal), "unregister root cause preserved");
                check(
                        (saveFailures(error) != null) == failSave,
                        "independent listener and save errors remain visible");
                unavailable(f.module);
                check(
                        f.runtime.handlerCount() == (afterEffect ? 0 : 1),
                        "actual refused external handler state is honest");
                Map<String, String> durable = f.snapshot();
                f.listener().onPlayerJoin(join(f.player(C, "StaleJoin")));
                f.listener().onPlayerQuit(quit(f.player(B, "StaleQuit")));
                check(
                        f.snapshot().equals(durable) && service.isLoaded(B) == failSave,
                        "inactive callbacks cannot mutate or unload retained state");
                held.refused = false;
                if (failSave) Files.writeString(f.file(B), originalB);
                f.module.disable();
                if (failSave)
                    check(
                            f.read(B).getName().equals("RetainedB"),
                            "combined failure retry uses original state");
                released(f);
            }
        }
    }

    private static void recovery() throws Exception {
        try (Fixture f = new Fixture()) {
            f.module.enable();
            f.load(A, "Recovery").updateName("Uncommitted");
            Path marker = SafeFileWriter.recoveryFile(f.file(A));
            Files.copy(f.file(A), marker);
            Map<String, String> evidence = f.snapshot();
            RuntimeException error = failure(f.module::disable);
            check(
                    containsText(error, "operator reconciliation"),
                    "existing recovery barrier remains cause");
            check(f.snapshot().equals(evidence), "recovery bytes and target preserved");
            failure(f.module::enable);
            failure(f.module::disable);
            check(f.snapshot().equals(evidence), "retry never deletes recovery evidence");
            // Intentionally leave evidence unresolved until the entire disposable fixture is
            // destroyed.
        }
    }

    private static void stale() throws Exception {
        try (Fixture f = new Fixture()) {
            f.module.enable();
            PlayerListener old = f.listener();
            f.module.disable();
            f.module.enable();
            PlayerService current = f.module.getPlayerService();
            f.load(A, "Current").updateName("UnsavedCurrent");
            Map<String, String> before = f.snapshot();
            old.onPlayerJoin(join(f.player(B, "Stale")));
            old.onPlayerQuit(quit(f.player(A, "StaleQuit")));
            check(
                    f.snapshot().equals(before) && current.isLoaded(A) && !current.isLoaded(B),
                    "old listener cannot act as second runtime");
            f.listener().onPlayerJoin(join(f.player(B, "Fresh")));
            check(current.isLoaded(B), "current listener retains normal authority");
            f.module.disable();
        }
    }

    private static void joinQuit() throws Exception {
        try (Fixture f = new Fixture()) {
            f.module.enable();
            Player player = f.player(A, "Joined");
            f.listener().onPlayerJoin(join(player));
            check(f.read(A).getName().equals("Joined"), "first join creates real default profile");
            CorePlayer loaded = f.module.getPlayerService().getPlayer(A).orElseThrow();
            loaded.updateName("QuitUnsaved");
            String original = Files.readString(f.file(A));
            f.future(A);
            f.listener().onPlayerQuit(quit(player));
            check(
                    f.module.getPlayerService().getPlayer(A).orElseThrow() == loaded,
                    "failed quit retains exact aggregate");
            check(
                    f.logs.stream().anyMatch(log -> log.getMessage().contains("remains cached")),
                    "failed quit diagnostic preserved");
            Files.writeString(f.file(A), original);
            f.listener().onPlayerQuit(quit(player));
            check(
                    !f.module.getPlayerService().isLoaded(A)
                            && f.read(A).getName().equals("QuitUnsaved"),
                    "quit retry saves before eviction");
            f.listener().onPlayerJoin(join(f.player(A, "Renamed")));
            check(
                    f.module
                            .getPlayerService()
                            .getPlayer(A)
                            .orElseThrow()
                            .getName()
                            .equals("Renamed"),
                    "existing join updates name");
            f.future(B, "schema-version: 99\nname: Future\nfirst-join: 1\nlast-join: 1\n");
            f.listener().onPlayerJoin(join(f.player(B, "Invalid")));
            check(
                    f.kicks == 1 && !f.module.getPlayerService().isLoaded(B),
                    "invalid join disconnects without default cache");
            f.module.disable();
        }
    }

    private static void publicSave() throws Exception {
        try (Fixture f = new Fixture()) {
            f.module.enable();
            f.load(A, "OriginalA").updateName("FailedA");
            f.load(B, "OriginalB").updateName("SavedB");
            f.future(A);
            f.module
                    .getPlayerService()
                    .saveAll(); // Existing public void/log-and-continue contract.
            check(
                    f.read(B).getName().equals("SavedB"),
                    "public saveAll retains independent failure isolation");
            check(
                    f.module.getPlayerService().isLoaded(A),
                    "public saveAll never evicts failed profile");
            failure(f.module::disable);
        }
    }

    private static void diagnostic() throws Exception {
        try (Fixture f = new Fixture()) {
            f.module.enable();
            f.load(A, "A").updateName("SavedA");
            f.load(B, "B").updateName("SavedB");
            RuntimeException diagnostic = new IllegalStateException("failed save diagnostic");
            Handler throwing =
                    handler(
                            record -> {
                                if (record.getMessage().startsWith("Saving ")) throw diagnostic;
                            });
            f.runtime.logger.addHandler(throwing);
            RuntimeException error = failure(f.module::disable);
            check(
                    contains(error, diagnostic)
                            && f.read(A).getName().equals("SavedA")
                            && f.read(B).getName().equals("SavedB"),
                    "diagnostic failure cannot skip independent commits");
            unavailable(f.module);
            f.runtime.logger.removeHandler(throwing);
            f.module.disable();
            released(f);
        }
    }

    private static void manager() throws Exception {
        try (Fixture f = new Fixture()) {
            List<String> stopped = new ArrayList<>();
            ModuleManager manager = new ModuleManager(f.runtime.logger);
            manager.register(
                    new CoreModule() {
                        public String getName() {
                            return "Predecessor";
                        }

                        public void enable() {}

                        public void disable() {
                            stopped.add("Predecessor");
                        }
                    });
            manager.register(f.module);
            manager.enableAll();
            f.load(A, "Before").updateName("PendingManager");
            String original = Files.readString(f.file(A));
            f.future(A);
            manager.disableAll();
            check(
                    stopped.equals(List.of("Predecessor")) && manager.getEnabledModules().isEmpty(),
                    "manager continues reverse shutdown and clears enabled list");
            check(
                    f.logs.stream()
                            .anyMatch(
                                    log ->
                                            log.getMessage()
                                                    .contains("Could not disable module Player")),
                    "manager reports incomplete Player shutdown");
            Files.writeString(f.file(A), original);
            manager.disableAll();
            check(
                    f.read(A).getName().equals("Before"),
                    "manager does not schedule or repeat failed module cleanup");
            f.module.disable();
            check(
                    f.read(A).getName().equals("PendingManager"),
                    "explicit same-process retry reaches retained service");
            released(f);
        }
        try (Fixture f = new Fixture()) {
            ModuleManager manager = new ModuleManager(f.runtime.logger);
            boolean[] stopped = {false};
            manager.register(
                    new CoreModule() {
                        public String getName() {
                            return "Predecessor";
                        }

                        public void enable() {}

                        public void disable() {
                            check(
                                    f.runtime.handlerCount() == 0,
                                    "failed module releases handlers before predecessor rollback");
                            stopped[0] = true;
                        }
                    });
            manager.register(f.module);
            f.runtime.failRegistration = 1;
            failure(manager::enableAll);
            check(
                    stopped[0] && manager.getEnabledModules().isEmpty(),
                    "failed startup is never recorded by manager");
            released(f);
        }
    }

    private static void aggregate() throws Exception {
        try (Fixture f = new Fixture()) {
            Files.createDirectories(f.directory());
            String source =
                    "schema-version: 1\nname: Aggregate\nfirst-join: 1000\nlast-join: 2000\n"
                            + "settings:\n  sounds: false\n  scoreboard: false\n  private-messages: false\n"
                            + "  friend-requests: false\n  friend-presence-notifications: true\n"
                            + "  visibility: {mode: FILTERED, show-friends: true, show-staff: true, show-added-users: true, show-game-participants: true, added-players: ['"
                            + OTHER
                            + "']}\n"
                            + "economy: {coins: 1234}\nsocial: {ignored: ['"
                            + OTHER
                            + "']}\n"
                            + "rewards: {online: {processed-playtime-ticks: 600}}\n"
                            + "quests:\n  daily: {cycle-id: '2026-10-25'}\n"
                            + "  active:\n    active: {progress: 4, status: ACTIVE}\n"
                            + "    pending: {progress: 9, status: REWARD_PENDING}\n"
                            + "    done: {progress: 12, status: COMPLETED}\n"
                            + "extension: {unknown: retained}\n";
            Files.writeString(f.file(A), source);
            f.module.enable();
            CorePlayer player = f.load(A, "AggregateLatest");
            player.getSettings().setSoundsEnabled(true);
            player.getSettings().getVisibility().setMode(VisibilityMode.HIDDEN);
            player.getSocial().ignore(C);
            player.getOnlineRewardProgress().setProcessedPlaytimeTicks(777);
            player.getDailyQuestState().setCycleId(DailyQuestCycleId.parse("2026-10-26"));
            PlayerService service = f.module.getPlayerService();
            new RewardService(new EconomyService(service), service, f.runtime.logger)
                    .grantCoins(A, 25, RewardSource.SYSTEM, "lifecycle:aggregate");
            List<UUID> commits = new ArrayList<>();
            f.module.getPlayerSettingsService().addChangeListener(commits::add);
            f.future(A);
            failure(() -> f.module.getPlayerSettingsService().setSoundsEnabled(A, false));
            check(
                    player.getSettings().isSoundsEnabled() && commits.isEmpty(),
                    "failed Settings save restores value without successful observer");
            failure(f.module::disable);
            check(
                    service.getPlayer(A).orElseThrow() == player && commits.isEmpty(),
                    "incomplete shutdown retains exact aggregate without invented commit event");
            Files.writeString(
                    f.file(A),
                    source); // Deliberate reconciliation of disposable future-version conflict.
            f.module.disable();
            CorePlayer reopened = f.read(A);
            check(
                    reopened.getSettings().isSoundsEnabled()
                            && !reopened.getSettings().isScoreboardEnabled()
                            && !reopened.getSettings().isPrivateMessagesEnabled()
                            && !reopened.getSettings().isFriendRequestsEnabled()
                            && reopened.getSettings().isFriendPresenceNotificationsEnabled(),
                    "settings aggregate preserved");
            check(
                    reopened.getSettings()
                                    .getVisibility()
                                    .getAddedPlayers()
                                    .equals(java.util.Set.of(OTHER))
                            && reopened.getSettings().getVisibility().isShowFriends()
                            && reopened.getSettings().getVisibility().isShowStaff()
                            && reopened.getSettings().getVisibility().isShowAddedUsers()
                            && reopened.getSettings().getVisibility().isShowGameParticipants()
                            && reopened.getSettings()
                                    .getVisibility()
                                    .getMode()
                                    .name()
                                    .equals("HIDDEN"),
                    "visibility aggregate preserved");
            check(
                    reopened.getWallet().getCoins() == 1259
                            && reopened.getSocial().isIgnoring(OTHER)
                            && reopened.getSocial().isIgnoring(C),
                    "wallet and Social preserved");
            check(
                    reopened.getOnlineRewardProgress().getProcessedPlaytimeTicks().orElseThrow()
                            == 777,
                    "unsaved reward progress persists");
            check(
                    reopened.getQuestState().snapshot().equals(player.getQuestState().snapshot()),
                    "all quest statuses preserved");
            check(
                    reopened.getDailyQuestState()
                            .cycleId()
                            .equals(player.getDailyQuestState().cycleId()),
                    "daily cycle preserved");
            check(
                    Files.readString(f.file(A)).contains("schema-version: 1")
                            && Files.readString(f.file(A)).contains("unknown: retained"),
                    "schema one and unknown field preserved");
            check(f.snapshot().size() == 1, "no separate Settings or emergency profile file");
        }
    }

    private static PlayerJoinEvent join(Player player) {
        return new PlayerJoinEvent(player, Component.empty());
    }

    @SuppressWarnings("removal")
    private static PlayerQuitEvent quit(Player player) {
        return new PlayerQuitEvent(player, Component.empty());
    }

    private static void unavailable(PlayerModule module) {
        failure(module::getPlayerService);
        failure(module::getPlayerSettingsService);
        check(true, "both inactive getters fail closed");
    }

    private static void released(Fixture f) {
        check(f.runtime.handlerCount() == 0, "owned handlers released before fixture teardown");
        unavailable(f.module);
    }

    private static RuntimeException failure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected lifecycle failure");
        } catch (RuntimeException expected) {
            return expected;
        }
    }

    private static boolean contains(Throwable root, Throwable expected) {
        return root == expected
                || root.getCause() != null && contains(root.getCause(), expected)
                || Arrays.stream(root.getSuppressed()).anyMatch(child -> contains(child, expected));
    }

    private static boolean containsText(Throwable root, String text) {
        return String.valueOf(root.getMessage()).contains(text)
                || root.getCause() != null && containsText(root.getCause(), text)
                || Arrays.stream(root.getSuppressed()).anyMatch(child -> containsText(child, text));
    }

    private static PlayerService.SaveFailures saveFailures(Throwable root) {
        if (root instanceof PlayerService.SaveFailures result) return result;
        if (root == null) return null;
        if (root.getCause() != null) {
            PlayerService.SaveFailures found = saveFailures(root.getCause());
            if (found != null) return found;
        }
        for (Throwable child : root.getSuppressed()) {
            PlayerService.SaveFailures found = saveFailures(child);
            if (found != null) return found;
        }
        return null;
    }

    private static Handler handler(Consumer<LogRecord> publish) {
        return new Handler() {
            public void publish(LogRecord record) {
                publish.accept(record);
            }

            public void flush() {}

            public void close() {}
        };
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture implements AutoCloseable {
        final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture();
        final Path root = Files.createTempDirectory("vapeecore-player-lifecycle-");
        final ConfigService config;
        final PluginManager originalManager = runtime.plugin.getServer().getPluginManager();
        final List<LogRecord> logs = new ArrayList<>();
        final List<RefusingHandlers> refusing = new ArrayList<>();
        Consumer<PlayerListener> registration =
                listener -> originalManager.registerEvents(listener, runtime.plugin);
        PlayerModule module;
        int kicks;

        Fixture() throws Exception {
            ModuleRuntimeFixture.set(runtime.plugin, "dataFolder", root.toFile());
            config = new ConfigService(runtime.plugin);
            runtime.logger.addHandler(handler(logs::add));
            PluginManager manager =
                    ModuleRuntimeFixture.proxy(
                            PluginManager.class,
                            (name, args) -> {
                                if (!name.equals("registerEvents")) throw new AssertionError(name);
                                registration.accept((PlayerListener) args[0]);
                                return null;
                            });
            Server server =
                    ModuleRuntimeFixture.proxy(
                            Server.class,
                            (name, args) -> {
                                if (name.equals("getPluginManager")) return manager;
                                throw new AssertionError(name);
                            });
            ModuleRuntimeFixture.set(runtime.plugin, "server", server);
            module = module(stage -> {});
        }

        PlayerModule module(Consumer<PlayerModule.EnableStage> checkpoint) {
            return new PlayerModule(runtime.plugin, config, runtime.messages, checkpoint);
        }

        Path directory() {
            return root.resolve("players");
        }

        Path file(UUID id) {
            return directory().resolve(id + ".yml");
        }

        CorePlayer load(UUID id, String name) {
            return module.getPlayerService().loadPlayer(id, name);
        }

        CorePlayer read(UUID id) {
            return new FilePlayerRepository(directory(), runtime.logger)
                    .findByUniqueId(id)
                    .orElseThrow();
        }

        PlayerListener listener() {
            return (PlayerListener) runtime.registered.getLast();
        }

        void future(UUID id) throws Exception {
            future(
                    id,
                    Files.readString(file(id)).replace("schema-version: 1", "schema-version: 99"));
        }

        void future(UUID id, String text) throws Exception {
            Files.writeString(file(id), text);
        }

        RefusingHandlers refuse(boolean afterEffect) {
            RefusingHandlers handlers =
                    new RefusingHandlers(listener(), runtime.plugin, afterEffect);
            refusing.add(handlers);
            return handlers;
        }

        Map<String, String> snapshot() throws Exception {
            Map<String, String> result = new LinkedHashMap<>();
            try (var paths = Files.walk(directory())) {
                for (Path path : paths.filter(Files::isRegularFile).sorted().toList())
                    result.put(directory().relativize(path).toString(), Files.readString(path));
            }
            return result;
        }

        Player player(UUID id, String name) {
            return ModuleRuntimeFixture.proxy(
                    Player.class,
                    (method, args) ->
                            switch (method) {
                                case "getUniqueId" -> id;
                                case "getName" -> name;
                                case "kick" -> {
                                    kicks++;
                                    yield null;
                                }
                                case "locale" -> java.util.Locale.ENGLISH;
                                default ->
                                        throw new AssertionError(
                                                "Unexpected player call: " + method);
                            });
        }

        public void close() throws Exception {
            // Safety teardown only: each contract above observes resource/file state first.
            refusing.forEach(handlers -> handlers.refused = false);
            runtime.close();
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    private static final class RefusingHandlers extends HandlerList {
        final Listener target;
        final boolean afterEffect;
        final RuntimeException refusal =
                new IllegalStateException("refused Player handler removal");
        boolean refused = true;

        RefusingHandlers(Listener target, Plugin plugin, boolean afterEffect) {
            this.target = target;
            this.afterEffect = afterEffect;
            register(
                    new RegisteredListener(
                            target, (listener, event) -> {}, EventPriority.NORMAL, plugin, false));
        }

        @Override
        public synchronized void unregister(Listener listener) {
            if (refused && target == listener) {
                if (afterEffect) super.unregister(listener);
                throw refusal;
            }
            super.unregister(listener);
        }
    }
}
