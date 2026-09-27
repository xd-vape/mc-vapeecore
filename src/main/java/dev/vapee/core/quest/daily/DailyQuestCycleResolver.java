package dev.vapee.core.quest.daily;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;

public final class DailyQuestCycleResolver {

    public DailyQuestCycleId resolve(Instant now, ZoneId zone, LocalTime resetTime) {
        Instant validatedNow = Objects.requireNonNull(now, "now");
        ZoneId validatedZone = Objects.requireNonNull(zone, "zone");
        LocalTime validatedResetTime = Objects.requireNonNull(resetTime, "resetTime");
        LocalDate localDate = validatedNow.atZone(validatedZone).toLocalDate();
        // atZone resolves gaps and overlaps using the zone's real transition rules.
        Instant boundary = localDate.atTime(validatedResetTime).atZone(validatedZone).toInstant();
        return new DailyQuestCycleId(validatedNow.isBefore(boundary) ? localDate.minusDays(1) : localDate);
    }
}
