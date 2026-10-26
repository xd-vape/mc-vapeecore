package dev.vapee.core.quest;

import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.reward.RewardModule;
import dev.vapee.core.reward.RewardService;

import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredListener;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Actual module, HandlerLists, services and disk repository; only external faults are controlled.
 */
final class QuestLifecycleFixture implements AutoCloseable {
    final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture();
    final Path directory = Files.createTempDirectory("vapeecore-quest-lifecycle-");
    final FilePlayerRepository disk =
            new FilePlayerRepository(directory.resolve("players"), runtime.logger);
    final FaultRepository repository;
    final PlayerService players;
    final RewardService rewards;
    final PlayerModule playerOwner;
    final RewardModule rewardOwner;
    final QuestModule module;
    final List<Task> tasks = new ArrayList<>();
    final List<RefusingHandlers> refusingLists = new ArrayList<>();
    final RuntimeException schedulingFailure =
            new IllegalStateException("controlled Quest scheduler refusal");
    final RuntimeException registrationFailure =
            new IllegalStateException("controlled Quest registration refusal");
    final RuntimeException rosterFailure =
            new IllegalStateException("controlled Quest online roster refusal");
    Runnable beforeRegistration = () -> {};
    Runnable afterRegistration = () -> {};
    Runnable afterSchedule = () -> {};
    boolean refuseRegistrationBefore;
    boolean refuseRegistrationAfter;
    boolean refuseScheduling;
    boolean refuseRoster;

