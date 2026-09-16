package dev.vapee.core.lobby.experience.navigator.activity;

import dev.vapee.core.activity.navigation.ActivityCatalog;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Objects;

public final class ActivitiesListener implements Listener {

    private final ActivityCatalog activityCatalog;
    private final ActivitiesMenu activitiesMenu;

    public ActivitiesListener(ActivityCatalog activityCatalog, ActivitiesMenu activitiesMenu) {
        this.activityCatalog = Objects.requireNonNull(activityCatalog, "activityCatalog");
        this.activitiesMenu = Objects.requireNonNull(activitiesMenu, "activitiesMenu");
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        ActivitiesInventoryHolder holder = getHolder(topInventory);
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

        int slot = event.getRawSlot();
        if (slot == ActivitiesMenu.BACK_SLOT) {
            activitiesMenu.openNavigator(player);
            return;
        }
        if (slot == ActivitiesMenu.CLOSE_SLOT) {
            player.closeInventory();
            return;
        }
        holder.getActivityKey(slot)
                .flatMap(activityCatalog::get)
                .ifPresent(entryPoint -> entryPoint.open(player));
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        ActivitiesInventoryHolder holder = getHolder(topInventory);
        if (holder == null) {
            return;
        }
        if (!holder.getOwnerUniqueId().equals(event.getWhoClicked().getUniqueId())
                || event.getRawSlots().stream().anyMatch(slot -> slot < topInventory.getSize())) {
            event.setCancelled(true);
        }
    }

    private ActivitiesInventoryHolder getHolder(Inventory inventory) {
        if (!(inventory.getHolder() instanceof ActivitiesInventoryHolder holder)) {
            return null;
        }
        return holder.getInventory() == inventory ? holder : null;
    }
}
