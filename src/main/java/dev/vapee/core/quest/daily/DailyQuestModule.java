package dev.vapee.core.quest.daily;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.quest.daily.command.DailyQuestCommand;
import dev.vapee.core.quest.daily.menu.DailyQuestMenu;
import dev.vapee.core.quest.daily.menu.DailyQuestMenuListener;
import org.bukkit.command.PluginCommand;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.quest.QuestDefinition;
import dev.vapee.core.quest.QuestDefinitionRegistry;
import dev.vapee.core.quest.QuestModule;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Level;

public final class DailyQuestModule implements CoreModule, ReloadParticipant {
    public static final long SYNC_INTERVAL_TICKS = 1200L;
    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final QuestModule questModule;
    private final MessageService messages;
    private RuntimeResources runtime;
    private RuntimeResources pendingCleanup;
    private boolean active;
    private boolean enabling;
    private boolean cleaning;

    public DailyQuestModule(JavaPlugin plugin, PlayerModule playerModule, QuestModule questModule, MessageService messages) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.questModule = Objects.requireNonNull(questModule, "questModule");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @Override public String getName() { return "DailyQuest"; }

    @Override public void enable() {
        if (active || enabling || cleaning) {
            throw new IllegalStateException("DailyQuest module is already enabled or changing lifecycle");
        }
        if (pendingCleanup != null) {
            throw new IllegalStateException("Previous DailyQuest cleanup is incomplete; retry disable() before enable().",
                    pendingCleanup.failure);
        }
        enabling = true;
        RuntimeResources acquired = new RuntimeResources();
        try {
            acquired.players = playerModule.getPlayerService();
            var quests = questModule.getQuestService();
            acquired.registry = questModule.getDefinitionRegistry();
            acquired.config = new DailyQuestConfig(plugin);
            acquired.config.initialize();
            acquired.previousDefinitions = acquired.registry.snapshotById();
            acquired.service = new DailyQuestService(acquired.players, quests, acquired.registry,
                    acquired.config::getState, plugin.getLogger());
            acquired.listener = new DailyQuestListener(acquired.service, () -> owns(acquired));
            acquired.menu = new DailyQuestMenu(plugin, acquired.service, quests, acquired.players, messages,
                    () -> owns(acquired));
            acquired.menuListener = new DailyQuestMenuListener(acquired.menu);
            acquired.registry.replaceAll(acquired.config.getState().definitions());
            acquired.installedDefinitions = acquired.registry.snapshotById();
            acquired.restoreCatalog = true;
            acquired.command = Objects.requireNonNull(plugin.getCommand("quests"), "Command 'quests' is missing from plugin.yml");
            acquired.executor = new DailyQuestCommand(acquired.menu, messages);
            plugin.getServer().getPluginManager().registerEvents(acquired.listener, plugin);
            plugin.getServer().getPluginManager().registerEvents(acquired.menuListener, plugin);
            acquired.command.setExecutor(acquired.executor);
            acquired.command.setTabCompleter(acquired.executor);
            acquired.menu.startUpdates();
            acquired.task = plugin.getServer().getScheduler().runTaskTimer(plugin,
                    () -> { if (owns(acquired)) syncOnlineSafely(acquired); },
                    SYNC_INTERVAL_TICKS, SYNC_INTERVAL_TICKS);
            plugin.getLogger().info("DailyQuest module enabled with " + acquired.registry.size()
                    + " definition(s) and one shared " + SYNC_INTERVAL_TICKS + "-tick sync task."
                    + (acquired.config.getState().enabled() ? "" : " Assignments are disabled by configuration."));
            acquired.restoreCatalog = false; // Successful shutdown deliberately keeps the borrowed catalog.
            runtime = acquired;
            active = true;
        } catch (RuntimeException exception) {
            active = false;
            runtime = null;
            cleanup(acquired, exception);
            if (!acquired.released()) pendingCleanup = acquired;
            throw exception;
        } finally {
            enabling = false;
        }
    }

    @Override public void disable() {
        if (enabling || cleaning) throw new IllegalStateException("DailyQuest module is changing lifecycle");
        RuntimeResources resources = pendingCleanup == null ? runtime : pendingCleanup;
        active = false;
        runtime = null;
        if (resources == null) return;
        cleaning = true;
        RuntimeException failure = new IllegalStateException("DailyQuest cleanup failed; unresolved ownership requires disable() retry.");
        try {
            cleanup(resources, failure);
            pendingCleanup = resources.released() ? null : resources;
            if (failure.getSuppressed().length != 0) throw failure;
        } finally {
            cleaning = false;
        }
    }

    private boolean owns(RuntimeResources resources) { return active && runtime == resources; }

