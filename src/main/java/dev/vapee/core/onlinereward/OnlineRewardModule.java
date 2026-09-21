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
        BukkitTask newTask = null;

        try {
            onlineRewardService = newService;
            messageRenderer = newRenderer;
            newTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    this::processOnlinePlayers,
                    PROCESS_INTERVAL_TICKS,
                    PROCESS_INTERVAL_TICKS
            );
            processingTask = newTask;
        } catch (RuntimeException exception) {
            if (newTask != null) {
                newTask.cancel();
            }
            processingTask = null;
            messageRenderer = null;
            onlineRewardService = null;
            throw exception;
        }

        plugin.getLogger().info(
                "OnlineReward module enabled with one shared "
                        + PROCESS_INTERVAL_TICKS + "-tick processing task."
        );
    }

    @Override
    public void disable() {
        if (processingTask != null) {
            processingTask.cancel();
        }
        processingTask = null;
        messageRenderer = null;
        onlineRewardService = null;
    }

    public OnlineRewardService getOnlineRewardService() {
        return Objects.requireNonNull(onlineRewardService, "OnlineRewardModule is not enabled");
    }

    private void processOnlinePlayers() {
        OnlineRewardService currentService = onlineRewardService;
        OnlineRewardMessageRenderer currentRenderer = messageRenderer;
        if (currentService == null || currentRenderer == null) {
            return;
        }

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                OnlineRewardProcessResult result = currentService.process(
                        player.getUniqueId(),
                        (long) player.getStatistic(Statistic.PLAY_ONE_MINUTE)
                );
                if (result.status() == OnlineRewardProcessStatus.REWARDED) {
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
