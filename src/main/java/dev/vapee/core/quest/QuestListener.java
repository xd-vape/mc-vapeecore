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

    public QuestListener(QuestService questService, QuestPlaytimeProducer playtime, Logger logger) {
        this.questService = Objects.requireNonNull(questService, "questService");
        this.playtime = Objects.requireNonNull(playtime, "playtime");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            playtime.seed(event.getPlayer());
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Quest playtime seed failed for " + event.getPlayer().getUniqueId(), exception);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        var player = Objects.requireNonNull(event, "event").getPlayer();
        UUID id = player.getUniqueId();
        try {
            playtime.sample(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Final quest playtime sample failed for " + id, exception);
        } finally {
            try { flushPlayer(id); } finally { playtime.forget(id); }
        }
    }

    void flushPlayer(UUID playerId) {
        questService.flushPlayer(playerId);
    }
}
