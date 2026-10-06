package dev.vapee.core.presentation.nametag;

import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.LobbySpawn;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.presentation.*;
import dev.vapee.core.rank.RankModule;
import dev.vapee.core.reload.ReloadPlan;
import net.kyori.adventure.text.Component;
import org.bukkit.World;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

import java.nio.file.Files;
import java.util.Optional;

/** Exercises the actual module's prepare/apply/rollback and single refresh task. */
public final class NametagLifecycleHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        try (var f = new PresentationFixture()) {
            var a = f.player("Alice"); var b = f.player("Bob");
            PresentationFixture.set(f.lobby, "spawn", new LobbySpawn("lobby", 0, 0, 0, 0, 0));
            var module = module(f);
            write(f, true, true, true, false, 20, "<prefix>"); module.enable();
            check(f.tasks.size() == 1, "one repeating presentation timer");
            var listener = (PresentationListener) PresentationFixture.get(module, "presentationListener");
            listener.onPlayerJoin(new PlayerJoinEvent(a.player, Component.empty())); f.tasks.getLast().runnable.run();
            var boardA = board(f, a); var teamB = boardA.teams.get(NametagService.teamName(b.id));
            check(teamB != null && teamB.entries.equals(java.util.Set.of("Bob")), "join populates native target entry");
            f.tasks.getFirst().runnable.run(); check(board(f, b).teams.size() == 2, "existing timer refreshes other viewer");
            var c = f.player("Carol"); listener.onPlayerJoin(new PlayerJoinEvent(c.player, Component.empty()));
            f.tasks.getLast().runnable.run(); f.tasks.getFirst().runnable.run();
            check(boardA.teams.size() == 3 && board(f, c).teams.size() == 3, "join extends every viewer at normal refresh");
            listener.onPlayerQuit(new PlayerQuitEvent(c.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED)); c.online = false;
            check(!boardA.teams.containsKey(NametagService.teamName(c.id)) && c.board == f.main.board, "quit removes target and viewer ownership");
            var reconnect = f.new TestPlayer(c.id, "Carol"); f.online.add(reconnect);
            listener.onPlayerJoin(new PlayerJoinEvent(reconnect.player, Component.empty())); f.tasks.getLast().runnable.run();
            check(board(f, reconnect).teams.size() == 3, "reconnect has fresh board/team state");

            write(f, true, false, true, false, 20, "P"); ReloadPlan sidebarOff = module.prepareReload();
            check(boardA.objectives.containsKey("vapeecore"), "prepare makes no board writes"); sidebarOff.apply();
            check(a.board == boardA.board && boardA.objectives.isEmpty() && boardA.teams.get(NametagService.teamName(b.id)) == teamB,
                    "sidebar disable retains same board and target handle");
            check(teamB.prefix.equals(Component.text("P")), "reload updates team prefix"); sidebarOff.rollback();
            check(boardA.objectives.containsKey("vapeecore") && teamB.prefix.equals(Component.empty()), "rollback restores both feature render state");
            write(f, true, true, false, false, 20, "P"); ReloadPlan tagsOff = module.prepareReload(); tagsOff.apply();
            check(boardA.teams.isEmpty() && a.board == boardA.board && !boardA.objectives.isEmpty(), "nametag disable leaves sidebar"); tagsOff.rollback();
            check(boardA.teams.size() == 3, "nametag rollback recreates owned teams");
            // Failure occurs after config/render publication and native team writes, not merely during prepare.
            var prior = (org.bukkit.scheduler.BukkitTask) PresentationFixture.get(module, "updateTask");
            var priorTask = f.tasks.stream().filter(t -> t.task == prior).findFirst().orElseThrow();
            priorTask.failCancel = true;
            write(f, true, false, true, false, 21, "NEW"); ReloadPlan failed = module.prepareReload();
            try { failed.apply(); throw new AssertionError("cancel failure not injected"); }
            catch (IllegalStateException expected) { check(expected.getMessage().contains("cancel"), "apply fails after task/config swap"); }
            check(boardA.teams.get(NametagService.teamName(b.id)).prefix.equals(Component.text("NEW")) && boardA.objectives.isEmpty(), "failure reached newly published presentation");
            priorTask.failCancel = false; failed.rollback();
            check(PresentationFixture.get(module, "updateTask") == prior && !priorTask.cancelled, "rollback restores original single timer");
            check(boardA.teams.get(NametagService.teamName(b.id)).prefix.equals(Component.empty()) && !boardA.objectives.isEmpty(), "failed apply rollback removes mixed config/render state");
            check(f.tasks.getLast().cancelled, "replacement timer cancelled on rollback");
            f.failTaskSchedule = true; write(f, true, true, true, false, 22, "SCHEDULE"); var scheduling = module.prepareReload();
            try { scheduling.apply(); throw new AssertionError("schedule failure not injected"); }
            catch (IllegalStateException expected) { check(expected.getMessage().contains("scheduler"), "schedule failure before publication"); }
            f.failTaskSchedule = false; scheduling.rollback();
            check(a.board == boardA.board && boardA.teams.get(NametagService.teamName(b.id)).prefix.equals(Component.empty()), "schedule failure rollback retains existing teams");

