package dev.vapee.core.reward;

import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.logging.Level;

public final class RewardModule implements CoreModule {

    public static final long FLUSH_INTERVAL_TICKS = 20L;

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final EconomyModule economyModule;

    private RewardService rewardService;
    private RewardListener rewardListener;
    private BukkitTask flushTask;

    public RewardModule(JavaPlugin plugin, PlayerModule playerModule, EconomyModule economyModule) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.economyModule = Objects.requireNonNull(economyModule, "economyModule");
    }

    @Override
    public String getName() {
        return "Reward";
    }

    @Override
    public void enable() {
        RewardService newRewardService = new RewardService(
                economyModule.getEconomyService(),
                playerModule.getPlayerService(),
                plugin.getLogger()
        );
        RewardListener newRewardListener = new RewardListener(newRewardService);
        BukkitTask newFlushTask = null;

        try {
            plugin.getServer().getPluginManager().registerEvents(newRewardListener, plugin);
            rewardService = newRewardService;
            rewardListener = newRewardListener;
            newFlushTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    this::flushSafely,
                    FLUSH_INTERVAL_TICKS,
                    FLUSH_INTERVAL_TICKS
            );
            flushTask = newFlushTask;
        } catch (RuntimeException exception) {
            if (newFlushTask != null) {
                newFlushTask.cancel();
            }
            HandlerList.unregisterAll(newRewardListener);
            rewardService = null;
            rewardListener = null;
            flushTask = null;
            throw exception;
        }

        plugin.getLogger().info(
                "Reward module enabled with one shared " + FLUSH_INTERVAL_TICKS + "-tick flush task."
        );
    }

    @Override
    public void disable() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        if (rewardService != null) {
            flushSafely();
        }
        if (rewardListener != null) {
            HandlerList.unregisterAll(rewardListener);
        }
        if (rewardService != null) {
            rewardService.clearDirtyTracking();
        }

        flushTask = null;
        rewardListener = null;
        rewardService = null;
    }

    public RewardService getRewardService() {
        return Objects.requireNonNull(rewardService, "RewardModule is not enabled");
    }

    private void flushSafely() {
        RewardService currentRewardService = rewardService;
        if (currentRewardService == null) {
            return;
        }
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
}
