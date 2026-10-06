package dev.vapee.core.presentation.nametag;

import dev.vapee.core.presentation.PresentationFixture;
import dev.vapee.core.presentation.PresentationService;
import dev.vapee.core.presentation.tablist.TablistService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/** Real RankService -> renderer -> native team writes, with Paper's RESET read failure. */
public final class NametagColorHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        try (var f = new PresentationFixture()) {
            var player = f.player("_ImVentex_");
            f.groups.put(player.id, "custom_group");
            f.groupColors.put("custom_group", "blue");
            f.prefixes.put(player.id, "&c[ADMIN] ");
            Files.writeString(f.directory.resolve("presentation.yml"),
                    "scoreboard:\n  enabled: false\nnametag:\n  lobby-only: false\n");
            f.presentationConfig.applyState(f.presentationConfig.prepareReloadState());
            List<LogRecord> logs = new ArrayList<>();
            f.logger.addHandler(new Handler() {
                public void publish(LogRecord record) { logs.add(record); }
                public void flush() { }
                public void close() { }
            });
            AtomicReference<Optional<String>> clan = new AtomicReference<>(Optional.empty());
            var renderer = f.renderer(ignored -> clan.get());
            var boards = f.scoreboards();
            boards.updatePlayer(player.player, Component.empty(), List.of(), true);
            var board = f.boards.stream().filter(b -> b.board == player.board).findFirst().orElseThrow();
            var probe = board.board.registerNewTeam("reset-probe");
            check(!probe.hasColor(), "fresh Paper team has no usable initial color");
            try { probe.color(); throw new AssertionError("fixture failed to model RESET"); }
            catch (IllegalStateException expected) {
                check(expected.getMessage().equals("Team colors must have hex values"), "Paper RESET read throws");
            }
            probe.unregister();
            var service = new PresentationService(f.plugin, f.presentationConfig, renderer, boards,
                    new TablistService(f.presentationConfig));
            service.updateAll();
            var owned = board.teams.get(NametagService.teamName(player.id));
            for (LogRecord log : logs) {
                if (log.getThrown() != null) {
                    throw new AssertionError("fresh-team update must not read unusable RESET color", log.getThrown());
                }
            }
            check(owned != null && owned.color == NamedTextColor.BLUE
                    && owned.entries.equals(Set.of("_ImVentex_")) && owned.prefix.equals(Component.empty())
                    && owned.suffix.equals(Component.empty()) && logs.stream().noneMatch(l -> l.getThrown() != null),
                    "fresh team safely writes RankInfo BLUE with complete membership and no clan wrapper");
            check(plain(player.name).equals("_ImVentex_") && containsColor(player.name, NamedTextColor.BLUE),
                    "default tablist is rank colored with no implicit ADMIN label");
            int unchangedWrites = owned.mutations;
            service.updateAll();
            check(owned.mutations == unchangedWrites, "unchanged color and components cause no writes");
            f.groupColors.put("custom_group", "red"); service.updateAll();
            check(board.teams.get(NametagService.teamName(player.id)) == owned && !owned.removed
                    && owned.color == NamedTextColor.RED && containsColor(player.name, NamedTextColor.RED),
                    "same primary group dynamic BLUE to RED updates the same owned team and tablist");
            String literal = "<red>ABC&c";
            clan.set(Optional.of(literal)); service.updateAll();
            check(owned.color == NamedTextColor.RED && owned.entries.equals(Set.of("_ImVentex_"))
                    && plain(owned.suffix).equals(" [" + literal + "]")
                    && plain(player.name).equals("_ImVentex_ [" + literal + "]")
                    && !containsColor(owned.suffix, NamedTextColor.RED), "clan display stays literal beside rank colored name");
            clan.set(Optional.empty()); service.updateAll();
            check(owned.suffix.equals(Component.empty()) && plain(player.name).equals("_ImVentex_"),
                    "clan leave removes wrapper without losing rank color");
            // Hex syntax with exactly the named RGB value must work, regardless of TextColor subtype.
            f.groupColors.put("custom_group", "#5555ff"); service.updateAll();
            check(owned.color == NamedTextColor.BLUE, "exact named RGB hex is represented without approximation");
            for (NamedTextColor named : NamedTextColor.NAMES.values()) {
                f.groupColors.put("custom_group", named.asHexString()); service.updateAll();
                check(owned.color == named, "all sixteen named values use exact native colors: " + named);
            }
            f.groupColors.put("custom_group", "#123456"); service.updateAll(); service.updateAll();
            check(owned.color == NamedTextColor.WHITE && renderer.render(player.player).nametagColor() == NamedTextColor.WHITE
                    && containsColor(player.name, TextColor.fromHexString("#123456")),
                    "unrepresentable RGB uses explicit white fallback while tablist keeps RGB");
            check(logs.stream().filter(l -> l.getMessage().contains("#123456")
                    && l.getMessage().contains("using white")).count() == 1,
                    "unsupported RGB fallback warns once per distinct color");
            f.groupColors.put("custom_group", "blue"); service.updateAll();
            check(owned.color == NamedTextColor.BLUE, "RGB fallback returns to exact configured native color");
            f.groups.remove(player.id); service.updateAll();
            check(owned.color == NamedTextColor.WHITE && plain(player.name).equals("_ImVentex_"),
                    "missing rank uses neutral team color with no label");
            service.removeAll();
            check(owned.removed && board.teams.isEmpty(), "color writes are tracked as owned state for cleanup");
        }
        for (boolean refresh : List.of(false, true)) for (boolean reset : List.of(false, true)) {
            try (var f = new PresentationFixture()) {
                var player = f.player("Player"); f.groups.put(player.id, "custom_group");
                f.groupColors.put("custom_group", "blue");
                Files.writeString(f.directory.resolve("presentation.yml"),
                        "scoreboard:\n  enabled: false\nnametag:\n  lobby-only: false\n");
                f.presentationConfig.applyState(f.presentationConfig.prepareReloadState());
                var boards = f.scoreboards();
                var service = new PresentationService(f.plugin, f.presentationConfig, f.renderer(id -> Optional.empty()),
                        boards, new TablistService(f.presentationConfig));
                service.updateAll();
                var board = f.boards.stream().filter(b -> b.board == player.board).findFirst().orElseThrow();
                var owned = board.teams.get(NametagService.teamName(player.id));
                owned.team.color(reset ? null : NamedTextColor.GREEN);
                int foreignWrites = owned.mutations;
                f.groupColors.put("custom_group", "red");
                if (refresh) { service.updateAll(); service.updateAll(); }
                service.removeAll(); service.removeAll();
                check(!owned.removed && owned.mutations == foreignWrites && owned.entries.equals(Set.of("Player"))
                        && owned.color == (reset ? null : NamedTextColor.GREEN),
                        "foreign color or RESET survives refresh=" + refresh + " reset=" + reset + " and cleanup");
            }
        }
        System.out.println("NametagColorHarness passed " + checks + " checks.");
    }

    private static String plain(Component value) { return PlainTextComponentSerializer.plainText().serialize(value); }
    private static boolean containsColor(Component value, TextColor color) {
        return color.equals(value.color()) || value.children().stream().anyMatch(child -> containsColor(child, color));
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