            write(f, true, true, true, true, 20, "<prefix>"); module.prepareReload().apply();
            World other = PresentationFixture.proxy(World.class, (method, values) -> method.equals("getName") ? "other" : null);
            b.currentWorld = other; listener.onPlayerChangedWorld(new PlayerChangedWorldEvent(b.player, f.world)); module.getPresentationService().updateAll();
            check(b.board == f.main.board && !boardA.teams.containsKey(NametagService.teamName(b.id)), "world exit releases viewer and removes target roster");
            b.currentWorld = f.world; module.getPresentationService().updateAll();
            check(boardA.teams.containsKey(NametagService.teamName(b.id)) && b.board != f.main.board, "world return restores normal ownership");
            var foreign = new PresentationFixture.Board(); a.board = foreign.board;
            write(f, true, true, true, false, 20, "RELOAD"); module.prepareReload().apply();
            check(a.board == foreign.board && foreign.teams.isEmpty() && foreign.objectives.isEmpty(), "reload cannot acquire foreign current board");
            a.board = f.main.board; module.getPresentationService().updateAll();
            a.header = Component.text("foreign header");
            write(f, false, true, true, false, 20, "OFF"); var disabled = module.prepareReload(); disabled.apply();
            check(a.board == f.main.board && b.board == f.main.board && a.header.equals(Component.text("foreign header")), "global disable cleans boards and preserves foreign tab field");
            disabled.rollback(); check(board(f, a).teams.size() == 3, "disabled rollback acquires new exact board");
            module.disable(); module.disable();
            check(f.boards.stream().allMatch(board -> board.teams.isEmpty() && board.objectives.isEmpty()), "shutdown repeated cleanup leaves no owned structures");
            check(f.tasks.stream().filter(t -> t == priorTask || t == f.tasks.getLast()).allMatch(t -> t.cancelled), "shutdown cancels current timer");
            check(f.main.teams.isEmpty() && f.main.writes == 0, "all module lifecycle paths leave main unrendered");
        }
        System.out.println("NametagLifecycleHarness passed " + checks + " checks.");
    }
    private static PresentationModule module(PresentationFixture f) throws Exception {
        var permission = PresentationFixture.allocate(PermissionModule.class); PresentationFixture.set(permission, "luckPermsService", f.luckPerms);
        var rank = PresentationFixture.allocate(RankModule.class); PresentationFixture.set(rank, "rankService", f.ranks);
        var players = PresentationFixture.allocate(PlayerModule.class); PresentationFixture.set(players, "playerSettingsService", f.settings);
        var economy = PresentationFixture.allocate(EconomyModule.class); PresentationFixture.set(economy, "economyService", f.economy);
        var lobby = PresentationFixture.allocate(LobbyModule.class); PresentationFixture.set(lobby, "lobbyService", f.lobby);
        return new PresentationModule(f.plugin, f.config, f.messages, permission, rank, players, economy, lobby, id -> Optional.empty());
    }
    private static void write(PresentationFixture f, boolean enabled, boolean sidebar, boolean tags, boolean lobby, int interval, String prefix) throws Exception {
        Files.writeString(f.directory.resolve("presentation.yml"), "enabled: " + enabled + "\nupdate-interval-ticks: " + interval
                + "\nscoreboard:\n  enabled: " + sidebar + "\n  lobby-only: " + lobby + "\nnametag:\n  enabled: " + tags
                + "\n  lobby-only: " + lobby + "\n  prefix: '" + prefix + "'\n");
    }
    private static PresentationFixture.Board board(PresentationFixture f, PresentationFixture.TestPlayer player) {
        return f.boards.stream().filter(board -> board.board == player.board).findFirst().orElseThrow();
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
