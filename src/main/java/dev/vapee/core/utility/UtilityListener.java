package dev.vapee.core.utility;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class UtilityListener implements Listener {

    private final JavaPlugin plugin;
    private final UtilityService utilityService;
    private boolean active = true;

    public UtilityListener(JavaPlugin plugin, UtilityService utilityService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.utilityService = Objects.requireNonNull(utilityService, "utilityService");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        utilityService.forgetPlayer(player.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (active && plugin.isEnabled() && player.isOnline()) {
                utilityService.normalizeTransientState(player);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        utilityService.cleanupPlayer(event.getPlayer());
    }

    public void disable() {
        active = false;
    }
}
