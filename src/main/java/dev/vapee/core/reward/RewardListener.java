package dev.vapee.core.reward;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;

public final class RewardListener implements Listener {

    private final RewardService rewardService;

    public RewardListener(RewardService rewardService) {
        this.rewardService = Objects.requireNonNull(rewardService, "rewardService");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        flushPlayer(Objects.requireNonNull(event, "event").getPlayer().getUniqueId());
    }

    void flushPlayer(UUID uniqueId) {
        rewardService.flushPlayer(uniqueId);
    }
}
