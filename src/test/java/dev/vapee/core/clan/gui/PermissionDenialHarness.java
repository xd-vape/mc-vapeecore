package dev.vapee.core.clan.gui;

import static dev.vapee.core.message.MessageColorAssertions.hasSplitPrefix;
import static dev.vapee.core.message.MessageColorAssertions.hasTextColor;

import dev.vapee.core.clan.command.ClanCommand;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.friend.*;
import dev.vapee.core.friend.command.FriendCommand;
import dev.vapee.core.i18n.*;
import dev.vapee.core.message.*;
import dev.vapee.core.staff.StaffPanel;
import dev.vapee.core.staff.StaffPanelTestSupport;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.file.Files;
import java.time.Clock;
import java.util.*;
import java.util.logging.Logger;

/** Executed commands and real localized spans, plus copied/custom catalog and reload behavior. */
public final class PermissionDenialHarness {
    private static int checks;
    private static final String EN = "You do not have permission to use this command.";
    private static final String DE = "Du hast keine Berechtigung, diesen Befehl zu verwenden.";
    private static final List<String> LEGACY =
            List.of(
                    "common.command-denied",
                    "core.reload.denied",
                    "core.quest.denied",
                    "spawn.set-denied",
                    "spawn.use-denied",
                    "warp.denied",
                    "rank.denied",
                    "social.denied",
                    "private-messages.error.denied",
                    "economy.denied",
                    "daily-quests.denied",
                    "clan.command.denied",
                    "staff.denied",
                    "tpa.permission",
                    "utility.build.denied",
                    "utility.invsee.denied",
                    "utility.teleport-here.denied");

    public static void main(String[] args) throws Exception {
        catalogs();
        commands();
        staff();
        overridesAndReload();
        System.out.println("PermissionDenialHarness passed " + checks + " checks.");
    }

    private static void catalogs() throws Exception {
        var f = new ClanMenuFixture();
        var player = f.player("Viewer", true);
        var localization = f.messages.localization();
        var bundled = localization.snapshot();
        for (boolean copied : List.of(false, true)) {
            localization.apply(
                    new LocalizationService.Snapshot(
                            bundled.bundled(), copied ? bundled.bundled() : Map.of()));
            for (Locale locale : List.of(Locale.US, Locale.GERMANY)) {
                player.locale = locale;
                for (String key : LEGACY) {
                    Component denial = f.messages.commandDenied(player.player, key);
                    packet(denial, locale, null);
                    check(
                            TranslationCatalog.placeholders(
                                                    localization.lookup(Language.ENGLISH, key))
                                            .isEmpty()
                                    && TranslationCatalog.placeholders(
                                                    localization.lookup(Language.GERMAN, key))
                                            .isEmpty(),
                            "legacy denial keeps its zero-placeholder schema: " + key);
                }
            }
        }
        var oldGerman = new HashMap<>(bundled.bundled().get(Language.GERMAN));
        oldGerman.put("common.command-denied", "Du darfst diesen Command nicht verwenden.");
        localization.apply(
                new LocalizationService.Snapshot(
                        bundled.bundled(),
                        Map.of(
                                Language.GERMAN,
                                oldGerman,
                                Language.ENGLISH,
                                bundled.bundled().get(Language.ENGLISH))));
        player.locale = Locale.GERMANY;
        for (String key : LEGACY)
            packet(f.messages.commandDenied(player.player, key), player.locale, null);
        check(
                localization
                        .snapshot()
                        .live()
                        .get(Language.GERMAN)
                        .get("common.command-denied")
                        .equals("Du darfst diesen Command nicht verwenden."),
                "copied default is interpreted without rewriting it");
    }

