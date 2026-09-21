package dev.vapee.core.quest;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;

public final class QuestListener implements Listener {

    private final QuestService questService;

    public QuestListener(QuestService questService) {
        this.questService = Objects.requireNonNull(questService, "questService");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        flushPlayer(Objects.requireNonNull(event, "event").getPlayer().getUniqueId());
    }

    void flushPlayer(UUID playerId) {
        questService.flushPlayer(playerId);
    }
}
