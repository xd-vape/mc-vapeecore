package dev.vapee.core.activity.blackjack.ui;

import dev.vapee.core.activity.blackjack.BlackjackService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

public final class BlackjackModeListener implements Listener {

    private final BlackjackService blackjackService;
    private final BlackjackTableMenu tableMenu;

    public BlackjackModeListener(BlackjackService blackjackService, BlackjackTableMenu tableMenu) {
        this.blackjackService = Objects.requireNonNull(blackjackService, "blackjackService");
        this.tableMenu = Objects.requireNonNull(tableMenu, "tableMenu");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        BlackjackModeInventoryHolder holder = getHolder(topInventory);
        if (holder == null) {
            return;
        }
        event.setCancelled(true);
        HumanEntity clickingEntity = event.getWhoClicked();
        if (!(clickingEntity instanceof Player player)
                || !holder.getOwnerUniqueId().equals(player.getUniqueId())
                || event.getClickedInventory() != topInventory
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) {
            return;
        }

        switch (event.getRawSlot()) {
            case BlackjackModeMenu.SOLO_SLOT -> blackjackService.launchSolo(player)
                    .ifPresent(session -> tableMenu.open(player, session));
            case BlackjackModeMenu.PUBLIC_SLOT -> blackjackService.launchPublic(player)
                    .ifPresent(session -> tableMenu.open(player, session));
            case BlackjackModeMenu.CLOSE_SLOT -> player.closeInventory();
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        BlackjackModeInventoryHolder holder = getHolder(topInventory);
        if (holder == null) {
            return;
        }
        if (!holder.getOwnerUniqueId().equals(event.getWhoClicked().getUniqueId())
                || event.getRawSlots().stream().anyMatch(slot -> slot < topInventory.getSize())) {
            event.setCancelled(true);
        }
    }

    private BlackjackModeInventoryHolder getHolder(Inventory inventory) {
        if (!(inventory.getHolder() instanceof BlackjackModeInventoryHolder holder)) {
            return null;
        }
        return holder.getInventory() == inventory ? holder : null;
    }
}
