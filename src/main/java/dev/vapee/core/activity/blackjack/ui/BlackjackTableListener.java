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

public final class BlackjackTableListener implements Listener {

    private final BlackjackService blackjackService;

    public BlackjackTableListener(BlackjackService blackjackService) {
        this.blackjackService = Objects.requireNonNull(blackjackService, "blackjackService");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        BlackjackTableInventoryHolder holder = getHolder(topInventory);
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
        if (blackjackService.getSessionForPlayer(player)
                .filter(session -> session.getSessionId().equals(holder.getSessionId()))
                .isEmpty()) {
            player.closeInventory();
            return;
        }

        switch (event.getRawSlot()) {
            case BlackjackTableMenu.DEAL_SLOT -> blackjackService.startRound(player);
            case BlackjackTableMenu.HIT_SLOT -> blackjackService.hit(player);
            case BlackjackTableMenu.STAND_SLOT -> blackjackService.stand(player);
            case BlackjackTableMenu.LEAVE_SLOT -> {
                blackjackService.leave(player);
                player.closeInventory();
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        BlackjackTableInventoryHolder holder = getHolder(topInventory);
        if (holder == null) {
            return;
        }
        if (!holder.getOwnerUniqueId().equals(event.getWhoClicked().getUniqueId())
                || event.getRawSlots().stream().anyMatch(slot -> slot < topInventory.getSize())) {
            event.setCancelled(true);
        }
    }

    private BlackjackTableInventoryHolder getHolder(Inventory inventory) {
        if (!(inventory.getHolder() instanceof BlackjackTableInventoryHolder holder)) {
            return null;
        }
        return holder.getInventory() == inventory ? holder : null;
    }
}
