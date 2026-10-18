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
        Instant boundary = boundary(localDate, validatedZone, validatedResetTime);
        return new DailyQuestCycleId(validatedNow.isBefore(boundary) ? localDate.minusDays(1) : localDate);
    }
    /** Strictly future scheduled boundary, using the same gap/overlap policy as resolve. */
    public Instant nextReset(Instant now, ZoneId zone, LocalTime resetTime) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(resetTime, "resetTime");
        LocalDate date = now.atZone(zone).toLocalDate();
        Instant today = boundary(date, zone, resetTime);
        return today.isAfter(now) ? today : boundary(date.plusDays(1), zone, resetTime);
    }

    private Instant boundary(LocalDate date, ZoneId zone, LocalTime resetTime) {
        return date.atTime(resetTime).atZone(zone).toInstant();
    }
}
