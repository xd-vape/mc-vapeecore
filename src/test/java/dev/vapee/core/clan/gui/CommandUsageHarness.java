package dev.vapee.core.clan.gui;

import static dev.vapee.core.message.MessageColorAssertions.hasSplitPrefix;
import static dev.vapee.core.message.MessageColorAssertions.hasTextColor;

import dev.vapee.core.clan.command.ClanCommand;
import dev.vapee.core.command.help.CommandHelpEntry;
import dev.vapee.core.command.help.CommandHelpPage;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.command.help.CommandHelpSection;
import dev.vapee.core.friend.FriendLimits;
import dev.vapee.core.friend.FriendRepository;
import dev.vapee.core.friend.FriendRequestDecision;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.friend.FriendSnapshot;
import dev.vapee.core.friend.command.FriendCommand;
import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.message.MessageStyle;
import dev.vapee.core.presentation.PresentationFixture;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import dev.vapee.core.reload.ReloadResult;
import dev.vapee.core.reload.ReloadService;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.file.Files;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;

/** Real command feedback and localized Adventure spans; no source-text/color-name oracle. */
public final class CommandUsageHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        TextColor previous = MessageStyle.accent();
        try {
            for (TextColor accent :
                    List.of(TextColor.color(0x123456), NamedTextColor.LIGHT_PURPLE)) {
                MessageStyle.applyAccentColor(accent);
                for (Locale locale : List.of(Locale.US, Locale.GERMANY)) commands(locale);
            }
            reload();
            overrides();
            bundledCopies();
            literalsAndHelp();
        } finally {
            MessageStyle.applyAccentColor(previous);
        }
        System.out.println("CommandUsageHarness passed " + checks + " checks.");
    }

    private static void commands(Locale locale) throws Exception {
        var f = new ClanMenuFixture();
        var actor = f.player("Actor", true);
        actor.locale = locale;
        int[] saves = {0};
        int[] menus = {0};
        var friends =
                new FriendService(
                        new FriendRepository() {
                            public FriendSnapshot initialize() {
                                return FriendSnapshot.empty();
                            }

                            public void save(FriendSnapshot snapshot) {
                                saves[0]++;
                            }
                        },
                        FriendLimits.defaults(),
                        (sender, recipient) -> FriendRequestDecision.ALLOW,
                        Clock.systemUTC());
        var help = new CommandHelpRenderer(f.messages);
        var friend =
                new FriendCommand(
                        friends,
                        f.identity,
                        f.messages,
                        help,
                        () -> List.of(actor.player),
                        id -> id.equals(actor.id) ? actor.player : null,
                        ClanMenuFixture.logger(),
                        player -> menus[0]++);
        var clan =
                new ClanCommand(
                        f.clans,
                        f.identity,
                        f.messages,
                        help,
                        () -> List.of(actor.player),
                        id -> id.equals(actor.id) ? actor.player : null,
                        ClanMenuFixture.logger(),
                        player -> menus[0]++,
                        player -> menus[0]++);
        friend.onCommand(actor.player, null, "friends", new String[0]);
        check(menus[0] == 1 && actor.received.isEmpty(), "friend root still opens only the menu");
        friend.onCommand(actor.player, null, "friend", new String[] {"add"});
        check(actor.received.size() == 1, "missing friend target delivers exactly one packet");
        packet(actor.received.getFirst(), locale, "/friend add", "<player|uuid>", "friends");
        for (String action : List.of("add", "accept", "deny", "cancel", "remove")) {
            actor.received.clear();
            friend.onCommand(actor.player, null, "friend", new String[] {action, "one", "extra"});
            check(actor.received.size() == 1, "one concrete friend syntax packet: " + action);
            packet(
                    actor.received.getFirst(),
                    locale,
                    "/friend " + action,
                    "<player|uuid>",
                    "friends");
        }
        actor.received.clear();
        friend.onCommand(actor.player, null, "friend", new String[] {"accept"});
        check(
                actor.received.size() == 1 && !plain(actor.received.getFirst()).contains("Usage:"),
                "contextual incoming-request empty state is retained");
        check(
                friend.onTabComplete(actor.player, null, "friends", new String[] {""}).size() == 8,
                "friend completion still exposes exactly the eight existing roots");
        actor.received.clear();
        clan.onCommand(actor.player, null, "clans", new String[] {"create"});
        check(actor.received.size() == 2, "complex clan creation retains its useful example only");
        packet(actor.received.getFirst(), locale, "/clan create", "<tag> <name...>", "clan");
        check(
                clicks(actor.received.getLast())
                        .contains(ClickEvent.suggestCommand("/clan create BMW BMW Community")),
                "real-valued example keeps its editable complete suggestion");
        for (String action : List.of("invite", "kick", "transfer", "tag")) {
            actor.received.clear();
            clan.onCommand(actor.player, null, "clan", new String[] {action});
            check(
                    actor.received.size() == 1,
                    "simple clan missing argument emits one usage: " + action);
            packet(
                    actor.received.getFirst(),
                    locale,
                    "/clan " + action,
                    action.equals("tag") ? "<tag>" : "<player|uuid>",
                    "clan");
        }
        actor.received.clear();
        clan.onCommand(actor.player, null, "clan", new String[] {"info", "one", "extra"});
        packet(actor.received.getFirst(), locale, "/clan info", "[tag|uuid]", "clan");
        actor.received.clear();
        clan.onCommand(actor.player, null, "clan", new String[] {"disband"});
        check(
                actor.received.size() == 1
                        && clicks(actor.received.getFirst())
                                .contains(ClickEvent.suggestCommand("/clan disband confirm")),
                "disband still requires explicit confirmation");
        check(
                f.clans.getClanOf(actor.id).isEmpty() && saves[0] == 0,
                "invalid syntax and confirmation hints do not mutate social persistence");
        actor.permitted = false;
        actor.received.clear();
        friend.onCommand(actor.player, null, "friend", new String[] {"add"});
        check(
                actor.received.size() == 1
                        && hasTextColor(
                                actor.received.getFirst(),
                                locale.equals(Locale.GERMANY)
                                        ? "Du hast keine Berechtigung, diesen Befehl zu verwenden."
                                        : "You do not have permission to use this command.",
                                MessageStyle.ERROR),
                "real permission error remains red");
        check(
                !plain(actor.received.getFirst()).contains("/friend add")
                        && clicks(actor.received.getFirst()).isEmpty(),
                "permission boundary precedes usage and exposes no suggestion");
        check(
                friend.onTabComplete(actor.player, null, "friends", new String[] {""}).isEmpty(),
                "denied completion remains empty");
    }

    private static void packet(
            Component component, Locale locale, String command, String arguments, String channel) {
        String label = locale.equals(Locale.GERMANY) ? "Verwendung: " : "Usage: ";
        String prefix =
                channel.equals("friends")
                        ? (locale.equals(Locale.GERMANY) ? "Freunde" : "Friends")
                        : "Clan";
        check(
                plain(component)
                        .equals(
                                "["
                                        + prefix
                                        + "] "
                                        + label
                                        + command
                                        + (arguments.isEmpty() ? "" : " " + arguments)),
                "exact compact localized usage for " + command);
        check(
                hasSplitPrefix(
                        component,
                        prefix,
                        MessageStyle.BRACKET,
                        channel.equals("friends")
                                ? MessageStyle.FRIENDS_LABEL
                                : MessageStyle.CLAN_LABEL),
                "independent prefix roles");
        check(hasTextColor(component, label, MessageStyle.TEXT), "usage label is neutral");
        check(
                hasTextColor(component, command, MessageStyle.accent()),
                "complete root and subcommand use current accent");
        if (!arguments.isEmpty())
            check(
                    hasTextColor(
                            component,
                            arguments,
                            arguments.startsWith("[") ? MessageStyle.MUTED : MessageStyle.TEXT),
                    "required/optional syntax role");
        check(
                clicks(component)
                        .equals(
                                List.of(
                                        ClickEvent.suggestCommand(
                                                command + (arguments.isEmpty() ? "" : " ")))),
                "usage only suggests executable tokens and never inserts placeholders or runs commands");
        check(
                hover(
                        component,
                        locale.equals(Locale.GERMANY)
                                ? "Klicke, um diesen Command"
                                : "Click to insert this command"),
                "existing localized command-insertion hover retained");
    }

    private static void reload() throws Exception {
        try (var fixture = new PresentationFixture()) {
            var file = fixture.directory.resolve("config.yml");
            Files.writeString(file, "messages:\n  accent-color: '#123456'\n");
            fixture.config.prepareReload().apply();
            Component original = MessageStyle.usage("/friend add", "<player> [page]");
            Files.writeString(file, "messages:\n  accent-color: '#abcdef'\n");
            var plan = fixture.config.prepareReload();
            check(
                    hasTextColor(
                            MessageStyle.usageSyntax("/clan create", "<tag>"),
                            "/clan create",
                            TextColor.color(0x123456)),
                    "config prepare does not publish a new usage accent");
            plan.apply();
            check(
                    hasTextColor(
                            MessageStyle.usageSyntax("/clan create", "<tag>"),
                            "/clan create",
                            TextColor.color(0xabcdef)),
                    "new usage reads the applied accent, not a static copy");
            check(
                    hasTextColor(original, "/friend add", TextColor.color(0x123456)),
                    "already built immutable output retains its accent");
            plan.rollback();
            check(
                    hasTextColor(
                            MessageStyle.usageSyntax("/friend add", "<player>"),
                            "/friend add",
                            TextColor.color(0x123456)),
                    "real config rollback restores new usage output");
            ReloadParticipant later =
                    new ReloadParticipant() {
                        public String getReloadName() {
                            return "ControlledFailure";
                        }

                        public ReloadPlan prepareReload() {
                            return ReloadPlan.of(
                                    () -> {
                                        throw new IllegalStateException("controlled");
                                    },
                                    () -> {});
                        }
                    };
            var result = new ReloadService(fixture.logger, List.of(fixture.config, later)).reload();
            check(
                    result.status() == ReloadResult.Status.APPLY_FAILED
                            && hasTextColor(
                                    MessageStyle.usageSyntax("/friend add", "<player>"),
                                    "/friend add",
                                    TextColor.color(0x123456)),
                    "actual coordinator restores usage colors after a later participant fails");
        }
    }

    private static void overrides() throws Exception {
        var directory = Files.createTempDirectory("vapeecore-usage-overrides-");
        var file = directory.resolve("en.yml");
        String source =
                "common.usage: 'Syntax please: '\nprofile.usage: '<gold>My /profile override</gold>'\n";
        try {
            Files.writeString(file, source);
            var localization = LocalizationService.bundled(Logger.getAnonymousLogger());
            var before = localization.snapshot();
            localization.apply(localization.prepare(directory));
            var messages = new MessageService(() -> "", localization);
            var f = new ClanMenuFixture();
            var player = f.player("Viewer", true);
            Component usage =
                    messages.localize(player.player, MessageStyle.usage("/friend add", "<player>"));
            check(
                    plain(usage).equals("Syntax please: /friend add <player>"),
                    "operator common.usage wording remains authoritative");
            check(
                    hasTextColor(usage, "Syntax please: ", MessageStyle.TEXT),
                    "plain usage override inherits neutral role");
            Component legacy =
                    messages.localize(
                            player.player,
                            messages.usage(
                                    player.player, "profile.usage", "/profile", "[player|uuid]"));
            check(
                    plain(legacy).equals("My /profile override")
                            && hasTextColor(legacy, "My /profile override", NamedTextColor.GOLD),
                    "complete legacy operator usage retains its wording and explicit color");
            player.locale = Locale.GERMANY;
            Component german =
                    messages.localize(
                            player.player,
                            messages.usage(
                                    player.player, "profile.usage", "/profile", "[player|uuid]"));
            check(
                    plain(german).equals("Verwendung: /profile [player|uuid]"),
                    "absent German override uses localized shared usage");
            localization.apply(before);
            check(
                    plain(
                                    messages.localize(
                                            player.player,
                                            MessageStyle.usage("/friend add", "<player>")))
                            .equals("Verwendung: /friend add <player>"),
                    "localization snapshot restoration keeps shared usage renderable");
            check(
                    Files.readString(file).equals(source),
                    "loading and rendering never rewrite operator catalogs");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void bundledCopies() throws Exception {
        var directory = Files.createTempDirectory("vapeecore-usage-copied-defaults-");
        var localization = LocalizationService.bundled(Logger.getAnonymousLogger());
        var f = new ClanMenuFixture();
        var player = f.player("Viewer", true);
        var messages = new MessageService(() -> "", localization);
        String[][] cases = {
            {"profile.usage", "/profile", "[player|uuid]"},
            {"core.quest.usage", "/core quest force", "[player]"},
            {"daily-quests.command-usage", "/quests", ""},
            {"utility.build.usage", "/build", ""},
            {"staff.usage", "/staff", "[list]"},
            {"tpa.usage-send", "/tpa", "<player>"},
            {"tpa.usage-answer", "/tpaccept", "[player]"},
            {"tpa.usage-cancel", "/tpcancel", ""}
        };
        try {
            for (String language : List.of("en", "de")) {
                try (var resource =
                        CommandUsageHarness.class.getResourceAsStream(
                                "/lang/" + language + ".yml")) {
                    Files.write(directory.resolve(language + ".yml"), resource.readAllBytes());
                }
            }
            localization.apply(localization.prepare(directory));
            for (Locale locale : List.of(Locale.US, Locale.GERMANY)) {
                player.locale = locale;
                var file = directory.resolve(locale.equals(Locale.GERMANY) ? "de.yml" : "en.yml");
                byte[] before = Files.readAllBytes(file);
                for (String[] row : cases) {
                    Component actual =
                            messages.localize(
                                    player.player,
                                    messages.usage(player.player, row[0], row[1], row[2]));
                    Component expected =
                            messages.localize(player.player, MessageStyle.usage(row[1], row[2]));
                    check(
                            actual.equals(expected),
                            "startup-copied declared default uses shared usage: "
                                    + locale
                                    + " "
                                    + row[0]);
                }
                check(
                        java.util.Arrays.equals(before, Files.readAllBytes(file)),
                        "copied defaults are rendered without rewriting the catalog");
            }
        } finally {
            Files.deleteIfExists(directory.resolve("en.yml"));
            Files.deleteIfExists(directory.resolve("de.yml"));
            Files.deleteIfExists(directory);
        }
    }

    private static void literalsAndHelp() throws Exception {
        var f = new ClanMenuFixture();
        var actor = f.player("Viewer", true);
        Component mixed =
                f.messages.localize(
                        actor.player,
                        MessageStyle.usage("/example action", "<required> [optional value]"));
        check(
                hasTextColor(mixed, "<required>", MessageStyle.TEXT)
                        && hasTextColor(mixed, "[optional value]", MessageStyle.MUTED),
                "mixed argument roles work independently");
        String attack = "<click:run_command:'/op Viewer'>literal</click>";
        Component literal =
                f.messages.localize(actor.player, MessageStyle.usage("/example", attack));
        check(
                plain(literal).endsWith(attack)
                        && clicks(literal).equals(List.of(ClickEvent.suggestCommand("/example "))),
                "syntax is literal Component text and cannot introduce a RUN_COMMAND");
        Component noArguments =
                f.messages.localize(actor.player, MessageStyle.usage("/example", ""));
        check(
                plain(noArguments).equals("Usage: /example")
                        && clicks(noArguments)
                                .equals(List.of(ClickEvent.suggestCommand("/example"))),
                "no-argument usage has no extra whitespace");
        for (String bad : List.of("/friend <player>", "/clan [tag]", "/op\nViewer")) {
            try {
                MessageStyle.usage(bad, "");
                throw new AssertionError("placeholder/control command accepted");
            } catch (IllegalArgumentException expected) {
                check(true, "invalid executable syntax cannot reach suggestion");
            }
        }
        var page =
                new CommandHelpPage(
                        "Title",
                        List.of(
                                new CommandHelpSection(
                                        "Actions",
                                        List.of(
                                                new CommandHelpEntry(
                                                        "/friend add <player>", "Description")))));
        Component help = new CommandHelpRenderer(f.messages).render(actor.player, page);
        check(
                plain(help).equals("Title\nActions\n/friend add <player> — Description"),
                "existing help page layout is unchanged");
        check(
                hasTextColor(help, "/friend", MessageStyle.accent())
                        && hasTextColor(help, " add <player>", MessageStyle.TEXT),
                "help still accents only its root while usage accents the complete executable prefix");
        check(
                clicks(help).equals(List.of(ClickEvent.suggestCommand("/friend add <player>"))),
                "existing help suggestion contract is unchanged");
    }

    private static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    private static List<ClickEvent> clicks(Component component) {
        var result = new ArrayList<ClickEvent>();
        if (component.clickEvent() != null) result.add(component.clickEvent());
        component.children().forEach(child -> result.addAll(clicks(child)));
        return result;
    }

    private static boolean hover(Component component, String text) {
        if (component.hoverEvent() != null
                && component.hoverEvent().value() instanceof Component value
                && plain(value).contains(text)) return true;
        return component.children().stream().anyMatch(child -> hover(child, text));
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
