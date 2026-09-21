package dev.vapee.core.reward;

import java.util.Objects;
import java.util.OptionalLong;

public record RewardResult(
        RewardStatus status,
        long grantedAmount,
        OptionalLong resultingBalance
) {

    public RewardResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(resultingBalance, "resultingBalance");
        if (grantedAmount < 0L) {
            throw new IllegalArgumentException("grantedAmount must not be negative");
        }
        if (status == RewardStatus.SUCCESS) {
            if (grantedAmount == 0L || resultingBalance.isEmpty()) {
                throw new IllegalArgumentException("Successful rewards require amount and balance");
            }
        } else if (grantedAmount != 0L || resultingBalance.isPresent()) {
            throw new IllegalArgumentException("Failed rewards must not report a grant or balance");
        }
    }

    static RewardResult success(long amount, long balance) {
        return new RewardResult(RewardStatus.SUCCESS, amount, OptionalLong.of(balance));
    }

    static RewardResult failure(RewardStatus status) {
        if (status == RewardStatus.SUCCESS) {
            throw new IllegalArgumentException("SUCCESS is not a failure status");
        }
        return new RewardResult(status, 0L, OptionalLong.empty());
    }
}
