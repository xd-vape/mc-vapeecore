package dev.vapee.core.quest.daily;

import java.util.Objects;
import java.util.Optional;

public final class PlayerDailyQuestState {

    private DailyQuestCycleId cycleId;

    private PlayerDailyQuestState(DailyQuestCycleId cycleId) {
        this.cycleId = cycleId;
    }

    public static PlayerDailyQuestState uninitialized() {
        return new PlayerDailyQuestState(null);
    }

    public static PlayerDailyQuestState initialized(DailyQuestCycleId cycleId) {
        return new PlayerDailyQuestState(Objects.requireNonNull(cycleId, "cycleId"));
    }

    public Optional<DailyQuestCycleId> cycleId() {
        return Optional.ofNullable(cycleId);
    }

    public void setCycleId(DailyQuestCycleId cycleId) {
        this.cycleId = Objects.requireNonNull(cycleId, "cycleId");
    }
}
