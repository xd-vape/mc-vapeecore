package dev.vapee.core.quest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class QuestListener implements Listener {

    private final QuestService questService;
    private final QuestPlaytimeProducer playtime;
    private final Logger logger;
    private final java.util.function.BooleanSupplier authorized;

    public QuestListener(QuestService questService, QuestPlaytimeProducer playtime, Logger logger) {
        this(questService, playtime, logger, () -> true);
    }

    QuestListener(QuestService questService, QuestPlaytimeProducer playtime, Logger logger,
                  java.util.function.BooleanSupplier authorized) {
        this.questService = Objects.requireNonNull(questService, "questService");
        this.playtime = Objects.requireNonNull(playtime, "playtime");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.authorized = Objects.requireNonNull(authorized, "authorized");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!authorized.getAsBoolean()) return;
        try {
            playtime.seed(event.getPlayer());
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Quest playtime seed failed for " + event.getPlayer().getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (!authorized.getAsBoolean()) return;
        var player = Objects.requireNonNull(event, "event").getPlayer();
        UUID id = player.getUniqueId();
        try {
            playtime.sample(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Final quest playtime sample failed for " + id, exception);
        } finally {
            try { flushPlayer(id); } finally { if (authorized.getAsBoolean()) playtime.forget(id); }
        }
    }

    void flushPlayer(UUID playerId) {
        if (authorized.getAsBoolean()) questService.flushPlayer(playerId);
    }
}
