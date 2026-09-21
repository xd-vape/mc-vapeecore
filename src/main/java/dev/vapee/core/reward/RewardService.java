package dev.vapee.core.reward;

import dev.vapee.core.economy.EconomyResult;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.player.PlayerService;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main-thread-owned coin reward domain service. Callers running asynchronously must
 * return to the server thread before granting or flushing rewards.
 */
public final class RewardService {

    private final EconomyService economyService;
    private final PlayerService playerService;
    private final Logger logger;
    private final Set<UUID> dirtyPlayers = new LinkedHashSet<>();

    public RewardService(EconomyService economyService, PlayerService playerService, Logger logger) {
        this.economyService = Objects.requireNonNull(economyService, "economyService");
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public RewardResult grantCoins(
            UUID uniqueId,
            long amount,
            RewardSource source,
            String reason
    ) {
        return grantCoins(new RewardGrant(uniqueId, amount, source, reason));
    }

    public RewardResult grantCoins(RewardGrant grant) {
        RewardGrant validatedGrant = Objects.requireNonNull(grant, "grant");
        EconomyResult economyResult = economyService.addCoinsDeferred(
                validatedGrant.playerId(),
                validatedGrant.coins()
        );

        return switch (economyResult) {
            case SUCCESS -> {
                long balance = economyService.getCoins(validatedGrant.playerId())
                        .orElseThrow(() -> new IllegalStateException(
                                "Successful deferred mutation lost loaded player " + validatedGrant.playerId()
                        ));
                dirtyPlayers.add(validatedGrant.playerId());
                yield RewardResult.success(validatedGrant.coins(), balance);
            }
            case PLAYER_NOT_LOADED -> RewardResult.failure(RewardStatus.PLAYER_NOT_LOADED);
            case BALANCE_OVERFLOW -> RewardResult.failure(RewardStatus.BALANCE_OVERFLOW);
            case INSUFFICIENT_FUNDS -> throw new IllegalStateException(
                    "A positive coin reward cannot produce INSUFFICIENT_FUNDS"
            );
        };
    }

    public void flushPlayer(UUID uniqueId) {
        UUID validatedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        if (!dirtyPlayers.contains(validatedUniqueId)) {
            return;
        }
        if (!playerService.isLoaded(validatedUniqueId)) {
            // PlayerService unload persists before removing the player from its loaded map.
            dirtyPlayers.remove(validatedUniqueId);
            return;
        }

        try {
            playerService.savePlayer(validatedUniqueId);
            dirtyPlayers.remove(validatedUniqueId);
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Pending rewards for player " + validatedUniqueId
                            + " could not be flushed; the player remains dirty for a later retry.",
                    exception
            );
        }
    }

    public void flushAll() {
        for (UUID uniqueId : List.copyOf(dirtyPlayers)) {
            flushPlayer(uniqueId);
        }
    }

    boolean isDirty(UUID uniqueId) {
        return dirtyPlayers.contains(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    int dirtyPlayerCount() {
        return dirtyPlayers.size();
    }

    void clearDirtyTracking() {
        dirtyPlayers.clear();
    }
}
