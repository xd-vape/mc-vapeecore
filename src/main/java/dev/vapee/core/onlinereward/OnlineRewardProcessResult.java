package dev.vapee.core.onlinereward;

import java.util.Objects;
import java.util.OptionalLong;

public record OnlineRewardProcessResult(
        OnlineRewardProcessStatus status,
        long intervals,
        long coinsGranted,
        long rewardedMinutes,
        OptionalLong resultingBalance
) {

    public OnlineRewardProcessResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(resultingBalance, "resultingBalance");
        if (intervals < 0L || coinsGranted < 0L || rewardedMinutes < 0L) {
            throw new IllegalArgumentException("Online reward result values must not be negative");
        }
        if (status == OnlineRewardProcessStatus.REWARDED) {
            if (intervals == 0L || coinsGranted == 0L || rewardedMinutes == 0L
                    || resultingBalance.isEmpty()) {
                throw new IllegalArgumentException("A rewarded result requires reward details");
            }
        } else if (intervals != 0L || coinsGranted != 0L || rewardedMinutes != 0L
                || resultingBalance.isPresent()) {
            throw new IllegalArgumentException("A non-reward result must not contain reward details");
        }
    }

    static OnlineRewardProcessResult status(OnlineRewardProcessStatus status) {
        if (status == OnlineRewardProcessStatus.REWARDED) {
            throw new IllegalArgumentException("REWARDED requires reward details");
        }
        return new OnlineRewardProcessResult(status, 0L, 0L, 0L, OptionalLong.empty());
    }

    static OnlineRewardProcessResult rewarded(
            long intervals,
            long coinsGranted,
            long rewardedMinutes,
            long resultingBalance
    ) {
        return new OnlineRewardProcessResult(
                OnlineRewardProcessStatus.REWARDED,
                intervals,
                coinsGranted,
                rewardedMinutes,
                OptionalLong.of(resultingBalance)
        );
    }
}
