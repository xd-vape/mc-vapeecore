package dev.vapee.core.lobby.experience.player;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerMenuListener implements Listener {
    private final PlayerMenu menu;
    public PlayerMenuListener(PlayerMenu menu) { this.menu = java.util.Objects.requireNonNull(menu); }
    @EventHandler public void onClick(InventoryClickEvent event) {
        var top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof PlayerMenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top
                || event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        menu.actionAt(player, top, holder, event.getRawSlot()).ifPresent(action -> menu.use(player, action));
    }
    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof PlayerMenuHolder) event.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent event) {
        var top = event.getView().getTopInventory();
        if (top.getHolder() instanceof PlayerMenuHolder holder) menu.forget(holder.owner(), top);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        var top = event.getPlayer().getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof PlayerMenuHolder holder) menu.forget(holder.owner(), top);
    }
    @EventHandler public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        var player = event.getPlayer(); var top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof PlayerMenuHolder holder && menu.isActive(player, top, holder)) {
            menu.forget(holder.owner(), top); player.closeInventory();
        }
    }
}
