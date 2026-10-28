package dev.vapee.core.friend.gui;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.friend.FriendMessages;
import dev.vapee.core.friend.FriendResult;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.friend.command.FriendCommand;
import dev.vapee.core.message.MessageService;
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
import java.util.logging.Level;
import java.util.logging.Logger;

public final class FriendMenuListener implements Listener {

    private final FriendMenu menu;
    private final FriendService friends;
    private final FriendMessages feedback;
    private final MessageService messages;
    private final Logger logger;

    public FriendMenuListener(FriendMenu menu, FriendService friends, FriendMessages feedback,
                              MessageService messages, Logger logger) {
        this.menu = Objects.requireNonNull(menu, "menu");
        this.friends = Objects.requireNonNull(friends, "friends");
        this.feedback = Objects.requireNonNull(feedback, "feedback");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof FriendMenuHolder holder)) {
            return;
        }
        // Cancel even forged, stale and bottom-inventory interactions: this GUI is never item storage.
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player viewer
                && holder.getOwnerUniqueId().equals(viewer.getUniqueId()) && holder.isBoundTo(top)
                && viewer.getOpenInventory().getTopInventory() != top) {
            menu.forgetIfActive(viewer.getUniqueId(), top); return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || !menu.isActive(player, top, holder)) {
            return;
        }
        if (!player.hasPermission(FriendCommand.PERMISSION)) {
            menu.forgetIfActive(player.getUniqueId(), top);
            if (player.getOpenInventory().getTopInventory() == top) player.closeInventory();
            dev.vapee.core.message.SocialChannel.FRIENDS.error(messages, player, tr("friends.menu.denied"));
            return;
        }
        if (event.getClickedInventory() != top) {
            return;
        }
        ClickType click = event.getClick();
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) {
            return;
        }
        try {
            if (click == ClickType.LEFT || click == ClickType.RIGHT) {
                if (navigate(player, holder, slot)) {
                    return;
                }
            }
            UUID target = holder.getTarget(slot).orElse(null);
            if (target == null) {
                return;
            }
            String action = switch (holder.getView()) {
                case FRIENDS -> click == ClickType.SHIFT_RIGHT ? "remove" : null;
                case INCOMING -> click == ClickType.LEFT ? "accept"
                        : click == ClickType.RIGHT ? "deny" : null;
                case OUTGOING -> click == ClickType.LEFT || click == ClickType.RIGHT ? "cancel" : null;
            };
            if (action == null) {
                return;
            }
            UUID owner = player.getUniqueId();
            String targetName = menu.displayName(target);
            FriendResult result = switch (action) {
                case "remove" -> friends.removeFriend(owner, target);
                case "accept" -> friends.acceptRequest(owner, target);
                case "deny" -> friends.denyRequest(owner, target);
                case "cancel" -> friends.cancelRequest(owner, target);
                default -> throw new IllegalStateException("Unexpected friend action");
            };
            feedback.report(player, action, target, targetName, result);
            menu.refreshActive(player.getUniqueId());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not process friend menu action for " + player.getUniqueId() + ".",
                    exception);
            player.closeInventory();
            dev.vapee.core.message.SocialChannel.FRIENDS.error(messages, player, tr("friends.menu.update-failed"));
        }
    }

    private boolean navigate(Player player, FriendMenuHolder holder, int slot) {
        var action = holder.actionAt(slot).orElse(null);
        if (action == null) return false;
        switch (action) {
            case PAGE_INFO -> { return true; }
            case PREVIOUS -> {
                if (holder.getPage() > 0) {
                    menu.open(player, holder.getView(), holder.getPage() - 1);
                }
                return true;
            }
            case ADD -> {
                menu.promptAdd(player);
                return true;
            }
            case FRIENDS -> {
                menu.open(player, FriendMenuView.FRIENDS, 0);
                return true;
            }
            case INCOMING -> {
                menu.open(player, FriendMenuView.INCOMING, 0);
                return true;
            }
            case CLOSE -> {
                player.closeInventory();
                return true;
            }
            case OUTGOING -> {
                menu.open(player, FriendMenuView.OUTGOING, 0);
                return true;
            }
            case REFRESH -> {
                menu.refreshActive(player.getUniqueId());
                return true;
            }
            case NEXT -> {
                if (menu.hasNext(player.getUniqueId(), holder.getView(), holder.getPage())) {
                    menu.open(player, holder.getView(), holder.getPage() + 1);
                }
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof FriendMenuHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof FriendMenuHolder holder) {
            menu.forgetIfActive(holder.getOwnerUniqueId(), top);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        menu.targetPresenceChanged(event.getPlayer().getUniqueId(), false);
        Player player = event.getPlayer();
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof FriendMenuHolder) {
            menu.forgetIfActive(player.getUniqueId(), top);
        }
    }
    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        menu.targetPresenceChanged(event.getPlayer().getUniqueId(), true);
    }

}
