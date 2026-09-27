package dev.vapee.core.format;

public final class PlaytimeFormatter {

    private static final long TICKS_PER_MINUTE = 20L * 60L;

    private PlaytimeFormatter() {
    }

    public static String formatTicks(long ticks) {
        long totalMinutes = Math.max(0L, ticks) / TICKS_PER_MINUTE;
        if (totalMinutes < 60L) {
            return totalMinutes + "m";
        }
        long totalHours = totalMinutes / 60L;
        if (totalHours < 24L) {
            return totalHours + "h " + (totalMinutes % 60L) + "m";
        }
        return (totalHours / 24L) + "d " + (totalHours % 24L) + "h";
    }
}
