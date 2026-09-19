package dev.vapee.core.activity.blackjack.interaction;

import dev.vapee.core.activity.blackjack.BlackjackService;
import dev.vapee.core.activity.blackjack.presentation.BlackjackAction;
import dev.vapee.core.activity.blackjack.presentation.BlackjackInventoryService;
import dev.vapee.core.activity.blackjack.table.BlackjackBlockPosition;
import dev.vapee.core.activity.blackjack.table.BlackjackTableSeatReference;
import dev.vapee.core.activity.blackjack.table.BlackjackTableService;
import dev.vapee.core.seat.SeatPositionResolver;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;

public final class BlackjackTableListener implements Listener {

    private final BlackjackService blackjackService;
    private final BlackjackTableService tableService;
    private final BlackjackInventoryService inventoryService;
    private final SeatPositionResolver seatPositionResolver;

    public BlackjackTableListener(BlackjackService blackjackService, BlackjackTableService tableService,
                                  BlackjackInventoryService inventoryService,
                                  SeatPositionResolver seatPositionResolver) {
        this.blackjackService = Objects.requireNonNull(blackjackService, "blackjackService");
        this.tableService = Objects.requireNonNull(tableService, "tableService");
        this.inventoryService = Objects.requireNonNull(inventoryService, "inventoryService");
        this.seatPositionResolver = Objects.requireNonNull(seatPositionResolver, "seatPositionResolver");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        var blackjackAction = inventoryService.getAction(event.getItem());
        if (blackjackAction.isPresent() && inventoryService.isOwner(player.getUniqueId())) {
            event.setCancelled(true);
            dispatch(player, blackjackAction.orElseThrow());
            return;
        }

        if (action != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null
                || player.isSneaking() || !isEmpty(event.getItem())) return;
        BlackjackBlockPosition clicked = BlackjackBlockPosition.fromBlock(event.getClickedBlock());
        BlackjackTableSeatReference seat = tableService.getSeatAt(clicked).orElse(null);
        if (seat != null) {
            event.setCancelled(true);
            if (seatPositionResolver.resolve(event.getClickedBlock(), player.getYaw()).isEmpty()) return;
            blackjackService.joinTableAtSeat(player, seat.tableId(), seat.seatNumber());
            return;
        }
        tableService.getTableAt(clicked).ifPresent(tableId -> {
            event.setCancelled(true);
            blackjackService.joinTable(player, tableId);
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !inventoryService.isOwner(player.getUniqueId())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && inventoryService.isOwner(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (inventoryService.isOwner(event.getPlayer().getUniqueId())
                && inventoryService.isManagedItem(event.getItemDrop().getItemStack())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (inventoryService.isOwner(event.getPlayer().getUniqueId())
                && (inventoryService.isManagedItem(event.getMainHandItem())
                || inventoryService.isManagedItem(event.getOffHandItem()))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && inventoryService.isOwner(player.getUniqueId())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        event.getDrops().removeIf(inventoryService::isManagedItem);
    }

    private void dispatch(Player player, BlackjackAction action) {
        switch (action) {
            case DEAL -> blackjackService.startRound(player);
            case HIT -> blackjackService.hit(player);
            case STAND -> blackjackService.stand(player);
            case DOUBLE -> blackjackService.doubleDown(player);
            case LEAVE -> blackjackService.leave(player);
        }
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
