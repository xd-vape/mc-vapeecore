package dev.vapee.core.lobby;

import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.player.PlayerService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public final class LobbyListener implements Listener {

    private final Consumer<Runnable> nextTick;
    private final BooleanSupplier pluginEnabled;
    private final Map<UUID, Player> pendingBuildCleanup = new HashMap<>();
    private boolean active = true;
    private final LobbyConfig lobbyConfig;
    private final LobbyService lobbyService;
    private final PlayerService playerService;
    private final LobbyPlayerStateService lobbyPlayerStateService;

    public LobbyListener(
            JavaPlugin plugin,
            LobbyConfig lobbyConfig,
            LobbyService lobbyService,
            PlayerService playerService,
            LobbyPlayerStateService lobbyPlayerStateService
    ) {
        this(lobbyConfig, lobbyService, playerService, lobbyPlayerStateService,
                task -> Objects.requireNonNull(plugin, "plugin").getServer().getScheduler().runTask(plugin, task),
                plugin::isEnabled);
    }

    LobbyListener(LobbyConfig lobbyConfig, LobbyService lobbyService, PlayerService playerService,
                  LobbyPlayerStateService lobbyPlayerStateService, Consumer<Runnable> nextTick,
                  BooleanSupplier pluginEnabled) {
        this.nextTick = Objects.requireNonNull(nextTick, "nextTick");
        this.pluginEnabled = Objects.requireNonNull(pluginEnabled, "pluginEnabled");
        this.lobbyConfig = Objects.requireNonNull(lobbyConfig, "lobbyConfig");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.lobbyPlayerStateService = Objects.requireNonNull(
                lobbyPlayerStateService,
                "lobbyPlayerStateService"
        );
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        lobbyPlayerStateService.handleQuit(player.getUniqueId());
        nextTick.accept(() -> {
            if (!active || !pluginEnabled.getAsBoolean()
                    || !player.isOnline()
                    || !playerService.isLoaded(player.getUniqueId())) {
                return;
            }
            if (lobbyConfig.isTeleportOnJoinEnabled() && lobbyService.hasSpawn()) {
                lobbyService.teleportToSpawn(player);
            }
            if (lobbyService.isLobbyWorld(player.getWorld())) {
                lobbyPlayerStateService.synchronizeJoin(player);
            }
        });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (lobbyConfig.isTeleportOnRespawnEnabled()
                && lobbyService.isLobbyWorld(event.getPlayer().getWorld())) {
            lobbyService.getSpawnLocation().ifPresent(event::setRespawnLocation);
        }
        scheduleSynchronization(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean wasInLobby = lobbyService.isLobbyWorld(event.getFrom());
        boolean isInLobby = lobbyService.isLobbyWorld(player.getWorld());
        if (wasInLobby && !isInLobby) {
            lobbyPlayerStateService.leaveLobby(player);
            return;
        }
        if (!wasInLobby && isInLobby && playerService.isLoaded(player.getUniqueId())) {
            lobbyPlayerStateService.synchronize(player);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        pendingBuildCleanup.remove(event.getPlayer().getUniqueId());
        lobbyPlayerStateService.handleQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !lobbyService.isLobbyWorld(player.getWorld())) {
            return;
        }

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            if (lobbyConfig.isVoidRescueEnabled() && lobbyService.teleportToSpawn(player)) {
                event.setCancelled(true);
            }
            return;
        }

        if (lobbyConfig.isDamageProtectionEnabled()) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !lobbyConfig.isHungerProtectionEnabled()
                || !lobbyService.isLobbyWorld(player.getWorld())) {
            return;
        }

        event.setCancelled(true);
        player.setFoodLevel(20);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (denyRevokedBuild(event.getPlayer()) || (lobbyConfig.isBlockBreakProtectionEnabled()
                && lobbyService.isLobbyWorld(event.getBlock().getWorld())
                && !canBuild(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (denyRevokedBuild(event.getPlayer()) || (lobbyConfig.isBlockPlaceProtectionEnabled()
                && lobbyService.isLobbyWorld(event.getBlock().getWorld())
                && !canBuild(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (denyRevokedBuild(event.getPlayer()) || (lobbyConfig.isItemDropProtectionEnabled()
                && lobbyService.isLobbyWorld(event.getPlayer().getWorld())
                && !canBuild(event.getPlayer()))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && (denyRevokedBuild(player) || (lobbyConfig.isItemPickupProtectionEnabled()
                && lobbyService.isLobbyWorld(player.getWorld())
                && !canBuild(player)))) {
            event.setCancelled(true);
        }
    }

    private boolean canBuild(Player player) {
        return lobbyPlayerStateService.hasBuildAuthority(player);
    }

    // Includes InventoryCreativeEvent; it shares InventoryClickEvent's handler list.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && denyRevokedBuild(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && denyRevokedBuild(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent event) {
        if (denyRevokedBuild(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (denyRevokedBuild(event.getPlayer())) event.setCancelled(true);
    }

    // These subclasses have their own handler lists, unlike InventoryCreativeEvent.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (denyRevokedBuild(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        if (denyRevokedBuild(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (denyRevokedBuild(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        // Effective LP contexts may change within one world as well as between worlds.
        denyRevokedBuild(event.getPlayer());
    }

    private boolean denyRevokedBuild(Player player) {
        if (!active || !lobbyPlayerStateService.isBuildMode(player.getUniqueId()) || canBuild(player)) return false;
        if (pendingBuildCleanup.putIfAbsent(player.getUniqueId(), player) == null) {
            nextTick.accept(() -> {
                if (!pendingBuildCleanup.remove(player.getUniqueId(), player) || !active
                        || !pluginEnabled.getAsBoolean() || !player.isOnline()) return;
                lobbyPlayerStateService.reconcileBuildAuthority(player);
            });
        }
        return true;
    }

    public void disable() {
        active = false;
        pendingBuildCleanup.clear();
    }

    private void scheduleSynchronization(Player player) {
        nextTick.accept(() -> {
            if (!active || !pluginEnabled.getAsBoolean()
                    || !player.isOnline()
                    || !playerService.isLoaded(player.getUniqueId())
                    || !lobbyService.isLobbyWorld(player.getWorld())) {
                return;
            }
            lobbyPlayerStateService.synchronize(player);
        });
    }
}