    private static void commands() throws Exception {
        var f = new ClanMenuFixture();
        var player = f.player("Actor", true);
        int[] saves = {0}, opens = {0};
        var friends =
                new FriendService(
                        new FriendRepository() {
                            public FriendSnapshot initialize() {
                                return FriendSnapshot.empty();
                            }

                            public void save(FriendSnapshot next) {
                                saves[0]++;
                            }
                        },
                        FriendLimits.defaults(),
                        (a, b) -> FriendRequestDecision.ALLOW,
                        Clock.systemUTC());
        var friend =
                new FriendCommand(
                        friends,
                        f.identity,
                        f.messages,
                        new CommandHelpRenderer(f.messages),
                        () -> List.of(player.player),
                        id -> player.player,
                        ClanMenuFixture.logger(),
                        p -> opens[0]++);
        var clan =
                new ClanCommand(
                        f.clans,
                        f.identity,
                        f.messages,
                        new CommandHelpRenderer(f.messages),
                        () -> List.of(player.player),
                        id -> player.player,
                        ClanMenuFixture.logger(),
                        p -> opens[0]++,
                        p -> opens[0]++);
        player.permitted = false;
        for (Locale locale : List.of(Locale.US, Locale.GERMANY)) {
            player.locale = locale;
            player.received.clear();
            friend.onCommand(player.player, null, "friends", new String[] {"add", "Other"});
            check(
                    player.received.size() == 1,
                    "real denied friend action sends exactly one packet");
            packet(player.received.getFirst(), locale, SocialChannel.FRIENDS);
            check(
                    friend.onTabComplete(player.player, null, "friend", new String[] {""})
                            .isEmpty(),
                    "friend completion still denied");
            player.received.clear();
            clan.onCommand(player.player, null, "clans", new String[] {"create", "Name"});
            check(player.received.size() == 1, "real denied clan action sends exactly one packet");
            packet(player.received.getFirst(), locale, SocialChannel.CLAN);
            check(
                    clan.onTabComplete(player.player, null, "clan", new String[] {""}).isEmpty(),
                    "clan completion still denied");
        }
        check(
                saves[0] == 0 && opens[0] == 0 && f.clans.getClanOf(player.id).isEmpty(),
                "denial grants no authority or mutations");
        // Custom wording is selected at the actual command boundary too.
        var snapshot = f.messages.localization().snapshot();
        f.messages
                .localization()
                .apply(
                        new LocalizationService.Snapshot(
                                snapshot.bundled(),
                                Map.of(
                                        Language.GERMAN,
                                        Map.of(
                                                "clan.command.denied",
                                                "<gold>Betreibertext</gold>"))));
        player.received.clear();
        clan.onCommand(player.player, null, "clan", new String[0]);
        Component custom = player.received.getFirst();
        check(
                plain(custom).equals("[Clan] Betreibertext")
                        && hasTextColor(custom, "Betreibertext", NamedTextColor.GOLD),
                "real command preserves deliberate legacy text and color");
        check(
                hasSplitPrefix(custom, "Clan", MessageStyle.BRACKET, MessageStyle.CLAN_LABEL),
                "custom body does not recolor clan prefix");
    }

    private static void staff() {
        for (Locale locale : List.of(Locale.US, Locale.GERMANY)) {
            var f = new StaffPanelTestSupport.Fixture();
            for (boolean primaryStaff : List.of(false, true)) {
                for (boolean permission : List.of(false, true)) {
                    var p = f.player("Viewer", primaryStaff ? "admin" : "default");
                    p.locale = locale;
                    if (permission) p.permissions.add(StaffPanel.PERMISSION);
                    p.op = true;
                    f.open(p);
                    if (permission && primaryStaff) {
                        check(
                                p.opens == 1 && f.panel.canOpen(p.player),
                                "current primary staff AND permission opens panel");
                        p.permissions.clear();
                        p.messages.clear();
                        f.open(p);
                        check(
                                p.messages.size() == 1
                                        && p.opens == 1
                                        && !f.panel.canOpen(p.player),
                                "permission revocation closes access without another open");
                        packet(p.messages.getFirst(), locale, SocialChannel.STAFF);
                    } else {
                        check(
                                p.opens == 0
                                        && p.messages.size() == 1
                                        && !f.panel.canOpen(p.player),
                                "neither permission alone nor primary staff alone nor OP suffices");
                        packet(p.messages.getFirst(), locale, SocialChannel.STAFF);
                    }
                    p.permissions.clear();
                    f.list(p);
                    check(
                            p.messages.size() == 1
                                    && !plain(p.messages.getFirst())
                                            .contains(locale.equals(Locale.GERMANY) ? DE : EN),
                            "staff list stays public with no panel permission");
                }
            }
            var actor = f.admin();
            actor.locale = locale;
            f.groups.put(actor.id, "default");
            f.open(actor);
            check(actor.opens == 0, "current primary group supersedes display-rank staff data");
            packet(actor.messages.getFirst(), locale, SocialChannel.STAFF);
            f.runtime = false;
            actor.messages.clear();
            f.open(actor);
            check(
                    actor.messages.isEmpty() && actor.opens == 0,
                    "disabled runtime still does not execute command");
            f.panel.disable();
        }
    }

