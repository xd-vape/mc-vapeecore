package dev.vapee.core.lobby.experience;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.activity.navigation.ActivityCatalog;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.experience.navigator.NavigatorListener;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
import dev.vapee.core.lobby.experience.navigator.activity.ActivitiesListener;
import dev.vapee.core.lobby.experience.navigator.activity.ActivitiesMenu;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.settings.SettingsMenu;
import dev.vapee.core.settings.SettingsModule;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class LobbyExperienceModule implements CoreModule {

    private final JavaPlugin plugin;
    private final LobbyModule lobbyModule;
    private final PlayerModule playerModule;
    private final SettingsModule settingsModule;
    private final ActivityModule activityModule;
    private final MessageService messageService;

    private LobbyVisibilityService visibilityService;
    private NavigatorMenu navigatorMenu;
    private ActivitiesMenu activitiesMenu;
    private LobbyItemService lobbyItemService;
    private LobbyExperienceListener experienceListener;
    private LobbyItemListener itemListener;
    private NavigatorListener navigatorListener;
    private ActivitiesListener activitiesListener;

    public LobbyExperienceModule(
            JavaPlugin plugin,
            LobbyModule lobbyModule,
            PlayerModule playerModule,
            SettingsModule settingsModule,
            ActivityModule activityModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyModule = Objects.requireNonNull(lobbyModule, "lobbyModule");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.settingsModule = Objects.requireNonNull(settingsModule, "settingsModule");
        this.activityModule = Objects.requireNonNull(activityModule, "activityModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "LobbyExperience";
    }

    @Override
    public void enable() {
        LobbyService newLobbyService = lobbyModule.getLobbyService();
        LobbyConfig newLobbyConfig = lobbyModule.getLobbyConfig();
        PlayerService newPlayerService = playerModule.getPlayerService();
        PlayerSettingsService newPlayerSettingsService = playerModule.getPlayerSettingsService();
        SettingsMenu newSettingsMenu = settingsModule.getSettingsMenu();
        ActivityCatalog newActivityCatalog = activityModule.getActivityCatalog();

        LobbyVisibilityService newVisibilityService = new LobbyVisibilityService(
                plugin,
                newLobbyService,
                newPlayerSettingsService
        );
        NavigatorMenu newNavigatorMenu = new NavigatorMenu(plugin);
        ActivitiesMenu newActivitiesMenu = new ActivitiesMenu(
                plugin,
                newActivityCatalog,
                newNavigatorMenu
        );
        LobbyItemService newLobbyItemService = new LobbyItemService(
                plugin,
                newLobbyService,
                newPlayerSettingsService
        );
        LobbyExperienceListener newExperienceListener = new LobbyExperienceListener(
                plugin,
                newLobbyConfig,
                newLobbyService,
                newPlayerService,
                newLobbyItemService,
                newVisibilityService,
                messageService
        );
        LobbyItemListener newItemListener = new LobbyItemListener(
                plugin,
                newLobbyService,
                newLobbyItemService,
                newVisibilityService,
                newPlayerSettingsService,
                newNavigatorMenu,
                newSettingsMenu,
                messageService
        );
        NavigatorListener newNavigatorListener = new NavigatorListener(
                newLobbyService,
                messageService,
                newActivitiesMenu
        );
        ActivitiesListener newActivitiesListener = new ActivitiesListener(
                newActivityCatalog,
                newActivitiesMenu
        );

        try {
            plugin.getServer().getPluginManager().registerEvents(newExperienceListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newItemListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newNavigatorListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newActivitiesListener, plugin);

            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!newLobbyService.isLobbyWorld(player.getWorld())) {
                    continue;
                }
                newLobbyItemService.applyLobbyItems(player);
                newVisibilityService.synchronizePlayer(player);
            }
        } catch (RuntimeException exception) {
            newExperienceListener.deactivate();
            HandlerList.unregisterAll(newActivitiesListener);
            HandlerList.unregisterAll(newNavigatorListener);
            HandlerList.unregisterAll(newItemListener);
            HandlerList.unregisterAll(newExperienceListener);
            cleanupRuntime(newNavigatorMenu, newActivitiesMenu, newLobbyItemService, newVisibilityService);
            throw exception;
        }

        visibilityService = newVisibilityService;
        navigatorMenu = newNavigatorMenu;
        activitiesMenu = newActivitiesMenu;
        lobbyItemService = newLobbyItemService;
        experienceListener = newExperienceListener;
        itemListener = newItemListener;
        navigatorListener = newNavigatorListener;
        activitiesListener = newActivitiesListener;
        plugin.getLogger().info("Lobby experience module enabled.");
    }

    @Override
    public void disable() {
        if (experienceListener != null) {
            experienceListener.deactivate();
            HandlerList.unregisterAll(experienceListener);
        }
        if (itemListener != null) {
            HandlerList.unregisterAll(itemListener);
        }
        if (navigatorListener != null) {
            HandlerList.unregisterAll(navigatorListener);
        }
        if (activitiesListener != null) {
            HandlerList.unregisterAll(activitiesListener);
        }

        cleanupRuntime(navigatorMenu, activitiesMenu, lobbyItemService, visibilityService);

        activitiesListener = null;
        navigatorListener = null;
        itemListener = null;
        experienceListener = null;
        lobbyItemService = null;
        activitiesMenu = null;
        navigatorMenu = null;
        visibilityService = null;
    }

    private void cleanupRuntime(
            NavigatorMenu activeNavigatorMenu,
            ActivitiesMenu activeActivitiesMenu,
            LobbyItemService activeLobbyItemService,
            LobbyVisibilityService activeVisibilityService
    ) {
        if (activeNavigatorMenu != null) {
            try {
                activeNavigatorMenu.closeOpenInventories();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not close all navigator inventories.", exception);
            }
        }
        if (activeActivitiesMenu != null) {
            try {
                activeActivitiesMenu.closeOpenInventories();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not close all activities inventories.", exception);
            }
        }
        if (activeLobbyItemService != null) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                try {
                    activeLobbyItemService.removeManagedItems(player);
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(
                            Level.WARNING,
                            "Could not remove lobby items for " + player.getUniqueId() + ".",
                            exception
                    );
                }
            }
        }
        if (activeVisibilityService != null) {
            try {
                activeVisibilityService.restoreAll();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not fully restore lobby visibility.", exception);
            }
        }
    }
}
