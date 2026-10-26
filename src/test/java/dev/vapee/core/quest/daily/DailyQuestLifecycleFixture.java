package dev.vapee.core.quest.daily;

import static dev.vapee.core.module.ModuleRuntimeFixture.allocate;
import static dev.vapee.core.module.ModuleRuntimeFixture.dependency;
import static dev.vapee.core.module.ModuleRuntimeFixture.proxy;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.quest.QuestChangeListener;
import dev.vapee.core.quest.QuestDefinitionRegistry;
import dev.vapee.core.quest.QuestModule;
import dev.vapee.core.quest.QuestService;
import dev.vapee.core.reward.RewardService;

import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/** Real module/domain/files and HandlerLists, with controlled external registration boundaries. */
public final class DailyQuestLifecycleFixture implements AutoCloseable {
    public final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture("quests");
    public final Path directory = Files.createTempDirectory("vapeecore-daily-lifecycle-");
    public final FilePlayerRepository repository =
            new FilePlayerRepository(directory.resolve("players"), runtime.logger);
    public final PlayerService players = new PlayerService(repository, runtime.logger);
    public final PlayerModule playerModule =
            dependency(PlayerModule.class, Map.of("playerService", players));
    public final QuestDefinitionRegistry registry = new QuestDefinitionRegistry();
    public final QuestService quests =
            new QuestService(
                    registry,
                    players,
                    new RewardService(new EconomyService(players), players, runtime.logger),
                    runtime.logger);
    public final QuestModule questModule = allocate(QuestModule.class);
    public final Faults<QuestChangeListener> changes = new Faults<>();
    public final Faults<java.util.function.Consumer<org.bukkit.entity.Player>> locales =
            new Faults<>();
    public final List<Task> tasks = new ArrayList<>();
    public final RuntimeException startup = new IllegalStateException("daily startup fault");
    public int registration;
    public int failRegistration;
    public boolean registrationAfter;
    public boolean failSchedule;
    public boolean scheduleAfter;
    public boolean failLog;
    public Runnable duringLog = () -> {};
    public Runnable duringRegistration = () -> {};

    public DailyQuestLifecycleFixture() throws Exception {
        set(runtime.plugin, "dataFolder", directory.toFile());
        config("daily", 10);
        Object borrowed =
                allocate(Class.forName("dev.vapee.core.quest.QuestModule$RuntimeResources"));
        set(borrowed, "registry", registry);
        set(borrowed, "service", quests);
        set(questModule, "runtime", borrowed);
        set(questModule, "active", true);
        set(quests, "changeListeners", changes);
        set(runtime.messages.localization(), "localeListeners", locales);
        Server original = runtime.plugin.getServer();
        PluginManager actual = original.getPluginManager();
        PluginManager manager =
                proxy(
                        PluginManager.class,
                        (m, a) -> {
                            if (!m.equals("registerEvents")) throw new AssertionError(m);
                            int index = ++registration;
                            duringRegistration.run();
                            if (index == failRegistration && !registrationAfter) throw startup;
                            actual.registerEvents((Listener) a[0], runtime.plugin);
                            if (index == failRegistration) throw startup;
                            return null;
                        });
        BukkitScheduler scheduler =
                proxy(
                        BukkitScheduler.class,
                        (m, a) -> {
                            if (!m.equals("runTaskTimer"))
                                throw new AssertionError("Unexpected scheduler call " + m);
                            if (failSchedule && !scheduleAfter) throw startup;
                            Task task = new Task((Runnable) a[1], (long) a[2], (long) a[3]);
                            tasks.add(task);
                            if (failSchedule) throw startup;
                            return task;
                        });
        runtime.serverCalls =
                (m, a) -> {
                    if (m.equals("getScheduler")) return scheduler;
                    throw new AssertionError(m);
                };
        Server wrapped =
                proxy(
                        Server.class,
                        (m, a) -> {
                            if (m.equals("getPluginManager")) return manager;
                            if (m.equals("getPlayer")) return original.getPlayer((UUID) a[0]);
                            try {
                                var method =
                                        Arrays.stream(Server.class.getMethods())
                                                .filter(
                                                        x ->
                                                                x.getName().equals(m)
                                                                        && x.getParameterCount()
                                                                                == (a == null
                                                                                        ? 0
                                                                                        : a.length))
                                                .findFirst()
                                                .orElseThrow();
                                return method.invoke(original, a);
                            } catch (java.lang.reflect.InvocationTargetException ex) {
                                throw (RuntimeException) ex.getCause();
                            } catch (ReflectiveOperationException ex) {
                                throw new AssertionError(ex);
                            }
                        });
        set(runtime.plugin, "server", wrapped);
        runtime.logger.addHandler(
                new Handler() {
                    public void publish(LogRecord r) {
                        if (r.getMessage().startsWith("DailyQuest module enabled")) {
                            duringLog.run();
                            if (failLog) throw startup;
                        }
                    }

                    public void flush() {}

                    public void close() {}
                });
    }

    public DailyQuestModule module() {
        return new DailyQuestModule(runtime.plugin, playerModule, questModule, runtime.messages);
    }

    public void config(String id, int target) throws Exception {
        Files.writeString(directory.resolve("daily-quests.yml"), yaml(id, target));
    }

    public static String yaml(String id, int target) {
        return "config-version: 1\n"
                + "enabled: true\n"
                + "quests-per-day: 1\n"
                + "reset:\n"
                + "  time: '00:00'\n"
                + "  timezone: Europe/Berlin\n"
                + "quests:\n"
                + "  "
                + id
                + ":\n"
                + "    name: Test\n"
                + "    description: Test\n"
                + "    progress-key: test:key\n"
                + "    target: "
                + target
                + "\n    reward-coins: 1\n";
    }

    public UUID load(String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        players.loadPlayer(id, name);
        return id;
    }

    public long running() {
        return tasks.stream().filter(t -> !t.cancelled).count();
    }

    public final class Task implements BukkitTask {
        public final Runnable callback;
        public final long delay, period;
        public boolean cancelled, refuse, throwAfter, ineffective, statusFailure;
        public int cancels;

        Task(Runnable callback, long delay, long period) {
            this.callback = callback;
            this.delay = delay;
            this.period = period;
        }

        public int getTaskId() {
            return tasks.indexOf(this) + 1;
        }

        public Plugin getOwner() {
            return runtime.plugin;
        }

        public boolean isSync() {
            return true;
        }

        public boolean isCancelled() {
            if (statusFailure) throw new IllegalStateException("task status fault");
            return cancelled;
        }

        public void cancel() {
            cancels++;
            if (refuse) throw new IllegalStateException("cancel refused");
            if (!ineffective) cancelled = true;
            if (throwAfter) throw new IllegalStateException("cancel after effect");
        }
    }

    public static final class Faults<E> extends LinkedHashSet<E> {
        public boolean failAdd, addAfter, failRemove, removeAfter;
        public int additions, removals;
        public final RuntimeException fault = new IllegalStateException("observer boundary fault");

        public boolean add(E e) {
            additions++;
            if (failAdd && !addAfter) throw fault;
            boolean r = super.add(e);
            if (failAdd) throw fault;
            return r;
        }

        public boolean remove(Object e) {
            removals++;
            if (failRemove && !removeAfter) throw fault;
            boolean r = super.remove(e);
            if (failRemove) throw fault;
            return r;
        }
    }

    public void close() throws Exception {
        runtime.close();
        try (var paths = Files.walk(directory)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }
}
