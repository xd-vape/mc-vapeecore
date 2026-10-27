package dev.vapee.core.reward;

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

import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/** Real RewardModule, real registered HandlerLists and executable scheduler callbacks. */
public final class RewardModuleLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            switch (args[0]) {
                case "late-log" -> lateLog();
                case "cleanup" -> refusedCancellation();
                case "duplicate" -> duplicate();
                case "reentrant" -> reentrant();
                case "restart" -> restart();
                case "stale-task" -> staleTask();
                case "shutdown" -> shutdown();
                default -> throw new IllegalArgumentException(args[0]);
            }
        } else {
            normal();
            missingDependency();
            partialRegistration();
            stageFailures();
            schedulingFailure();
            lateLog();
            refusedCancellation();
            refusedHandlers();
            cleanupAfterEffect();
            duplicate();
            reentrant();
            restart();
            staleTask();
            shutdown();
            flushFailure();
            persistence();
            managerRollback();
        }
        System.out.println("RewardModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static void normal() throws Exception {
        try (Fixture f = new Fixture()) {
            RewardModule module = f.module();
            unavailable(module);
            module.enable();
            RewardService rewards = module.getRewardService();
            check(f.runtime.handlerCount() == 1, "one actual quit handler");
            check(f.tasks.size() == 1 && f.activeTasks() == 1, "one shared task");
            check(
                    f.tasks.getFirst().owner == f.runtime.plugin
                            && f.tasks.getFirst().isSync()
                            && f.tasks.getFirst().delay == 20
                            && f.tasks.getFirst().period == 20,
                    "owned synchronous 20-tick cadence");
            UUID player = f.load();
            rewards.grantCoins(player, 4, RewardSource.SYSTEM, "lifecycle:normal");
            rewards.grantCoins(player, 6, RewardSource.SYSTEM, "lifecycle:normal");
            check(f.repository.saves == 0 && rewards.isDirty(player), "grants remain deferred");
            f.tasks.getFirst().action.run();
            check(
                    f.repository.saves == 1 && f.repository.balances.get(player) == 10L,
                    "actual scheduled callback flushes current aggregate once");
            check(!rewards.isDirty(player), "successful batch clears dirty marker");
            module.disable();
            released(f, module);
            check(
                    f.players.isLoaded(player) && f.economy.getCoins(player).orElseThrow() == 10,
                    "borrowed owners remain usable");
            int cancellations = f.tasks.getFirst().cancellations;
            module.disable();
            check(
                    f.tasks.getFirst().cancellations == cancellations,
                    "repeated disable acquires/releases nothing");
        }
    }

    private static void missingDependency() throws Exception {
        for (boolean missingPlayer : List.of(true, false)) {
            try (Fixture f = new Fixture()) {
                ModuleRuntimeFixture.set(
                        missingPlayer ? f.playerOwner : f.economyOwner,
                        missingPlayer ? "playerService" : "economyService",
                        null);
                RewardModule module = f.module();
                failure(module::enable);
                released(f, module);
                check(
                        f.runtime.registered.isEmpty() && f.tasks.isEmpty(),
                        "missing dependency has no external effects");
            }
        }
    }

    private static void partialRegistration() throws Exception {
        try (Fixture f = new Fixture()) {
            f.runtime.failRegistration = 1;
            RewardModule module = f.module();
            RuntimeException error = failure(module::enable);
            check(
                    error.getMessage().equals("injected registration failure"),
                    "actual registration failure stays primary");
            released(f, module);
            check(
                    f.runtime.registered.size() == 1 && f.tasks.isEmpty(),
                    "after-effect registration rolled back before scheduling");
            f.runtime.failRegistration = 0;
            module.enable();
            check(
                    f.runtime.handlerCount() == 1 && f.activeTasks() == 1,
                    "registration retry installs one runtime");
            module.disable();
        }
    }

    private static void stageFailures() throws Exception {
        for (RewardModule.EnableStage stage : RewardModule.EnableStage.values()) {
            try (Fixture f = new Fixture()) {
                RuntimeException original = new IllegalStateException("stage " + stage);
                RewardModule module =
                        f.module(
                                actual -> {
                                    if (actual == stage) throw original;
                                });
                check(
                        failure(module::enable) == original,
                        "original startup exception preserved at " + stage);
                released(f, module);
                check(f.repository.saves == 0, "failed enable performs no persistence at " + stage);
            }
        }
    }

    private static void schedulingFailure() throws Exception {
        try (Fixture f = new Fixture()) {
            f.refuseScheduling = true;
            RewardModule module = f.module();
            check(
                    failure(module::enable) == f.schedulingFailure,
                    "scheduler failure remains primary");
            released(f, module);
            f.refuseScheduling = false;
            module.enable();
            check(
                    f.activeTasks() == 1 && f.runtime.handlerCount() == 1,
                    "schedule failure retry installs one runtime");
            module.disable();
        }
    }

    private static void lateLog() throws Exception {
        try (Fixture f = new Fixture()) {
            RewardModule module =
                    f.module(
                            stage -> {
                                if (stage == RewardModule.EnableStage.RUNTIME_PUBLISHED)
                                    unavailable(f.current);
                            });
            f.current = module;
            RuntimeException original = new IllegalStateException("late Reward logger");
            Handler log =
                    handler(
                            record -> {
                                if (record.getMessage().startsWith("Reward module enabled"))
                                    throw original;
                            });
            f.runtime.logger.addHandler(log);
            check(
                    failure(module::enable) == original,
                    "original startup exception preserved after final logging");
            check(f.runtime.handlerCount() == 0, "listener rollback leaves no registered handler");
            check(f.activeTasks() == 0, "task rollback cancels acquired timer");
            released(f, module);
            f.runtime.logger.removeHandler(log);
            module.enable();
            check(
                    f.activeTasks() == 1 && f.runtime.handlerCount() == 1,
                    "late failure can re-enable exactly once");
            module.disable();
        }
    }

    private static void refusedCancellation() throws Exception {
        try (Fixture f = new Fixture()) {
            RewardModule module = f.module();
            module.enable();
            RewardService old = module.getRewardService();
            UUID player = f.load();
            old.grantCoins(player, 9, RewardSource.SYSTEM, "lifecycle:cleanup");
            Task task = f.tasks.getFirst();
            task.refuseCancel = true;
            RuntimeException error = failure(module::disable);
            check(
                    contains(error, task.cancelFailure),
                    "refused cancellation is explicitly reported");
            check(
                    f.runtime.handlerCount() == 0 && f.repository.balances.get(player) == 9L,
                    "cancel failure cannot skip independent flush and handler release");
            check(
                    old.dirtyPlayerCount() == 0,
                    "dirty release still attempted after cancel failure");
            unavailable(module);
            check(f.activeTasks() == 1, "genuinely refused task remains observable");
            int saves = f.repository.saves;
            task.action.run();
            check(f.repository.saves == saves, "uncancelled old task loses active authority");
            failure(module::enable);
            check(
                    f.tasks.size() == 1 && f.runtime.handlerCount() == 0,
                    "refused cleanup blocks new acquisition");
            task.refuseCancel = false;
            module.disable();
            module.enable();
            check(
                    task.cancelled && module.getRewardService() != old && f.activeTasks() == 1,
                    "explicit disable retry reconciles cleanup before fresh runtime");
            module.disable();
            released(f, module);
        }
    }

    private static void refusedHandlers() throws Exception {
        try (Fixture f = new Fixture()) {
            RuntimeException original = new IllegalStateException("startup with refused handler");
            RefusingHandlers[] refused = new RefusingHandlers[1];
            RewardModule module =
                    f.module(
                            stage -> {
                                if (stage == RewardModule.EnableStage.TASK_SCHEDULED) {
                                    refused[0] =
                                            new RefusingHandlers(
                                                    f.runtime.registered.getLast(),
                                                    f.runtime.plugin);
                                    throw original;
                                }
                            });
            try {
                check(
                        failure(module::enable) == original,
                        "cleanup refusal preserves exact original exception");
                check(
                        contains(original, refused[0].refusal),
                        "listener refusal retained as suppressed cause");
                check(
                        f.activeTasks() == 0 && f.runtime.handlerCount() > 0,
                        "listener refusal cannot skip task cancellation");
                unavailable(module);
                failure(module::enable);
                check(f.tasks.size() == 1, "handler refusal blocks reacquisition");
                refused[0].refused = false;
                module.disable();
                released(f, module);
            } finally {
                if (refused[0] != null) refused[0].refused = false;
            }
        }
    }

    private static void cleanupAfterEffect() throws Exception {
        try (Fixture f = new Fixture()) {
            RuntimeException original = new IllegalStateException("startup with cancel error");
            RewardModule module =
                    f.module(
                            stage -> {
                                if (stage == RewardModule.EnableStage.TASK_SCHEDULED) {
                                    f.tasks.getFirst().throwAfterCancel = true;
                                    throw original;
                                }
                            });
            check(
                    failure(module::enable) == original,
                    "original startup exception survives cleanup error");
            check(
                    contains(original, f.tasks.getFirst().cancelFailure),
                    "cancel error has contextual suppressed cause");
            check(
                    f.runtime.handlerCount() == 0 && f.activeTasks() == 0,
                    "after-effect cancellation error cannot skip handler release");
            unavailable(module);
            f.tasks.getFirst().throwAfterCancel = false;
            module.disable();
            released(f, module);
        }
    }

    private static void duplicate() throws Exception {
        try (Fixture f = new Fixture()) {
            RewardModule module = f.module();
            module.enable();
            RewardService first = module.getRewardService();
            boolean rejected = false;
            try {
                module.enable();
            } catch (RuntimeException expected) {
                rejected = true;
            }
            check(
                    rejected
                            && module.getRewardService() == first
                            && f.tasks.size() == 1
                            && f.runtime.handlerCount() == 1,
                    "duplicate enable preserves existing runtime without duplicate resources");
            module.disable();
            released(f, module);
        }
    }

    private static void reentrant() throws Exception {
        try (Fixture f = new Fixture()) {
            RewardModule module = f.module();
            boolean[] rejected = new boolean[1];
            f.afterSchedule =
                    () -> {
                        f.afterSchedule = () -> {};
                        try {
                            module.enable();
                        } catch (RuntimeException expected) {
                            rejected[0] = true;
                        }
                    };
            module.enable();
            check(
                    rejected[0] && f.tasks.size() == 1 && f.runtime.handlerCount() == 1,
                    "reentrant enable installs no second runtime");
            module.disable();
            released(f, module);
        }
    }

    private static void restart() throws Exception {
        try (Fixture f = new Fixture()) {
            RewardModule module = f.module();
            module.enable();
            RewardService first = module.getRewardService();
            Listener listener = f.runtime.registered.getLast();
            Task task = f.tasks.getLast();
            module.disable();
            MemoryRepository replacementRepository = new MemoryRepository();
            PlayerService replacementPlayers =
                    new PlayerService(replacementRepository, f.runtime.logger);
            ModuleRuntimeFixture.set(f.playerOwner, "playerService", replacementPlayers);
            ModuleRuntimeFixture.set(
                    f.economyOwner, "economyService", new EconomyService(replacementPlayers));
            UUID player = UUID.randomUUID();
            replacementPlayers.loadPlayer(player, "Replacement");
            replacementRepository.saves = 0;
            module.enable();
            RewardService fresh = module.getRewardService();
            check(
                    first != fresh
                            && listener != f.runtime.registered.getLast()
                            && task != f.tasks.getLast(),
                    "re-enable creates fresh service listener and task");
            check(
                    f.runtime.handlerCount() == 1 && f.activeTasks() == 1,
                    "restart has one registration and task");
            check(
                    HandlerList.getRegisteredListeners(f.runtime.plugin).stream()
                            .noneMatch(handler -> handler.getListener() == listener),
                    "old listener has no registered event authority");
            fresh.grantCoins(player, 11, RewardSource.SYSTEM, "lifecycle:restart");
            f.tasks.getLast().action.run();
            check(
                    replacementRepository.saves == 1
                            && replacementRepository.balances.get(player) == 11L,
                    "re-enable reacquires current borrowed services");
            module.disable();
            released(f, module);
        }
    }

    private static void staleTask() throws Exception {
        try (Fixture f = new Fixture()) {
            RewardModule module = f.module();
            module.enable();
            Task old = f.tasks.getFirst();
            module.disable();
            module.enable();
            UUID player = f.load();
            RewardService fresh = module.getRewardService();
            fresh.grantCoins(player, 3, RewardSource.SYSTEM, "lifecycle:stale");
            old.action.run();
            check(
                    f.repository.saves == 0 && fresh.isDirty(player),
                    "old task cannot flush a fresh runtime");
            f.tasks.getLast().action.run();
            check(f.repository.saves == 1, "fresh task retains its own authority");
            module.disable();
        }
    }

    public record ShutdownObservation(
            boolean cancelBeforeFlush,
            boolean flushBeforeUnregister,
            boolean dirtyTrackingCleared,
            boolean singleSynchronousTimer) {}

    public static ShutdownObservation observeShutdown() throws Exception {
        try (Fixture f = new Fixture()) {
            RewardModule module = f.module();
            module.enable();
            RewardService rewards = module.getRewardService();
            UUID player = f.load();
            rewards.grantCoins(player, 7, RewardSource.SYSTEM, "lifecycle:shutdown");
            boolean[] order = new boolean[2];
            f.repository.beforeSave =
                    () -> {
                        order[0] = f.tasks.getFirst().cancelled;
                        order[1] = f.runtime.handlerCount() == 1;
                    };
            module.disable();
            check(
                    f.repository.saves == 1 && f.repository.balances.get(player) == 7L,
                    "shutdown flush persists pending reward once");
            released(f, module);
            return new ShutdownObservation(
                    order[0],
                    order[1],
                    rewards.dirtyPlayerCount() == 0,
                    f.tasks.size() == 1 && f.tasks.getFirst().isSync());
        }
    }

    private static void shutdown() throws Exception {
        ShutdownObservation observed = observeShutdown();
        check(observed.cancelBeforeFlush(), "shutdown cancels timer before pending flush");
        check(observed.flushBeforeUnregister(), "shutdown flush precedes quit listener removal");
        check(observed.dirtyTrackingCleared(), "shutdown releases old dirty tracking");
        check(observed.singleSynchronousTimer(), "exactly one synchronous timer");
    }

    private static void flushFailure() throws Exception {
        try (Fixture f = new Fixture()) {
            RewardModule module = f.module();
            module.enable();
            UUID player = f.load();
            RewardService rewards = module.getRewardService();
            rewards.grantCoins(player, 17, RewardSource.SYSTEM, "lifecycle:flush-failure");
            f.repository.failSave = true;
            f.tasks.getFirst().action.run();
            check(
                    rewards.isDirty(player) && f.economy.getCoins(player).orElseThrow() == 17,
                    "ordinary failed batch keeps wallet and dirty retry");
            Handler log =
                    handler(
                            record -> {
                                if (record.getLevel().intValue()
                                        >= java.util.logging.Level.WARNING.intValue()) {
                                    throw new IllegalStateException("diagnostic failed");
                                }
                            });
            f.runtime.logger.addHandler(log);
            RuntimeException error = failure(module::disable);
            check(
                    error.getSuppressed().length != 0,
                    "shutdown flush diagnostic failure remains visible");
            check(
                    f.runtime.handlerCount() == 0
                            && f.activeTasks() == 0
                            && rewards.dirtyPlayerCount() == 1,
                    "flush failure retains dirty work and cannot skip independent resource release");
            unavailable(module);
            check(
                    f.players.isLoaded(player) && f.economy.getCoins(player).orElseThrow() == 17,
                    "Player shutdown remains persistence fallback");
            f.runtime.logger.removeHandler(log);
            f.repository.failSave = false;
            f.players.saveAll();
            check(
                    f.repository.balances.get(player) == 17L,
                    "borrowed Player fallback still saves current aggregate");
            module.disable();
        }
    }

    private static void persistence() throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-reward-lifecycle-");
        try (Fixture f = new Fixture()) {
            FilePlayerRepository repository = new FilePlayerRepository(directory, f.runtime.logger);
            repository.initialize();
            PlayerService players = new PlayerService(repository, f.runtime.logger);
            UUID id = UUID.randomUUID();
            CorePlayer player = players.loadPlayer(id, "Durable");
            player.getSettings().setSoundsEnabled(false);
            players.savePlayer(id);
            ModuleRuntimeFixture.set(f.playerOwner, "playerService", players);
            ModuleRuntimeFixture.set(f.economyOwner, "economyService", new EconomyService(players));
            Path file = directory.resolve(id + ".yml");
            byte[] original = Files.readAllBytes(file);
            RewardModule module = f.module();
            module.enable();
            module.disable();
            check(
                    Arrays.equals(original, Files.readAllBytes(file)),
                    "clean lifecycle leaves durable profile bytes unchanged");
            module.enable();
            module.getRewardService()
                    .grantCoins(id, 23, RewardSource.SYSTEM, "lifecycle:persistence");
            module.disable();
            CorePlayer persisted = repository.findByUniqueId(id).orElseThrow();
            check(
                    persisted.getWallet().getCoins() == 23
                            && !persisted.getSettings().isSoundsEnabled(),
                    "shutdown retains aggregate fields and persists pending coins");
            check(
                    Files.readString(file).contains("schema-version: 1"),
                    "existing player schema remains one");
            try (var files = Files.list(directory)) {
                check(files.count() == 1, "no feature file or manufactured backup");
            }
        } finally {
            try (var files = Files.walk(directory)) {
                for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(file);
            }
        }
    }

    private static void managerRollback() throws Exception {
        try (Fixture f = new Fixture()) {
            List<String> stopped = new ArrayList<>();
            ModuleManager manager = new ModuleManager(f.runtime.logger);
            manager.register(
                    owner(
                            "PlayerOwner",
                            () ->
                                    check(
                                            f.runtime.handlerCount() == 0 && f.activeTasks() == 0,
                                            "failed module cleans itself before predecessor shutdown"),
                            stopped));
            manager.register(owner("EconomyOwner", () -> {}, stopped));
            RewardModule reward =
                    f.module(
                            stage -> {
                                if (stage == RewardModule.EnableStage.TASK_SCHEDULED)
                                    throw new IllegalStateException("manager startup");
                            });
            manager.register(reward);
            failure(manager::enableAll);
            check(
                    stopped.equals(List.of("EconomyOwner", "PlayerOwner")),
                    "manager retains reverse predecessor rollback");
            check(
                    manager.getEnabledModules().isEmpty(),
                    "failed Reward is never recorded by manager");
            unavailable(reward);
        }
    }

    private static CoreModule owner(String name, Runnable disable, List<String> stopped) {
        return new CoreModule() {
            public String getName() {
                return name;
            }

            public void enable() {}

            public void disable() {
                stopped.add(name);
                disable.run();
            }
        };
    }

    private static void released(Fixture f, RewardModule module) {
        check(f.runtime.handlerCount() == 0, "all owned handlers removed before fixture teardown");
        check(f.activeTasks() == 0, "all acquired tasks cancelled before fixture teardown");
        check(
                ModuleRuntimeFixture.get(module, "rewardService") == null
                        && ModuleRuntimeFixture.get(module, "rewardListener") == null
                        && ModuleRuntimeFixture.get(module, "flushTask") == null,
                "all published runtime references released");
        unavailable(module);
    }

    private static void unavailable(RewardModule module) {
        failure(module::getRewardService);
        check(true, "inactive getter fails closed");
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
        final MemoryRepository repository = new MemoryRepository();
        final PlayerService players = new PlayerService(repository, runtime.logger);
        final EconomyService economy = new EconomyService(players);
        final PlayerModule playerOwner =
                ModuleRuntimeFixture.dependency(
                        PlayerModule.class, Map.of("playerService", players));
        final EconomyModule economyOwner =
                ModuleRuntimeFixture.dependency(
                        EconomyModule.class, Map.of("economyService", economy));
        final List<Task> tasks = new ArrayList<>();
        final RuntimeException schedulingFailure = new IllegalStateException("refused schedule");
        Runnable afterSchedule = () -> {};
        boolean refuseScheduling;
        RewardModule current;

        Fixture() throws Exception {
            BukkitScheduler scheduler =
                    ModuleRuntimeFixture.proxy(
                            BukkitScheduler.class,
                            (name, args) -> {
                                if (!name.equals("runTaskTimer") || !(args[1] instanceof Runnable))
                                    throw new AssertionError(name);
                                if (refuseScheduling) throw schedulingFailure;
                                Task task =
                                        new Task(
                                                (Plugin) args[0],
                                                (Runnable) args[1],
                                                (long) args[2],
                                                (long) args[3]);
                                tasks.add(task);
                                afterSchedule.run();
                                return task;
                            });
            runtime.serverCalls =
                    (name, args) -> {
                        if (name.equals("getScheduler")) return scheduler;
                        throw new AssertionError(name);
                    };
        }

        RewardModule module() {
            return module(stage -> {});
        }

        RewardModule module(Consumer<RewardModule.EnableStage> checkpoint) {
            return new RewardModule(runtime.plugin, playerOwner, economyOwner, checkpoint);
        }

        UUID load() {
            UUID id = UUID.randomUUID();
            players.loadPlayer(id, "RewardPlayer");
            repository.saves = 0;
            return id;
        }

        long activeTasks() {
            return tasks.stream().filter(task -> !task.cancelled).count();
        }

        public void close() throws Exception {
            // Safety teardown only; assertions always inspect actual effects before this point.
            tasks.forEach(
                    task -> {
                        task.refuseCancel = false;
                        task.throwAfterCancel = false;
                        task.cancel();
                    });
            runtime.close();
        }
    }

    private static final class Task implements BukkitTask {
        final Plugin owner;
        final Runnable action;
        final long delay;
        final long period;
        final RuntimeException cancelFailure =
                new IllegalStateException("refused Reward cancellation");
        boolean cancelled;
        boolean refuseCancel;
        boolean throwAfterCancel;
        int cancellations;

        Task(Plugin owner, Runnable action, long delay, long period) {
            this.owner = owner;
            this.action = action;
            this.delay = delay;
            this.period = period;
        }

        public int getTaskId() {
            return System.identityHashCode(this);
        }

        public Plugin getOwner() {
            return owner;
        }

        public boolean isSync() {
            return true;
        }

        public boolean isCancelled() {
            return cancelled;
        }

        public void cancel() {
            cancellations++;
            if (refuseCancel) throw cancelFailure;
            cancelled = true;
            if (throwAfterCancel) throw cancelFailure;
        }
    }

    private static final class MemoryRepository implements PlayerRepository {
        final Map<UUID, Long> balances = new HashMap<>();
        int saves;
        boolean failSave;
        Runnable beforeSave = () -> {};

        public Optional<CorePlayer> findByUniqueId(UUID id) {
            return Optional.empty();
        }

        public boolean exists(UUID id) {
            return balances.containsKey(id);
        }

        public void save(CorePlayer player) {
            beforeSave.run();
            if (failSave) throw new IllegalStateException("repository refused save");
            saves++;
            balances.put(player.getUniqueId(), player.getWallet().getCoins());
        }
    }

    private static final class RefusingHandlers extends HandlerList {
        final Listener target;
        final RuntimeException refusal =
                new IllegalStateException("refused Reward handler removal");
        boolean refused = true;

        RefusingHandlers(Listener target, Plugin plugin) {
            this.target = target;
            register(
                    new RegisteredListener(
                            target, (listener, event) -> {}, EventPriority.NORMAL, plugin, false));
        }

        public synchronized void unregister(Listener listener) {
            if (refused && target == listener) throw refusal;
            super.unregister(listener);
        }
    }
}
