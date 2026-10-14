package dev.vapee.core.presentation;

import dev.vapee.core.presentation.config.PresentationConfig;
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

    public static void main(String[] args) throws Exception {
        check(PresentationConfig.DEFAULT_TABLIST_NAME_FORMAT.equals("<rank_name><clan_tag_display>"),
                "missing tablist name format includes rank-colored name and conditional clan without prefix");
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
        Component coloredTabName = MINI_MESSAGE.deserialize(
                PresentationConfig.DEFAULT_TABLIST_NAME_FORMAT,
                net.kyori.adventure.text.minimessage.tag.resolver.TagResolver.resolver(
                        PresentationRenderer.createRankAndPlaytimePlaceholders(Optional.of(rank), Component.text("rx29"), 0L),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("prefix", Component.empty()),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("clan_tag_display", Component.empty()))
        );
        check(PLAIN.serialize(coloredTabName).equals("rx29")
                        && hasColoredText(coloredTabName, "rx29", rankColor),
                "default tablist name uses the configured hex rank color");

        Component normalName = Component.text("TestPlayer", NamedTextColor.YELLOW);
        RankInfo owner = new RankInfo(
                "owner", "Owner", Optional.empty(), Optional.of(NamedTextColor.DARK_RED), OptionalInt.empty()
        );
        Component named = MINI_MESSAGE.deserialize(
                "<name>|<rank_name>|<rank>|<rank_id>|<group>",
                PresentationRenderer.createRankAndPlaytimePlaceholders(
                        Optional.of(owner), normalName, 0L
                )
        );
        check(PLAIN.serialize(named).equals("TestPlayer|TestPlayer|Owner|owner|owner"),
                "presentation keeps friendly rank, technical ID, alias, and normal name distinct");
        check(hasColoredText(named, "TestPlayer", NamedTextColor.YELLOW)
                        && hasColoredText(named, "TestPlayer", NamedTextColor.DARK_RED)
                        && hasColoredText(named, "Owner", NamedTextColor.DARK_RED),
                "named rank color applies to rank_name and rank without changing name");

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

        serverNameColors();
        personalLanguages();

        System.out.println("PresentationHarness passed " + checks + " checks.");
    }


    private static void personalLanguages() throws Exception {
        try (var f = new PresentationFixture()) {
            var german = f.player("GermanViewer"); german.locale = java.util.Locale.GERMANY;
            var english = f.player("EnglishViewer");
            var renderer = f.renderer(ignored -> Optional.empty());
            var de = renderer.render(german.player); var en = renderer.render(english.player);
            check(de.scoreboardLines().stream().map(PLAIN::serialize).anyMatch("Rang:"::equals)
                    && en.scoreboardLines().stream().map(PLAIN::serialize).anyMatch("Rank:"::equals), "own sidebar labels follow each viewer's current language");
            check(!PLAIN.serialize(de.tablistHeader()).equals(PLAIN.serialize(en.tablistHeader()))
                    && PLAIN.serialize(de.tablistFooter()).contains("nicht")
                    && PLAIN.serialize(en.tablistFooter()).contains("don't"), "personal header/footer independently localized");
            check(PLAIN.serialize(de.tablistName()).equals("GermanViewer") && PLAIN.serialize(en.tablistName()).equals("EnglishViewer"), "player names remain literal identities");
        }
    }

    private static void serverNameColors() throws Exception {
        try (var f = new PresentationFixture()) {
            var player = f.player("ServerNameTest");
            var renderer = f.renderer(ignored -> Optional.empty());
            String[] names = {"Color Server", "<red>Color Server</red>", "<#ff8800>Color Server", "<gradient:red:yellow>Color Server</gradient>"};
            TextColor[] colors = {NamedTextColor.AQUA, NamedTextColor.RED, TextColor.color(0xff8800)};
            for (int index = 0; index < names.length; index++) {
                String yaml = "server:\n  name: '" + names[index] + "'\n";
                java.nio.file.Path file = f.directory.resolve("config.yml");
                java.nio.file.Files.writeString(file, yaml);
                var plan = f.config.prepareReload(); plan.apply();
                var result = renderer.render(player.player);
                check(PLAIN.serialize(result.scoreboardTitle()).equals("Color Server"), "server formatting is parsed rather than displayed literally");
                check(PLAIN.serialize(result.tablistHeader()).startsWith("Color Server\n"), "same server component reaches tablist header");
                var titleColors = effectiveColors(result.scoreboardTitle(), null);
                var headerColors = effectiveColors(result.tablistHeader(), null).subList(0, "Color Server".length());
                if (index < colors.length) {
                    TextColor expected = colors[index];
                    check(titleColors.stream().allMatch(expected::equals), "server color overrides aqua template, or inherits it when uncolored");
                    check(headerColors.stream().allMatch(expected::equals), "tablist preserves configured named/hex color");
                } else {
                    check(titleColors.stream().distinct().count() > 1 && titleColors.equals(headerColors), "gradient survives both aqua wrappers");
                }
                check(java.nio.file.Files.readString(file).equals(yaml), "reload leaves operator config unchanged");
                plan.rollback();
                check(PLAIN.serialize(renderer.render(player.player).scoreboardTitle()).equals("Vapee Community"), "reload rollback restores previous server name");
            }
        }
    }

    private static java.util.List<TextColor> effectiveColors(Component component, TextColor inherited) {
        TextColor color = component.color() == null ? inherited : component.color();
        var colors = new java.util.ArrayList<TextColor>();
        if (component instanceof net.kyori.adventure.text.TextComponent text)
            for (int i = 0; i < text.content().length(); i++) colors.add(color);
        for (Component child : component.children()) colors.addAll(effectiveColors(child, color));
        return colors;
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
