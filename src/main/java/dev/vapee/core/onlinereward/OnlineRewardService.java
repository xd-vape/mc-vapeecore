package dev.vapee.core.onlinereward;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.reward.RewardResult;
import dev.vapee.core.reward.RewardService;
import dev.vapee.core.reward.RewardSource;
import dev.vapee.core.reward.RewardStatus;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main-thread-owned processor for cumulative Minecraft playtime rewards.
 * Bukkit statistic access belongs to the module adapter, not this service.
 */
public final class OnlineRewardService {

    public static final String REWARD_REASON = "online:playtime";

    private final PlayerService playerService;
    private final RewardGranter rewardGranter;
    private final Supplier<OnlineRewardConfig> configSupplier;
    private final Logger logger;

    public OnlineRewardService(
            ConfigService configService,
            PlayerService playerService,
            RewardService rewardService,
            Logger logger
    ) {
        this(
                Objects.requireNonNull(configService, "configService")::getOnlineRewardConfig,
                playerService,
                Objects.requireNonNull(rewardService, "rewardService")::grantCoins,
                logger
        );
    }

    OnlineRewardService(
            Supplier<OnlineRewardConfig> configSupplier,
            PlayerService playerService,
            RewardGranter rewardGranter,
            Logger logger
    ) {
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier");
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.rewardGranter = Objects.requireNonNull(rewardGranter, "rewardGranter");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public OnlineRewardProcessResult process(UUID playerId, long currentPlaytimeTicks) {
        UUID validatedPlayerId = Objects.requireNonNull(playerId, "playerId");
        Optional<CorePlayer> player = playerService.getPlayer(validatedPlayerId);
        if (player.isEmpty()) {
            return OnlineRewardProcessResult.status(OnlineRewardProcessStatus.PLAYER_NOT_LOADED);
        }

        long safeCurrentTicks = Math.max(0L, currentPlaytimeTicks);
        OnlineRewardProgress progress = player.get().getOnlineRewardProgress();
        if (!progress.isInitialized()) {
            progress.setProcessedPlaytimeTicks(safeCurrentTicks);
            return OnlineRewardProcessResult.status(OnlineRewardProcessStatus.INITIALIZED);
        }

        long processedTicks = progress.getProcessedPlaytimeTicks().orElseThrow();
        if (safeCurrentTicks < processedTicks) {
            progress.setProcessedPlaytimeTicks(safeCurrentTicks);
            logger.warning("Minecraft playtime for player " + validatedPlayerId
                    + " moved behind the processed online-reward baseline; rebased to "
                    + safeCurrentTicks + " tick(s) without granting coins."
            );
            return OnlineRewardProcessResult.status(OnlineRewardProcessStatus.REBASED);
        }

        OnlineRewardConfig config = Objects.requireNonNull(configSupplier.get(), "onlineRewardConfig");
        if (!config.enabled()) {
            progress.setProcessedPlaytimeTicks(safeCurrentTicks);
            return OnlineRewardProcessResult.status(OnlineRewardProcessStatus.DISABLED);
        }

        long unprocessedTicks = safeCurrentTicks - processedTicks;
        long intervalTicks = config.intervalTicks();
        if (unprocessedTicks < intervalTicks) {
            return OnlineRewardProcessResult.status(OnlineRewardProcessStatus.NO_REWARD);
        }

        long intervals = unprocessedTicks / intervalTicks;
        long coinsToGrant;
        long rewardedTicks;
        long rewardedMinutes;
        try {
            coinsToGrant = Math.multiplyExact(intervals, config.coins());
            rewardedTicks = Math.multiplyExact(intervals, intervalTicks);
            rewardedMinutes = Math.multiplyExact(intervals, config.intervalMinutes());
        } catch (ArithmeticException exception) {
            logger.log(
                    Level.WARNING,
                    "Online reward calculation overflow for player " + validatedPlayerId
                            + "; progress remains pending.",
                    exception
            );
            return OnlineRewardProcessResult.status(OnlineRewardProcessStatus.REWARD_FAILED);
        }

        RewardResult rewardResult = rewardGranter.grantCoins(
                validatedPlayerId,
                coinsToGrant,
                RewardSource.PLAYTIME,
                REWARD_REASON
        );
        if (rewardResult.status() == RewardStatus.PLAYER_NOT_LOADED) {
            return OnlineRewardProcessResult.status(OnlineRewardProcessStatus.PLAYER_NOT_LOADED);
        }
        if (rewardResult.status() != RewardStatus.SUCCESS) {
            return OnlineRewardProcessResult.status(OnlineRewardProcessStatus.REWARD_FAILED);
        }

        progress.setProcessedPlaytimeTicks(Math.addExact(processedTicks, rewardedTicks));
        return OnlineRewardProcessResult.rewarded(
                intervals,
                rewardResult.grantedAmount(),
                rewardedMinutes,
                rewardResult.resultingBalance().orElseThrow()
        );
    }

    @FunctionalInterface
    interface RewardGranter {

        RewardResult grantCoins(UUID playerId, long coins, RewardSource source, String reason);
    }
}
