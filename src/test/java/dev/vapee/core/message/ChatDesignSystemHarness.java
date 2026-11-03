package dev.vapee.core.message;

import static dev.vapee.core.i18n.Translations.tr;
import static dev.vapee.core.message.MessageColorAssertions.hasTextColor;

import dev.vapee.core.presentation.PresentationFixture;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.moderation.ModerationAction;
import dev.vapee.core.moderation.ModerationComponents;
import dev.vapee.core.moderation.ModerationTestSupport;
import dev.vapee.core.moderation.command.MuteCommand;
import dev.vapee.core.moderation.command.WarnCommand;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/** Exercises actual localized/delivered Components and commands, not source spelling or materials. */
public final class ChatDesignSystemHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (Locale locale : List.of(Locale.US, Locale.GERMANY)) {
            channels(locale);
            blocks(locale);
            moderation(locale);
        }
        paletteAndReload();
        try {
            MessageStyle.block();
            throw new AssertionError("empty block accepted");
        } catch (IllegalArgumentException expected) {
            check(true, "empty blocks are rejected");
        }
        System.out.println("ChatDesignSystemHarness passed " + checks + " checks.");
    }

    private static void channels(Locale locale) {
        var localization = LocalizationService.bundled(Logger.getAnonymousLogger());
        String[] labels = locale.equals(Locale.US)
                ? new String[]{"[Friends]", "[Clan]", "[Staff]"}
                : new String[]{"[Freunde]", "[Clan]", "[Team]"};
        int i = 0;
        var labelColors = List.of(MessageStyle.FRIENDS_LABEL, MessageStyle.CLAN_LABEL, MessageStyle.STAFF_LABEL);
        for (SocialChannel channel : SocialChannel.values()) {
            Component body = Component.text("Body ").append(Component.text("RankName", NamedTextColor.AQUA))
                    .append(Component.space()).append(action());
            Component actual = localization.render(locale, channel.message(body));
            check(plain(actual).equals(labels[i] + " Body RankName Accept"), "localized prefix and exact one-line body");
            String label = labels[i].substring(1, labels[i].length() - 1);
            check(MessageColorAssertions.hasSplitPrefix(actual, label, MessageStyle.BRACKET, labelColors.get(i++)), "every channel separates bracket and stable feature color");
            check(hasTextColor(actual, "Body ", MessageStyle.TEXT), "body is neutral");
            check(hasTextColor(actual, "RankName", NamedTextColor.AQUA), "rank color remains explicit");
            check(clicks(actual).equals(List.of(ClickEvent.runCommand("/example accept"))), "action is retained separately from prefix");
        }
        Component future = MessageStyle.feature(Component.text("Party"), Component.text("Created ")
                .append(MessageStyle.success(Component.text("OK"))));
        check(plain(future).equals("[Party] Created OK") && MessageColorAssertions.hasSplitPrefix(future,"Party",MessageStyle.BRACKET,MessageStyle.DEFAULT_LABEL),
                "a future feature needs no own prefix colors or enum case");
        check(hasTextColor(future, "Created ", MessageStyle.TEXT) && hasTextColor(future, "OK", MessageStyle.SUCCESS),
                "success label does not recolor surrounding explanation");
        check(hasTextColor(MessageStyle.warning(Component.text("Warning")), "Warning", MessageStyle.WARNING)
                        && hasTextColor(MessageStyle.error(Component.text("Error")), "Error", MessageStyle.ERROR),
                "warning and error helpers use semantic labels");
        check(hasTextColor(MessageStyle.status(Component.text("online"), true), "online", MessageStyle.ONLINE)
                        && hasTextColor(MessageStyle.status(Component.text("offline"), false), "offline", MessageStyle.OFFLINE),
                "status colors are independent of the accent");
    }

    private static void blocks(Locale locale) throws Exception {
        var fixture = new ModerationTestSupport.Fixture();
        fixture.target.locale = locale;
        var messages = new MessageService(() -> "<gold>[Operator]</gold> ", LocalizationService.bundled(fixture.logger));
        messages.sendRawBlock(fixture.target.sender,
                tr("moderation.warn.received", Map.of("reason", MessageStyle.value(ModerationTestSupport.ATTACK))),
                action().hoverEvent(HoverEvent.showText(tr("common.accept").append(Component.newline()).append(Component.text("Hint")))));
        Component actual = fixture.target.output.getLast();
        String[] lines = plain(actual).split("\n", -1);
        check(lines.length == 5 && lines[0].isEmpty() && lines[4].isEmpty(), "one surrounding empty line per block");
        check(List.of(lines[1], lines[2], lines[3]).stream().allMatch(line -> line.startsWith("  ")),
                "embedded translation newlines are indented after localization");
        check(plain(actual).contains(ModerationTestSupport.ATTACK) && !plain(actual).contains("Operator"),
                "raw block preserves literal reason and owns its wrapper");
        check(clicks(actual).equals(List.of(ClickEvent.runCommand("/example accept"))), "block preserves exact click command");
        check(hoverContains(actual, (locale.equals(Locale.US) ? "Accept" : "Annehmen") + "\nHint"), "hover text localizes without chat indentation leaking into it");
        messages.send(fixture.target.sender, Component.text("ordinary"));
        check(plain(fixture.target.output.getLast()).equals("[Operator] ordinary"), "ordinary operator wrapper remains unchanged");
    }

    private static void moderation(Locale locale) throws Exception {
        var f = new ModerationTestSupport.Fixture();
        f.staff.locale = locale;
        f.target.locale = locale;
        new WarnCommand(f.context).onCommand(f.staff.sender, null, "warn", new String[]{"Alex", ModerationTestSupport.ATTACK});
        for (Component delivered : List.of(f.staff.output.getLast(), f.target.output.getLast())) {
            check(framed(delivered), "actual warning actor and recipient receive aligned blocks");
            check(hasTextColor(delivered, ModerationTestSupport.ATTACK, MessageStyle.accent()), "warning reason is literal accent text");
        }
        String prefix = locale.equals(Locale.US) ? "[Staff]" : "[Team]";
        check(hasTextColor(f.staff.output.getLast(), prefix, MessageStyle.PREFIX)
                        && !plain(f.target.output.getLast()).contains(prefix), "only the moderator receives the Staff prefix");
        new MuteCommand(f.context).onCommand(f.staff.sender, null, "mute", new String[]{"Alex", "1m", ModerationTestSupport.ATTACK});
        check(framed(f.staff.output.getLast()) && framed(f.target.output.getLast()), "actual mute actor and recipient use the same layout");
        check(plain(f.staff.output.getLast()).contains("1m") && plain(f.target.output.getLast()).contains("UTC"),
                "duration and expiry remain available");
        check(hasTextColor(f.target.output.getLast(), ModerationTestSupport.ATTACK, MessageStyle.accent()), "mute reason uses accent");
        var record = f.service.getAllRecords().stream().filter(row -> row.action() == ModerationAction.MUTE).findFirst().orElseThrow();
        Component reminder = f.messages.localize(f.target.sender, ModerationComponents.muteReminder(record));
        check(!plain(reminder).contains("\n") && !plain(reminder).contains(ModerationTestSupport.ATTACK),
                "blocked-chat reminders remain compact and do not repeat the full sanction block");
    }

    private static void paletteAndReload() throws Exception {
        try (var fixture = new PresentationFixture()) {
            var file = fixture.directory.resolve("config.yml");
            Files.writeString(file, "messages:\n  accent-color: '#123456'\n");
            var config = fixture.config;
            config.prepareReload().apply();
            var messages = new MessageService(config);
            for (var row : Map.of("prefix", MessageStyle.PREFIX, "text", MessageStyle.TEXT, "muted", MessageStyle.MUTED,
                    "success", MessageStyle.SUCCESS, "error", MessageStyle.ERROR, "warning", MessageStyle.WARNING,
                    "online", MessageStyle.ONLINE, "offline", MessageStyle.OFFLINE).entrySet()) {
                Component actual = messages.deserialize("<vc_" + row.getKey() + ">value</vc_" + row.getKey() + ">");
                check(hasTextColor(actual, "value", row.getValue()), "semantic MiniMessage tag " + row.getKey());
            }
            check(hasTextColor(messages.deserialize("<accent>value</accent>"), "value", TextColor.color(0x123456)), "accent tag uses applied config");
            Files.writeString(file, "messages:\n  accent-color: 'dark_purple'\n");
            var reload = config.prepareReload();
            reload.apply();
            check(hasTextColor(messages.deserialize("<accent>value</accent>"), "value", NamedTextColor.DARK_PURPLE), "existing resolver sees reloaded accent");
            reload.rollback();
            check(hasTextColor(messages.deserialize("<accent>value</accent>"), "value", TextColor.color(0x123456)), "rollback reaches semantic tag resolver");
            check(hasTextColor(messages.deserialize("<aqua>operator</aqua>"), "operator", NamedTextColor.AQUA), "literal operator colors are preserved");
            MessageStyle.applyAccentColor(MessageStyle.DEFAULT_HIGHLIGHT);
        }
    }

    private static Component action() { return Component.text("Accept", MessageStyle.SUCCESS).clickEvent(ClickEvent.runCommand("/example accept")); }
    private static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }
    private static List<ClickEvent> clicks(Component component) {
        var result = new ArrayList<ClickEvent>();
        if (component.clickEvent() != null) result.add(component.clickEvent());
        component.children().forEach(child -> result.addAll(clicks(child)));
        return result;
    }
    private static boolean hoverContains(Component component, String text) {
        if (component.hoverEvent() != null && component.hoverEvent().value() instanceof Component value && plain(value).contains(text)) return true;
        return component.children().stream().anyMatch(child -> hoverContains(child, text));
    }
    private static boolean framed(Component component) {
        String[] lines = plain(component).split("\n", -1);
        return lines.length == 4 && lines[0].isEmpty() && lines[3].isEmpty()
                && lines[1].startsWith("  ") && lines[2].startsWith("  ");
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}
