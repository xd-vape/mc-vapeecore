package dev.vapee.core.lobby.experience.glide;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.player.*;
import java.util.Objects;

public final class LobbyGlideListener implements Listener {
    private final LobbyGlideService glide;
    public LobbyGlideListener(LobbyGlideService glide) { this.glide = Objects.requireNonNull(glide); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (glide.onFlightToggle(event.getPlayer(), event.isFlying())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() && event.getEntity() instanceof Player player && glide.keepGliding(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!glide.isOwned(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) return;
        glide.moved(event.getPlayer(), event.getFrom().getY() != event.getTo().getY());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { glide.onPlayerAvailable(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) { glide.release(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterQuit(PlayerQuitEvent event) { glide.release(event.getPlayer()); glide.equipment().cleanupOrphans(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) { glide.release(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterDeath(PlayerDeathEvent event) { glide.release(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) { glide.onPlayerAvailable(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void beforeWorldChange(PlayerChangedWorldEvent event) { glide.release(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterWorldChange(PlayerChangedWorldEvent event) { glide.deferEquipmentCleanup(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { glide.release(event.getPlayer()); glide.deferEquipmentCleanup(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) { glide.release(event.getPlayer()); glide.deferEquipmentCleanup(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentClick(InventoryClickEvent event) {
        if (managed(event.getCurrentItem()) || managed(event.getCursor())) { deny(event, event.getWhoClicked()); return; }
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int hotbar = event.getHotbarButton();
        if ((hotbar >= 0 && hotbar < 9 && managed(player.getInventory().getItem(hotbar)))
                || (event.getClick() == ClickType.SWAP_OFFHAND && managed(player.getInventory().getItemInOffHand()))) deny(event, player);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentDrag(InventoryDragEvent event) {
        if (managed(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(this::managed)) deny(event, event.getWhoClicked());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentDrop(PlayerDropItemEvent event) {
        if (managed(event.getItemDrop().getItemStack())) deny(event, event.getPlayer());
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentSwap(PlayerSwapHandItemsEvent event) {
        if (managed(event.getMainHandItem()) || managed(event.getOffHandItem())) deny(event, event.getPlayer());
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEquipmentDamage(PlayerItemDamageEvent event) { if (managed(event.getItem())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentUse(PlayerInteractEvent event) { if (managed(event.getItem())) deny(event, event.getPlayer()); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentDeathDrops(PlayerDeathEvent event) {
        event.getDrops().removeIf(this::managed);
        event.getItemsToKeep().removeIf(this::managed);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentPickup(EntityPickupItemEvent event) {
        if (managed(event.getItem().getItemStack())) { event.setCancelled(true); event.getItem().remove(); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentMove(InventoryMoveItemEvent event) { if (managed(event.getItem())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentHopperPickup(InventoryPickupItemEvent event) {
        if (managed(event.getItem().getItemStack())) { event.setCancelled(true); event.getItem().remove(); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEquipmentArmorStand(PlayerArmorStandManipulateEvent event) {
        if (managed(event.getPlayerItem()) || managed(event.getArmorStandItem())) deny(event, event.getPlayer());
    }
    private void deny(Cancellable event, org.bukkit.entity.HumanEntity actor) {
        event.setCancelled(true);
        if (actor instanceof Player player) glide.deferEquipmentCleanup(player.getUniqueId());
    }
    private boolean managed(ItemStack item) { return glide.equipment().isManaged(item); }
}
