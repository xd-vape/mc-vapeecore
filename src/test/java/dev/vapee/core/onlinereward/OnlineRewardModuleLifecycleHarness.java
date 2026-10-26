package dev.vapee.core.onlinereward;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.reward.RewardModule;
import dev.vapee.core.reward.RewardService;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Real module/services, observable scheduler registrations and disposable aggregate persistence. */
public final class OnlineRewardModuleLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            switch (args[0]) {
                case "late-log" -> lateLog();
                case "duplicate" -> duplicate();
                case "reentrant" -> reentrant();
                case "cancellation" -> cancellation();
                case "stale-task" -> staleTask();
                case "getter" -> transitions();
                case "cadence" -> normal();
                default -> throw new IllegalArgumentException(args[0]);
            }
        } else {
            normal();
            missingDependencies();
            scheduling();
            lateLog();
            duplicate();
            reentrant();
            cancellation();
            cancellationStatus();
            staleTask();
            transitions();
            interruptedCallback();
            behavior();
            persistence();
            manager();
        }
        System.out.println("OnlineRewardModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static void normal() throws Exception {
        try (Fixture f = new Fixture()) {
            unavailable(f.module);
            Actor player = f.actor("Normal", true);
            f.module.enable();
            check(f.tasks.size() == 1 && f.activeTasks() == 1, "one shared processing task");
            Task task = f.tasks.getFirst();
            check(task.owner == f.runtime.plugin && task.isSync() && task.delay == 20 && task.period == 20,
                    "owned synchronous twenty-tick delay and period");
            f.afterSchedule = () -> { throw new AssertionError("second schedule"); };
            task.action.run();
            check(player.reads == 1 && f.progress(player) == 0 && f.balance(player) == 0,
                    "active callback initializes actual playtime without retroactive grant");
            player.ticks = 1200;
            task.action.run();
            check(f.balance(player) == 7 && f.progress(player) == 1200, "one interval grants configured coins");
            check(f.repository.saves == 0 && f.dirty().contains(player.id), "persistence stays deferred to Reward owner");
            int reads = player.reads;
            f.module.disable();
            released(f);
            task.action.run();
            check(player.reads == reads && f.balance(player) == 7, "disabled callback has no player effects");
            check(f.players.getPlayer(player.id).isPresent() && f.playerOwner.getPlayerService() == f.players
                            && f.rewardOwner.getRewardService() == f.rewards && f.dirty().contains(player.id),
                    "cleanup preserves borrowed owners, aggregate and pending Reward tracking");
            check(f.repository.saves == 0, "OnlineReward shutdown neither saves nor flushes");
            int cancellations = task.cancellations;
            f.module.disable();
            check(task.cancellations == cancellations, "repeated disable has no external effects");
        }
    }

    private static void missingDependencies() throws Exception {
        for (boolean player : List.of(true, false)) {
            try (Fixture f = new Fixture()) {
                ModuleRuntimeFixture.set(player ? f.playerOwner : f.rewardOwner,
                        player ? "playerService" : "rewardService", null);
                failure(f.module::enable);
                check(f.tasks.isEmpty(), "missing borrowed dependency schedules nothing");
                released(f);
            }
        }
    }

    private static void scheduling() throws Exception {
        try (Fixture f = new Fixture()) {
            f.refuseScheduling = true;
            check(failure(f.module::enable) == f.schedulingFailure, "scheduler failure identity preserved");
            check(f.tasks.isEmpty(), "pre-registration scheduler failure owns no task");
            released(f);
            f.refuseScheduling = false;
            f.module.enable();
            check(f.activeTasks() == 1, "schedule failure permits clean retry");
            f.module.disable();
        }
        try (Fixture f = new Fixture()) {
            Actor player = f.actor("NoHandle", true);
            f.afterSchedule = () -> { throw f.schedulingFailure; };
            check(failure(f.module::enable) == f.schedulingFailure, "post-registration scheduler failure stays primary");
            check(f.activeTasks() == 1 && f.tasks.getFirst().cancellations == 0,
                    "throw-before-return leaves no exact acquired handle; no plugin-wide cancellation");
            f.tasks.getFirst().action.run();
            check(player.reads == 0 && !f.core(player).getOnlineRewardProgress().isInitialized(),
                    "unreturned task cannot authorize abandoned startup");
            unavailable(f.module);
        }
    }

    private static void lateLog() throws Exception {
        for (boolean refuseCleanup : List.of(false, true)) {
            try (Fixture f = new Fixture()) {
                Actor player = f.actor("Late", true);
                RuntimeException original = new IllegalStateException("late OnlineReward logger");
                Handler handler = handler(record -> {
                    if (record.getMessage().startsWith("OnlineReward module enabled")) {
                        unavailable(f.module);
                        f.tasks.getFirst().action.run();
                        if (refuseCleanup) f.tasks.getFirst().refuseCancel = true;
                        throw original;
                    }
                });
                f.runtime.logger.addHandler(handler);
                RuntimeException thrown = failure(f.module::enable);
                check(thrown == original, "original late logger exception identity preserved");
                check(f.tasks.getFirst().cancellations == 1, "late failure attempts exact task rollback");
                check(f.activeTasks() == (refuseCleanup ? 1 : 0), "late failure cancels acquired processing task");
                unavailable(f.module);
                f.tasks.getFirst().action.run();
                check(player.reads == 0 && f.balance(player) == 0
                                && !f.core(player).getOnlineRewardProgress().isInitialized()
                                && f.repository.saves == 0 && f.dirty().isEmpty(),
                        "failed startup performs no coins, progress, notifications or persistence");
                check(f.runtime.sentMessages == 0, "failed startup sends no notification");
                if (refuseCleanup) {
                    check(contains(thrown, f.tasks.getFirst().cancelFailure) && thrown.getSuppressed().length > 0,
                            "cleanup failure is suppressed on original startup exception");
                    check(ModuleRuntimeFixture.get(f.module, "pendingCancellation") == f.tasks.getFirst(),
                            "failed startup retains exact refused task handle");
                } else check(thrown.getSuppressed().length == 0, "successful rollback adds no false cleanup error");
                f.runtime.logger.removeHandler(handler);
                f.tasks.getFirst().refuseCancel = false;
                f.module.disable();
                released(f);
            }
        }
    }

    private static void duplicate() throws Exception {
        try (Fixture f = new Fixture()) {
            f.module.enable();
            OnlineRewardService first = f.module.getOnlineRewardService();
            RuntimeException rejected = null;
            try { f.module.enable(); } catch (RuntimeException expected) { rejected = expected; }
            check(rejected != null && f.tasks.size() == 1 && f.module.getOnlineRewardService() == first,
                    "duplicate enable preserves existing runtime and single task");
            f.module.disable();
        }
    }

    private static void reentrant() throws Exception {
        try (Fixture f = new Fixture()) {
            f.afterSchedule = () -> {
                f.afterSchedule = () -> { };
                RuntimeException rejected = null;
                try { f.module.enable(); } catch (RuntimeException expected) { rejected = expected; }
                check(rejected != null && f.tasks.size() == 1, "reentrant enable acquires no second task");
                unavailable(f.module);
            };
            f.module.enable();
            check(f.tasks.size() == 1, "outer enable survives rejected reentry");
            f.module.disable();
        }
    }

    private static void cancellation() throws Exception {
        try (Fixture f = new Fixture()) {
            Actor player = f.actor("Refused", true);
            f.module.enable();
            OnlineRewardService oldService = f.module.getOnlineRewardService();
            Task old = f.tasks.getFirst();
            old.refuseCancel = true;
            RuntimeException error = null;
            try { f.module.disable(); } catch (RuntimeException expected) { error = expected; }
            check(error != null && contains(error, old.cancelFailure), "refused cancellation is reported");
            check(f.activeTasks() == 1 && !old.cancelled, "refused cancellation remains registered");
            unavailable(f.module);
            check(ModuleRuntimeFixture.get(f.module, "onlineRewardService") == null
                            && ModuleRuntimeFixture.get(f.module, "messageRenderer") == null,
                    "refused cancellation releases published runtime references");
            old.action.run();
            check(player.reads == 0 && f.balance(player) == 0, "refused old callback is inert");
            RuntimeException blocked = null;
            try { f.module.enable(); } catch (RuntimeException expected) { blocked = expected; }
            check(blocked != null && f.tasks.size() == 1 && old.cancellations == 2,
                    "pending cancellation blocks new task acquisition and retries exact handle");
            check(ModuleRuntimeFixture.get(f.module, "pendingCancellation") == old,
                    "pending cleanup preserves its sole owned handle");
            old.refuseCancel = false;
            f.module.enable();
            check(old.cancelled && f.activeTasks() == 1 && f.tasks.size() == 2,
                    "enable reconciles pending cancellation before acquiring fresh task");
            check(f.module.getOnlineRewardService() != oldService, "successful retry publishes fresh service");
            f.module.disable();
            released(f);
        }
        try (Fixture f = new Fixture()) {
            f.module.enable();
            Task task = f.tasks.getFirst();
            task.throwAfterCancel = true;
            check(contains(failure(f.module::disable), task.cancelFailure), "after-effect cancel failure is reported");
            check(task.cancelled && ModuleRuntimeFixture.get(f.module, "pendingCancellation") == null,
                    "confirmed after-effect cancellation needs no retained handle");
            unavailable(f.module);
            int attempts = task.cancellations;
            f.module.disable();
            check(task.cancellations == attempts, "confirmed cancellation is not repeated");
            f.module.enable();
            check(f.activeTasks() == 1 && f.tasks.size() == 2, "after-effect failure permits fresh activation");
            f.module.disable();
        }
    }

    private static void cancellationStatus() throws Exception {
        for (boolean silentRefusal : List.of(false, true)) {
            try (Fixture f = new Fixture()) {
                f.module.enable();
                Task task = f.tasks.getFirst();
                task.refuseStatus = !silentRefusal;
                task.silentRefusal = silentRefusal;
                RuntimeException failure = failure(f.module::disable);
                check(failure.getSuppressed().length > 0 && ModuleRuntimeFixture.get(f.module, "pendingCancellation") == task,
                        "unconfirmed cancellation retains ownership even without cancel exception");
                unavailable(f.module);
                task.refuseStatus = false;
                task.silentRefusal = false;
                f.module.disable();
                released(f);
            }
        }
    }

    private static void staleTask() throws Exception {
        try (Fixture f = new Fixture()) {
            Actor player = f.actor("Stale", true);
            f.module.enable();
            Task old = f.tasks.getFirst();
            OnlineRewardService first = f.module.getOnlineRewardService();
            old.action.run();
            f.module.disable();
            int reads = player.reads;
            old.action.run();
            check(player.reads == reads, "old callback has no authority after disable");
            f.module.enable();
            check(f.module.getOnlineRewardService() != first, "re-enable creates fresh service generation");
            player.ticks = 1200;
            old.action.run();
            check(player.reads == reads && f.balance(player) == 0 && f.progress(player) == 0,
                    "old callback cannot process fresh generation");
            f.tasks.getLast().action.run();
            check(f.balance(player) == 7 && f.progress(player) == 1200, "fresh callback alone can grant reward");
            f.module.disable();
        }
    }

    private static void transitions() throws Exception {
        try (Fixture f = new Fixture()) {
            Actor player = f.actor("Transition", true);
            unavailable(f.module);
            f.afterSchedule = () -> {
                unavailable(f.module);
                f.tasks.getLast().action.run();
                check(player.reads == 0, "startup-boundary callback is inert");
                failure(f.module::disable);
            };
            Handler logger = handler(record -> {
                if (record.getMessage().startsWith("OnlineReward module enabled")) unavailable(f.module);
            });
            f.runtime.logger.addHandler(logger);
            f.module.enable();
            Task task = f.tasks.getFirst();
            task.beforeCancel = () -> {
                unavailable(f.module);
                task.action.run();
                check(player.reads == 0, "cleanup-boundary callback is inert");
                failure(f.module::enable);
                failure(f.module::disable);
            };
            f.module.disable();
            released(f);
        }
    }

    private static void interruptedCallback() throws Exception {
        try (Fixture f = new Fixture()) {
            Actor first = f.actor("Interrupt", true);
            Actor later = f.actor("Later", true);
            f.core(first).getOnlineRewardProgress().setProcessedPlaytimeTicks(0);
            first.ticks = 1200;
            f.module.enable();
            first.afterRead = f.module::disable;
            f.tasks.getFirst().action.run();
            check(f.balance(first) == 0 && f.progress(first) == 0 && later.reads == 0
                            && f.runtime.sentMessages == 0, "reentrant authority revocation stops grant and subsequent players");
            released(f);
        }
    }

    private static void behavior() throws Exception {
        try (Fixture f = new Fixture()) {
            Actor broken = f.actor("Broken", true);
            Actor player = f.actor("Eligible", true);
            Actor unloaded = f.actor("Unloaded", false);
            Actor offline = f.actor("Offline", true);
            f.runtime.online.remove(offline.player);
            broken.failStatistic = true;
            f.module.enable();
            Task task = f.tasks.getFirst();
            player.ticks = 100;
            task.action.run();
            check(f.progress(player) == 100 && f.balance(player) == 0, "uninitialized legacy time is rebased without reward");
            check(offline.reads == 0 && unloaded.reads == 1, "only online roster is adapted; unloaded profiles are skipped");
            player.ticks = 1350;
            task.action.run();
            check(f.balance(player) == 7 && f.progress(player) == 1300 && f.runtime.sentMessages == 1,
                    "single interval preserves remainder and existing notification");
            player.ticks = 5000;
            task.action.run();
            check(f.balance(player) == 28 && f.progress(player) == 4900 && f.runtime.sentMessages == 2,
                    "multiple intervals aggregate one grant and one notification");
            task.action.run();
            check(f.balance(player) == 28 && f.runtime.sentMessages == 2, "unchanged playtime cannot grant twice");
            f.configure(true, 1, 11, false);
            player.ticks = 6100;
            task.action.run();
            check(f.balance(player) == 39 && f.runtime.sentMessages == 2, "live coin and notification config affects next callback");
            f.configure(false, 1, 11, false);
            player.ticks = 12100;
            task.action.run();
            check(f.progress(player) == 12100 && f.balance(player) == 39, "disabled config consumes time without reward");
            f.configure(true, 1, 11, false);
            task.action.run();
            check(f.balance(player) == 39, "disabled-time progress is never paid retroactively");
            player.ticks = 50;
            task.action.run();
            check(f.progress(player) == 50 && f.balance(player) == 39, "decreasing statistic rebases without coins");
            check(broken.reads == player.reads && f.repository.saves == 0,
                    "individual processing failure preserves other players and delegated persistence");
            f.module.disable();
        }
    }

    private static void persistence() throws Exception {
        try (Fixture f = new Fixture()) {
            ModuleRuntimeFixture.set(f.runtime.plugin, "dataFolder", f.directory.toFile());
            PlayerModule players = new PlayerModule(f.runtime.plugin, f.config, f.runtime.messages);
            players.enable();
            PlayerService profiles = players.getPlayerService();
            EconomyModule economy = ModuleRuntimeFixture.dependency(EconomyModule.class,
                    Map.of("economyService", new EconomyService(profiles)));
            RewardModule rewards = new RewardModule(f.runtime.plugin, players, economy);
            rewards.enable();
            OnlineRewardModule online = new OnlineRewardModule(f.runtime.plugin, f.config, players, rewards, f.runtime.messages);
            UUID id = UUID.randomUUID();
            CorePlayer profile = profiles.loadPlayer(id, "Persistent");
            profile.getOnlineRewardProgress().setProcessedPlaytimeTicks(30);
            profiles.savePlayer(id);
            Actor actor = f.actor("PersistenceAdapter", false);
            actor.id = id;
            actor.ticks = 1230;
            online.enable();
            Task processing = f.tasks.getLast();
            processing.action.run();
            check(profile.getWallet().getCoins() == 7 && profile.getOnlineRewardProgress().getProcessedPlaytimeTicks().orElseThrow() == 1230,
                    "real services mutate wallet and progress in the same aggregate");
            Path path = f.directory.resolve("players").resolve(id + ".yml");
            byte[] before = Files.readAllBytes(path);
            online.disable();
            check(Arrays.equals(before, Files.readAllBytes(path)) && profiles.isLoaded(id),
                    "OnlineReward disable leaves real profile bytes and loaded owner intact");
            check(ModuleRuntimeFixture.get(rewards.getRewardService(), "dirtyPlayers") instanceof Set<?> dirty && dirty.contains(id),
                    "OnlineReward disable preserves borrowed Reward flush obligation");
            rewards.disable();
            players.disable();
            FilePlayerRepository repository = new FilePlayerRepository(f.directory.resolve("players"), f.runtime.logger);
            repository.initialize();
            CorePlayer stored = repository.findByUniqueId(id).orElseThrow();
            check(stored.getWallet().getCoins() == 7 && stored.getOnlineRewardProgress().getProcessedPlaytimeTicks().orElseThrow() == 1230,
                    "existing owners persist matching reward balance and progress on clean shutdown");
            players.enable();
            profiles = players.getPlayerService();
            CorePlayer restarted = profiles.loadPlayer(id, "Persistent");
            ModuleRuntimeFixture.set(economy, "economyService", new EconomyService(profiles));
            rewards.enable();
            online.enable();
            Task next = f.tasks.getLast();
            next.action.run();
            check(restarted.getWallet().getCoins() == 7 && next != processing && processing.cancelled,
                    "clean restart creates new task without replaying persisted interval");
            actor.ticks = 2430;
            next.action.run();
            check(restarted.getWallet().getCoins() == 14, "new generation rewards only new elapsed interval");
            online.disable();
            rewards.disable();
            players.disable();
        }
    }

    private static void manager() throws Exception {
        for (boolean failedOnline : List.of(true, false)) {
            try (Fixture f = new Fixture()) {
                List<String> stopped = new ArrayList<>();
                ModuleManager manager = new ModuleManager(f.runtime.logger);
                manager.register(owner("Player", () -> check(f.activeTasks() == 0, "Player stops after OnlineReward cancellation"), stopped));
                manager.register(owner("Reward", () -> check(f.activeTasks() == 0, "Reward stops after OnlineReward cancellation"), stopped));
                manager.register(f.module);
                if (failedOnline) f.runtime.logger.addHandler(handler(record -> {
                    if (record.getMessage().startsWith("OnlineReward module enabled")) throw f.schedulingFailure;
                }));
                else manager.register(new CoreModule() {
                    public String getName() { return "Quest"; }
                    public void enable() { throw f.schedulingFailure; }
                    public void disable() { throw new AssertionError("failed consumer must not be disabled by manager"); }
                });
                check(failure(manager::enableAll) == f.schedulingFailure, "manager preserves startup failure identity");
                check(stopped.equals(List.of("Reward", "Player")) && manager.getEnabledModules().isEmpty(),
                        "manager rollback preserves reverse dependency shutdown");
                released(f);
            }
        }
        try (Fixture f = new Fixture()) {
            List<String> stopped = new ArrayList<>();
            ModuleManager manager = new ModuleManager(f.runtime.logger);
            manager.register(owner("Player", () -> check(f.activeTasks() == 0, "normal Player shutdown follows task cancellation"), stopped));
            manager.register(owner("Reward", () -> check(f.activeTasks() == 0, "normal Reward shutdown follows task cancellation"), stopped));
            manager.register(f.module);
            manager.register(owner("Quest", () -> check(f.activeTasks() == 1, "consumer stops before OnlineReward"), stopped));
            manager.enableAll();
            manager.disableAll();
            check(stopped.equals(List.of("Quest", "Reward", "Player")), "normal manager reverse shutdown preserves consumers and owners");
            released(f);
        }
    }

    private static CoreModule owner(String name, Runnable disable, List<String> stopped) {
        return new CoreModule() {
            public String getName() { return name; }
            public void enable() { }
            public void disable() { stopped.add(name); disable.run(); }
        };
    }

    /** Used by the retained legacy entry point instead of exact callback/cleanup source spelling. */
    public static void assertTaskLifecycle() throws Exception { normal(); staleTask(); transitions(); }

    private static void released(Fixture f) {
        unavailable(f.module);
        check(f.activeTasks() == 0, "all acquired tasks cancelled before fixture teardown");
        check(ModuleRuntimeFixture.get(f.module, "processingTask") == null
                        && ModuleRuntimeFixture.get(f.module, "onlineRewardService") == null
                        && ModuleRuntimeFixture.get(f.module, "messageRenderer") == null
                        && ModuleRuntimeFixture.get(f.module, "pendingCancellation") == null,
                "all published and cleanup ownership references released");
    }

    private static void unavailable(OnlineRewardModule module) {
        RuntimeException rejected = null;
        try { module.getOnlineRewardService(); } catch (RuntimeException expected) { rejected = expected; }
        check(rejected != null, "inactive getter rejects abandoned runtime");
    }

    private static RuntimeException failure(Runnable action) {
        try { action.run(); throw new AssertionError("expected lifecycle failure"); }
        catch (RuntimeException expected) { return expected; }
    }

    private static boolean contains(Throwable root, Throwable expected) {
        return root == expected || root.getCause() != null && contains(root.getCause(), expected)
                || Arrays.stream(root.getSuppressed()).anyMatch(child -> contains(child, expected));
    }

    private static Handler handler(Consumer<LogRecord> publish) {
        return new Handler() {
            public void publish(LogRecord record) { publish.accept(record); }
            public void flush() { }
            public void close() { }
        };
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class Fixture implements AutoCloseable {
        final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture();
        final Path directory = Files.createTempDirectory("vapeecore-online-lifecycle-");
        final MemoryRepository repository = new MemoryRepository();
        final PlayerService players = new PlayerService(repository, runtime.logger);
        final RewardService rewards = new RewardService(new EconomyService(players), players, runtime.logger);
        final PlayerModule playerOwner = ModuleRuntimeFixture.dependency(PlayerModule.class, Map.of("playerService", players));
        final RewardModule rewardOwner = ModuleRuntimeFixture.dependency(RewardModule.class, Map.of("rewardService", rewards, "active", true));
        final List<Task> tasks = new ArrayList<>();
        final RuntimeException schedulingFailure = new IllegalStateException("refused OnlineReward schedule");
        final ConfigService config;
        final OnlineRewardModule module;
        Runnable afterSchedule = () -> { };
        boolean refuseScheduling;

        Fixture() throws Exception {
            var constructor = ConfigService.class.getDeclaredConstructor(Path.class, Logger.class);
            constructor.setAccessible(true);
            config = constructor.newInstance(directory.resolve("config.yml"), runtime.logger);
            configure(true, 1, 7, true);
            module = new OnlineRewardModule(runtime.plugin, config, playerOwner, rewardOwner, runtime.messages);
            BukkitScheduler scheduler = ModuleRuntimeFixture.proxy(BukkitScheduler.class, (name, args) -> {
                if (!name.equals("runTaskTimer") || !(args[1] instanceof Runnable)) throw new AssertionError(name);
                if (refuseScheduling) throw schedulingFailure;
                Task task = new Task((Plugin) args[0], (Runnable) args[1], (long) args[2], (long) args[3]);
                tasks.add(task);
                afterSchedule.run();
                return task;
            });
            runtime.serverCalls = (name, args) -> {
                if (name.equals("getScheduler")) return scheduler;
                throw new AssertionError(name);
            };
        }

        void configure(boolean enabled, long minutes, long coins, boolean message) throws Exception {
            Files.writeString(directory.resolve("config.yml"), "online-rewards:\n  enabled: " + enabled
                    + "\n  interval-minutes: " + minutes + "\n  coins: " + coins
                    + "\n  message:\n    enabled: " + message + "\n    format: '<coins>'\n");
            config.prepareReload().apply();
        }

        Actor actor(String name, boolean loaded) {
            Actor actor = new Actor();
            actor.id = UUID.randomUUID();
            if (loaded) players.loadPlayer(actor.id, name);
            repository.saves = 0;
            actor.player = ModuleRuntimeFixture.proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> actor.id;
                case "getName" -> name;
                case "isOnline" -> true;
                case "locale" -> java.util.Locale.US;
                case "getLocale" -> "en_us";
                case "getStatistic" -> {
                    check(args[0] == Statistic.PLAY_ONE_MINUTE, "adapter reads canonical Minecraft playtime statistic");
                    actor.reads++;
                    actor.afterRead.run();
                    if (actor.failStatistic) throw new IllegalStateException("individual player statistic refused");
                    yield actor.ticks;
                }
                case "sendMessage" -> { runtime.sentMessages++; yield null; }
                default -> throw new AssertionError("Unexpected player call: " + method);
            });
            runtime.online.add(actor.player);
            return actor;
        }

        CorePlayer core(Actor actor) { return players.getPlayer(actor.id).orElseThrow(); }
        long balance(Actor actor) { return core(actor).getWallet().getCoins(); }
        long progress(Actor actor) { return core(actor).getOnlineRewardProgress().getProcessedPlaytimeTicks().orElseThrow(); }
        long activeTasks() { return tasks.stream().filter(task -> !task.cancelled).count(); }
        @SuppressWarnings("unchecked") Set<UUID> dirty() { return (Set<UUID>) ModuleRuntimeFixture.get(rewards, "dirtyPlayers"); }

        public void close() throws Exception {
            // Safety teardown is deliberately after every resource observation.
            tasks.forEach(task -> {
                task.refuseCancel = false;
                task.throwAfterCancel = false;
                task.silentRefusal = false;
                task.beforeCancel = () -> { };
                task.cancel();
            });
            runtime.close();
            Path root = directory.toAbsolutePath().normalize();
            if (!root.startsWith(Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())
                    || !root.getFileName().toString().startsWith("vapeecore-online-lifecycle-")) throw new IllegalStateException("Unsafe fixture path");
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }

    private static final class Actor {
        UUID id;
        Player player;
        int ticks;
        int reads;
        boolean failStatistic;
        Runnable afterRead = () -> { };
    }

    private static final class Task implements BukkitTask {
        final Plugin owner;
        final Runnable action;
        final long delay;
        final long period;
        final RuntimeException cancelFailure = new IllegalStateException("refused OnlineReward cancellation");
        boolean cancelled;
        boolean refuseCancel;
        boolean throwAfterCancel;
        boolean refuseStatus;
        boolean silentRefusal;
        int cancellations;
        Runnable beforeCancel = () -> { };
        Task(Plugin owner, Runnable action, long delay, long period) {
            this.owner = owner;
            this.action = action;
            this.delay = delay;
            this.period = period;
        }
        public int getTaskId() { return System.identityHashCode(this); }
        public Plugin getOwner() { return owner; }
        public boolean isSync() { return true; }
        public boolean isCancelled() {
            if (refuseStatus) throw new IllegalStateException("cancellation status unavailable");
            return cancelled;
        }
        public void cancel() {
            cancellations++;
            beforeCancel.run();
            if (refuseCancel) throw cancelFailure;
            if (!silentRefusal) cancelled = true;
            if (throwAfterCancel) throw cancelFailure;
        }
    }

    private static final class MemoryRepository implements PlayerRepository {
        int saves;
        public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.empty(); }
        public boolean exists(UUID id) { return false; }
        public void save(CorePlayer player) { saves++; }
    }
}