    QuestLifecycleFixture() throws Exception {
        disk.initialize();
        repository = new FaultRepository(disk);
        players = new PlayerService(repository, runtime.logger);
        rewards = new RewardService(new EconomyService(players), players, runtime.logger);
        playerOwner =
                ModuleRuntimeFixture.dependency(
                        PlayerModule.class, Map.of("playerService", players));
        rewardOwner =
                ModuleRuntimeFixture.dependency(
                        RewardModule.class, Map.of("rewardService", rewards, "active", true));
        Server original = runtime.plugin.getServer();
        PluginManager manager =
                ModuleRuntimeFixture.proxy(
                        PluginManager.class,
                        (name, args) -> {
                            if (!name.equals("registerEvents")) throw new AssertionError(name);
                            beforeRegistration.run();
                            if (refuseRegistrationBefore) throw registrationFailure;
                            original.getPluginManager()
                                    .registerEvents((Listener) args[0], (Plugin) args[1]);
                            afterRegistration.run();
                            if (refuseRegistrationAfter) throw registrationFailure;
                            return null;
                        });
        Server server =
                (Server)
                        Proxy.newProxyInstance(
                                Server.class.getClassLoader(),
                                new Class<?>[] {Server.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("getPluginManager")) return manager;
                                    if (method.getName().equals("getOnlinePlayers") && refuseRoster)
                                        throw rosterFailure;
                                    try {
                                        return method.invoke(original, args);
                                    } catch (InvocationTargetException exception) {
                                        throw exception.getCause();
                                    }
                                });
        ModuleRuntimeFixture.set(runtime.plugin, "server", server);
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
        module = new QuestModule(runtime.plugin, playerOwner, rewardOwner, runtime.messages);
    }

    Actor actor(String name, int ticks) {
        return actor(name, ticks, players);
    }

    Actor actor(String name, int ticks, PlayerService owner) {
        Actor actor = new Actor();
        actor.id = UUID.randomUUID();
        actor.ticks = ticks;
        owner.loadPlayer(actor.id, name);
        actor.player =
                ModuleRuntimeFixture.proxy(
                        Player.class,
                        (method, args) ->
                                switch (method) {
                                    case "getUniqueId" -> actor.id;
                                    case "getName" -> name;
                                    case "isOnline" -> actor.online;
                                    case "locale" -> java.util.Locale.US;
                                    case "getLocale" -> "en_us";
                                    case "getStatistic" -> {
                                        if (args[0] != Statistic.PLAY_ONE_MINUTE)
                                            throw new AssertionError("Wrong playtime statistic");
                                        actor.reads++;
                                        actor.onRead.run();
                                        if (actor.readFailure != null) throw actor.readFailure;
                                        yield actor.ticks;
                                    }
                                    case "sendMessage" -> {
                                        runtime.sentMessages++;
                                        yield null;
                                    }
                                    default ->
                                            throw new AssertionError(
                                                    "Unexpected Player call " + method);
                                });
        runtime.online.add(actor.player);
        repository.attempts.clear();
        return actor;
    }

    void define(String id, long target, long coins) {
        module.getDefinitionRegistry()
                .replaceAll(
                        List.of(
                                new QuestDefinition(
                                        id,
                                        id,
                                        "fixture",
                                        QuestProgressKey.of("playtime:minute"),
                                        target,
                                        coins)));
    }

    CorePlayer core(Actor actor) {
        return players.getPlayer(actor.id).orElseThrow();
    }

    PlayerQuestProgress progress(Actor actor, String id) {
        return core(actor).getQuestState().snapshot().get(id);
    }

    CorePlayer stored(Actor actor) {
        return disk.findByUniqueId(actor.id).orElseThrow();
    }

    QuestListener listener() {
        return (QuestListener)
                runtime.registered.stream()
                        .filter(QuestListener.class::isInstance)
                        .reduce((a, b) -> b)
                        .orElseThrow();
    }

    long activeTasks() {
        return tasks.stream().filter(task -> !task.cancelled).count();
    }

    long handlers() {
        return HandlerList.getRegisteredListeners(runtime.plugin).stream()
                .filter(handler -> handler.getListener() instanceof QuestListener)
                .count();
    }

    RefusingHandlers refuseUnregister() {
        RefusingHandlers result = new RefusingHandlers(listener(), runtime.plugin);
        refusingLists.add(result);
        return result;
    }

    public void close() throws Exception {
        // Resource observations always precede this safety teardown.
        refusingLists.forEach(
                list -> {
                    list.refuse = false;
                    list.throwAfterEffect = false;
                });
        tasks.forEach(
                task -> {
                    task.refuse = false;
                    task.afterEffect = false;
                    task.silent = false;
                    task.beforeCancel = () -> {};
                    task.cancel();
                });
        runtime.close();
        refusingLists.forEach(list -> HandlerList.getHandlerLists().remove(list));
        Path root = directory.toAbsolutePath().normalize();
        if (!root.startsWith(
                        Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath().normalize())
                || !root.getFileName().toString().startsWith("vapeecore-quest-lifecycle-"))
            throw new IllegalStateException("Unsafe fixture cleanup");
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
        }
    }

    static final class Actor {
        UUID id;
        Player player;
        int ticks;
        int reads;
        boolean online = true;
        Runnable onRead = () -> {};
        RuntimeException readFailure;
    }

    static final class Task implements BukkitTask {
        final Plugin owner;
        final Runnable action;
        final long delay;
        final long period;
        final RuntimeException cancelFailure =
                new IllegalStateException("controlled Quest cancel refusal");
        boolean cancelled;
        boolean refuse;
        boolean afterEffect;
        boolean silent;
        boolean unknownStatus;
        int cancellations;
        Runnable beforeCancel = () -> {};

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
            if (unknownStatus) throw new IllegalStateException("unknown task status");
            return cancelled;
        }

        public void cancel() {
            cancellations++;
            beforeCancel.run();
            if (refuse) throw cancelFailure;
            if (!silent) cancelled = true;
            if (afterEffect) throw cancelFailure;
        }
    }

    static final class FaultRepository implements PlayerRepository {
        final FilePlayerRepository delegate;
        final Map<UUID, RuntimeException> failures = new LinkedHashMap<>();
        final Map<UUID, Integer> attempts = new LinkedHashMap<>();
        Consumer<UUID> beforeSave = id -> {};

        FaultRepository(FilePlayerRepository delegate) {
            this.delegate = delegate;
        }

        public Optional<CorePlayer> findByUniqueId(UUID id) {
            return delegate.findByUniqueId(id);
        }

        public boolean exists(UUID id) {
            return delegate.exists(id);
        }

        public void save(CorePlayer player) {
            UUID id = player.getUniqueId();
            attempts.merge(id, 1, Integer::sum);
            beforeSave.accept(id);
            if (failures.containsKey(id)) throw failures.get(id);
            delegate.save(player);
        }
    }

    static final class RefusingHandlers extends HandlerList {
        final Listener owned;
        final RuntimeException failure =
                new IllegalStateException("controlled Quest listener refusal");
        boolean refuse = true;
        boolean throwAfterEffect;

        RefusingHandlers(Listener owned, Plugin plugin) {
            this.owned = owned;
            register(
                    new RegisteredListener(
                            owned, (listener, event) -> {}, EventPriority.MONITOR, plugin, false));
        }

        public synchronized void unregister(Listener listener) {
            if (listener == owned && refuse) throw failure;
            super.unregister(listener);
            if (listener == owned && throwAfterEffect) throw failure;
        }
    }
}
