package dev.vapee.core.lobby;

import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.player.PlayerService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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

public final class LobbyListener implements Listener {

    private final JavaPlugin plugin;
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
        this.plugin = Objects.requireNonNull(plugin, "plugin");
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
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()
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

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (lobbyConfig.isBlockBreakProtectionEnabled()
                && lobbyService.isLobbyWorld(event.getBlock().getWorld())
                && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (lobbyConfig.isBlockPlaceProtectionEnabled()
                && lobbyService.isLobbyWorld(event.getBlock().getWorld())
                && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemDrop(PlayerDropItemEvent event) {
        if (lobbyConfig.isItemDropProtectionEnabled()
                && lobbyService.isLobbyWorld(event.getPlayer().getWorld())
                && !canBuild(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && lobbyConfig.isItemPickupProtectionEnabled()
                && lobbyService.isLobbyWorld(player.getWorld())
                && !canBuild(player)) {
            event.setCancelled(true);
        }
    }

    private boolean canBuild(Player player) {
        return lobbyPlayerStateService.isBuildMode(player.getUniqueId());
    }

    private void scheduleSynchronization(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()
                    || !player.isOnline()
                    || !playerService.isLoaded(player.getUniqueId())
                    || !lobbyService.isLobbyWorld(player.getWorld())) {
                return;
            }
            lobbyPlayerStateService.synchronize(player);
        });
    }
}
