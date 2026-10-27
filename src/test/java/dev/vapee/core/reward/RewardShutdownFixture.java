package dev.vapee.core.reward;

import static dev.vapee.core.module.ModuleRuntimeFixture.allocate;
import static dev.vapee.core.module.ModuleRuntimeFixture.dependency;
import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.proxy;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.rank.RankModule;
import dev.vapee.core.rank.staff.StaffHierarchyService;

import org.bukkit.entity.Player;
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Real owners/services/files; only repository and Bukkit external outcomes are controlled. */
public final class RewardShutdownFixture implements AutoCloseable {
    public final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture("coins");
    public final Path directory = Files.createTempDirectory("vapeecore-reward-shutdown-");
    public final ConfigService config;
    public final PlayerModule playerOwner;
    public final EconomyModule economyOwner;
    public final RewardModule module;
    public final ModuleManager manager;
    public final List<Task> tasks = new ArrayList<>();
    public final List<RefusingHandlers> refusing = new ArrayList<>();
    public final List<LogRecord> logs = new ArrayList<>();
    public PlayerService players;
    public FaultRepository repository;
    public final UUID a = UUID.fromString("65c00000-0000-0000-0000-000000000001");
    public final UUID b = UUID.fromString("65c00000-0000-0000-0000-000000000002");
    public Runnable afterSchedule = () -> {};
    public boolean failSchedule;
    public final RuntimeException scheduleFailure =
            new IllegalStateException("Reward scheduler before-effect failure");

    public RewardShutdownFixture() throws Exception {
        this(stage -> {});
    }

    public RewardShutdownFixture(Consumer<RewardModule.EnableStage> checkpoint) throws Exception {
        set(runtime.plugin, "dataFolder", directory.toFile());
        var ctor = ConfigService.class.getDeclaredConstructor(Path.class, Logger.class);
        ctor.setAccessible(true);
        config = ctor.newInstance(directory.resolve("config.yml"), runtime.logger);
        configure();
        playerOwner = new PlayerModule(runtime.plugin, config, runtime.messages);
        var rank =
                dependency(
                        RankModule.class,
                        Map.of("staffHierarchyService", allocate(StaffHierarchyService.class)));
        economyOwner =
                new EconomyModule(
                        runtime.plugin,
                        playerOwner,
                        rank,
                        runtime.messages,
                        new CommandHelpRenderer(runtime.messages));
        module = new RewardModule(runtime.plugin, playerOwner, economyOwner, checkpoint);
        manager = new ModuleManager(runtime.logger);
        manager.register(playerOwner);
        manager.register(economyOwner);
        manager.register(module);
        runtime.serverCalls =
                (m, args) -> {
                    if (!m.equals("getScheduler")) throw new AssertionError(m);
                    return proxy(
                            BukkitScheduler.class,
                            (n, x) -> {
                                if (!n.equals("runTaskTimer"))
                                    throw new AssertionError("Unowned scheduler action " + n);
                                if (failSchedule) throw scheduleFailure;
                                Task t =
                                        new Task(
                                                runtime.plugin,
                                                (Runnable) x[1],
                                                (long) x[2],
                                                (long) x[3]);
                                tasks.add(t);
                                afterSchedule.run();
                                return t;
                            });
                };
        runtime.logger.addHandler(
                new Handler() {
                    public void publish(LogRecord r) {
                        logs.add(r);
                    }

                    public void flush() {}

                    public void close() {}
                });
    }

    private void configure() throws Exception {
        Files.writeString(
                directory.resolve("config.yml"),
                "online-rewards:\n"
                        + "  enabled: true\n"
                        + "  interval-minutes: 1\n"
                        + "  coins: 7\n"
                        + "  message:\n"
                        + "    enabled: false\n"
                        + "    format: '<coins>'\n");
        config.load();
    }

    public void enableOwners() {
        playerOwner.enable();
        economyOwner.enable();
        attachRepository();
    }

    public void start() {
        enableOwners();
        module.enable();
    }

    public void attachRepository() {
        players = playerOwner.getPlayerService();
        repository = new FaultRepository((FilePlayerRepository) get(playerOwner, "repository"));
        set(players, "repository", repository);
    }

    public CorePlayer load(UUID id) {
        CorePlayer p = players.loadPlayer(id, id.equals(a) ? "Alpha" : "Beta");
        repository.attempts.clear();
        repository.successes.clear();
        return p;
    }

    public void pair() {
        load(a);
        load(b);
        repository.attempts.clear();
        repository.successes.clear();
    }

