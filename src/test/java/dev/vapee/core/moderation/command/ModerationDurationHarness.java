package dev.vapee.core.moderation.command;

import java.time.Instant;
import java.util.List;

public final class ModerationDurationHarness {
    private static int checks;
    public static void main(String[] args) {
        Instant now = Instant.parse("2026-09-30T12:00:00.123456789Z");
        for (String permanent : List.of("permanent", "PERMANENT", "perm", "PERM")) {
            var parsed = ModerationDurationParser.parse(permanent);
            check(parsed.duration().isEmpty() && parsed.expiresAt(now).isEmpty(), "permanent " + permanent);
            check(parsed.display().equals("permanent"), "permanent display");
        }
        String[] tokens = {"1s", "30s", "10m", "30m", "2h", "7d", "2w", "0002H"};
        long[] seconds = {1, 30, 600, 1800, 7200, 604800, 1209600, 7200};
        for (int i = 0; i < tokens.length; i++) {
            var parsed = ModerationDurationParser.parse(tokens[i]);
            check(parsed.duration().orElseThrow().getSeconds() == seconds[i], "duration " + tokens[i]);
            check(parsed.expiresAt(now).orElseThrow().equals(now.plusSeconds(seconds[i])), "nanosecond-preserving expiry");
        }
        check(ModerationDurationParser.parse("0002H").display().equals("2h"), "normalized display");
        for (String invalid : List.of("0m", "-1h", "+2h", "1.5h", "1,5h", "1e3s", "", " ", "1d12h", "2h30m",
                "1month", "1y", "1Mth", "1x", "1", "h", " 1h", "1h ", " perm", "permanent ", "1\nh",
                "9223372036854775808s", "9223372036854775807w", "15250284452472w")) {
            reject(() -> ModerationDurationParser.parse(invalid), "invalid " + invalid);
        }
        reject(() -> ModerationDurationParser.parse(null), "null");
        reject(() -> ModerationDurationParser.parse("1s").expiresAt(Instant.MAX), "Instant overflow");
        reject(() -> ModerationDurationParser.parse("9223372036854775807s").expiresAt(now), "addition overflow");
        check(ModerationDurationParser.parse("1s").expiresAt(Instant.MAX.minusSeconds(1)).orElseThrow().equals(Instant.MAX), "exact max boundary");
        System.out.println("ModerationDurationHarness passed " + checks + " checks.");
    }
    private static void reject(Runnable runnable, String label) {
        try { runnable.run(); throw new AssertionError(label); } catch (IllegalArgumentException expected) { checks++; }
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
}
