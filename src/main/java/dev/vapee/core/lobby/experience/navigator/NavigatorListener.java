package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.lobby.LobbyService;
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

    private final LobbyService lobbyService;
    private final MessageService messageService;

    public NavigatorListener(LobbyService lobbyService, MessageService messageService) {
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
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

        switch (event.getRawSlot()) {
            case NavigatorMenu.LOBBY_SLOT -> {
                if (!lobbyService.teleportToSpawn(player)) {
                    messageService.send(player, "<red>The lobby spawn is not available.</red>");
                }
                player.closeInventory();
            }
            case NavigatorMenu.ACTIVITIES_SLOT ->
                    messageService.send(player, "<yellow>Activities are not available yet.</yellow>");
            case NavigatorMenu.MINIGAMES_SLOT ->
                    messageService.send(player, "<yellow>Minigames are not available yet.</yellow>");
            case NavigatorMenu.CLOSE_SLOT -> player.closeInventory();
            default -> {
            }
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
