package dev.vapee.core.reward;

import java.util.UUID;

public record RewardGrant(
        UUID playerId,
        long coins,
        RewardSource source,
        String reason
) {

    public RewardGrant {
        if (playerId == null) {
            throw new IllegalArgumentException("playerId must not be null");
        }
        if (coins <= 0L) {
            throw new IllegalArgumentException("Reward coins must be positive");
        }
        if (source == null) {
            throw new IllegalArgumentException("source must not be null");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        reason = reason.trim();
    }
}