    public long stored(UUID id) {
        return org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                        directory.resolve("players").resolve(id + ".yml").toFile())
                .getLong("economy.coins");
    }

    public byte[] bytes(UUID id) throws Exception {
        return Files.readAllBytes(directory.resolve("players").resolve(id + ".yml"));
    }

    public CorePlayer cold(UUID id) {
        return new FilePlayerRepository(directory.resolve("players"), runtime.logger)
                .findByUniqueId(id)
                .orElseThrow();
    }

    public int rewardHandlers() {
        return (int)
                HandlerList.getRegisteredListeners(runtime.plugin).stream()
                        .filter(r -> r.getListener() instanceof RewardListener)
                        .count();
    }

    public RewardListener listener() {
        return (RewardListener)
                runtime.registered.stream()
                        .filter(l -> l instanceof RewardListener)
                        .reduce((a, b) -> b)
                        .orElseThrow();
    }

    public long running() {
        return tasks.stream().filter(t -> !t.cancelled).count();
    }

    public Player actor(UUID id) {
        return proxy(
                Player.class,
                (m, args) ->
                        switch (m) {
                            case "getUniqueId" -> id;
                            case "getName" -> "Actor";
                            case "isOnline" -> true;
                            case "getStatistic" -> 0;
                            default -> throw new AssertionError(m);
                        });
    }

    public RefusingHandlers refuseListener(boolean after) {
        var h = new RefusingHandlers(listener(), runtime.plugin, after);
        refusing.add(h);
        return h;
    }

    @Override
    public void close() throws Exception {
        refusing.forEach(h -> h.refuse = false);
        runtime.close();
        tasks.forEach(t -> t.cancelled = true);
        try (var paths = Files.walk(directory)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    public static final class FaultRepository implements PlayerRepository {
        public final FilePlayerRepository disk;
        public final Map<UUID, RuntimeException> failures = new LinkedHashMap<>();
        public final Set<UUID> afterEffect = new HashSet<>();
        public final List<UUID> attempts = new ArrayList<>(), successes = new ArrayList<>();
        public Consumer<CorePlayer> beforeSave = p -> {};

        FaultRepository(FilePlayerRepository disk) {
            this.disk = disk;
        }

        public Optional<CorePlayer> findByUniqueId(UUID id) {
            return disk.findByUniqueId(id);
        }

        public boolean exists(UUID id) {
            return disk.exists(id);
        }

        public Set<UUID> findUniqueIdsByName(String name) {
            return disk.findUniqueIdsByName(name);
        }

        public void save(CorePlayer player) {
            UUID id = player.getUniqueId();
            attempts.add(id);
            beforeSave.accept(player);
            RuntimeException fail = failures.get(id);
            if (fail != null && !afterEffect.contains(id)) throw fail;
            disk.save(player);
            successes.add(id);
            if (fail != null) throw fail;
        }

        public RuntimeException fail(UUID id) {
            var fault = new IllegalStateException("Controlled save outcome for " + id);
            failures.put(id, fault);
            return fault;
        }
    }

    public static final class Task implements BukkitTask {
        public final Plugin owner;
        public final Runnable callback;
        public final long delay, period;
        public boolean cancelled, refuse, afterEffect, ineffective, unknownStatus;
        public int cancellations;
        public Runnable duringCancel = () -> {};
        public final RuntimeException cancelFailure =
                new IllegalStateException("Controlled Reward cancellation outcome");

        Task(Plugin owner, Runnable callback, long delay, long period) {
            this.owner = owner;
            this.callback = callback;
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
            if (unknownStatus) throw new IllegalStateException("Unknown cancellation status");
            return cancelled;
        }

        public void cancel() {
            cancellations++;
            duringCancel.run();
            if (refuse) throw cancelFailure;
            if (!ineffective) cancelled = true;
            if (afterEffect) throw cancelFailure;
        }
    }

    public static final class RefusingHandlers extends HandlerList {
        public final RewardListener owned;
        public final boolean after;
        public boolean refuse = true;
        public final RuntimeException failure =
                new IllegalStateException("Controlled Reward unregister outcome");

        RefusingHandlers(RewardListener owned, Plugin plugin, boolean after) {
            this.owned = owned;
            this.after = after;
            register(
                    new RegisteredListener(
                            owned, (l, e) -> {}, EventPriority.LOWEST, plugin, false));
        }

        @Override
        public synchronized void unregister(Listener listener) {
            if (refuse && listener == owned && !after) throw failure;
            super.unregister(listener);
            if (refuse && listener == owned) throw failure;
        }
    }
}
