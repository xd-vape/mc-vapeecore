package dev.vapee.core.activity;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;

public final class ActivityListener implements Listener {

    private final ActivityService activityService;

    public ActivityListener(ActivityService activityService) {
        this.activityService = Objects.requireNonNull(activityService, "activityService");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handlePlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        handlePlayerChangedWorld(event.getPlayer().getUniqueId(), event.getPlayer().getWorld().getName());
    }

    void handlePlayerQuit(UUID playerId) {
        if (activityService.isParticipating(playerId)) {
            activityService.leaveCurrentSession(playerId, ActivityLeaveReason.DISCONNECT);
        }
    }

    void handlePlayerChangedWorld(UUID playerId, String newWorldName) {
        activityService.getSessionForPlayer(playerId).ifPresent(session -> {
            String venueWorldName = session.getVenue().area().worldName();
            if (!venueWorldName.equals(Objects.requireNonNull(newWorldName, "newWorldName"))) {
                activityService.leaveCurrentSession(playerId, ActivityLeaveReason.WORLD_CHANGE);
            }
        });
    }
}
