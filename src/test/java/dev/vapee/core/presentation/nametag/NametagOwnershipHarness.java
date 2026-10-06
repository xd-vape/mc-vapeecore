package dev.vapee.core.presentation.nametag;

import dev.vapee.core.presentation.PresentationFixture;
import dev.vapee.core.presentation.PresentationRenderer;
import dev.vapee.core.presentation.PresentationService;
import dev.vapee.core.presentation.config.PresentationConfig;
import dev.vapee.core.presentation.scoreboard.ScoreboardService;
import dev.vapee.core.presentation.tablist.TablistService;
import dev.vapee.core.visibility.VisibilityPolicy;
import dev.vapee.core.visibility.VisibilityService;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.Team;

import java.nio.file.Files;
import java.util.List;
import java.util.Optional;

/** Real shared-board and team services; only Paper's external surfaces are fixtures. */
public final class NametagOwnershipHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (boolean sidebar : List.of(false, true)) for (boolean nametag : List.of(false, true)) {
            try (Rig r = new Rig()) {
                var a = r.f.player("A"); var b = r.f.player("B");
                r.configure(sidebar, nametag); r.service.updateAll();
                check(r.boards.ownsScoreboard(a.player) == (sidebar || nametag), "four-feature board ownership");
                check(r.boards.ownsScoreboard(b.player) == (sidebar || nametag), "second viewer feature ownership");
                if (sidebar || nametag) {
                    var board = r.board(a);
                    check(board.objectives.containsKey("vapeecore") == sidebar, "sidebar objective independent");
                    check(board.teams.size() == (nametag ? 2 : 0), "nametag roster independent of sidebar");
                    if (nametag) check(board.teams.values().stream().allMatch(t -> t.team.hasColor()
                            && t.color == net.kyori.adventure.text.format.NamedTextColor.WHITE && t.entries.size() == 1),
                            "Paper RESET default becomes a neutral colored complete native membership");
                    check(a.board != b.board && a.board != r.f.main.board, "teams reside on independent viewer boards");
                    check(r.f.main.teams.isEmpty() && r.f.main.writes == 0, "main scoreboard never rendered");
                }
                r.service.removeAll(); r.service.removeAll();
                check(a.board == r.f.main.board && b.board == r.f.main.board, "both-feature cleanup releases exact owned board");
                check(r.f.boards.stream().allMatch(board -> board.teams.isEmpty() && board.objectives.isEmpty()),
                        "all owned features cleaned idempotently");
            }
        }
        try (Rig r = new Rig()) {
            var a = r.f.player("A"); var b = r.f.player("B"); var c = r.f.player("C"); var d = r.f.player("D");
            r.configure(true, true); r.service.updateAll();
            var boardA = r.board(a); var boardB = r.board(b);
            var aToB = boardA.teams.get(team(b)); var bToA = boardB.teams.get(team(a));
            check(aToB != bToA && aToB.team.getScoreboard() == a.board && bToA.team.getScoreboard() == b.board,
                    "directional team handles belong only to their viewer boards");
            check(boardA.teams.size() == 4 && boardB.teams.size() == 4, "multiple eligible targets per viewer");
            int teamWrites = writes(r.f), objectiveWrites = boardA.writes;
            r.service.updateAll(); r.service.updateAll();
            check(writes(r.f) == teamWrites && boardA.writes == objectiveWrites, "unchanged refresh performs no team/sidebar writes");
            check(r.f.boards.stream().flatMap(board -> board.teams.values().stream()).allMatch(t -> t.optionWrites == 0
                            && t.color == net.kyori.adventure.text.format.NamedTextColor.WHITE),
                    "no gameplay option changes and neutral owned color");
            r.boards.updatePlayer(a.player, Component.text("changed"), List.of(Component.text("one line")), true);
            check(a.board == boardA.board && boardA.teams.get(team(b)) == aToB && boardA.objectives.get("vapeecore").lines.size() == 1,
                    "sidebar line-count change preserves active nametag board/team identity");
            r.service.updateAll();
            check(a.board == boardA.board && boardA.teams.get(team(b)) == aToB, "sidebar line-count restoration retains nametag ownership");
            r.f.settings.setScoreboardEnabled(a.id, false); r.service.updatePlayer(a.player);
            check(a.board == boardA.board && boardA.objectives.isEmpty() && boardA.teams.get(team(b)) == aToB,
                    "sidebar preference removes only objective while retaining nametags");
            r.configure(false, true); r.service.updateAll();
            check(a.board == boardA.board && b.board == boardB.board && boardB.teams.size() == 4,
                    "sidebar global disable retains owned boards and teams");
            r.configure(true, false); r.service.updateAll();
            check(a.board == r.f.main.board && b.board == boardB.board && boardB.teams.isEmpty()
                    && boardB.objectives.containsKey("vapeecore"), "nametag disable preserves eligible sidebar only");
            r.configure(true, true); r.f.settings.setScoreboardEnabled(a.id, true); r.service.updateAll();
            var nextA = r.board(a);
            var policy = new VisibilityPolicy(id -> r.f.settings.getSettings(id).orElseThrow().getVisibility(),
                    (v, t) -> false, (v, t) -> false, (v, t) -> false);
            var visibility = new VisibilityService(r.f.plugin, () -> r.f.online.stream().map(p -> p.player).toList(),
                    w -> w == r.f.world, policy, r.f.logger);
            r.f.settings.setLobbyPlayersVisible(a.id, false); visibility.applyViewerPreference(a.player);
            int hides = a.visibilityWrites;
            r.service.updateAll();
            check(!nextA.teams.containsKey(team(b)) && boardB.teams.containsKey(team(a)), "asymmetric visibility roster");
            check(a.visibilityWrites == hides && b.visibilityWrites == 0, "presentation performs no hide/show writes");
            r.f.settings.setLobbyPlayersVisible(a.id, true); visibility.applyViewerPreference(a.player); r.service.updateAll();
            check(nextA.teams.containsKey(team(b)), "visibility restore recreates own relation on normal refresh");
            r.service.removePlayer(c.player); c.online = false;
            check(r.f.boards.stream().noneMatch(board -> board.teams.containsKey(team(c))), "target quit removes every own relation");
            r.service.updateAll();
            check(nextA.teams.size() == 3, "offline target not reasserted");
            var joinedHidden = r.f.player("JoinedHidden"); a.hidden.add(joinedHidden.id); r.service.updatePlayer(a.player);
            check(!nextA.teams.containsKey(team(joinedHidden)), "join after hidden relation exists remains hidden");
            r.f.players.unloadPlayer(d.id); r.service.updateAll();
            check(r.f.boards.stream().noneMatch(board -> board.teams.containsKey(team(d))), "unloaded target is not eligible");
            r.service.removeAll();
        }
        foreignBoards();
        foreignTeams();
        System.out.println("NametagOwnershipHarness passed " + checks + " checks.");
    }

    private static void foreignBoards() throws Exception {
        try (Rig r = new Rig()) {
            var a = r.f.player("A"); var b = r.f.player("B");
            var foreign = new PresentationFixture.Board();
            foreign.board.registerNewObjective("vapeecore", Criteria.DUMMY, Component.text("foreign"));
            Team foreignTeam = foreign.board.registerNewTeam(team(b)); foreignTeam.prefix(Component.text("foreign"));
            foreignTeam.addEntry("B"); int writes = foreign.teams.get(team(b)).mutations, objWrites = foreign.writes;
            a.board = foreign.board; r.configure(true, true); r.service.updateAll();
            check(!r.boards.ownsScoreboard(a.player) && a.board == foreign.board, "foreign board before acquisition is skipped");
            check(foreign.teams.size() == 1 && foreign.teams.get(team(b)).mutations == writes && foreign.writes == objWrites,
                    "foreign same-name team/objective untouched");
            a.board = r.f.main.board; r.service.updateAll(); var owned = r.board(a);
            check(r.boards.ownsScoreboard(a.player) && owned.teams.containsKey(team(b)), "safe main-board return reacquires viewer board");
            a.board = foreign.board; a.hidden.add(b.id); r.service.updateAll();
            check(a.board == foreign.board && owned.teams.isEmpty() && owned.objectives.isEmpty(), "foreign replacement suspends and cleans only captured owned board");
            r.service.removeAll(); r.service.removeAll();
            check(foreign.teams.get(team(b)).mutations == writes && foreign.writes == objWrites && a.board == foreign.board,
                    "removeAll with foreign current never resets or mutates it");
        }
    }

    private static void foreignTeams() throws Exception {
        try (Rig r = new Rig()) {
            var a = r.f.player("A"); var b = r.f.player("B"); r.configure(false, true); r.service.updateAll();
            var board = r.board(a); board.teams.get(team(b)).team.unregister();
            Team recreated = board.board.registerNewTeam(team(b)); recreated.addEntry("B");
            int writes = board.teams.get(team(b)).mutations;
            r.service.removePlayer(b.player); r.service.removeAll();
            check(board.board.getTeam(team(b)) == recreated && board.teams.get(team(b)).mutations == writes,
                    "direct cleanup preserves recreated same-name foreign team without intervening refresh");
        }
        try (Rig r = new Rig()) {
            var a = r.f.player("A"); var b = r.f.player("B"); r.configure(false, true);
            r.boards.updatePlayer(a.player, Component.empty(), List.of(), true); var board = r.board(a);
            Team foreign = board.board.registerNewTeam("foreign"); foreign.addEntry("B");
            int writes = board.teams.get("foreign").mutations; r.service.updateAll(); r.service.removeAll();
            check(foreign.hasEntry("B") && board.teams.get("foreign").mutations == writes
                    && !board.teams.containsKey(team(b)), "preexisting foreign entry membership is never stolen");
        }
        for (String takeover : List.of("name", "entry", "prefix", "suffix", "extra-entry", "color")) {
            try (Rig r = new Rig()) {
                var a = r.f.player("A"); var b = r.f.player("B");
                r.configure(false, true); r.service.updateAll(); var board = r.board(a);
                var original = board.teams.get(team(b));
                PresentationFixture.Board.TestTeam preserved = original;
                switch (takeover) {
                    case "name" -> {
                        original.team.unregister();
                        board.board.registerNewTeam(team(b)); preserved = board.teams.get(team(b));
                        preserved.team.addEntry("B");
                    }
                    case "entry" -> { board.board.registerNewTeam("foreign"); preserved = board.teams.get("foreign"); preserved.team.addEntry("B"); }
                    case "prefix" -> original.team.prefix(Component.text("foreign prefix"));
                    case "suffix" -> original.team.suffix(Component.text("foreign suffix"));
                    case "extra-entry" -> original.team.addEntry("foreign entry");
                    case "color" -> original.team.color(net.kyori.adventure.text.format.NamedTextColor.RED);
                    default -> throw new AssertionError(takeover);
                }
                int writes = preserved.mutations;
                r.service.updateAll(); r.service.updateAll(); r.service.removeAll(); r.service.removeAll();
                check(!preserved.removed && preserved.mutations == writes, "foreign " + takeover + " takeover survives refresh/cleanup");
                check(board.teams.containsValue(preserved), "recreated or modified foreign team retained by identity");
                check(r.f.main.teams.isEmpty(), "foreign cleanup never touches main scoreboard");
            }
        }
        try (Rig r = new Rig()) {
            var a = r.f.player("A"); var b = r.f.player("B"); r.configure(true, true);
            r.boards.updatePlayer(a.player, Component.empty(), List.of(), true); var board = r.board(a);
            Team collision = board.board.registerNewTeam(team(b)); collision.prefix(Component.text("preexisting"));
            int writes = board.teams.get(team(b)).mutations; r.service.updateAll();
            check(board.teams.get(team(b)).mutations == writes && !collision.hasEntry("B"), "preexisting team-name collision is skipped");
            r.service.removeAll(); check(board.teams.containsKey(team(b)), "name collision never becomes owned during cleanup");
        }
    }

    static final class Rig implements AutoCloseable {
        final PresentationFixture f = new PresentationFixture();
        final PresentationRenderer renderer = f.renderer(id -> Optional.empty());
        final ScoreboardService boards = f.scoreboards();
        final PresentationService service = new PresentationService(f.plugin, f.presentationConfig, renderer, boards, new TablistService(f.presentationConfig));
        Rig() throws Exception { }
        void configure(boolean sidebar, boolean nametag) throws Exception {
            Files.writeString(f.directory.resolve("presentation.yml"), "scoreboard:\n  enabled: " + sidebar
                    + "\n  lobby-only: false\nnametag:\n  enabled: " + nametag + "\n  lobby-only: false\n");
            f.presentationConfig.applyState(f.presentationConfig.prepareReloadState());
            renderer.applyState(renderer.prepareState(f.presentationConfig.getState()));
        }
        PresentationFixture.Board board(PresentationFixture.TestPlayer player) {
            return f.boards.stream().filter(board -> board.board == player.board).findFirst().orElseThrow();
        }
        @Override public void close() throws Exception { service.removeAll(); f.close(); }
    }
    private static int writes(PresentationFixture f) { return f.boards.stream().flatMap(board -> board.teams.values().stream()).mapToInt(t -> t.mutations).sum(); }
    private static String team(PresentationFixture.TestPlayer player) { return NametagService.teamName(player.id); }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
