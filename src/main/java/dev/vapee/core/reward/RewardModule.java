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
        enabling = true;
        RewardService newRewardService = null;
        RewardListener newRewardListener = null;
        BukkitTask newFlushTask = null;
        try {
            if (pendingCleanup != null) {
                RuntimeException failure = new IllegalStateException("Previous Reward cleanup is incomplete.");
                if (!cleanup(pendingCleanup, false, failure)) throw failure;
                pendingCleanup = null;
            }
            newRewardService = new RewardService(economyModule.getEconomyService(),
                    playerModule.getPlayerService(), plugin.getLogger());
            newRewardListener = new RewardListener(newRewardService);
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
            CleanupResources acquired = new CleanupResources(newRewardService, newRewardListener, newFlushTask);
            if (!cleanup(acquired, false, exception)) pendingCleanup = acquired;
            clearRuntime();
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
        RuntimeException failure = new IllegalStateException("Reward cleanup failed.");
        try {
            pendingCleanup = cleanup(resources, flushRewards, failure) ? null : resources;
            clearRuntime();
            if (failure.getSuppressed().length != 0) throw failure;
        } finally {
            cleaning = false;
        }
    }

    private boolean cleanup(CleanupResources resources, boolean flushRewards, RuntimeException failure) {
        int failuresBefore = failure.getSuppressed().length;
        if (resources.task() != null) {
            cleanupStep(failure, "Reward flush task", resources.task()::cancel);
        }
        // Keep the existing shutdown flush while borrowed Economy/Player owners are still available.
        if (flushRewards && resources.service() != null) {
            cleanupStep(failure, "pending Reward flush", () -> flushSafely(resources.service()));
        }
        if (resources.listener() != null) {
            cleanupStep(failure, "Reward quit listener", () -> HandlerList.unregisterAll(resources.listener()));
        }
        if (resources.service() != null) {
            cleanupStep(failure, "Reward dirty tracking", resources.service()::clearDirtyTracking);
        }
        return failure.getSuppressed().length == failuresBefore;
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

    // Uncertain external releases stay retryable without exposing an active runtime.
    private record CleanupResources(RewardService service, RewardListener listener, BukkitTask task) { }
}
