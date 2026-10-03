package dev.vapee.core.quest.daily;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.quest.daily.command.DailyQuestCommand;
import dev.vapee.core.quest.daily.menu.DailyQuestMenu;
import dev.vapee.core.quest.daily.menu.DailyQuestMenuListener;
import org.bukkit.command.PluginCommand;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.quest.QuestDefinition;
import dev.vapee.core.quest.QuestDefinitionRegistry;
import dev.vapee.core.quest.QuestModule;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class DailyQuestModule implements CoreModule, ReloadParticipant {

    public static final long SYNC_INTERVAL_TICKS = 1200L;

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final QuestModule questModule;
    private final MessageService messages;

    private DailyQuestConfig config;
    private DailyQuestService service;
    private DailyQuestListener listener;
    private BukkitTask syncTask;
    private DailyQuestMenu menu;
    private DailyQuestMenuListener menuListener;
    private PluginCommand command;

    public DailyQuestModule(JavaPlugin plugin, PlayerModule playerModule, QuestModule questModule, MessageService messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.questModule = Objects.requireNonNull(questModule, "questModule");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override
    public String getName() {
        return "DailyQuest";
    }

    @Override
    public void enable() {
        DailyQuestConfig newConfig = new DailyQuestConfig(plugin);
        newConfig.initialize();
        QuestDefinitionRegistry registry = questModule.getDefinitionRegistry();
        List<QuestDefinition> previousDefinitions = registry.snapshot();
        DailyQuestService newService = new DailyQuestService(
                playerModule.getPlayerService(), questModule.getQuestService(),
                registry, newConfig::getState, plugin.getLogger()
        );
        DailyQuestListener newListener = new DailyQuestListener(newService);
        DailyQuestMenu newMenu = new DailyQuestMenu(plugin, newService, questModule.getQuestService(),
                playerModule.getPlayerService(), messages);
        DailyQuestMenuListener newMenuListener = new DailyQuestMenuListener(newMenu);
        PluginCommand newCommand = null;
        BukkitTask newTask = null;
        try {
            registry.replaceAll(newConfig.getState().definitions());
            newCommand = Objects.requireNonNull(plugin.getCommand("quests"), "Command 'quests' is missing from plugin.yml");
            DailyQuestCommand executor = new DailyQuestCommand(newMenu, messages);
            plugin.getServer().getPluginManager().registerEvents(newListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newMenuListener, plugin);
            newCommand.setExecutor(executor);
            newCommand.setTabCompleter(executor);
            config = newConfig;
            service = newService;
            listener = newListener;
            menu = newMenu;
            menuListener = newMenuListener;
            command = newCommand;
            newTask = plugin.getServer().getScheduler().runTaskTimer(
                    plugin, this::syncOnlineSafely, SYNC_INTERVAL_TICKS, SYNC_INTERVAL_TICKS
            );
            syncTask = newTask;
        } catch (RuntimeException exception) {
            if (newTask != null) newTask.cancel();
            HandlerList.unregisterAll(newListener);
            HandlerList.unregisterAll(newMenuListener);
            newMenu.closeOpenInventories();
            if (newCommand != null) {
                newCommand.setExecutor(null);
                newCommand.setTabCompleter(null);
            }
            registry.replaceAll(previousDefinitions);
            syncTask = null;
            listener = null;
            service = null;
            config = null;
            menu = null;
            menuListener = null;
            command = null;
            throw exception;
        }
        plugin.getLogger().info("DailyQuest module enabled with " + registry.size()
                + " definition(s) and one shared " + SYNC_INTERVAL_TICKS + "-tick sync task."
                + (newConfig.getState().enabled() ? "" : " Assignments are disabled by configuration."));
    }

    @Override
    public void disable() {
        if (syncTask != null) syncTask.cancel();
        if (menu != null) menu.closeOpenInventories();
        if (menuListener != null) HandlerList.unregisterAll(menuListener);
        if (listener != null) HandlerList.unregisterAll(listener);
        if (command != null) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
        syncTask = null;
        listener = null;
        service = null;
        config = null;
        menu = null;
        menuListener = null;
        command = null;
    }

    @Override
    public String getReloadName() {
        return "daily-quests.yml";
    }

    @Override
    public ReloadPlan prepareReload() {
        DailyQuestConfig activeConfig = Objects.requireNonNull(config, "DailyQuestModule is not enabled");
        QuestDefinitionRegistry registry = questModule.getDefinitionRegistry();
        ReloadPlan plan = prepareReloadPlan(activeConfig, registry);
        return ReloadPlan.of(() -> {
            plan.apply();
            menu.closeOpenInventories();
        }, plan::rollback);
    }

    static ReloadPlan prepareReloadPlan(DailyQuestConfig activeConfig, QuestDefinitionRegistry registry) {
        Objects.requireNonNull(activeConfig, "activeConfig");
        Objects.requireNonNull(registry, "registry");
        DailyQuestConfig.State previousState = activeConfig.getState();
        List<QuestDefinition> previousDefinitions = registry.snapshot();
        DailyQuestConfig.State preparedState = activeConfig.prepareReloadState();
        return ReloadPlan.of(
                () -> {
                    registry.replaceAll(preparedState.definitions());
                    activeConfig.applyState(preparedState);
                },
                () -> {
                    registry.replaceAll(previousDefinitions);
                    activeConfig.applyState(previousState);
                }
        );
    }

    public DailyQuestService getDailyQuestService() {
        return Objects.requireNonNull(service, "DailyQuestModule is not enabled");
    }

    private void syncOnlineSafely() {
        DailyQuestService currentService = service;
        if (currentService == null) return;
        Instant now = Instant.now();
        for (CorePlayer player : playerModule.getPlayerService().getLoadedPlayers()) {
            try {
                currentService.syncPlayer(player.getUniqueId(), now);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING,
                        "Daily quest sync failed for " + player.getUniqueId() + "; the next sync will retry.",
                        exception);
            }
        }
    }
}
