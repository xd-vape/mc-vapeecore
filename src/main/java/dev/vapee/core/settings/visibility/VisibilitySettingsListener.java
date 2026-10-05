package dev.vapee.core.settings.visibility;

import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.settings.command.SettingsCommand;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.settings.SettingsMenu;
import dev.vapee.core.visibility.VisibilityService;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VisibilitySettingsListener implements Listener {
    private final VisibilitySettingsMenu menu;
    private final VisiblePlayersMenu visiblePlayersMenu;
    private final Consumer<Player> settingsMenuOpener;
    private final PlayerSettingsService settings;
    private final Consumer<Player> applyVisibility;
    private final Consumer<Player> refreshHotbar;
    private final Consumer<Player> soundFeedback;
    private final MessageService messages;
    private final Logger logger;

    public VisibilitySettingsListener(VisibilitySettingsMenu menu, VisiblePlayersMenu visiblePlayersMenu,
                                      SettingsMenu settingsMenu, PlayerSettingsService settings,
                                      VisibilityService visibilityService, LobbyItemService lobbyItems,
                                      MessageService messages, Logger logger) {
        this(menu, visiblePlayersMenu, Objects.requireNonNull(settingsMenu, "settingsMenu")::open, settings,
                Objects.requireNonNull(visibilityService, "visibilityService")::applyViewerPreference,
                Objects.requireNonNull(lobbyItems, "lobbyItems")::refreshVisibilityItem,
                player -> playFeedbackSound(player, settings, logger),
                messages, logger);
    }

    VisibilitySettingsListener(VisibilitySettingsMenu menu, VisiblePlayersMenu visiblePlayersMenu,
                               Consumer<Player> settingsMenuOpener, PlayerSettingsService settings,
                               Consumer<Player> applyVisibility, Consumer<Player> refreshHotbar,
                               Consumer<Player> soundFeedback,
                               MessageService messages, Logger logger) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.visiblePlayersMenu = Objects.requireNonNull(visiblePlayersMenu, "visiblePlayersMenu");
        this.settingsMenuOpener = Objects.requireNonNull(settingsMenuOpener, "settingsMenuOpener");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.applyVisibility = Objects.requireNonNull(applyVisibility, "applyVisibility");
        this.refreshHotbar = Objects.requireNonNull(refreshHotbar, "refreshHotbar");
        this.soundFeedback = Objects.requireNonNull(soundFeedback, "soundFeedback");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof VisibilitySettingsHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.isActive(player, top, holder)) return;
        if (!player.hasPermission(SettingsCommand.PERMISSION)) {
            menu.forgetIfActive(player.getUniqueId(), top);
            if (player.getOpenInventory().getTopInventory() == top) player.closeInventory();
            messages.send(player, "<red>You do not have permission to use settings.</red>");
            return;
        }
        if (event.getClickedInventory() != top) return;
        ClickType click = event.getClick();
        if (click != ClickType.LEFT && click != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= VisibilitySettingsMenu.INVENTORY_SIZE) return;

        switch (slot) {
            case VisibilitySettingsMenu.BACK_SLOT -> settingsMenuOpener.accept(player);
            case VisibilitySettingsMenu.CLOSE_SLOT -> player.closeInventory();
            case VisibilitySettingsMenu.REFRESH_SLOT -> menu.open(player);
            case VisibilitySettingsMenu.MANAGE_PLAYERS_SLOT -> visiblePlayersMenu.open(player);
            case VisibilitySettingsMenu.GAME_PARTICIPANTS_SLOT,
                 VisibilitySettingsMenu.GAME_PARTICIPANTS_STATUS_SLOT -> messages.send(player,
                    "<yellow>This visibility option will become available with game integration.</yellow>");
            case VisibilitySettingsMenu.MASTER_SLOT, VisibilitySettingsMenu.MASTER_STATUS_SLOT ->
                    toggle(player, Toggle.MASTER);
            case VisibilitySettingsMenu.FRIENDS_SLOT, VisibilitySettingsMenu.FRIENDS_STATUS_SLOT ->
                    toggle(player, Toggle.FRIENDS);
            case VisibilitySettingsMenu.STAFF_SLOT, VisibilitySettingsMenu.STAFF_STATUS_SLOT ->
                    toggle(player, Toggle.STAFF);
            case VisibilitySettingsMenu.ADDED_USERS_SLOT, VisibilitySettingsMenu.ADDED_USERS_STATUS_SLOT ->
                    toggle(player, Toggle.ADDED_USERS);
            default -> { }
        }
    }

    private void toggle(Player player, Toggle toggle) {
        UUID owner = player.getUniqueId();
        Optional<PlayerSettings> current = settings.getSettings(owner);
        if (current.isEmpty()) {
            unavailable(player);
            return;
        }
        PlayerSettings value = current.get();
        boolean saved;
        try {
            saved = switch (toggle) {
                case MASTER -> settings.setLobbyPlayersVisible(owner, !value.isLobbyPlayersVisible());
                case FRIENDS -> settings.setLobbyFriendsVisible(owner,
                        !value.getVisibility().isShowFriends());
                case STAFF -> settings.setLobbyStaffVisible(owner,
                        !value.getVisibility().isShowStaff());
                case ADDED_USERS -> settings.setLobbyAddedUsersVisible(owner,
                        !value.getVisibility().isShowAddedUsers());
            };
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not save visibility settings for " + owner + ".", exception);
            messages.send(player, "<red>Your visibility setting could not be saved. Please try again.</red>");
            menu.open(player);
            return;
        }
        if (!saved) {
            unavailable(player);
            return;
        }

        try {
            applyVisibility.accept(player);
            if (toggle == Toggle.MASTER) refreshHotbar.accept(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not immediately apply visibility settings for " + owner + ".",
                    exception);
            messages.send(player,
                    "<yellow>Your setting was saved but could not be applied immediately.</yellow>");
        }
        menu.open(player);
        soundFeedback.accept(player);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof VisibilitySettingsHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof VisibilitySettingsHolder holder) {
            menu.forgetIfActive(holder.getOwnerUniqueId(), top);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof VisibilitySettingsHolder) {
            menu.forgetIfActive(event.getPlayer().getUniqueId(), top);
        }
    }

    private void unavailable(Player player) {
        player.closeInventory();
        messages.send(player, "<red>Your player profile is not available.</red>");
    }

    private static void playFeedbackSound(Player player, PlayerSettingsService settings, Logger logger) {
        if (!settings.areSoundsEnabled(player.getUniqueId()).orElse(false)) return;
        try {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, SoundCategory.MASTER, 0.5F, 1.0F);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING,
                    "Could not play visibility settings sound for " + player.getUniqueId() + ".", exception);
        }
    }

    private enum Toggle { MASTER, FRIENDS, STAFF, ADDED_USERS }
}
