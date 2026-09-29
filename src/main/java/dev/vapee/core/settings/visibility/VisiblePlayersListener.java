package dev.vapee.core.settings.visibility;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.AddedVisiblePlayerResult;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.visibility.VisibilityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VisiblePlayersListener implements Listener {
    private final VisiblePlayersMenu menu;
    private final VisibilitySettingsMenu visibilitySettingsMenu;
    private final PlayerSettingsService settings;
    private final Consumer<Player> applyVisibility;
    private final Consumer<Player> soundFeedback;
    private final MessageService messages;
    private final Logger logger;

    public VisiblePlayersListener(VisiblePlayersMenu menu, VisibilitySettingsMenu visibilitySettingsMenu,
                                  PlayerSettingsService settings, VisibilityService visibilityService,
                                  MessageService messages, Logger logger) {
        this(menu, visibilitySettingsMenu, settings,
                Objects.requireNonNull(visibilityService, "visibilityService")::applyViewerPreference,
                player -> playFeedbackSound(player, settings, logger),
                messages, logger);
    }

    VisiblePlayersListener(VisiblePlayersMenu menu, VisibilitySettingsMenu visibilitySettingsMenu,
                           PlayerSettingsService settings, Consumer<Player> applyVisibility,
                           Consumer<Player> soundFeedback,
                           MessageService messages, Logger logger) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.visibilitySettingsMenu = Objects.requireNonNull(visibilitySettingsMenu, "visibilitySettingsMenu");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.applyVisibility = Objects.requireNonNull(applyVisibility, "applyVisibility");
        this.soundFeedback = Objects.requireNonNull(soundFeedback, "soundFeedback");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof VisiblePlayersHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.isActive(player, top, holder)
                || event.getClickedInventory() != top) return;
        ClickType click = event.getClick();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= VisiblePlayersMenu.INVENTORY_SIZE) return;
        if (click == ClickType.LEFT || click == ClickType.RIGHT) {
            if (navigate(player, holder, slot)) return;
        }
        if (slot >= VisiblePlayersMenu.CONTENT_SIZE || click != ClickType.RIGHT) return;
        UUID target = holder.getTarget(slot).orElse(null);
        if (target == null) return;
        remove(player, holder, target);
    }

    private boolean navigate(Player player, VisiblePlayersHolder holder, int slot) {
        switch (slot) {
            case VisiblePlayersMenu.PREVIOUS_SLOT -> {
                if (holder.getPage() > 0) menu.open(player, holder.getPage() - 1);
                return true;
            }
            case VisiblePlayersMenu.ADD_SLOT -> {
                menu.promptAdd(player);
                return true;
            }
            case VisiblePlayersMenu.BACK_SLOT -> {
                visibilitySettingsMenu.open(player);
                return true;
            }
            case VisiblePlayersMenu.CLOSE_SLOT -> {
                player.closeInventory();
                return true;
            }
            case VisiblePlayersMenu.REFRESH_SLOT -> {
                menu.open(player, holder.getPage());
                return true;
            }
            case VisiblePlayersMenu.NEXT_SLOT -> {
                if (menu.hasNext(player.getUniqueId(), holder.getPage())) {
                    menu.open(player, holder.getPage() + 1);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private void remove(Player player, VisiblePlayersHolder holder, UUID target) {
        UUID owner = player.getUniqueId();
        AddedVisiblePlayerResult result;
        try {
            result = settings.removeLobbyVisiblePlayer(owner, target);
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not remove visible player for " + owner + ".", exception);
            messages.send(player, "<red>Your visible players list could not be saved. Please try again.</red>");
            menu.open(player, holder.getPage());
            return;
        }
        switch (result) {
            case SUCCESS -> {
                try {
                    applyVisibility.accept(player);
                } catch (RuntimeException exception) {
                    logger.log(Level.WARNING, "Could not immediately apply visible players for " + owner + ".",
                            exception);
                }
                messages.send(player, Component.text("Removed ", NamedTextColor.GREEN)
                        .append(Component.text(menu.displayName(target), NamedTextColor.WHITE))
                        .append(Component.text(" from your visible players list.", NamedTextColor.GREEN)));
                menu.open(player, holder.getPage());
                soundFeedback.accept(player);
            }
            case NOT_ADDED -> {
                messages.send(player, "<yellow>That player is no longer in your visible players list.</yellow>");
                menu.open(player, holder.getPage());
            }
            case OWNER_NOT_LOADED -> unavailable(player);
            case CANNOT_ADD_SELF, ALREADY_ADDED -> throw new IllegalStateException(
                    "Remove returned an add-only result: " + result);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof VisiblePlayersHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof VisiblePlayersHolder holder) {
            menu.forgetIfActive(holder.getOwnerUniqueId(), top);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Inventory top = event.getPlayer().getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof VisiblePlayersHolder) {
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
                    "Could not play visible players sound for " + player.getUniqueId() + ".", exception);
        }
    }
}
