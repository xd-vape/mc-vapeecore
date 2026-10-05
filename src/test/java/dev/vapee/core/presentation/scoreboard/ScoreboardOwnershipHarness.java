package dev.vapee.core.presentation.scoreboard;

import dev.vapee.core.presentation.PresentationFixture;
import dev.vapee.core.presentation.PresentationService;
import dev.vapee.core.presentation.tablist.TablistService;
import dev.vapee.core.visibility.VisibilityPolicy;
import dev.vapee.core.visibility.VisibilityService;
import net.kyori.adventure.text.Component;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;

import java.util.List;
import java.util.Optional;

public final class ScoreboardOwnershipHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        try (var f = new PresentationFixture()) {
            var a = f.player("A"); var b = f.player("B"); var target = f.player("Target");
            var service = f.scoreboards();
            Component title = Component.text("title"); List<Component> lines = List.of(Component.text("line"));
            check(!service.ownsScoreboard(a.player), "main scoreboard is not an owned viewer board");
            service.updatePlayer(a.player, title, lines); service.updatePlayer(b.player, title, lines);
            check(a.board != b.board && a.board != f.main.board, "independent viewer boards");
            check(service.ownsScoreboard(a.player) && service.ownsScoreboard(b.player), "both current boards owned");
            var originalA = f.boards.get(0); var originalB = f.boards.get(1);
            int writes = originalA.writes;
            service.updatePlayer(a.player, title, lines);
            check(originalA.writes == writes, "identical update remains delta-free");
            service.updatePlayer(a.player, Component.text("changed"), List.of(Component.text("changed line")));
            check(originalA.objectives.get("vapeecore").title.equals(Component.text("changed"))
                    && originalB.objectives.get("vapeecore").title.equals(title), "A update leaves B unchanged");
            var foreign = new PresentationFixture.Board();
            var foreignObjective = foreign.board.registerNewObjective("vapeecore", Criteria.DUMMY, Component.text("foreign"));
            foreignObjective.setDisplaySlot(DisplaySlot.SIDEBAR); int foreignWrites = foreign.writes;
            a.board = foreign.board;
            check(!service.ownsScoreboard(a.player) && service.ownsScoreboard(b.player), "foreign replacement detected by current viewer identity");
            service.updatePlayer(a.player, title, lines);
            service.updatePlayer(a.player, title, lines);
            check(a.board == foreign.board && foreign.writes == foreignWrites && originalA.objectives.isEmpty(),
                    "foreign board and same-name objective untouched; detached own objective removed");
            service.removePlayer(a.player);
            check(a.board == foreign.board && foreign.writes == foreignWrites, "foreign cleanup does not reset board");
            b.board = foreign.board; service.removePlayer(b.player);
            check(b.board == foreign.board && foreign.writes == foreignWrites && originalB.objectives.isEmpty(),
                    "direct removal also preserves foreign board");
            a.board = f.main.board; b.board = f.main.board;
            service.updatePlayer(a.player, title, lines); service.updatePlayer(b.player, title, lines);
            var before = a.board;
            service.updatePlayer(a.player, title, List.of());
            check(a.board != before && service.ownsScoreboard(a.player), "line-count change recreates only own board");
            f.settings.setScoreboardEnabled(a.id, false); service.updatePlayer(a.player, title, lines);
            check(a.board == f.main.board && !service.ownsScoreboard(a.player), "settings disable releases own board");
            f.settings.setScoreboardEnabled(a.id, true);
            service.updatePlayer(a.player, title, lines);
            check(service.ownsScoreboard(a.player), "settings re-enable starts own board cycle");

            var policy = new VisibilityPolicy(id -> f.settings.getSettings(id).orElseThrow().getVisibility(),
                    (viewer, other) -> false, (viewer, other) -> false, (viewer, other) -> false);
            var visibility = new VisibilityService(f.plugin, () -> f.online.stream().map(p -> p.player).toList(),
                    world -> world == f.world, policy, f.logger);
            f.settings.setLobbyPlayersVisible(a.id, false);
            visibility.synchronizePlayer(target.player);
            check(!a.player.canSee(target.player) && b.player.canSee(target.player), "real visibility service has asymmetric viewer relations");
            int aVisibility = a.visibilityWrites, bVisibility = b.visibilityWrites;
            var presentation = new PresentationService(f.plugin, f.presentationConfig, f.renderer(id -> Optional.empty()),
                    service, new TablistService(f.presentationConfig));
            presentation.updateAll(); presentation.removePlayer(target.player); presentation.updateAll();
            check(!a.player.canSee(target.player) && b.player.canSee(target.player)
                    && a.visibilityWrites == aVisibility && b.visibilityWrites == bVisibility,
                    "presentation refresh/remove/rejoin never reveals or hides target");
            f.settings.setLobbyPlayersVisible(a.id, true); visibility.applyViewerPreference(a.player);
            check(a.player.canSee(target.player), "only visibility owner changes target visibility");
            presentation.removeAll(); presentation.removeAll();
            check(a.board == f.main.board && b.board == f.main.board && target.board == f.main.board,
                    "module-style removeAll restores owned viewer boards idempotently");
            check(f.boards.stream().allMatch(board -> board.objectives.isEmpty() && board.teamCalls == 0)
                    && foreign.writes == foreignWrites && foreign.teamCalls == 0 && f.main.writes == 0,
                    "no teams created or touched; foreign objective and main board unchanged");
        }
        System.out.println("ScoreboardOwnershipHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
