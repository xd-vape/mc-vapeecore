package dev.vapee.core.presentation;

import dev.vapee.core.rank.RankInfo;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.Optional;
import java.util.OptionalInt;

public final class PresentationHarness {

    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    private PresentationHarness() {
    }

    public static void main(String[] args) {
        TextColor rankColor = TextColor.color(0xc35cff);
        RankInfo rank = new RankInfo(
                "developer",
                "Developer",
                Optional.of("Development Team"),
                Optional.of(rankColor),
                OptionalInt.of(80)
        );
        Component rendered = MINI_MESSAGE.deserialize(
                "<rank>|<rank_name>|<rank_id>|<group>|<playtime>",
                PresentationRenderer.createRankAndPlaytimePlaceholders(
                        Optional.of(rank),
                        Component.text("rx29"),
                        ticksForMinutes(6L * 60L + 43L)
                )
        );
        check(PLAIN.serialize(rendered).equals("Developer|rx29|developer|developer|6h 43m"),
                "presentation resolves colored rank values, raw IDs, and compact playtime");
        check(hasColoredText(rendered, "Developer", rankColor),
                "<rank> keeps the friendly display name and configured rank color");
        check(hasColoredText(rendered, "rx29", rankColor),
                "<rank_name> applies the primary-rank color to the player display name");

        RankInfo missingColor = new RankInfo(
                "event_host",
                "Event Host",
                Optional.empty(),
                Optional.empty(),
                OptionalInt.empty()
        );
        Component neutral = MINI_MESSAGE.deserialize(
                "<rank>|<rank_name>",
                PresentationRenderer.createRankAndPlaytimePlaceholders(
                        Optional.of(missingColor),
                        Component.text("Host"),
                        0L
                )
        );
        check(hasColoredText(neutral, "Event Host", NamedTextColor.WHITE)
                        && hasColoredText(neutral, "Host", NamedTextColor.WHITE),
                "missing color safely uses the neutral white presentation fallback");

        Component originalName = Component.text("Player", NamedTextColor.YELLOW);
        Component unavailable = MINI_MESSAGE.deserialize(
                "<rank><rank_name>|<rank_id>|<group>|<playtime>",
                PresentationRenderer.createRankAndPlaytimePlaceholders(
                        Optional.empty(),
                        originalName,
                        0L
                )
        );
        check(PLAIN.serialize(unavailable).equals("Player|||0m")
                        && hasColoredText(unavailable, "Player", NamedTextColor.YELLOW),
                "unavailable rank is empty while the normal display name remains unchanged");

        MiniMessage.builder().strict(true).build().deserialize(
                "<rank_name><rank><rank_id><group><playtime>",
                PresentationRenderer.emptyPlaceholders()
        );
        check(true, "strict presentation validation recognizes rank_name and playtime");

        System.out.println("PresentationHarness passed " + checks + " checks.");
    }

    private static long ticksForMinutes(long minutes) {
        return minutes * 60L * 20L;
    }

    private static boolean hasColoredText(Component component, String text, TextColor color) {
        if (color.equals(component.color()) && PLAIN.serialize(component).contains(text)) {
            return true;
        }
        return component.children().stream().anyMatch(child -> hasColoredText(child, text, color));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
