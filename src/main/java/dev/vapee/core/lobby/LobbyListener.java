package dev.vapee.core.lobby;

import dev.vapee.core.lobby.config.LobbyConfig;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class LobbyListener implements Listener {

    private static final String BUILD_PERMISSION = "vapeecore.lobby.build";

    private final JavaPlugin plugin;
    private final LobbyConfig lobbyConfig;
    private final LobbyService lobbyService;

    public LobbyListener(JavaPlugin plugin, LobbyConfig lobbyConfig, LobbyService lobbyService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyConfig = Objects.requireNonNull(lobbyConfig, "lobbyConfig");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!lobbyConfig.isTeleportOnJoinEnabled() || !lobbyService.hasSpawn()) {
            return;
        }

        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!plugin.isEnabled()
                    || !player.isOnline()
                    || !lobbyConfig.isTeleportOnJoinEnabled()
                    || !lobbyService.hasSpawn()) {
                return;
            }
            lobbyService.teleportToSpawn(player);
        });
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (!lobbyConfig.isTeleportOnRespawnEnabled()
                || !lobbyService.isLobbyWorld(event.getPlayer().getWorld())) {
            return;
        }

        lobbyService.getSpawnLocation().ifPresent(event::setRespawnLocation);
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
        return player.hasPermission(BUILD_PERMISSION);
    }
}
