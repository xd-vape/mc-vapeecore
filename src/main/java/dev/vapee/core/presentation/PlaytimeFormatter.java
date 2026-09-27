package dev.vapee.core.presentation;

public final class PlaytimeFormatter {

    private PlaytimeFormatter() {
    }

    public static String formatTicks(long ticks) {
        return dev.vapee.core.format.PlaytimeFormatter.formatTicks(ticks);
    }
}
