package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.lobby.warp.WarpResult;
import dev.vapee.core.lobby.warp.WarpService;
import dev.vapee.core.message.MessageService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

public final class NavigatorListener implements Listener {

    private final WarpService warpService;
    private final MessageService messageService;
    private final NavigatorMenu navigatorMenu;

    public NavigatorListener(
            WarpService warpService,
            MessageService messageService,
            NavigatorMenu navigatorMenu
    ) {
        this.warpService = Objects.requireNonNull(warpService, "warpService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.navigatorMenu = Objects.requireNonNull(navigatorMenu, "navigatorMenu");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        NavigatorInventoryHolder holder = getNavigatorHolder(topInventory);
        if (holder == null) {
            return;
        }

        event.setCancelled(true);
        HumanEntity clickingEntity = event.getWhoClicked();
        if (!(clickingEntity instanceof Player player)
                || !navigatorMenu.isActive(player, topInventory, holder)) {
            return;
        }
        if (!navigatorMenu.canAccess(player)) {
            navigatorMenu.close(player);
            return;
        }
        if (event.getClickedInventory() != topInventory) {
            return;
        }
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) {
            return;
        }

        int slot = event.getRawSlot();
        if (slot == NavigatorMenu.PREVIOUS_SLOT && holder.getPage() > 0) {
            navigatorMenu.open(player, holder.getPage() - 1);
            return;
        }
        if (slot == NavigatorMenu.NEXT_SLOT) {
            navigatorMenu.open(player, holder.getPage() + 1);
            return;
        }
        if (slot == NavigatorMenu.CLOSE_SLOT) {
            navigatorMenu.close(player);
            return;
        }
        holder.getWarpId(slot).ifPresent(warpId -> teleport(player, warpId, holder.getPage()));
    }

    private void teleport(Player player, String warpId, int page) {
        WarpResult result = warpService.teleportFromNavigator(player, warpId);
        switch (result) {
            case SUCCESS -> navigatorMenu.close(player);
            case NOT_FOUND, NOT_NAVIGABLE -> {
                messageService.send(player, "<red>That destination is no longer available.</red>");
                navigatorMenu.open(player, page);
            }
            case WORLD_NOT_LOADED -> messageService.send(player, "<red>The warp world is not loaded.</red>");
            case TELEPORT_FAILED -> messageService.send(player, "<red>The teleport was cancelled or failed.</red>");
            case PLAYER_OFFLINE, INVALID_ID, INVALID_NAME, INVALID_ICON, INVALID_ORDER ->
                    messageService.send(player, "<red>The warp is currently unavailable.</red>");
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        NavigatorInventoryHolder holder = getNavigatorHolder(topInventory);
        if (holder == null) {
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)
                || !navigatorMenu.isActive(player, topInventory, holder)) {
            event.setCancelled(true);
            return;
        }
        if (!navigatorMenu.canAccess(player)) {
            event.setCancelled(true);
            navigatorMenu.close(player);
            return;
        }

        int topSize = topInventory.getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot >= 0 && slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private NavigatorInventoryHolder getNavigatorHolder(Inventory inventory) {
        if (!(inventory.getHolder() instanceof NavigatorInventoryHolder holder)) {
            return null;
        }
        return holder;
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory.getHolder() instanceof NavigatorInventoryHolder holder
                && holder.getOwnerUniqueId().equals(event.getPlayer().getUniqueId())) {
            navigatorMenu.forgetIfActive(holder.getOwnerUniqueId(), inventory);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        navigatorMenu.forget(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        if (!navigatorMenu.isLobbyWorld(event.getPlayer().getWorld())) {
            navigatorMenu.close(event.getPlayer());
        }
    }
}
