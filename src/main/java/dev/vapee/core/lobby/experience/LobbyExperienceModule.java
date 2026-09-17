package dev.vapee.core.lobby.experience;

import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.experience.navigator.NavigatorListener;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
import dev.vapee.core.lobby.warp.WarpModule;
import dev.vapee.core.lobby.warp.WarpService;
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
    private final WarpModule warpModule;
    private final MessageService messageService;

    private LobbyVisibilityService visibilityService;
    private NavigatorMenu navigatorMenu;
    private LobbyItemService lobbyItemService;
    private LobbyExperienceListener experienceListener;
    private LobbyItemListener itemListener;
    private NavigatorListener navigatorListener;

    public LobbyExperienceModule(
            JavaPlugin plugin,
            LobbyModule lobbyModule,
            PlayerModule playerModule,
            SettingsModule settingsModule,
            WarpModule warpModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyModule = Objects.requireNonNull(lobbyModule, "lobbyModule");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.settingsModule = Objects.requireNonNull(settingsModule, "settingsModule");
        this.warpModule = Objects.requireNonNull(warpModule, "warpModule");
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
        WarpService newWarpService = warpModule.getWarpService();

        LobbyVisibilityService newVisibilityService = new LobbyVisibilityService(
                plugin,
                newLobbyService,
                newPlayerSettingsService
        );
        NavigatorMenu newNavigatorMenu = new NavigatorMenu(plugin, newWarpService);
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
                newWarpService,
                messageService,
                newNavigatorMenu
        );

        try {
            plugin.getServer().getPluginManager().registerEvents(newExperienceListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newItemListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newNavigatorListener, plugin);

            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!newLobbyService.isLobbyWorld(player.getWorld())) {
                    continue;
                }
                newLobbyItemService.applyLobbyItems(player);
                newVisibilityService.synchronizePlayer(player);
            }
        } catch (RuntimeException exception) {
            newExperienceListener.deactivate();
            HandlerList.unregisterAll(newNavigatorListener);
            HandlerList.unregisterAll(newItemListener);
            HandlerList.unregisterAll(newExperienceListener);
            cleanupRuntime(newNavigatorMenu, newLobbyItemService, newVisibilityService);
            throw exception;
        }

        visibilityService = newVisibilityService;
        navigatorMenu = newNavigatorMenu;
        lobbyItemService = newLobbyItemService;
        experienceListener = newExperienceListener;
        itemListener = newItemListener;
        navigatorListener = newNavigatorListener;
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
        cleanupRuntime(navigatorMenu, lobbyItemService, visibilityService);

        navigatorListener = null;
        itemListener = null;
        experienceListener = null;
        lobbyItemService = null;
        navigatorMenu = null;
        visibilityService = null;
    }

    private void cleanupRuntime(
            NavigatorMenu activeNavigatorMenu,
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
