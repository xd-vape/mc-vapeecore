package dev.vapee.core.lobby.experience;

import dev.vapee.core.lobby.experience.player.PlayerSelectionListener;
import dev.vapee.core.lobby.experience.player.PlayerMenu;
import dev.vapee.core.lobby.experience.player.PlayerMenuListener;
import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.lobby.item.LobbyItemRegistrations;
import dev.vapee.core.visibility.VisibilityLobbyItemAction;
import dev.vapee.core.lobby.experience.glide.LobbyGlideService;
import dev.vapee.core.lobby.experience.glide.LobbyGlideListener;
import dev.vapee.core.utility.UtilityModule;
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
    private final UtilityModule utilityModule;
    private LobbyGlideService glideService;
    private LobbyGlideListener glideListener;
    private final MessageService messageService;
    private final java.util.function.Consumer<Player> localeChanges = this::refreshLocaleItems;
    private final java.util.function.Consumer<EnableStage> enableCheckpoint;
    private VisibilityLobbyItemAction visibilityAction;

    // Package-local fault boundary for lifecycle tests; production supplies a no-op.
    enum EnableStage { BEFORE_CALLBACKS, VISIBILITY_BACK_INSTALLED, SETTINGS_BACK_INSTALLED, RUNTIME_ACTIVE }

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
            UtilityModule utilityModule,
            MessageService messageService
    ) {
        this(plugin, lobbyModule, playerModule, settingsModule, warpModule, visibilityModule,
                activityModule, friendModule, utilityModule, messageService, stage -> { });
    }

    LobbyExperienceModule(JavaPlugin plugin, LobbyModule lobbyModule, PlayerModule playerModule,
                          SettingsModule settingsModule, WarpModule warpModule, VisibilityModule visibilityModule,
                          ActivityModule activityModule, FriendModule friendModule, UtilityModule utilityModule,
                          MessageService messageService, java.util.function.Consumer<EnableStage> enableCheckpoint) {
        this.enableCheckpoint = Objects.requireNonNull(enableCheckpoint, "enableCheckpoint");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyModule = Objects.requireNonNull(lobbyModule, "lobbyModule");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.settingsModule = Objects.requireNonNull(settingsModule, "settingsModule");
        this.warpModule = Objects.requireNonNull(warpModule, "warpModule");
        this.visibilityModule = Objects.requireNonNull(visibilityModule, "visibilityModule");
        this.activityModule = Objects.requireNonNull(activityModule, "activityModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.friendModule = Objects.requireNonNull(friendModule, "friendModule");
        this.utilityModule = Objects.requireNonNull(utilityModule, "utilityModule");
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
        var newGlide = new LobbyGlideService(plugin, newAccessPolicy, utilityModule.getUtilityService());
        var newGlideListener = new LobbyGlideListener(newGlide);
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

        // Registry activation gates these suppliers until startup has completed enough to use them.
        navigatorMenu = newNavigatorMenu;
        playerMenu = newPlayerMenu;
        playerMenuListener = newPlayerMenuListener;
        playerSelectionListener = newSelectionListener;
        experienceListener = newExperienceListener;
        itemListener = newItemListener;
        navigatorListener = newNavigatorListener;
        glideService = newGlide;
        glideListener = newGlideListener;
        try {
            utilityModule.getUtilityService().addFlightListener(newGlide);
            lobbyModule.getLobbyPlayerStateService().addStateListener(newGlide);
            activityModule.getActivityService().addMembershipListener(newGlide);
            plugin.getServer().getPluginManager().registerEvents(newGlideListener, plugin);
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
            enableCheckpoint.accept(EnableStage.BEFORE_CALLBACKS);
            newPlayerMenu.startUpdates();
            settingsModule.setVisibilityBackOpener(player -> {
                if (!newPlayerMenu.open(player)) player.closeInventory();
            });
            enableCheckpoint.accept(EnableStage.VISIBILITY_BACK_INSTALLED);
            settingsModule.setSettingsBackOpener(player -> {
                if (!newPlayerMenu.open(player)) {
                    player.closeInventory();
                }
            });
            enableCheckpoint.accept(EnableStage.SETTINGS_BACK_INSTALLED);
            newNavigatorMenu.startUpdates();
            visibilityAction = newVisibilityAction;
            registry.activate();
            messageService.localization().addLocaleListener(localeChanges);
            lobbyModule.getLobbyPlayerStateService().setItemRefreshEligibility(player ->
                    newPlayerService.isLoaded(player.getUniqueId())
                            && activityModule.getActivityService().getSessionForPlayer(player.getUniqueId()).isEmpty());
            lobbyModule.getLobbyPlayerStateService().refreshLobbyItems();
            for (Player player : plugin.getServer().getOnlinePlayers()) newGlide.onPlayerAvailable(player);
            enableCheckpoint.accept(EnableStage.RUNTIME_ACTIVE);
            plugin.getLogger().info("Lobby experience module enabled.");
        } catch (RuntimeException exception) {
            cleanupRuntime(exception);
            throw exception;
        }
    }

    @Override
    public void disable() {
        if (playerMenu == null && navigatorMenu == null && glideService == null) {
            return;
        }
        RuntimeException failures = new IllegalStateException("LobbyExperience cleanup failed.");
        cleanupRuntime(failures);
        if (failures.getSuppressed().length != 0) {
            plugin.getLogger().log(Level.WARNING, "Could not release all LobbyExperience resources.", failures);
        }
    }

    private void cleanupRuntime(RuntimeException failure) {
        cleanupStep(failure, "item registry", () -> lobbyModule.getItemRegistry().deactivate());
        cleanupStep(failure, "item refresh eligibility", () ->
                lobbyModule.getLobbyPlayerStateService().setItemRefreshEligibility(player -> false));
        cleanupStep(failure, "visibility back callback", () -> settingsModule.setVisibilityBackOpener(Player::closeInventory));
        cleanupStep(failure, "settings back callback", () -> settingsModule.setSettingsBackOpener(Player::closeInventory));
        cleanupStep(failure, "locale observer", () -> messageService.localization().removeLocaleListener(localeChanges));
        if (experienceListener != null) {
            cleanupStep(failure, "experience callbacks", experienceListener::deactivate);
        }
        if (playerMenu != null) {
            cleanupStep(failure, "player menu", playerMenu::shutdown);
        }
        if (navigatorMenu != null) {
            cleanupStep(failure, "navigator menu", navigatorMenu::shutdown);
        }
        if (glideService != null) {
            LobbyGlideService ownedGlide = glideService;
            cleanupStep(failure, "flight subscription",
                    () -> utilityModule.getUtilityService().removeFlightListener(ownedGlide));
            cleanupStep(failure, "lobby state subscription",
                    () -> lobbyModule.getLobbyPlayerStateService().removeStateListener(ownedGlide));
            cleanupStep(failure, "activity subscription",
                    () -> activityModule.getActivityService().removeMembershipListener(ownedGlide));
            cleanupStep(failure, "glide runtime", ownedGlide::shutdown);
        }
        if (glideListener != null) {
            cleanupStep(failure, "glide listener",
                    () -> HandlerList.unregisterAll(glideListener));
        }
        if (playerMenuListener != null) {
            cleanupStep(failure, "player menu listener",
                    () -> HandlerList.unregisterAll(playerMenuListener));
        }
        if (playerSelectionListener != null) {
            cleanupStep(failure, "player selection listener",
                    () -> HandlerList.unregisterAll(playerSelectionListener));
        }
        if (navigatorListener != null) {
            cleanupStep(failure, "navigator listener",
                    () -> HandlerList.unregisterAll(navigatorListener));
        }
        if (itemListener != null) {
            cleanupStep(failure, "item listener",
                    () -> HandlerList.unregisterAll(itemListener));
        }
        if (experienceListener != null) {
            cleanupStep(failure, "experience listener",
                    () -> HandlerList.unregisterAll(experienceListener));
        }
        glideService = null;
        glideListener = null;
        playerMenu = null;
        playerMenuListener = null;
        playerSelectionListener = null;
        navigatorMenu = null;
        navigatorListener = null;
        itemListener = null;
        experienceListener = null;
        visibilityAction = null;
    }

    private void cleanupStep(RuntimeException failure, String resource, Runnable release) {
        try {
            release.run();
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(new IllegalStateException("Could not release " + resource, cleanupFailure));
        }
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

}
