package dev.vapee.core.onlinereward;

import java.util.OptionalLong;

public final class OnlineRewardProgress {

    private boolean initialized;
    private long processedPlaytimeTicks;

    private OnlineRewardProgress(boolean initialized, long processedPlaytimeTicks) {
        this.initialized = initialized;
        this.processedPlaytimeTicks = requireNonNegative(processedPlaytimeTicks);
    }

    public static OnlineRewardProgress uninitialized() {
        return new OnlineRewardProgress(false, 0L);
    }

    public static OnlineRewardProgress initialized(long processedPlaytimeTicks) {
        return new OnlineRewardProgress(true, processedPlaytimeTicks);
    }

    public boolean isInitialized() {
        return initialized;
    }

    public OptionalLong getProcessedPlaytimeTicks() {
        return initialized ? OptionalLong.of(processedPlaytimeTicks) : OptionalLong.empty();
    }

    public void setProcessedPlaytimeTicks(long processedPlaytimeTicks) {
        this.processedPlaytimeTicks = requireNonNegative(processedPlaytimeTicks);
        this.initialized = true;
    }

    private static long requireNonNegative(long ticks) {
        if (ticks < 0L) {
            throw new IllegalArgumentException("processedPlaytimeTicks must not be negative");
        }
        return ticks;
    }
}
