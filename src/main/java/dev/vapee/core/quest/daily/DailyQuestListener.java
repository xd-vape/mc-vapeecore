package dev.vapee.core.quest.daily;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.Instant;
import java.util.Objects;

public final class DailyQuestListener implements Listener {

    private final DailyQuestService service;
    private final java.util.function.BooleanSupplier authorized;

    public DailyQuestListener(DailyQuestService service) {
        this(service, () -> true);
    }

    DailyQuestListener(DailyQuestService service, java.util.function.BooleanSupplier authorized) {
        this.service = Objects.requireNonNull(service, "service");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // PlayerListener loads CorePlayer at NORMAL; an unavailable player is safely skipped.
        if (!authorized.getAsBoolean()) return;
        var id = event.getPlayer().getUniqueId();
        if (authorized.getAsBoolean()) service.syncPlayer(id, Instant.now());
    }
}
