package dev.vapee.core.moderation.command;

import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Objects;

/** Strict single-unit durations. Whitespace, signs and compound/calendar units are not syntax. */
public final class ModerationDurationParser {
    private ModerationDurationParser() { }

    public static ParsedDuration parse(String input) {
        if (input == null) throw new IllegalArgumentException("Invalid duration");
        String value = input.toLowerCase(Locale.ROOT);
        if (value.equals("permanent") || value.equals("perm")) {
            return new ParsedDuration(Optional.empty(), "permanent");
        }
        if (!value.matches("[0-9]+[smhdw]")) throw new IllegalArgumentException("Invalid duration");
        try {
            long number = Long.parseLong(value.substring(0, value.length() - 1));
            if (number <= 0) throw new IllegalArgumentException("Duration must be positive");
            long multiplier = switch (value.charAt(value.length() - 1)) {
                case 's' -> 1; case 'm' -> 60; case 'h' -> 3600; case 'd' -> 86400; case 'w' -> 604800;
                default -> throw new IllegalArgumentException("Invalid unit");
            };
            return new ParsedDuration(Optional.of(Duration.ofSeconds(Math.multiplyExact(number, multiplier))),
                    number + value.substring(value.length() - 1));
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("Duration is too large", exception);
        }
    }

    public record ParsedDuration(Optional<Duration> duration, String display) {
        public ParsedDuration {
            Objects.requireNonNull(duration); Objects.requireNonNull(display);
            if (duration.filter(value -> value.isNegative() || value.isZero()).isPresent()) {
                throw new IllegalArgumentException("Duration must be positive");
            }
        }
        public Optional<Instant> expiresAt(Instant now) {
            Objects.requireNonNull(now);
            try { return duration.map(now::plus); }
            catch (ArithmeticException | DateTimeException exception) {
                throw new IllegalArgumentException("Duration exceeds the timestamp range", exception);
            }
        }
    }
}
