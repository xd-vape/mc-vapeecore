package dev.vapee.core.quest.daily;

import java.time.LocalDate;
import java.util.Objects;

public record DailyQuestCycleId(LocalDate date) {

    public DailyQuestCycleId {
        Objects.requireNonNull(date, "date");
    }

    public static DailyQuestCycleId parse(String value) {
        return new DailyQuestCycleId(LocalDate.parse(Objects.requireNonNull(value, "value")));
    }

    @Override
    public String toString() {
        return date.toString();
    }
}
