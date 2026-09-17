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
                || !holder.getOwnerUniqueId().equals(player.getUniqueId())) {
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
            player.closeInventory();
            return;
        }
        holder.getWarpId(slot).ifPresent(warpId -> teleport(player, warpId));
    }

    private void teleport(Player player, String warpId) {
        WarpResult result = warpService.teleport(player, warpId);
        switch (result) {
            case SUCCESS -> player.closeInventory();
            case NOT_FOUND -> {
                messageService.send(player, "<red>That warp no longer exists.</red>");
                navigatorMenu.open(player, 0);
            }
            case WORLD_NOT_LOADED -> messageService.send(player, "<red>The warp world is not loaded.</red>");
            case TELEPORT_FAILED -> messageService.send(player, "<red>The teleport was cancelled or failed.</red>");
            case PLAYER_OFFLINE, INVALID_ID, INVALID_NAME, INVALID_ICON ->
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

        if (!holder.getOwnerUniqueId().equals(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        int topSize = topInventory.getSize();
        if (event.getRawSlots().stream().anyMatch(slot -> slot < topSize)) {
            event.setCancelled(true);
        }
    }

    private NavigatorInventoryHolder getNavigatorHolder(Inventory inventory) {
        if (!(inventory.getHolder() instanceof NavigatorInventoryHolder holder)) {
            return null;
        }
        return holder.getInventory() == inventory ? holder : null;
    }
}
