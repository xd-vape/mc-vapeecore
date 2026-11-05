package dev.vapee.core.staff;

import static dev.vapee.core.staff.StaffPanelTestSupport.*;

import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.message.MessageStyle;
import dev.vapee.core.ui.HeadTestSupport;
import dev.vapee.core.ui.UiItemSpec;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import org.bukkit.Material;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;

/** Executable removal, authorization and old-catalog compatibility contracts. */
public final class StaffRosterRemovalHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        routes();
        removedControl();
        catalogs();
        System.out.println("StaffRosterRemovalHarness passed " + checks + " checks.");
    }

    private static void routes() {
        for (Locale locale : List.of(Locale.US, Locale.GERMANY)) {
            for (boolean node : List.of(false, true)) {
                for (boolean group : List.of(false, true)) {
                    Fixture f = new Fixture();
                    Probe actor =
                            f.player(
                                    "Viewer",
                                    group ? "admin" : "default",
                                    node ? StaffPanel.PERMISSION : "unrelated.permission");
                    actor.locale = locale;
                    Probe secret = f.player("UndisclosedOwner", "owner");
                    var ranks = java.util.Map.copyOf(f.ranks);
                    var groups = java.util.Map.copyOf(f.groups);
                    var permissions = java.util.Set.copyOf(actor.permissions);
                    // Invalid arguments must not even enumerate the private roster or resolve
                    // ranks.
                    StaffPanel guarded =
                            new StaffPanel(
                                    f.hierarchy,
                                    id -> {
                                        throw new AssertionError(
                                                "Invalid arguments resolved a rank");
                                    },
                                    f.messages,
                                    () -> {
                                        throw new AssertionError(
                                                "Invalid arguments enumerated players");
                                    },
                                    id -> f.players.get(id).player,
                                    (holder, size, title) -> {
                                        throw new AssertionError("Invalid arguments opened UI");
                                    },
                                    HeadTestSupport::item,
                                    f.profiles.heads::apply,
                                    f.queued::add,
                                    () -> f.runtime,
                                    (p, command) -> f.executed.add(command),
                                    f.logger);
                    for (String[] arguments :
                            List.of(
                                    new String[] {"list"},
                                    new String[] {"LIST"},
                                    new String[] {"unknown"},
                                    new String[] {"list", "extra"})) {
                        actor.messages.clear();
                        check(
                                guarded.onCommand(actor.player, null, "staff", arguments),
                                "retired/unknown route handled");
                        check(
                                actor.messages.size() == 1
                                        && plain(actor.messages.getFirst()).equals(usage(locale)),
                                "exact one-line localized usage, no roster disclosure");
                        check(
                                actor.opens == 0 && f.executed.isEmpty() && f.queued.isEmpty(),
                                "invalid route has no UI/action side effects");
                        check(
                                guarded.onTabComplete(actor.player, null, "staff", arguments)
                                        .isEmpty(),
                                "no retired subcommand completion");
                    }
                    guarded.disable();
                    actor.messages.clear();
                    f.open(actor);
                    if (node && group) {
                        check(
                                actor.opens == 1
                                        && actor.holder()
                                                .content()
                                                .targets()
                                                .containsValue(secret.player),
                                "both current capabilities open the internal staff view");
                    } else {
                        check(
                                actor.opens == 0
                                        && actor.messages.size() == 1
                                        && plain(actor.messages.getFirst())
                                                .equals(
                                                        locale.equals(Locale.GERMANY)
                                                                ? "[Team] Du hast keine Berechtigung, diesen Befehl zu verwenden."
                                                                : "[Staff] You do not have permission to use this command."),
                                "either missing capability denies through localized channel");
                    }
                    check(
                            f.ranks.equals(ranks)
                                    && f.groups.equals(groups)
                                    && actor.permissions.equals(permissions)
                                    && f.players.values().stream()
                                            .allMatch(p -> p.appearanceWrites == 0),
                            "routing never changes authority or player appearance");
                    f.panel.disable();
                }
            }
        }
    }

    private static void removedControl() {
        Fixture f = new Fixture();
        Probe actor = f.admin(), target = f.player("StaffTarget", "builder");
        f.open(actor);
        check(
                actor.view.getTopInventory().getItem(22) == null
                        && !actor.holder().content().actions().containsKey(22),
                "roster status slot is vacant and unbound");
        actor.view
                .getTopInventory()
                .setItem(
                        22,
                        HeadTestSupport.item(
                                new UiItemSpec(
                                        Material.PLAYER_HEAD,
                                        Component.text("HIDDEN"),
                                        List.of(Component.text("Forged old toggle")))));
        check(
                f.click(actor, 22).isCancelled() && f.queued.isEmpty(),
                "injected retired item has no authority");
        for (var entry : actor.holder().content().targets().entrySet()) {
            var head = actor.view.getTopInventory().getItem(entry.getKey());
            check(
                    HeadTestSupport.owner(head).equals(entry.getValue().getUniqueId()),
                    "private head retains authoritative identity");
            check(
                    head.getItemMeta().displayName().color().equals(NamedTextColor.GOLD),
                    "private head retains rank color");
            check(
                    head.getItemMeta().lore().stream()
                            .map(StaffRosterRemovalHarness::plain)
                            .noneMatch(
                                    line ->
                                            line.contains("Publicly")
                                                    || line.contains("Hidden")
                                                    || line.contains("PUBLIC")
                                                    || line.contains("HIDDEN")),
                    "private head contains no public status lore");
        }
        f.select(actor, target);
        check(
                actor.view.getTopInventory().getItem(22) == null
                        && !actor.holder().content().actions().containsKey(22),
                "target view has no status control");
        check(
                actor.holder()
                        .content()
                        .actions()
                        .keySet()
                        .containsAll(List.of(10, 11, 12, 14, 15, 18, 26)),
                "all authorized target actions and navigation remain");
        f.panel.disable();
    }

    private static void catalogs() throws Exception {
        var directory = Files.createTempDirectory("vapeecore-staff-removal-");
        Fixture f = new Fixture();
        Probe actor = f.player("Viewer", "default");
        LocalizationService localization = f.messages.localization();
        var bundled = localization.snapshot();
        try {
            for (String code : List.of("en", "de")) {
                String current;
                try (var stream =
                        StaffRosterRemovalHarness.class.getResourceAsStream(
                                "/lang/" + code + ".yml")) {
                    current = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                }
                String modern = code.equals("de") ? "Verwendung: /staff" : "Usage: /staff";
                String old =
                        code.equals("de")
                                ? "Mit /staff öffnest du das Panel, mit /staff list die öffentliche Teamliste."
                                : "Use /staff to open the panel or /staff list for the public team list.";
                check(
                        current.contains("  usage: \"" + modern + "\""),
                        "declared staff usage found");
                String copied =
                        current.replace("  usage: \"" + modern + "\"", "  usage: \"" + old + "\"");
                var file = directory.resolve(code + ".yml");
                Files.writeString(file, copied);
                var prepared = localization.prepare(directory);
                check(
                        localization.snapshot() == bundled,
                        "prepare does not publish language changes");
                localization.apply(prepared);
                actor.locale = code.equals("de") ? Locale.GERMANY : Locale.US;
                f.list(actor);
                check(
                        actor.messages.size() == 1
                                && plain(actor.messages.getFirst()).equals(usage(actor.locale)),
                        "full copied old catalog renders new usage without old roster wording");
                Component rendered = actor.messages.getFirst();
                check(
                        hasColor(rendered, "[", MessageStyle.BRACKET)
                                && hasColor(
                                        rendered,
                                        code.equals("de") ? "Team" : "Staff",
                                        MessageStyle.STAFF_LABEL),
                        "staff prefix brackets and localized identity retain separate colors");
                check(hasSuggest(rendered), "new usage suggests /staff and never runs a command");
                check(
                        Files.readString(file).equals(copied),
                        "old complete operator catalog remains byte-for-byte intact");
                localization.apply(bundled);
                String custom =
                        "staff.usage: '<gold>My /staff list documentation</gold>'\n"
                                + "staff.roster: 'Private override: <players>'\n"
                                + "staff.status-hidden: 'My hidden status'\n";
                Files.writeString(file, custom);
                localization.apply(localization.prepare(directory));
                f.list(actor);
                check(
                        actor.messages.size() == 1
                                && plain(actor.messages.getFirst())
                                        .endsWith("My /staff list documentation")
                                && hasColor(
                                        actor.messages.getFirst(),
                                        "My /staff list documentation",
                                        NamedTextColor.GOLD),
                        "deliberate legacy usage retains complete wording/color but cannot restore enumeration");
                check(
                        actor.opens == 0 && f.executed.isEmpty() && f.queued.isEmpty(),
                        "legacy roster/status overrides cannot restore removed actions");
                check(Files.readString(file).equals(custom), "custom override is never rewritten");
                localization.apply(bundled);
                f.list(actor);
                check(
                        plain(actor.messages.getFirst()).equals(usage(actor.locale)),
                        "snapshot rollback restores standard localized usage");
            }
        } finally {
            f.panel.disable();
            Files.deleteIfExists(directory.resolve("en.yml"));
            Files.deleteIfExists(directory.resolve("de.yml"));
            Files.deleteIfExists(directory);
        }
    }

    private static boolean hasSuggest(Component c) {
        if (c.clickEvent() != null) {
            check(
                    c.clickEvent().action() == ClickEvent.Action.SUGGEST_COMMAND,
                    "usage cannot execute commands");
            return c.clickEvent().value().equals("/staff");
        }
        return c.children().stream().anyMatch(StaffRosterRemovalHarness::hasSuggest);
    }

    private static boolean hasColor(
            Component c, String text, net.kyori.adventure.text.format.TextColor color) {
        var content = new StringBuilder();
        var colors = new java.util.ArrayList<net.kyori.adventure.text.format.TextColor>();
        collect(c, null, content, colors);
        int offset = content.indexOf(text);
        if (offset < 0) return false;
        for (int i = offset; i < offset + text.length(); i++)
            if (!color.equals(colors.get(i))) return false;
        return true;
    }

    private static void collect(
            Component c,
            net.kyori.adventure.text.format.TextColor inherited,
            StringBuilder content,
            List<net.kyori.adventure.text.format.TextColor> colors) {
        var color = c.color() == null ? inherited : c.color();
        if (c instanceof net.kyori.adventure.text.TextComponent text) {
            content.append(text.content());
            for (int i = 0; i < text.content().length(); i++) colors.add(color);
        }
        for (var child : c.children()) collect(child, color, content, colors);
    }

    private static String usage(Locale locale) {
        return locale.equals(Locale.GERMANY)
                ? "[Team] Verwendung: /staff"
                : "[Staff] Usage: /staff";
    }

    private static String plain(Component c) {
        return PlainTextComponentSerializer.plainText().serialize(c);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
