package dev.vapee.core.lobby.experience;

import dev.vapee.core.lobby.experience.player.PlayerSelectionListener;
import dev.vapee.core.lobby.experience.player.PlayerMenu;
import dev.vapee.core.lobby.experience.player.PlayerMenuListener;
import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.lobby.item.LobbyItemRegistrations;
import dev.vapee.core.visibility.VisibilityLobbyItemAction;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.experience.navigator.NavigatorListener;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
import dev.vapee.core.lobby.experience.navigator.NavigatorAccessPolicy;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.lobby.message.LobbyMessageService;
import dev.vapee.core.lobby.warp.WarpModule;
import dev.vapee.core.lobby.warp.WarpService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.settings.SettingsModule;
import dev.vapee.core.visibility.VisibilityModule;
import dev.vapee.core.visibility.VisibilityService;
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
    private final VisibilityModule visibilityModule;
    private final ActivityModule activityModule;
    private final MessageService messageService;
    private final java.util.function.Consumer<Player> localeChanges = this::refreshLocaleItems;    private VisibilityLobbyItemAction visibilityAction;

    private NavigatorMenu navigatorMenu;
    private LobbyExperienceListener experienceListener;
    private LobbyItemListener itemListener;
    private NavigatorListener navigatorListener;
    private PlayerMenu playerMenu;
    private PlayerMenuListener playerMenuListener;
    private PlayerSelectionListener playerSelectionListener;
    private final FriendModule friendModule;

    public LobbyExperienceModule(
            JavaPlugin plugin,
            LobbyModule lobbyModule,
            PlayerModule playerModule,
            SettingsModule settingsModule,
            WarpModule warpModule,
            VisibilityModule visibilityModule,
            ActivityModule activityModule,
            FriendModule friendModule,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyModule = Objects.requireNonNull(lobbyModule, "lobbyModule");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.settingsModule = Objects.requireNonNull(settingsModule, "settingsModule");
        this.warpModule = Objects.requireNonNull(warpModule, "warpModule");
        this.visibilityModule = Objects.requireNonNull(visibilityModule, "visibilityModule");
        this.activityModule = Objects.requireNonNull(activityModule, "activityModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.friendModule = Objects.requireNonNull(friendModule, "friendModule");
        // Registration precedes Lobby configuration loading; menu suppliers are invoked only after activation.
        LobbyItemRegistrations.register(lobbyModule.getItemRegistry(), () -> navigatorMenu,
                settingsModule::getSettingsMenu, friendModule::getFriendMenu, () -> visibilityAction, () -> playerMenu);
    }

    @Override
    public String getName() {
        return "LobbyExperience";
    }

    @Override
    public void enable() {
        LobbyService newLobbyService = lobbyModule.getLobbyService();
        LobbyItemService newLobbyItemService = lobbyModule.getLobbyItemService();
        LobbyMessageService newLobbyMessageService = lobbyModule.getLobbyMessageService();
        PlayerService newPlayerService = playerModule.getPlayerService();
        PlayerSettingsService newPlayerSettingsService = playerModule.getPlayerSettingsService();
        WarpService newWarpService = warpModule.getWarpService();

        VisibilityService newVisibilityService = visibilityModule.getVisibilityService();
        NavigatorAccessPolicy newAccessPolicy = new NavigatorAccessPolicy(newPlayerService, newLobbyService,
                lobbyModule.getLobbyPlayerStateService(), activityModule.getActivityService());
        NavigatorMenu newNavigatorMenu = new NavigatorMenu(plugin, newWarpService, newAccessPolicy, messageService);
        LobbyExperienceListener newExperienceListener = new LobbyExperienceListener(
                plugin,
                newLobbyService,
                newPlayerService,
                newVisibilityService,
                newLobbyMessageService
        );
        var registry = lobbyModule.getItemRegistry();
        var newVisibilityAction = new VisibilityLobbyItemAction(plugin, newLobbyService, newLobbyItemService,
                newVisibilityService, newPlayerSettingsService, messageService, this::playFeedbackSound,
                player -> registry.isActive() && newAccessPolicy.canAccess(player) && newLobbyItemService.isEnabled("visibility"));
        var newPlayerMenu = new PlayerMenu(plugin, newPlayerSettingsService,
                messageService, player -> registry.isActive() && newAccessPolicy.canAccess(player)
                        && newLobbyItemService.isEnabled("player-menu"), java.util.Map.of(
                PlayerMenu.Action.VISIBILITY, player -> settingsModule.getVisibilitySettingsMenu().open(player),
                PlayerMenu.Action.FRIENDS, player -> friendModule.getFriendMenu().open(player),
                PlayerMenu.Action.SETTINGS, player -> settingsModule.getSettingsMenu().open(player)));
        var newPlayerMenuListener = new PlayerMenuListener(newPlayerMenu);
        var newSelectionListener = new PlayerSelectionListener(registry,
                newLobbyItemService, newPlayerSettingsService, newAccessPolicy::canAccess,
                newVisibilityService::applyViewerPreference, messageService, plugin.getLogger());
        LobbyItemListener newItemListener = new LobbyItemListener(newLobbyService, newLobbyItemService,
                registry, newAccessPolicy::canAccess, this::playFeedbackSound, plugin.getLogger());
        NavigatorListener newNavigatorListener = new NavigatorListener(
                newWarpService,
                messageService,
                newNavigatorMenu
        );

        try {
            plugin.getServer().getPluginManager().registerEvents(newExperienceListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newItemListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newNavigatorListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newPlayerMenuListener, plugin);
            plugin.getServer().getPluginManager().registerEvents(newSelectionListener, plugin);

            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (!newLobbyService.isLobbyWorld(player.getWorld())) {
                    continue;
                }
                newVisibilityService.synchronizePlayer(player);
            }
            navigatorMenu = newNavigatorMenu;
            playerMenu = newPlayerMenu;
            playerMenuListener = newPlayerMenuListener;
            playerSelectionListener = newSelectionListener;
            newPlayerMenu.startUpdates();
            settingsModule.setVisibilityBackOpener(player -> {
                if (!newPlayerMenu.open(player)) player.closeInventory();
            });
            newNavigatorMenu.startUpdates();
            visibilityAction = newVisibilityAction;
            registry.activate();
            messageService.localization().addLocaleListener(localeChanges);
            experienceListener = newExperienceListener;
            itemListener = newItemListener;
            navigatorListener = newNavigatorListener;
            lobbyModule.getLobbyPlayerStateService().setItemRefreshEligibility(player ->
                    newPlayerService.isLoaded(player.getUniqueId())
                            && activityModule.getActivityService().getSessionForPlayer(player.getUniqueId()).isEmpty());
            lobbyModule.getLobbyPlayerStateService().refreshLobbyItems();
        } catch (RuntimeException exception) {
            settingsModule.setVisibilityBackOpener(Player::closeInventory);
            registry.deactivate();
            newPlayerMenu.shutdown();
            HandlerList.unregisterAll(newPlayerMenuListener);
            HandlerList.unregisterAll(newSelectionListener);
            playerMenu = null; playerMenuListener = null; playerSelectionListener = null;
            visibilityAction = null;
            navigatorMenu = null;
            experienceListener = null;
            itemListener = null;
            navigatorListener = null;
            lobbyModule.getLobbyPlayerStateService().setItemRefreshEligibility(player -> false);
            newExperienceListener.deactivate();
            HandlerList.unregisterAll(newNavigatorListener);
            HandlerList.unregisterAll(newItemListener);
            HandlerList.unregisterAll(newExperienceListener);
            cleanupRuntime(newNavigatorMenu);
            throw exception;
        }

        plugin.getLogger().info("Lobby experience module enabled.");
    }

    @Override
    public void disable() {
        settingsModule.setVisibilityBackOpener(Player::closeInventory);
        lobbyModule.getItemRegistry().deactivate();
        if (playerMenu != null) playerMenu.shutdown();
        if (playerMenuListener != null) HandlerList.unregisterAll(playerMenuListener);
        if (playerSelectionListener != null) HandlerList.unregisterAll(playerSelectionListener);
        playerMenu = null; playerMenuListener = null; playerSelectionListener = null;
        visibilityAction = null;
        lobbyModule.getLobbyPlayerStateService().setItemRefreshEligibility(player -> false);
        cleanupRuntime(navigatorMenu);
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
        navigatorListener = null;
        itemListener = null;
        experienceListener = null;
        navigatorMenu = null;
    }

    private void playFeedbackSound(Player player) {
        if (!playerModule.getPlayerSettingsService().areSoundsEnabled(player.getUniqueId()).orElse(false)) return;
        try { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5F, 1.0F); }
        catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not play lobby UI feedback sound for " + player.getUniqueId(), exception);
        }
    }

    private void refreshLocaleItems(Player player) {
        if (navigatorMenu != null && navigatorMenu.canAccess(player) && lobbyModule.getItemRegistry().isActive())
            lobbyModule.getLobbyItemService().applyLobbyItems(player);
    }

    private void cleanupRuntime(NavigatorMenu activeNavigatorMenu) {
        messageService.localization().removeLocaleListener(localeChanges);
        if (activeNavigatorMenu != null) {
            try {
                activeNavigatorMenu.shutdown();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Could not close all navigator inventories.", exception);
            }
        }
    }
}
