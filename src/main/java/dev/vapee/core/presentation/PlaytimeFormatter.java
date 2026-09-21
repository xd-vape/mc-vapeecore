package dev.vapee.core.presentation;

public final class PlaytimeFormatter {

    private static final long TICKS_PER_MINUTE = 20L * 60L;
    private static final long MINUTES_PER_HOUR = 60L;
    private static final long HOURS_PER_DAY = 24L;

    private PlaytimeFormatter() {
    }

    public static String formatTicks(long ticks) {
        long totalMinutes = Math.max(0L, ticks) / TICKS_PER_MINUTE;
        if (totalMinutes < MINUTES_PER_HOUR) {
            return totalMinutes + "m";
        }

        long totalHours = totalMinutes / MINUTES_PER_HOUR;
        if (totalHours < HOURS_PER_DAY) {
            return totalHours + "h " + (totalMinutes % MINUTES_PER_HOUR) + "m";
        }

        return (totalHours / HOURS_PER_DAY) + "d " + (totalHours % HOURS_PER_DAY) + "h";
    }
}
