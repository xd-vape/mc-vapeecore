package dev.vapee.core.lobby.experience;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.lobby.item.LobbyItemType;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.settings.SettingsMenu;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class LobbyItemListener implements Listener {

    private static final long VISIBILITY_TOGGLE_COOLDOWN_TICKS = 10L;

    private final JavaPlugin plugin;
    private final LobbyService lobbyService;
    private final LobbyItemService lobbyItemService;
    private final LobbyVisibilityService visibilityService;
    private final PlayerSettingsService playerSettingsService;
    private final NavigatorMenu navigatorMenu;
    private final SettingsMenu settingsMenu;
    private final MessageService messageService;
    private final Logger logger;
    private final Set<UUID> visibilityToggleCooldowns = new HashSet<>();

    public LobbyItemListener(
            JavaPlugin plugin,
            LobbyService lobbyService,
            LobbyItemService lobbyItemService,
            LobbyVisibilityService visibilityService,
            PlayerSettingsService playerSettingsService,
            NavigatorMenu navigatorMenu,
            SettingsMenu settingsMenu,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.lobbyItemService = Objects.requireNonNull(lobbyItemService, "lobbyItemService");
        this.visibilityService = Objects.requireNonNull(visibilityService, "visibilityService");
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
        this.navigatorMenu = Objects.requireNonNull(navigatorMenu, "navigatorMenu");
        this.settingsMenu = Objects.requireNonNull(settingsMenu, "settingsMenu");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.logger = plugin.getLogger();
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

        Optional<LobbyItemType> itemType = lobbyItemService.getItemType(event.getItem());
        if (itemType.isEmpty()) {
            return;
        }

        switch (itemType.get()) {
            case NAVIGATOR -> {
                navigatorMenu.open(player);
                playFeedbackSound(player);
            }
            case SETTINGS -> {
                if (playerSettingsService.getSettings(player.getUniqueId()).isEmpty()) {
                    settingsMenu.open(player);
                    return;
                }
                settingsMenu.open(player);
                playFeedbackSound(player);
            }
            case VISIBILITY -> handleVisibilityInteraction(player, event.getAction());
        }
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

    private void toggleVisibility(Player player) {
        UUID uniqueId = player.getUniqueId();
        if (!beginVisibilityToggleCooldown(player, uniqueId)) {
            return;
        }

        Optional<Boolean> currentValue = playerSettingsService.areLobbyPlayersVisible(uniqueId);
        if (currentValue.isEmpty()) {
            messageService.send(player, "<red>Your player profile is not available.</red>");
            return;
        }

        boolean saved;
        try {
            saved = playerSettingsService.setLobbyPlayersVisible(uniqueId, !currentValue.get());
        } catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not save lobby visibility setting for " + uniqueId + ".", exception);
            messageService.send(player, "<red>Your visibility setting could not be saved. Please try again.</red>");
            return;
        }
        if (!saved) {
            messageService.send(player, "<red>Your player profile is not available.</red>");
            return;
        }

        visibilityService.applyViewerPreference(player);
        lobbyItemService.refreshVisibilityItem(player);
        playFeedbackSound(player);
    }

    private void handleVisibilityInteraction(Player player, Action action) {
        if (action != Action.RIGHT_CLICK_BLOCK) {
            toggleVisibility(player);
            return;
        }

        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || !lobbyService.isLobbyWorld(player.getWorld())) {
                return;
            }
            toggleVisibility(player);
        });
    }

    private boolean beginVisibilityToggleCooldown(Player player, UUID uniqueId) {
        if (!visibilityToggleCooldowns.add(uniqueId)) {
            return false;
        }

        try {
            player.setCooldown(Material.LIME_DYE, (int) VISIBILITY_TOGGLE_COOLDOWN_TICKS);
            player.setCooldown(Material.GRAY_DYE, (int) VISIBILITY_TOGGLE_COOLDOWN_TICKS);
            plugin.getServer().getScheduler().runTaskLater(
                    plugin,
                    () -> visibilityToggleCooldowns.remove(uniqueId),
                    VISIBILITY_TOGGLE_COOLDOWN_TICKS
            );
            return true;
        } catch (RuntimeException exception) {
            visibilityToggleCooldowns.remove(uniqueId);
            throw exception;
        }
    }

    private void playFeedbackSound(Player player) {
        if (!playerSettingsService.areSoundsEnabled(player.getUniqueId()).orElse(false)) {
            return;
        }

        try {
            player.playSound(
                    player.getLocation(),
                    Sound.UI_BUTTON_CLICK,
                    SoundCategory.MASTER,
                    0.5F,
                    1.0F
            );
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Could not play lobby UI feedback sound for " + player.getUniqueId() + ".",
                    exception
            );
        }
    }
}
