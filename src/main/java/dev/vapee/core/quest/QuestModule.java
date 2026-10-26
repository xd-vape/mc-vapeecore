package dev.vapee.core.quest;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.reward.RewardModule;
import dev.vapee.core.reward.RewardService;
import dev.vapee.core.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

public final class QuestModule implements CoreModule {
    public static final long QUEST_FLUSH_INTERVAL_TICKS = 100L;
    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final RewardModule rewardModule;
    private final MessageService messages;
    private RuntimeResources runtime;
    private RuntimeResources pendingCleanup;
    private boolean active;
    private boolean enabling;
    private boolean cleaning;

    public QuestModule(JavaPlugin plugin, PlayerModule playerModule, RewardModule rewardModule,
                       MessageService messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.rewardModule = Objects.requireNonNull(rewardModule, "rewardModule");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override public String getName() { return "Quest"; }

    @Override
    public void enable() {
        if (active || enabling || cleaning) {
            throw new IllegalStateException("Quest module is already enabled or changing lifecycle");
        }
        if (pendingCleanup != null) {
            throw new IllegalStateException("Previous Quest cleanup is incomplete; retry disable() while borrowed owners remain available.",
                    pendingCleanup.failure);
        }
        enabling = true;
        RuntimeResources acquired = null;
        try {
            acquired = new RuntimeResources(playerModule.getPlayerService(), rewardModule.getRewardService());
            plugin.getServer().getPluginManager().registerEvents(acquired.listener, plugin);
            acquired.seeding = true;
            try {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    try {
                        acquired.playtime.seed(player);
                    } catch (RuntimeException exception) {
                        plugin.getLogger().log(Level.WARNING, "Quest playtime enable seed failed for "
                                + player.getUniqueId() + "; the first later sample will establish its baseline.", exception);
                    }
                }
            } finally {
                acquired.seeding = false;
            }
            RuntimeResources scheduled = acquired;
            acquired.task = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    () -> { if (owns(scheduled)) flushSafely(scheduled); },
                    QUEST_FLUSH_INTERVAL_TICKS,
                    QUEST_FLUSH_INTERVAL_TICKS
            );
            plugin.getLogger().info("Quest module enabled with an empty definition registry and one shared "
                    + QUEST_FLUSH_INTERVAL_TICKS + "-tick playtime and flush task.");
            runtime = acquired;
            active = true;
        } catch (RuntimeException exception) {
            active = false;
            runtime = null;
            if (acquired != null) {
                cleanup(acquired, exception);
                if (!acquired.released()) pendingCleanup = acquired;
            }
            throw exception;
        } finally {
            enabling = false;
        }
    }

    @Override
    public void disable() {
        if (enabling || cleaning) throw new IllegalStateException("Quest module is already changing lifecycle");
        boolean wasActive = active;
        RuntimeResources resources = pendingCleanup == null ? runtime : pendingCleanup;
        active = false;
        runtime = null; // Close getters, task and listener authority before any external cleanup.
        if (resources == null) return;
        resources.finalSamplePending |= wasActive;
        resources.flushPending |= wasActive;
        cleaning = true;
        RuntimeException failure = new IllegalStateException(
                "Quest cleanup incomplete; retry disable() while the original PlayerService remains available."
                        + " JVM exit can lose unresolved in-memory state.");
        try {
            cleanup(resources, failure);
            pendingCleanup = resources.released() ? null : resources;
            if (failure.getSuppressed().length != 0) throw failure;
        } finally {
            cleaning = false;
        }
    }

    private void cleanup(RuntimeResources resources, RuntimeException failure) {
        resources.seeding = false;
        resources.reporter.silence();
        if (resources.task != null && cancelTask(resources.task, failure)) resources.task = null;
        // Failed startup never earns progress. Normal shutdown samples before persistence.
        if (resources.finalSamplePending) {
            resources.flushPending = true;
            resources.finalSampling = true;
            try {
                if (sampleOnline(resources, () -> resources.finalSampling, failure)) resources.finalSamplePending = false;
            } finally {
                resources.finalSampling = false;
            }
        }
        if (resources.flushPending) {
            try {
                resources.service.flushAllOrThrow();
                resources.flushPending = false;
            } catch (RuntimeException exception) {
                failure.addSuppressed(new IllegalStateException("Could not complete Quest profile flush", exception));
            }
        }
        if (resources.listener != null && unregister(resources.listener, failure)) resources.listener = null;
        if (!resources.finalSamplePending && !resources.flushPending) {
            resources.playtime.clear();
            resources.samplesReleased = true;
        }
        resources.failure = failure;
    }

    private boolean unregister(QuestListener listener, RuntimeException failure) {
        try {
            HandlerList.unregisterAll(listener);
        } catch (RuntimeException exception) {
            failure.addSuppressed(new IllegalStateException("Could not unregister Quest listener", exception));
        }
        try {
            if (HandlerList.getRegisteredListeners(plugin).stream().noneMatch(handler -> handler.getListener() == listener)) return true;
            failure.addSuppressed(new IllegalStateException("Quest listener release is unconfirmed"));
        } catch (RuntimeException exception) {
            failure.addSuppressed(new IllegalStateException("Could not confirm Quest listener release", exception));
        }
        return false;
    }

    private static boolean cancelTask(BukkitTask task, RuntimeException failure) {
        try {
            task.cancel();
        } catch (RuntimeException exception) {
            failure.addSuppressed(new IllegalStateException("Could not cancel Quest task", exception));
        }
        try {
            if (task.isCancelled()) return true;
            failure.addSuppressed(new IllegalStateException("Quest task cancellation is unconfirmed"));
        } catch (RuntimeException exception) {
            failure.addSuppressed(new IllegalStateException("Could not confirm Quest task cancellation", exception));
        }
        return false;
    }

    public QuestDefinitionRegistry getDefinitionRegistry() { return activeRuntime().registry; }
    public QuestService getQuestService() { return activeRuntime().service; }
    public QuestProgressReporter getProgressReporter() { return activeRuntime().reporter; }

    private RuntimeResources activeRuntime() {
        return Objects.requireNonNull(active ? runtime : null, "QuestModule is not enabled");
    }

    private boolean owns(RuntimeResources candidate) { return active && runtime == candidate; }

    private void flushSafely(RuntimeResources current) {
        if (!owns(current)) return;
        RuntimeException failure = new IllegalStateException("Quest processing incomplete; dirty state remains retryable.");
        sampleOnline(current, () -> owns(current), failure);
        if (owns(current)) {
            try {
                current.service.flushAllOrThrow();
            } catch (RuntimeException exception) {
                failure.addSuppressed(exception);
            }
        }
        if (failure.getSuppressed().length != 0) {
            try {
                plugin.getLogger().log(Level.WARNING, failure.getMessage(), failure);
            } catch (RuntimeException diagnostic) {
                failure.addSuppressed(diagnostic);
                throw failure;
            }
        }
    }

    private boolean sampleOnline(RuntimeResources resources, BooleanSupplier authorized, RuntimeException failure) {
        List<? extends Player> online;
        try {
            if (!authorized.getAsBoolean()) return false;
            online = List.copyOf(plugin.getServer().getOnlinePlayers());
        } catch (RuntimeException exception) {
            failure.addSuppressed(new IllegalStateException("Could not read Quest online roster", exception));
            return false;
        }
        boolean complete = true;
        for (Player player : online) {
            UUID id = null;
            try {
                if (!authorized.getAsBoolean()) return false;
                id = player.getUniqueId();
                if (!authorized.getAsBoolean()) return false;
                if (!player.isOnline()) continue;
                resources.playtime.sample(player);
            } catch (RuntimeException exception) {
                complete = false;
                failure.addSuppressed(new IllegalStateException("Quest playtime sample failed for " + id, exception));
                try {
                    plugin.getLogger().log(Level.WARNING, "Quest playtime sample failed for " + id
                            + "; other players and the batch flush continue.", exception);
                } catch (RuntimeException diagnostic) {
                    failure.addSuppressed(diagnostic);
                }
            }
        }
        return complete;
    }

    // Retired resources, not another player cache; the service borrows the original aggregate owner.
    private final class RuntimeResources {
        private final QuestDefinitionRegistry registry = new QuestDefinitionRegistry();
        private final QuestService service;
        private final QuestProgressReporter reporter;
        private final QuestPlaytimeProducer playtime;
        private QuestListener listener;
        private BukkitTask task;
        private boolean seeding;
        private boolean finalSampling;
        private boolean finalSamplePending;
        private boolean flushPending;
        private boolean samplesReleased;
        private RuntimeException failure;

        private RuntimeResources(PlayerService players, RewardService rewards) {
            service = new QuestService(registry, players, rewards, plugin.getLogger());
            reporter = new QuestProgressReporter(service, messages, plugin.getServer()::getPlayer, plugin.getLogger());
            playtime = new QuestPlaytimeProducer(players, reporter, plugin.getLogger(),
                    () -> owns(this) || seeding || finalSampling);
            listener = new QuestListener(service, playtime, plugin.getLogger(), () -> owns(this));
        }

        private boolean released() {
            return task == null && listener == null && !finalSamplePending && !flushPending && samplesReleased;
        }
    }
}
