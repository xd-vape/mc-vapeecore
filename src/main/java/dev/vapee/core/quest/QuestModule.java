package dev.vapee.core.quest;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.reward.RewardModule;
import dev.vapee.core.message.MessageService;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.Objects;
import java.util.logging.Level;

public final class QuestModule implements CoreModule {

    public static final long QUEST_FLUSH_INTERVAL_TICKS = 100L;

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final RewardModule rewardModule;
    private final MessageService messages;

    private QuestDefinitionRegistry definitionRegistry;
    private QuestService questService;
    private QuestListener questListener;
    private BukkitTask flushTask;
    private QuestProgressReporter reporter;
    private QuestPlaytimeProducer playtime;

    public QuestModule(
            JavaPlugin plugin,
            PlayerModule playerModule,
            RewardModule rewardModule,
            MessageService messages
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.rewardModule = Objects.requireNonNull(rewardModule, "rewardModule");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String getName() {
        return "Quest";
    }

    @Override
    public void enable() {
        QuestDefinitionRegistry newRegistry = new QuestDefinitionRegistry();
        QuestService newService = new QuestService(
                newRegistry,
                playerModule.getPlayerService(),
                rewardModule.getRewardService(),
                plugin.getLogger()
        );
        QuestProgressReporter newReporter = new QuestProgressReporter(newService, messages,
                plugin.getServer()::getPlayer, plugin.getLogger());
        QuestPlaytimeProducer newPlaytime = new QuestPlaytimeProducer(playerModule.getPlayerService(),
                newReporter, plugin.getLogger());
        QuestListener newListener = new QuestListener(newService, newPlaytime, plugin.getLogger());
        BukkitTask newFlushTask = null;

        try {
            plugin.getServer().getPluginManager().registerEvents(newListener, plugin);
            definitionRegistry = newRegistry;
            questService = newService;
            questListener = newListener;
            reporter = newReporter;
            playtime = newPlaytime;
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                try {
                    newPlaytime.seed(player);
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING, "Quest playtime enable seed failed for "
                            + player.getUniqueId() + "; the first later sample will establish its baseline.", exception);
                }
            }
            newFlushTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin,
                    this::flushSafely,
                    QUEST_FLUSH_INTERVAL_TICKS,
                    QUEST_FLUSH_INTERVAL_TICKS
            );
            flushTask = newFlushTask;
        } catch (RuntimeException exception) {
            if (newFlushTask != null) {
                newFlushTask.cancel();
            }
            HandlerList.unregisterAll(newListener);
            flushTask = null;
            questListener = null;
            newReporter.silence();
            newPlaytime.clear();
            reporter = null;
            playtime = null;
            questService = null;
            definitionRegistry = null;
            throw exception;
        }

        plugin.getLogger().info(
                "Quest module enabled with an empty definition registry and one shared "
                        + QUEST_FLUSH_INTERVAL_TICKS + "-tick playtime and flush task."
        );
    }

    @Override
    public void disable() {
        if (flushTask != null) {
            flushTask.cancel();
        }
        if (questService != null) {
            reporter.silence();
            flushSafely();
        }
        if (questListener != null) {
            HandlerList.unregisterAll(questListener);
        }
        if (questService != null) {
            questService.clearDirtyTracking();
        }
        if (playtime != null) playtime.clear();

        flushTask = null;
        questListener = null;
        questService = null;
        definitionRegistry = null;
        reporter = null;
        playtime = null;
    }

    public QuestDefinitionRegistry getDefinitionRegistry() {
        return Objects.requireNonNull(definitionRegistry, "QuestModule is not enabled");
    }

    public QuestService getQuestService() {
        return Objects.requireNonNull(questService, "QuestModule is not enabled");
    }

    public QuestProgressReporter getProgressReporter() {
        return Objects.requireNonNull(reporter, "QuestModule is not enabled");
    }

    private void flushSafely() {
        QuestService currentService = questService;
        if (currentService == null) {
            return;
        }
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline()) continue;
            try {
                playtime.sample(player);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Quest playtime sample failed for "
                        + player.getUniqueId() + "; other players and the batch flush continue.", exception);
            }
        }
        try {
            currentService.flushAll();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.SEVERE,
                    "Unexpected quest batch flush failure; PlayerModule shutdown remains a persistence fallback.",
                    exception
            );
        }
    }
}