    private void cleanup(RuntimeResources resources, RuntimeException failure) {
        if (resources.task != null && cancelTask(resources.task, failure)) resources.task = null;
        // Keep the inventory close listener installed while the menu detaches its views.
        if (resources.menu != null) {
            attempt(failure, "DailyQuest menu shutdown", resources.menu::shutdown);
            if (!resources.menu.hasPendingCleanup()) resources.menu = null;
        }
        if (resources.menuListener != null && unregister(resources.menuListener, failure)) resources.menuListener = null;
        if (resources.listener != null && unregister(resources.listener, failure)) resources.listener = null;
        if (resources.command != null) {
            attempt(failure, "DailyQuest executor release", () -> {
                if (resources.command.getExecutor() == resources.executor) resources.command.setExecutor(null);
            });
            attempt(failure, "DailyQuest completer release", () -> {
                if (resources.command.getTabCompleter() == resources.executor) resources.command.setTabCompleter(null);
            });
            try {
                if (resources.command.getExecutor() != resources.executor
                        && resources.command.getTabCompleter() != resources.executor) resources.command = null;
            } catch (RuntimeException exception) { failure.addSuppressed(exception); }
        }
        if (resources.restoreCatalog) {
            attempt(failure, "DailyQuest startup catalog restoration", () -> {
                Map<String, QuestDefinition> current = resources.registry.snapshotById();
                if (current == resources.installedDefinitions) {
                    resources.registry.replaceAll(resources.previousDefinitions.values());
                } else if (current != resources.previousDefinitions) {
                    throw new IllegalStateException("DailyQuest startup catalog was independently replaced; previous catalog retained, external replacement preserved.");
                }
                resources.restoreCatalog = false;
            });
        }
        resources.failure = failure;
    }

    private static void attempt(RuntimeException failure, String context, Runnable action) {
        try { action.run(); }
        catch (RuntimeException exception) {
            failure.addSuppressed(new IllegalStateException(context, exception));
        }
    }

    private static boolean cancelTask(BukkitTask task, RuntimeException failure) {
        attempt(failure, "DailyQuest main task cancellation", task::cancel);
        try {
            if (task.isCancelled()) return true;
            failure.addSuppressed(new IllegalStateException("DailyQuest main task cancellation was not confirmed"));
        } catch (RuntimeException exception) { failure.addSuppressed(exception); }
        return false;
    }

    private static boolean unregister(Listener listener, RuntimeException failure) {
        attempt(failure, "DailyQuest listener unregister: " + listener.getClass().getSimpleName(),
                () -> HandlerList.unregisterAll(listener));
        try {
            for (HandlerList list : HandlerList.getHandlerLists()) {
                for (var handler : list.getRegisteredListeners()) {
                    if (handler.getListener() == listener) {
                        failure.addSuppressed(new IllegalStateException("DailyQuest listener remains registered: " + listener.getClass().getSimpleName()));
                        return false;
                    }
                }
            }
            return true;
        } catch (RuntimeException exception) { failure.addSuppressed(exception); return false; }
    }

    @Override public String getReloadName() { return "daily-quests.yml"; }

    @Override public ReloadPlan prepareReload() {
        RuntimeResources current = requireRuntime();
        ReloadPlan plan = prepareReloadPlan(current.config, current.registry);
        return ReloadPlan.of(() -> {
            if (!owns(current)) throw new IllegalStateException("DailyQuest reload runtime retired");
            plan.apply();
            current.menu.closeOpenInventories();
        }, () -> {
            if (!owns(current)) throw new IllegalStateException("DailyQuest reload runtime retired");
            plan.rollback();
        });
    }

    static ReloadPlan prepareReloadPlan(DailyQuestConfig activeConfig, QuestDefinitionRegistry registry) {
        Objects.requireNonNull(activeConfig, "activeConfig");
        Objects.requireNonNull(registry, "registry");
        DailyQuestConfig.State previousState = activeConfig.getState();
        List<QuestDefinition> previousDefinitions = registry.snapshot();
        DailyQuestConfig.State preparedState = activeConfig.prepareReloadState();
        return ReloadPlan.of(() -> {
            registry.replaceAll(preparedState.definitions());
            activeConfig.applyState(preparedState);
        }, () -> {
            registry.replaceAll(previousDefinitions);
            activeConfig.applyState(previousState);
        });
    }

    private RuntimeResources requireRuntime() {
        return Objects.requireNonNull(active ? runtime : null, "DailyQuestModule is not enabled");
    }

    public DailyQuestService getDailyQuestService() { return requireRuntime().service; }

    private void syncOnlineSafely(RuntimeResources current) {
        Instant now = Instant.now();
        for (CorePlayer player : current.players.getLoadedPlayers()) {
            if (!owns(current)) return;
            try {
                current.service.syncPlayer(player.getUniqueId(), now);
            } catch (RuntimeException exception) {
                try {
                    plugin.getLogger().log(Level.WARNING, "Daily quest sync failed for " + player.getUniqueId()
                            + "; the next sync will retry.", exception);
                } catch (RuntimeException diagnostic) { exception.addSuppressed(diagnostic); }
            }
        }
    }

    // Exact retired resources only; borrowed services and their dirty profiles remain with their owners.
    private static final class RuntimeResources {
        private PlayerService players;
        private QuestDefinitionRegistry registry;
        private DailyQuestConfig config;
        private DailyQuestService service;
        private DailyQuestListener listener;
        private DailyQuestMenu menu;
        private DailyQuestMenuListener menuListener;
        private PluginCommand command;
        private DailyQuestCommand executor;
        private BukkitTask task;
        private Map<String, QuestDefinition> previousDefinitions;
        private Map<String, QuestDefinition> installedDefinitions;
        private boolean restoreCatalog;
        private RuntimeException failure;
        private boolean released() {
            return task == null && menu == null && menuListener == null && listener == null
                    && command == null && !restoreCatalog;
        }
    }
}
