package dev.vapee.core.social;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class SocialListener implements Listener {

    private final SocialService socialService;

    public SocialListener(SocialService socialService) {
        this.socialService = Objects.requireNonNull(socialService, "socialService");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        socialService.activatePlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        socialService.deactivatePlayer(event.getPlayer().getUniqueId());
    }
}
