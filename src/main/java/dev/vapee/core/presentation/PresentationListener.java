package dev.vapee.core.presentation;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class PresentationListener implements Listener {

    private final JavaPlugin plugin;
    private final PresentationService presentationService;

    public PresentationListener(JavaPlugin plugin, PresentationService presentationService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.presentationService = Objects.requireNonNull(presentationService, "presentationService");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && player.isOnline()) {
                presentationService.updatePlayer(player);
            }
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        presentationService.removePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        presentationService.updatePlayer(event.getPlayer());
    }
}