    private static void overridesAndReload() throws Exception {
        var directory = Files.createTempDirectory("vc-command-denial-");
        var file = directory.resolve("en.yml");
        var localization = LocalizationService.bundled(Logger.getAnonymousLogger());
        var messages = new MessageService(() -> "", localization);
        var viewer = new ClanMenuFixture().player("Viewer", true);
        var before = localization.snapshot();
        try {
            String source =
                    "common.command-denied: '<yellow><click:suggest_command:/help><hover:show_text:Help>Ask staff.</hover></click></yellow>'\n"
                            + "staff.denied: '<light_purple>Custom panel policy.</light_purple>'\n";
            Files.writeString(file, source);
            var candidate = localization.prepare(directory);
            packet(messages.commandDenied(viewer.player, "staff.denied"), Locale.US, null);
            localization.apply(candidate);
            var denial = messages.commandDenied(viewer.player, "core.reload.denied");
            check(
                    plain(denial).equals("Ask staff.")
                            && hasTextColor(denial, "Ask staff.", NamedTextColor.YELLOW),
                    "canonical custom text/color applies to default legacy denials");
            check(
                    hasSuggestion(denial, "/help") && hasHover(denial),
                    "operator click/hover survive localized rendering");
            var staff = messages.commandDenied(viewer.player, "staff.denied");
            check(
                    plain(staff).equals("Custom panel policy.")
                            && hasTextColor(
                                    staff, "Custom panel policy.", NamedTextColor.LIGHT_PURPLE),
                    "specific custom legacy message has priority");
            viewer.locale = Locale.GERMANY;
            packet(messages.commandDenied(viewer.player, "staff.denied"), viewer.locale, null);
            check(
                    Files.readString(file).equals(source),
                    "prepare/apply and rendering never rewrite operator catalog");
            viewer.locale = Locale.US;
            Files.writeString(file, "common.command-denied: 'Changed live wording.'\n");
            localization.apply(localization.prepare(directory));
            check(
                    plain(messages.commandDenied(viewer.player)).equals("Changed live wording."),
                    "successful localization reload is read without a second cache");
            localization.apply(candidate);
            check(
                    plain(messages.commandDenied(viewer.player)).equals("Ask staff."),
                    "restored snapshot restores operator denial after rollback");
            Files.writeString(file, "common.command-denied: 'Bad <player>'\n");
            boolean failed = false;
            try {
                localization.prepare(directory);
            } catch (IllegalArgumentException expected) {
                failed = true;
            }
            check(
                    failed && localization.snapshot() == candidate,
                    "invalid placeholder candidate fails before changing live messages");
            localization.apply(before);
            packet(messages.commandDenied(viewer.player), Locale.US, null);
            viewer.locale = Locale.GERMANY;
            localization.apply(
                    new LocalizationService.Snapshot(
                            before.bundled(),
                            Map.of(
                                    Language.GERMAN,
                                    Map.of(
                                            "common.command-denied",
                                            "<red>Du darfst diesen Command nicht verwenden.</red>"))));
            check(
                    plain(messages.commandDenied(viewer.player))
                            .equals("Du darfst diesen Command nicht verwenden."),
                    "even similar deliberate formatting is not guessed to be a copied default");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void packet(Component component, Locale locale, SocialChannel channel) {
        String text = locale.equals(Locale.GERMANY) ? DE : EN;
        String label =
                channel == SocialChannel.FRIENDS
                        ? (locale.equals(Locale.GERMANY) ? "Freunde" : "Friends")
                        : channel == SocialChannel.CLAN
                                ? "Clan"
                                : channel == SocialChannel.STAFF
                                        ? (locale.equals(Locale.GERMANY) ? "Team" : "Staff")
                                        : null;
        check(
                plain(component).equals(label == null ? text : "[" + label + "] " + text),
                "one-line canonical localized denial with existing wrapper");
        check(
                hasTextColor(component, text, MessageStyle.ERROR),
                "entire actual error text uses semantic ERROR spans");
        check(
                !plain(component).contains("vapeecore.")
                        && !plain(component).contains("primary")
                        && !plain(component).contains("LuckPerms"),
                "standard text exposes no permission or internal group details");
        if (channel != null)
            check(
                    hasSplitPrefix(
                            component,
                            label,
                            MessageStyle.BRACKET,
                            channel == SocialChannel.FRIENDS
                                    ? MessageStyle.FRIENDS_LABEL
                                    : channel == SocialChannel.CLAN
                                            ? MessageStyle.CLAN_LABEL
                                            : MessageStyle.STAFF_LABEL),
                    "brackets and label retain independent channel colors");
    }

    private static boolean hasSuggestion(Component c, String command) {
        return ClickEvent.suggestCommand(command).equals(c.clickEvent())
                || c.children().stream().anyMatch(child -> hasSuggestion(child, command));
    }

    private static boolean hasHover(Component c) {
        return c.hoverEvent() != null
                || c.children().stream().anyMatch(PermissionDenialHarness::hasHover);
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static void check(boolean ok, String message) {
        checks++;
        if (!ok) throw new AssertionError(message);
    }
}
