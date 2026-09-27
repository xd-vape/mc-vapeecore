package dev.vapee.core.quest.daily;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;
import java.util.Objects;

public final class DailyQuestListener implements Listener {

    private final DailyQuestService service;

    public DailyQuestListener(DailyQuestService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // PlayerListener loads CorePlayer at NORMAL; an unavailable player is safely skipped.
        service.syncPlayer(event.getPlayer().getUniqueId(), Instant.now());
    }
}
