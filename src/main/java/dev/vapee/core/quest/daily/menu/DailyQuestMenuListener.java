package dev.vapee.core.quest.daily.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class DailyQuestMenuListener implements Listener {
    private final DailyQuestMenu menu;
    public DailyQuestMenuListener(DailyQuestMenu menu) { this.menu = Objects.requireNonNull(menu, "menu"); }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        var top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof DailyQuestInventoryHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || !menu.isActive(player, top, holder)
                || !menu.canUse(player)) return;
        if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= top.getSize()) return;
        switch (slot) {
            case DailyQuestMenu.CLOSE -> player.closeInventory();
            case DailyQuestMenu.REFRESH -> menu.open(player, holder.page());
            case DailyQuestMenu.PREVIOUS -> {
                if (top.getItem(slot) != null) menu.open(player, holder.page() - 1);
            }
            case DailyQuestMenu.NEXT -> {
                if (top.getItem(slot) != null) menu.open(player, holder.page() + 1);
            }
            default -> { }
        }
    }
    @EventHandler public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof DailyQuestInventoryHolder) event.setCancelled(true);
    }
    @EventHandler public void onInventoryClose(InventoryCloseEvent event) {
        var top = event.getView().getTopInventory();
        if (top.getHolder() instanceof DailyQuestInventoryHolder holder) menu.forgetIfActive(holder.owner(), top);
    }
    @EventHandler public void onPlayerQuit(PlayerQuitEvent event) { menu.forget(event.getPlayer().getUniqueId()); }
}
