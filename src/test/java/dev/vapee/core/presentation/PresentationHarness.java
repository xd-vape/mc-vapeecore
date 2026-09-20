package dev.vapee.core.presentation;

import dev.vapee.core.rank.RankInfo;

import java.util.Optional;
import java.util.OptionalInt;

public final class PresentationHarness {

    private static int checks;

    private PresentationHarness() {
    }

    public static void main(String[] args) {
        RankInfo rank = new RankInfo(
                "vip",
                "VIP Supporter",
                Optional.of("Supporter rank."),
                OptionalInt.of(50)
        );
        PresentationRenderer.RankValues values = PresentationRenderer.resolveRankValues(
                Optional.of(rank),
                Optional.of("stale-group")
        );
        check(values.displayName().equals("VIP Supporter"),
                "<rank> resolves the friendly primary-rank display name");
        check(values.id().equals("vip"),
                "<rank_id> and compatibility <group> resolve the raw primary group ID");

        PresentationRenderer.RankValues fallback = PresentationRenderer.resolveRankValues(
                Optional.empty(),
                Optional.of("default")
        );
        check(fallback.displayName().equals("default") && fallback.id().equals("default"),
                "missing RankInfo falls back to the known primary group for every rank placeholder");
        PresentationRenderer.RankValues unavailable = PresentationRenderer.resolveRankValues(
                Optional.empty(),
                Optional.empty()
        );
        check(unavailable.displayName().isEmpty() && unavailable.id().isEmpty(),
                "fully unavailable identity produces a controlled empty value");

        System.out.println("PresentationHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
