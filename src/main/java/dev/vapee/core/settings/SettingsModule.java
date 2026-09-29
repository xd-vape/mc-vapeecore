package dev.vapee.core.settings;

import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.presentation.PresentationModule;
import dev.vapee.core.presentation.PresentationService;
import dev.vapee.core.settings.command.SettingsCommand;
import dev.vapee.core.settings.visibility.VisiblePlayersListener;
import dev.vapee.core.settings.visibility.VisiblePlayersMenu;
import dev.vapee.core.settings.visibility.VisibilitySettingsListener;
import dev.vapee.core.settings.visibility.VisibilitySettingsMenu;
import dev.vapee.core.visibility.VisibilityModule;
import dev.vapee.core.visibility.VisibilityService;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.logging.Level;

public final class SettingsModule implements CoreModule {

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final PresentationModule presentationModule;
    private final IdentityModule identityModule;
    private final VisibilityModule visibilityModule;
    private final LobbyModule lobbyModule;
    private final MessageService messageService;

    private PlayerSettingsService playerSettingsService;
    private PresentationService presentationService;
    private SettingsMenu settingsMenu;
    private SettingsListener settingsListener;
    private VisibilitySettingsMenu visibilitySettingsMenu;
    private VisibilitySettingsListener visibilitySettingsListener;
    private VisiblePlayersMenu visiblePlayersMenu;
    private VisiblePlayersListener visiblePlayersListener;
    private PluginCommand settingsCommand;

    public SettingsModule(
            JavaPlugin plugin,
            PlayerModule playerModule,
            PresentationModule presentationModule,
            IdentityModule identityModule,
            VisibilityModule visibilityModule,
            LobbyModule lobbyModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.presentationModule = Objects.requireNonNull(presentationModule, "presentationModule");
        this.identityModule = Objects.requireNonNull(identityModule, "identityModule");
        this.visibilityModule = Objects.requireNonNull(visibilityModule, "visibilityModule");
        this.lobbyModule = Objects.requireNonNull(lobbyModule, "lobbyModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Settings";
    }

    @Override
    public void enable() {
        PlayerSettingsService newPlayerSettingsService = playerModule.getPlayerSettingsService();
        PresentationService newPresentationService = presentationModule.getPresentationService();
        PlayerIdentityService newIdentityService = identityModule.getIdentityService();
        VisibilityService newVisibilityService = visibilityModule.getVisibilityService();
        LobbyItemService newLobbyItemService = lobbyModule.getLobbyItemService();
        SettingsMenu newSettingsMenu = new SettingsMenu(plugin, newPlayerSettingsService, messageService);
        VisibilitySettingsMenu newVisibilitySettingsMenu = new VisibilitySettingsMenu(
                plugin, newPlayerSettingsService, messageService);
        VisiblePlayersMenu newVisiblePlayersMenu = new VisiblePlayersMenu(
                plugin, newPlayerSettingsService, newIdentityService, messageService);
        SettingsListener newSettingsListener = new SettingsListener(
                newSettingsMenu,
                newPlayerSettingsService,
                newPresentationService,
                newVisibilitySettingsMenu::open,
                messageService,
                plugin.getLogger()
        );
        VisibilitySettingsListener newVisibilitySettingsListener = new VisibilitySettingsListener(
                newVisibilitySettingsMenu,
                newVisiblePlayersMenu,
                newSettingsMenu,
                newPlayerSettingsService,
                newVisibilityService,
                newLobbyItemService,
                messageService,
                plugin.getLogger()
        );
        VisiblePlayersListener newVisiblePlayersListener = new VisiblePlayersListener(
                newVisiblePlayersMenu,
                newVisibilitySettingsMenu,
                newPlayerSettingsService,
                newVisibilityService,
                messageService,
                plugin.getLogger()
        );
        PluginCommand newSettingsCommand = Objects.requireNonNull(
                plugin.getCommand("settings"),
                "Command 'settings' is missing from plugin.yml"
        );
        SettingsCommand newSettingsExecutor = new SettingsCommand(plugin, newSettingsMenu,
                newVisibilitySettingsMenu, newPlayerSettingsService, newIdentityService,
                newVisibilityService, messageService);

        try {
            plugin.getServer().getPluginManager().registerEvents(newSettingsListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newVisibilitySettingsListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newVisiblePlayersListener, plugin);
            newSettingsCommand.setExecutor(newSettingsExecutor);
            newSettingsCommand.setTabCompleter(newSettingsExecutor);
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(newSettingsListener);
            HandlerList.unregisterAll(newVisibilitySettingsListener);
            HandlerList.unregisterAll(newVisiblePlayersListener);
            newSettingsCommand.setExecutor(null);
            newSettingsCommand.setTabCompleter(null);
            throw exception;
        }

        playerSettingsService = newPlayerSettingsService;
        presentationService = newPresentationService;
        settingsMenu = newSettingsMenu;
        settingsListener = newSettingsListener;
        visibilitySettingsMenu = newVisibilitySettingsMenu;
        visibilitySettingsListener = newVisibilitySettingsListener;
        visiblePlayersMenu = newVisiblePlayersMenu;
        visiblePlayersListener = newVisiblePlayersListener;
        settingsCommand = newSettingsCommand;
        plugin.getLogger().info("Settings module enabled.");
    }

    @Override
    public void disable() {
        closeVisibilityMenus();
        try {
            if (settingsMenu != null) settingsMenu.closeOpenInventories();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not close all player settings menus.", exception);
        }
        if (settingsListener != null) {
            HandlerList.unregisterAll(settingsListener);
        }
        if (visibilitySettingsListener != null) {
            HandlerList.unregisterAll(visibilitySettingsListener);
        }
        if (visiblePlayersListener != null) {
            HandlerList.unregisterAll(visiblePlayersListener);
        }
        if (settingsCommand != null) {
            settingsCommand.setExecutor(null);
            settingsCommand.setTabCompleter(null);
        }

        settingsCommand = null;
        visiblePlayersListener = null;
        visiblePlayersMenu = null;
        visibilitySettingsListener = null;
        visibilitySettingsMenu = null;
        settingsListener = null;
        settingsMenu = null;
        presentationService = null;
        playerSettingsService = null;
    }

    public SettingsMenu getSettingsMenu() {
        return Objects.requireNonNull(settingsMenu, "SettingsModule is not enabled");
    }

    public VisibilitySettingsMenu getVisibilitySettingsMenu() {
        return Objects.requireNonNull(visibilitySettingsMenu, "SettingsModule is not enabled");
    }

    private void closeVisibilityMenus() {
        try {
            if (visibilitySettingsMenu != null) visibilitySettingsMenu.closeOpenInventories();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not close all visibility settings menus.", exception);
        }
        try {
            if (visiblePlayersMenu != null) visiblePlayersMenu.closeOpenInventories();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not close all visible players menus.", exception);
        }
    }
}
