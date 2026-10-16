package dev.vapee.core.lobby.experience.glide;

import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import java.util.Objects;

public final class LobbyGlideListener implements Listener {
    private final LobbyGlideService glide;
    public LobbyGlideListener(LobbyGlideService glide) { this.glide = Objects.requireNonNull(glide); }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (glide.onFlightToggle(event.getPlayer(), event.isFlying())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isGliding() && event.getEntity() instanceof Player player && glide.keepGliding(player)) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!glide.isOwned(event.getPlayer().getUniqueId()) || event.getTo() == null) return;
        if (event.getFrom().getX() == event.getTo().getX() && event.getFrom().getY() == event.getTo().getY()
                && event.getFrom().getZ() == event.getTo().getZ()) return;
        glide.moved(event.getPlayer(), event.getFrom().getY() != event.getTo().getY());
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { glide.deferReconcile(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) { glide.release(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterQuit(PlayerQuitEvent event) { glide.release(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) { glide.release(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterDeath(PlayerDeathEvent event) { glide.release(event.getEntity()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) { glide.deferReconcile(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void beforeWorldChange(PlayerChangedWorldEvent event) { glide.release(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void afterWorldChange(PlayerChangedWorldEvent event) { glide.deferReconcile(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { glide.release(event.getPlayer()); glide.deferReconcile(event.getPlayer().getUniqueId()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) { glide.release(event.getPlayer()); glide.deferReconcile(event.getPlayer().getUniqueId()); }
}
