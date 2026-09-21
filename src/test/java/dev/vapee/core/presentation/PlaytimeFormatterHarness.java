package dev.vapee.core.presentation;

public final class PlaytimeFormatterHarness {

    private static int checks;

    private PlaytimeFormatterHarness() {
    }

    public static void main(String[] args) {
        checkFormat(0L, "0m", "zero ticks");
        checkFormat(59L * 20L, "0m", "59 seconds");
        checkMinutes(1L, "1m");
        checkMinutes(43L, "43m");
        checkMinutes(59L, "59m");
        checkMinutes(60L, "1h 0m");
        checkMinutes(61L, "1h 1m");
        checkMinutes(6L * 60L + 43L, "6h 43m");
        checkMinutes(23L * 60L + 59L, "23h 59m");
        checkMinutes(24L * 60L, "1d 0h");
        checkMinutes(25L * 60L, "1d 1h");
        checkMinutes(54L * 60L, "2d 6h");
        checkMinutes((12L * 24L + 7L) * 60L, "12d 7h");
        checkFormat(Long.MIN_VALUE, "0m", "negative overflow edge");

        System.out.println("PlaytimeFormatterHarness passed " + checks + " checks.");
    }

    private static void checkMinutes(long minutes, String expected) {
        checkFormat(minutes * 60L * 20L, expected, minutes + " minutes");
    }

    private static void checkFormat(long ticks, String expected, String description) {
        checks++;
        String actual = PlaytimeFormatter.formatTicks(ticks);
        if (!actual.equals(expected)) {
            throw new AssertionError(description + ": expected " + expected + " but got " + actual);
        }
    }
}
