package dev.vapee.core.reward;

import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class RewardModule implements CoreModule {

    public static final long FLUSH_INTERVAL_TICKS = 20L;

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final EconomyModule economyModule;
    private final Consumer<EnableStage> enableCheckpoint;

    private RewardService rewardService;
    private RewardListener rewardListener;
    private BukkitTask flushTask;
    private boolean active;
    private boolean enabling;
    private boolean cleaning;
    private CleanupResources pendingCleanup;

    enum EnableStage { LISTENER_REGISTERED, TASK_SCHEDULED, RUNTIME_PUBLISHED }

    public RewardModule(JavaPlugin plugin, PlayerModule playerModule, EconomyModule economyModule) {
        this(plugin, playerModule, economyModule, stage -> { });
    }

    RewardModule(JavaPlugin plugin, PlayerModule playerModule, EconomyModule economyModule,
                 Consumer<EnableStage> enableCheckpoint) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.economyModule = Objects.requireNonNull(economyModule, "economyModule");
        this.enableCheckpoint = Objects.requireNonNull(enableCheckpoint, "enableCheckpoint");
    }

    @Override
    public String getName() {
        return "Reward";
    }

    @Override
    public void enable() {
        if (active || enabling || cleaning) {
            throw new IllegalStateException("Reward module is already enabled or changing lifecycle");
        }
        if (pendingCleanup != null) {
            throw new IllegalStateException("Previous Reward cleanup is incomplete; retry disable() with the original PlayerService before enable().",
                    pendingCleanup.failure);
        }
        enabling = true;
        RewardService newRewardService = null;
        RewardListener newRewardListener = null;
        BukkitTask newFlushTask = null;
        try {
            newRewardService = new RewardService(economyModule.getEconomyService(),
                    playerModule.getPlayerService(), plugin.getLogger());
            RewardService listenerService = newRewardService;
            newRewardListener = new RewardListener(listenerService,
                    () -> active && rewardService == listenerService);
            plugin.getServer().getPluginManager().registerEvents(newRewardListener, plugin);
            enableCheckpoint.accept(EnableStage.LISTENER_REGISTERED);
            RewardService scheduledService = newRewardService;
            newFlushTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    () -> {
                        if (active && rewardService == scheduledService) flushSafely(scheduledService);
                    },
                    FLUSH_INTERVAL_TICKS,
                    FLUSH_INTERVAL_TICKS
            );
            enableCheckpoint.accept(EnableStage.TASK_SCHEDULED);
            rewardService = newRewardService;
            rewardListener = newRewardListener;
            flushTask = newFlushTask;
            enableCheckpoint.accept(EnableStage.RUNTIME_PUBLISHED);
            plugin.getLogger().info(
                    "Reward module enabled with one shared " + FLUSH_INTERVAL_TICKS + "-tick flush task."
            );
            active = true;
        } catch (RuntimeException exception) {
            clearRuntime();
            CleanupResources acquired = new CleanupResources(newRewardService, newRewardListener, newFlushTask);
            cleanup(acquired, exception);
            if (!acquired.released()) pendingCleanup = acquired;
            throw exception;
        } finally {
            enabling = false;
        }
    }

    @Override
    public void disable() {
        if (enabling || cleaning) {
            throw new IllegalStateException("Reward module is already changing lifecycle");
        }
        boolean flushRewards = active;
        active = false;
        cleaning = true;
        CleanupResources resources = pendingCleanup == null
                ? new CleanupResources(rewardService, rewardListener, flushTask) : pendingCleanup;
        resources.flushPending |= flushRewards;
        clearRuntime(); // Close getters and generation authority before calling external cleanup.
        RuntimeException failure = new IllegalStateException("Reward cleanup incomplete; retry disable() with the original PlayerService."
                + " Save outcomes may be uncertain; JVM exit can lose unresolved in-memory changes.");
        try {
            cleanup(resources, failure);
            pendingCleanup = resources.released() ? null : resources;
            if (failure.getSuppressed().length != 0) throw failure;
        } finally {
            cleaning = false;
        }
    }

    private void cleanup(CleanupResources resources, RuntimeException failure) {
        if (resources.task != null && cancelTask(resources.task, failure)) resources.task = null;
        // This obligation survives active=false. Startup rollback never invents persistence work.
        if (resources.flushPending && resources.service != null) {
            try {
                resources.service.flushAllOrThrow();
                resources.flushPending = false;
            } catch (RuntimeException exception) {
                failure.addSuppressed(new IllegalStateException("Could not complete pending Reward flush", exception));
            }
        }
        if (resources.listener != null && unregister(resources.listener, failure)) resources.listener = null;
        if (!resources.flushPending) resources.service = null;
        resources.failure = failure;
    }

    private static boolean cancelTask(BukkitTask task, RuntimeException failure) {
        cleanupStep(failure, "Reward flush task", task::cancel);
        try {
            if (task.isCancelled()) return true;
            failure.addSuppressed(new IllegalStateException("Reward task cancellation is unconfirmed"));
        } catch (RuntimeException exception) { failure.addSuppressed(exception); }
        return false;
    }

    private boolean unregister(RewardListener listener, RuntimeException failure) {
        cleanupStep(failure, "Reward quit listener", () -> HandlerList.unregisterAll(listener));
        try {
            if (HandlerList.getRegisteredListeners(plugin).stream().noneMatch(handler -> handler.getListener() == listener)) return true;
            failure.addSuppressed(new IllegalStateException("Reward listener release is unconfirmed"));
        } catch (RuntimeException exception) { failure.addSuppressed(exception); }
        return false;
    }

    private static void cleanupStep(RuntimeException failure, String resource, Runnable release) {
        try {
            release.run();
        } catch (RuntimeException exception) {
            failure.addSuppressed(new IllegalStateException("Could not release " + resource, exception));
        }
    }

    private void clearRuntime() {
        active = false;
        flushTask = null;
        rewardListener = null;
        rewardService = null;
    }

    public RewardService getRewardService() {
        return Objects.requireNonNull(active ? rewardService : null, "RewardModule is not enabled");
    }

    private void flushSafely(RewardService currentRewardService) {
        try {
            currentRewardService.flushAll();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Unexpected reward batch flush failure; PlayerModule shutdown remains a persistence fallback.",
                    exception
            );
        }
    }

    // Retired exact ownership, not a second active Reward service or Player cache.
    private static final class CleanupResources {
        private RewardService service;
        private RewardListener listener;
        private BukkitTask task;
        private boolean flushPending;
        private RuntimeException failure;

        private CleanupResources(RewardService service, RewardListener listener, BukkitTask task) {
            this.service = service;
            this.listener = listener;
            this.task = task;
        }

        private boolean released() { return service == null && listener == null && task == null && !flushPending; }
    }
}
