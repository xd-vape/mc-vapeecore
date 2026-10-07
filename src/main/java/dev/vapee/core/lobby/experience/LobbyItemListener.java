package dev.vapee.core.lobby.experience;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.item.LobbyItemRegistry;
import dev.vapee.core.lobby.item.LobbyItemService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LobbyItemListener implements Listener {
    private final LobbyService lobbyService;
    private final LobbyItemService lobbyItemService;
    private final LobbyItemRegistry registry;
    private final Predicate<Player> eligible;
    private final Consumer<Player> feedback;
    private final Logger logger;

    public LobbyItemListener(LobbyService lobbyService, LobbyItemService lobbyItemService,
                             LobbyItemRegistry registry, Predicate<Player> eligible,
                             Consumer<Player> feedback, Logger logger) {
        this.lobbyService = Objects.requireNonNull(lobbyService);
        this.lobbyItemService = Objects.requireNonNull(lobbyItemService);
        this.registry = Objects.requireNonNull(registry);
        this.eligible = Objects.requireNonNull(eligible);
        this.feedback = Objects.requireNonNull(feedback);
        this.logger = Objects.requireNonNull(logger);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!lobbyItemService.isManagedItem(event.getItem())) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!lobbyService.isLobbyWorld(player.getWorld())) {
            lobbyItemService.removeManagedItems(player);
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND
                || (event.getAction() != Action.RIGHT_CLICK_AIR
                && event.getAction() != Action.RIGHT_CLICK_BLOCK)) {
            return;
        }

        if (!eligible.test(player)) return;
        lobbyItemService.getItemId(event.getItem()).filter(lobbyItemService::isEnabled).ifPresent(id -> {
            try {
                if (registry.handleClick(id, player, event.getAction())) feedback.accept(player);
            } catch (RuntimeException exception) {
                logger.log(Level.WARNING, "Could not use lobby item '" + id + "' for " + player.getUniqueId(), exception);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (lobbyItemService.isManagedItem(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (lobbyItemService.isManagedItem(event.getMainHandItem())
                || lobbyItemService.isManagedItem(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (involvesManagedItem(event)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (lobbyItemService.isManagedItem(event.getOldCursor())
                || event.getNewItems().values().stream().anyMatch(lobbyItemService::isManagedItem)) {
            event.setCancelled(true);
        }
    }

    private boolean involvesManagedItem(InventoryClickEvent event) {
        if (lobbyItemService.isManagedItem(event.getCurrentItem())
                || lobbyItemService.isManagedItem(event.getCursor())) {
            return true;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return false;
        }

        int hotbarButton = event.getHotbarButton();
        if (hotbarButton >= 0
                && lobbyItemService.isManagedItem(player.getInventory().getItem(hotbarButton))) {
            return true;
        }
        return event.getClick() == ClickType.SWAP_OFFHAND
                && lobbyItemService.isManagedItem(player.getInventory().getItemInOffHand());
    }

}
