package dev.vapee.core.onlinereward;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.reward.RewardModule;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.logging.Level;

public final class OnlineRewardModule implements CoreModule {

    public static final long PROCESS_INTERVAL_TICKS = 20L;

    private final JavaPlugin plugin;
    private final ConfigService configService;
    private final PlayerModule playerModule;
    private final RewardModule rewardModule;
    private final MessageService messageService;

    private OnlineRewardService onlineRewardService;
    private OnlineRewardMessageRenderer messageRenderer;
    private BukkitTask processingTask;
    private boolean active;
    private boolean enabling;
    private boolean cleaning;
    private BukkitTask pendingCancellation;

    public OnlineRewardModule(
            JavaPlugin plugin,
            ConfigService configService,
            PlayerModule playerModule,
            RewardModule rewardModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.rewardModule = Objects.requireNonNull(rewardModule, "rewardModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "OnlineReward";
    }

    @Override
    public void enable() {
        if (active || enabling || cleaning) {
            throw new IllegalStateException("OnlineReward module is already enabled or changing lifecycle");
        }
        enabling = true;
        BukkitTask newTask = null;
        try {
            if (pendingCancellation != null) {
                RuntimeException failure = new IllegalStateException("Previous OnlineReward cleanup is incomplete.");
                if (cancelTask(pendingCancellation, failure)) pendingCancellation = null;
                if (failure.getSuppressed().length != 0) throw failure;
            }
            OnlineRewardService newService = new OnlineRewardService(
                    configService,
                    playerModule.getPlayerService(),
                    rewardModule.getRewardService(),
                    plugin.getLogger()
            );
            OnlineRewardMessageRenderer newRenderer = new OnlineRewardMessageRenderer(
                    messageService,
                    plugin.getLogger()
            );
            newTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    () -> processOnlinePlayers(newService, newRenderer),
                    PROCESS_INTERVAL_TICKS,
                    PROCESS_INTERVAL_TICKS
            );
            plugin.getLogger().info(
                    "OnlineReward module enabled with one shared "
                            + PROCESS_INTERVAL_TICKS + "-tick processing task."
            );
            onlineRewardService = newService;
            messageRenderer = newRenderer;
            processingTask = newTask;
            active = true;
        } catch (RuntimeException exception) {
            clearRuntime();
            if (newTask != null && !cancelTask(newTask, exception)) pendingCancellation = newTask;
            throw exception;
        } finally {
            enabling = false;
        }
    }

    @Override
    public void disable() {
        if (enabling || cleaning) {
            throw new IllegalStateException("OnlineReward module is already changing lifecycle");
        }
        cleaning = true;
        BukkitTask task = pendingCancellation == null ? processingTask : pendingCancellation;
        clearRuntime(); // Revoke this generation before calling external cancellation code.
        RuntimeException failure = new IllegalStateException("OnlineReward cleanup failed.");
        try {
            pendingCancellation = task != null && !cancelTask(task, failure) ? task : null;
            if (failure.getSuppressed().length != 0) throw failure;
        } finally {
            cleaning = false;
        }
    }

    private static boolean cancelTask(BukkitTask task, RuntimeException failure) {
        try {
            task.cancel();
        } catch (RuntimeException exception) {
            failure.addSuppressed(new IllegalStateException("Could not cancel OnlineReward processing task", exception));
        }
        try {
            if (task.isCancelled()) return true;
            failure.addSuppressed(new IllegalStateException("OnlineReward processing task cancellation is unconfirmed"));
        } catch (RuntimeException exception) {
            failure.addSuppressed(new IllegalStateException("Could not confirm OnlineReward task cancellation", exception));
        }
        return false;
    }

    private void clearRuntime() {
        active = false;
        processingTask = null;
        messageRenderer = null;
        onlineRewardService = null;
    }

    public OnlineRewardService getOnlineRewardService() {
        return Objects.requireNonNull(active ? onlineRewardService : null, "OnlineRewardModule is not enabled");
    }

    private boolean owns(OnlineRewardService service) {
        return active && onlineRewardService == service;
    }

    private void processOnlinePlayers(OnlineRewardService currentService, OnlineRewardMessageRenderer currentRenderer) {
        if (!owns(currentService)) {
            return;
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                if (!owns(currentService)) return;
                var playerId = player.getUniqueId();
                long playtimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
                if (!owns(currentService)) return;
                OnlineRewardProcessResult result = currentService.process(playerId, playtimeTicks);
                if (owns(currentService) && result.status() == OnlineRewardProcessStatus.REWARDED) {
                    currentRenderer.notifyReward(
                            player,
                            configService.getOnlineRewardConfig(),
                            result
                    );
                }
            } catch (RuntimeException exception) {
                plugin.getLogger().log(
                        Level.WARNING,
                        "Online reward processing failed for player " + player.getUniqueId()
                                + "; continuing with other online players.",
                        exception
                );
            }
        }
    }
}
