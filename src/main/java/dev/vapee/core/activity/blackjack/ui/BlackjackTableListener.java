package dev.vapee.core.activity.blackjack.ui;

import dev.vapee.core.activity.blackjack.BlackjackService;
import dev.vapee.core.activity.blackjack.table.BlackjackBlockPosition;
import dev.vapee.core.activity.blackjack.table.BlackjackTableService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class BlackjackTableListener implements Listener {

    private final JavaPlugin plugin;
    private final BlackjackService blackjackService;
    private final BlackjackTableService tableService;
    private final BlackjackTableMenu tableMenu;

    public BlackjackTableListener(
            JavaPlugin plugin,
            BlackjackService blackjackService,
            BlackjackTableService tableService,
            BlackjackTableMenu tableMenu
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.blackjackService = Objects.requireNonNull(blackjackService, "blackjackService");
        this.tableService = Objects.requireNonNull(tableService, "tableService");
        this.tableMenu = Objects.requireNonNull(tableMenu, "tableMenu");
    }

    @EventHandler
    public void onTableInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null) {
            return;
        }
        var tableId = tableService.getTableAt(BlackjackBlockPosition.fromBlock(event.getClickedBlock()));
        if (tableId.isEmpty()) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            blackjackService.joinTable(player, tableId.get())
                    .ifPresent(session -> tableMenu.open(player, session));
        });
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
