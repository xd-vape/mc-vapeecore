package dev.vapee.core.quest.daily.menu;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

/** Elapsed hours do not wrap at 24 on a long DST day. */
public final class ResetCountdownFormatter {
    private ResetCountdownFormatter() { }
    public static String format(Duration remaining) {
        long seconds = Math.max(0L, Objects.requireNonNull(remaining, "remaining").getSeconds());
        return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60);
    }
}
