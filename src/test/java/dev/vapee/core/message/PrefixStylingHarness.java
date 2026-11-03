package dev.vapee.core.message;

import static dev.vapee.core.message.MessageColorAssertions.hasSplitPrefix;
import static dev.vapee.core.message.MessageColorAssertions.hasTextColor;

import dev.vapee.core.i18n.Language;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.moderation.ModerationTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Prefix-only spans, actual localized Components, complete overrides and existing delivery. */
public final class PrefixStylingHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(MessageStyle.BRACKET.equals(NamedTextColor.DARK_GRAY), "shared bracket color remains dark gray");
        check(MessageStyle.FRIENDS_LABEL.equals(NamedTextColor.GOLD)
                        && MessageStyle.CLAN_LABEL.equals(NamedTextColor.DARK_PURPLE)
                        && MessageStyle.STAFF_LABEL.equals(NamedTextColor.DARK_GRAY), "declared feature palette retains original identity colors");
        for (Locale locale : List.of(Locale.US, Locale.GERMANY)) channels(locale);
        newFeature();
        overrides();
        System.out.println("PrefixStylingHarness passed " + checks + " checks.");
    }

    private static void channels(Locale locale) throws Exception {
        var fixture = new ModerationTestSupport.Fixture();
        fixture.target.locale = locale;
        var messages = new MessageService(() -> "<aqua>[Operator]</aqua> ", LocalizationService.bundled(fixture.logger));
        var labels = locale.equals(Locale.US) ? List.of("Friends", "Clan", "Staff") : List.of("Freunde", "Clan", "Team");
        var colors = List.of(NamedTextColor.GOLD, NamedTextColor.DARK_PURPLE, NamedTextColor.DARK_GRAY);
        int i = 0;
        for (SocialChannel channel : SocialChannel.values()) {
            Component action = Component.text("[Accept]", MessageStyle.SUCCESS)
                    .clickEvent(ClickEvent.runCommand("/example accept"))
                    .hoverEvent(HoverEvent.showText(Component.text("[hover]", MessageStyle.ERROR)));
            Component body = Component.text("Body ").append(Component.text("RankName", NamedTextColor.AQUA))
                    .append(Component.space()).append(action);
            channel.send(messages, fixture.target.sender, body);
            Component actual = fixture.target.output.getLast();
            String label = labels.get(i);
            check(hasSplitPrefix(actual, label, NamedTextColor.DARK_GRAY, colors.get(i)), "localized bracket/label spans " + channel + " " + locale);
            check(hasNode(actual, "[") && hasNode(actual, "]") && !hasNode(actual, "[" + label + "]"), "prefix brackets and label occupy separate text components");
            check(hasTextColor(actual, "Body ", MessageStyle.TEXT), "body does not inherit label/bracket colors");
            check(hasTextColor(actual, "RankName", NamedTextColor.AQUA), "existing player/rank color is retained");
            check(hasTextColor(actual, "[Accept]", MessageStyle.SUCCESS), "interactive action brackets keep their action color");
            check(clicks(actual).equals(List.of(ClickEvent.runCommand("/example accept"))), "exact existing action retained");
            check(hover(actual, "[hover]", MessageStyle.ERROR), "hover bracket/color is not treated as a prefix");
            check(count(plain(actual), "[" + label + "]") == 1, "no duplicate system prefix");
            if (channel == SocialChannel.STAFF) check(!plain(actual).contains("Operator"), "Staff keeps raw wrapper ownership");
            else check(plain(actual).startsWith("[Operator] ") && hasTextColor(actual, "[Operator]", NamedTextColor.AQUA), "operator wrapper remains outside system bracket formatting");
            i++;
        }
    }

    private static void newFeature() {
        TextColor custom = TextColor.color(0x12AB98);
        Component prefix = MessageStyle.prefix(Component.text("Future"), custom);
        check(prefix.children().size() == 3, "bare-label API constructs exactly three prefix parts");
        check(prefix.children().get(0).color().equals(MessageStyle.BRACKET)
                        && prefix.children().get(1).color().equals(custom)
                        && prefix.children().get(2).color().equals(MessageStyle.BRACKET), "explicit label color is independent of both brackets");
        Component output = MessageStyle.feature(Component.text("Future"), custom, Component.text("Body"));
        check(plain(output).equals("[Future] Body") && hasSplitPrefix(output, "Future", MessageStyle.BRACKET, custom), "unregistered future feature uses the existing API");
        check(hasTextColor(output, "Body", MessageStyle.TEXT), "new feature body stays neutral");
        check(hasSplitPrefix(MessageStyle.prefix(Component.text("Team"), NamedTextColor.RED), "Team", MessageStyle.BRACKET, NamedTextColor.RED), "a different future Staff-label color needs no bracket-color change");
        check(hasSplitPrefix(MessageStyle.prefix(Component.text("Default")), "Default", MessageStyle.BRACKET, MessageStyle.DEFAULT_LABEL), "existing no-color overload remains available");
        check(hasSplitPrefix(MessageStyle.message(Component.text("[Legacy]"), Component.text("Body")), "Legacy", MessageStyle.BRACKET, MessageStyle.DEFAULT_LABEL), "literal preformatted-prefix adapter remains usable without localization");
    }

    private static void overrides() throws Exception {
        var fixture = new ModerationTestSupport.Fixture();
        var bundled = LocalizationService.bundledSnapshot().bundled();
        var english = Map.of("friends.prefix", "<bold><hover:show_text:'<red>[tip]</red>'><click:suggest_command:'/friend help'>[Same]</click></hover></bold>",
                "clan.prefix", "[Same]");
        var german = Map.of("friends.prefix", "[Anderer Name]", "clan.prefix", "[Anderer Name]");
        var service = new LocalizationService(fixture.logger, new LocalizationService.Snapshot(bundled,
                Map.of(Language.ENGLISH, english, Language.GERMAN, german)));
        var messages = new MessageService(() -> "", service);
        for (Locale locale : List.of(Locale.US, Locale.GERMANY)) {
            fixture.target.locale = locale;
            String label = locale.equals(Locale.US) ? "Same" : "Anderer Name";
            SocialChannel.FRIENDS.send(messages, fixture.target.sender, Component.text("Body"));
            Component friends = fixture.target.output.getLast();
            SocialChannel.CLAN.send(messages, fixture.target.sender, Component.text("Body"));
            Component clan = fixture.target.output.getLast();
            check(hasSplitPrefix(friends, label, MessageStyle.BRACKET, MessageStyle.FRIENDS_LABEL)
                            && hasSplitPrefix(clan, label, MessageStyle.BRACKET, MessageStyle.CLAN_LABEL), "identical translated text still selects different stable feature colors");
            check(count(plain(friends), "[") == 1 && count(plain(friends), "]") == 1, "complete operator prefixes are not wrapped twice");
            if (locale.equals(Locale.US)) {
                check(clicks(friends).stream().distinct().toList().equals(List.of(ClickEvent.suggestCommand("/friend help"))), "operator prefix click command survives formatting");
                check(hover(friends, "[tip]", NamedTextColor.RED), "operator prefix hover stays unchanged");
                check(hasBold(friends), "operator prefix decorations survive");
            }
        }
        check(service.snapshot().live().get(Language.ENGLISH).equals(english)
                        && service.snapshot().live().get(Language.GERMAN).equals(german), "formatting does not rewrite override values");
    }

    private static String plain(Component value) { return PlainTextComponentSerializer.plainText().serialize(value); }
    private static int count(String text, String value) { return (text.length() - text.replace(value, "").length()) / value.length(); }
    private static boolean hasNode(Component value, String text) {
        return value instanceof TextComponent literal && literal.content().equals(text)
                || value.children().stream().anyMatch(child -> hasNode(child, text));
    }
    private static boolean hasBold(Component value) {
        return value.decoration(TextDecoration.BOLD) == TextDecoration.State.TRUE || value.children().stream().anyMatch(PrefixStylingHarness::hasBold);
    }
    private static List<ClickEvent> clicks(Component value) {
        var result = new ArrayList<ClickEvent>();
        if (value.clickEvent() != null) result.add(value.clickEvent());
        value.children().forEach(child -> result.addAll(clicks(child)));
        return result;
    }
    private static boolean hover(Component value, String text, TextColor color) {
        if (value.hoverEvent() != null && value.hoverEvent().value() instanceof Component body
                && plain(body).equals(text) && hasTextColor(body, text, color)) return true;
        return value.children().stream().anyMatch(child -> hover(child, text, color));
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}
