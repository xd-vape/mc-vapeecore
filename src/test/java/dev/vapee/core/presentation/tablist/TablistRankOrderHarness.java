package dev.vapee.core.presentation.tablist;

import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.permission.PermissionModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.presentation.PresentationFixture;
import dev.vapee.core.presentation.PresentationFixture.TestPlayer;
import dev.vapee.core.presentation.PresentationListener;
import dev.vapee.core.presentation.PresentationModule;
import dev.vapee.core.rank.RankModule;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Applies actual Paper-order setters through the real owner and current primary LP rank APIs. */
public final class TablistRankOrderHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (String scenario :
                args.length == 0
                        ? List.of(
                                "weights", "ties", "fallback", "ownership", "lifecycle", "failure")
                        : List.of(args)) {
            switch (scenario) {
                case "weights" -> weights();
                case "ties" -> ties();
                case "fallback" -> fallback();
                case "ownership" -> ownership();
                case "lifecycle" -> lifecycle();
                case "failure" -> failure();
                default -> throw new AssertionError(scenario);
            }
        }
        System.out.println("TablistRankOrderHarness passed " + checks + " checks.");
    }

    private static void weights() throws Exception {
        try (var f = new PresentationFixture()) {
            TestPlayer low = ranked(f, "Aardvark", "arbitrary-low", 1);
            TestPlayer middle = ranked(f, "Middle", "arbitrary-middle", 50);
            TestPlayer high = ranked(f, "Zebra", "arbitrary-high", 900);
            low.name = Component.text("ZZZ nickname");
            high.name = Component.text("AAA nickname");
            TablistService service = new TablistService(f.presentationConfig, f.ranks);
            service.updateOrder(roster(f));
            ordered(f, "higher primary weight first", high, middle, low);
            check(
                    low.name.equals(Component.text("ZZZ nickname"))
                            && high.name.equals(Component.text("AAA nickname")),
                    "ordering does not change configured display names");
            check(
                    f.main.teams.isEmpty() && f.main.objectives.isEmpty() && f.main.teamCalls == 0,
                    "ordering creates no teams or sidebar");
            f.groupWeights.put("arbitrary-low", Integer.MAX_VALUE);
            f.groupWeights.put("arbitrary-high", Integer.MIN_VALUE);
            service.updateOrder(roster(f));
            ordered(f, "extreme LP weights do not overflow", low, middle, high);
            f.groups.put(high.id, "arbitrary-low");
            service.updateOrder(roster(f));
            ordered(f, "actual primary group change is reread", low, high, middle);
            service.removePlayer(low.player);
            service.removePlayer(middle.player);
            service.removePlayer(high.player);
            check(low.order == 0 && middle.order == 0 && high.order == 0, "default zero restored");
        }
    }

    private static void ties() throws Exception {
        try (var f = new PresentationFixture()) {
            TestPlayer z = ranked(f, "Zulu", "one", 30);
            TestPlayer lower = ranked(f, "alpha", "two", 30);
            TestPlayer upper = ranked(f, "Beta", "one", 30);
            lower.name = Component.text("zzz");
            upper.name = Component.text("aaa");
            TablistService service = new TablistService(f.presentationConfig, f.ranks);
            service.updateOrder(roster(f));
            ordered(f, "equal priority uses case-insensitive actual username", lower, upper, z);
            for (TestPlayer player : f.online) service.removePlayer(player.player);
            f.online.clear();
            TestPlayer larger = f.new TestPlayer(new UUID(0, 2), "SAME");
            TestPlayer smaller = f.new TestPlayer(new UUID(0, 1), "same");
            f.online.add(larger);
            f.online.add(smaller);
            service.updateOrder(roster(f));
            ordered(f, "case-insensitive tie uses UUID", smaller, larger);
            service.updateOrder(List.of(smaller.player, larger.player));
            ordered(f, "tie is independent of roster encounter order", smaller, larger);
        }
    }

    private static void fallback() throws Exception {
        try (var f = new PresentationFixture()) {
            TestPlayer low = ranked(f, "A", "start", null);
            TestPlayer middle = ranked(f, "B", "middle", null);
            TestPlayer high = ranked(f, "C", "end", null);
            TestPlayer unknown = f.player("Unknown");
            f.tracks.put("ranks", List.of("start", "middle", "end"));
            TablistService service = new TablistService(f.presentationConfig, f.ranks);
            service.updateOrder(roster(f));
            ordered(
                    f,
                    "weightless track uses highest ending group first",
                    high,
                    middle,
                    low,
                    unknown);
            f.tracks.put("ranks", List.of("end", "middle", "start"));
            service.updateOrder(roster(f));
            ordered(f, "track changes are live without a rank cache", low, middle, high, unknown);
            f.tracks.remove("ranks");
            service.updateOrder(roster(f));
            ordered(f, "missing track falls back to username", low, middle, high, unknown);
            f.tracks.put("custom", List.of("start", "middle", "end"));
            Files.writeString(f.directory.resolve("config.yml"), "ranks:\n  track: custom\n");
            f.config.prepareReload().apply();
            service.updateOrder(roster(f));
            ordered(f, "configured track name is respected", high, middle, low, unknown);
            f.tracks.put("custom", List.of("start", "start", "end"));
            service.updateOrder(roster(f));
            ordered(
                    f,
                    "ambiguous duplicate track does not invent priority",
                    low,
                    middle,
                    high,
                    unknown);
            f.tracks.put("custom", List.of());
            service.updateOrder(roster(f));
            ordered(f, "empty track is neutral", low, middle, high, unknown);
        }
        try (var f = new PresentationFixture()) {
            TestPlayer low = ranked(f, "Low", "base", 20);
            TestPlayer middle = ranked(f, "Middle", "middle", null);
            TestPlayer high = ranked(f, "High", "upper", 100);
            TestPlayer top = ranked(f, "Top", "top", null);
            f.tracks.put("ranks", List.of("base", "middle", "upper", "top"));
            TablistService service = new TablistService(f.presentationConfig, f.ranks);
            service.updateOrder(roster(f));
            ordered(f, "missing weights fit compatible track anchors", top, high, middle, low);
            f.groupWeights.put("base", 100);
            f.groupWeights.put("upper", 20);
            service.updateOrder(roster(f));
            ordered(f, "contradicting track cannot reverse known weights", low, high, middle, top);
            f.groupWeights.put("base", 20);
            f.groupWeights.put("upper", 100);
            TestPlayer outsider = ranked(f, "Outside", "outside", 50);
            service.updateOrder(roster(f));
            ordered(
                    f,
                    "unrelated weighted rank prevents ambiguous numeric interpolation",
                    high,
                    outsider,
                    low,
                    top,
                    middle);
        }
        try (var f = new PresentationFixture()) {
            TestPlayer low = ranked(f, "Zulu", "base", 20);
            TestPlayer middle = ranked(f, "alpha", "middle", null);
            TestPlayer high = ranked(f, "Beta", "upper", 20);
            f.tracks.put("ranks", List.of("base", "middle", "upper"));
            new TablistService(f.presentationConfig, f.ranks).updateOrder(roster(f));
            ordered(
                    f,
                    "equal weighted anchors preserve shared alphabetical priority",
                    middle,
                    high,
                    low);
        }
    }

    private static void ownership() throws Exception {
        try (var f = new PresentationFixture()) {
            TestPlayer a = ranked(f, "A", "high", 100);
            TestPlayer b = ranked(f, "B", "low", 20);
            a.order = 42;
            b.order = 77;
            TablistService service = new TablistService(f.presentationConfig, f.ranks);
            service.updateOrder(roster(f));
            int writes = a.orderWrites + b.orderWrites;
            service.updateOrder(roster(f));
            check(
                    writes == a.orderWrites + b.orderWrites,
                    "unchanged order sends no unnecessary writes");
            f.groupWeights.put("low", 200);
            service.updateOrder(roster(f));
            ordered(f, "successful reordering applies to both players", b, a);
            b.order = 888;
            service.removePlayer(a.player);
            service.removePlayer(b.player);
            check(a.order == 42, "refresh retains original order baseline");
            check(b.order == 888, "external order ownership survives cleanup");
            writes = a.orderWrites + b.orderWrites;
            service.removePlayer(a.player);
            service.removePlayer(b.player);
            check(writes == a.orderWrites + b.orderWrites, "repeated cleanup has no order writes");
            a.order = 51;
            b.order = 52;
            service.updateOrder(roster(f));
            a.order = 999;
            service.updateOrder(roster(f));
            check(a.order == 1, "active refresh remains configured writer");
            service.removePlayer(a.player);
            service.removePlayer(b.player);
            check(
                    a.order == 51 && b.order == 52,
                    "intervening writer does not rebase active cycle");
            a.online = false;
            TestPlayer reconnect = f.new TestPlayer(a.id, "Reconnect");
            reconnect.order = 63;
            f.online.add(reconnect);
            service.updateOrder(roster(f));
            service.removePlayer(reconnect.player);
            check(reconnect.order == 63, "reconnect captures new session order");
        }
        try (var f = new PresentationFixture()) {
            TestPlayer a = f.player("A");
            a.order = 1;
            TablistService service = new TablistService(f.presentationConfig, f.ranks);
            service.updateOrder(roster(f));
            check(a.orderWrites == 0, "already matching order claims no write");
            a.order = 81;
            service.removePlayer(a.player);
            check(a.order == 81 && a.orderWrites == 0, "untouched order has no cleanup authority");
        }
    }

    private static void lifecycle() throws Exception {
        try (var f = new PresentationFixture()) {
            TestPlayer a = ranked(f, "Alpha", "low", 1);
            TestPlayer z = ranked(f, "Zulu", "high", 100);
            a.order = 17;
            z.order = 18;
            config(f, true, true);
            PresentationModule module = module(f);
            module.enable();
            check(f.tasks.size() == 1, "only existing presentation timer is used");
            PresentationListener listener =
                    (PresentationListener) PresentationFixture.get(module, "presentationListener");
            listener.onPlayerJoin(new PlayerJoinEvent(z.player, Component.empty()));
            f.tasks.getLast().runnable.run();
            ordered(f, "join presentation refresh orders complete roster", z, a);
            f.groups.put(a.id, "high");
            f.tasks.getFirst().runnable.run();
            ordered(f, "shared timer sees changed primary rank", a, z);
            f.groupWeights.put("high", 0);
            f.groups.put(z.id, "low");
            module.getPresentationService().updatePlayer(z.player);
            ordered(f, "successful individual refresh updates global order", z, a);
            listener.onPlayerQuit(
                    new PlayerQuitEvent(
                            z.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
            check(z.order == 18, "quit restores order while session remains reachable");
            z.online = false;
            f.tasks.getFirst().runnable.run();
            ordered(f, "next shared refresh drops offline player", a);
            check(z.order == 18, "offline player receives no new ordering writes");
            z.online = true;
            module.getPresentationService().updateAll();
            ordered(f, "returning online player receives deterministic order", z, a);
            z.order = 700;
            config(f, true, false);
            var disabled = module.prepareReload();
            disabled.apply();
            check(
                    a.order == 17 && z.order == 700,
                    "tablist disabled releases owned order and preserves foreign order");
            check(
                    !f.tasks.getFirst().cancelled,
                    "tablist toggle does not create or replace scheduler");
            disabled.rollback();
            ordered(f, "reload rollback reestablishes ordering", z, a);
            module.disable();
            check(a.order == 17 && z.order == 700, "shutdown restores current activation baseline");
            check(f.tasks.getFirst().cancelled, "existing repeating task cancelled at shutdown");
            config(f, true, true);
            module.enable();
            module.getPresentationService().updateAll();
            ordered(f, "module re-enable rebuilds ordering", z, a);
            config(f, false, true);
            module.prepareReload().apply();
            check(a.order == 17 && z.order == 700, "global presentation disable releases order");
            module.disable();
        }
    }

    private static void failure() throws Exception {
        try (var f = new PresentationFixture()) {
            TestPlayer a = ranked(f, "A", "high", 100);
            TestPlayer b = ranked(f, "B", "low", 1);
            a.order = 41;
            b.order = 42;
            TablistService service = new TablistService(f.presentationConfig, f.ranks);
            f.failRankRead = true;
            expectFailure(() -> service.updateOrder(roster(f)));
            check(
                    a.order == 41 && b.order == 42,
                    "rank read failure does not invent neutral priority or partially write");
            f.failRankRead = false;
            a.failOrderWrite = true;
            expectFailure(() -> service.updateOrder(roster(f)));
            a.failOrderWrite = false;
            a.order = 71;
            service.removePlayer(a.player);
            check(a.order == 71, "failed setter gains no cleanup ownership");
            service.updateOrder(roster(f));
            ordered(f, "later refresh retries failed order", a, b);
            service.removePlayer(a.player);
            service.removePlayer(b.player);
            check(a.order == 71 && b.order == 42, "successful retry restores correct baseline");
        }
    }

    private static TestPlayer ranked(
            PresentationFixture f, String name, String group, Integer weight) {
        TestPlayer player = f.player(name);
        f.groups.put(player.id, group);
        f.groupColors.put(group, "white");
        if (weight != null) f.groupWeights.put(group, weight);
        return player;
    }

    private static List<Player> roster(PresentationFixture f) {
        return f.online.stream().map(player -> player.player).toList();
    }

    private static void ordered(PresentationFixture f, String contract, TestPlayer... expected) {
        List<TestPlayer> actual =
                f.online.stream()
                        .filter(player -> player.online)
                        .sorted(
                                Comparator.comparingInt((TestPlayer player) -> player.order)
                                        .reversed())
                        .toList();
        check(actual.equals(Arrays.asList(expected)), contract);
        for (int index = 0; index < expected.length; index++) {
            check(
                    expected[index].order == expected.length - index,
                    "explicit unique positive Paper orders: " + contract);
        }
    }

    private static void config(PresentationFixture f, boolean enabled, boolean tablist)
            throws Exception {
        Files.writeString(
                f.directory.resolve("presentation.yml"),
                "enabled: "
                        + enabled
                        + "\nscoreboard:\n  enabled: false\nnametag:\n  enabled: false\ntablist:\n  enabled: "
                        + tablist
                        + "\n");
    }

    private static PresentationModule module(PresentationFixture f) throws Exception {
        var permission = PresentationFixture.allocate(PermissionModule.class);
        PresentationFixture.set(permission, "luckPermsService", f.luckPerms);
        var rank = PresentationFixture.allocate(RankModule.class);
        PresentationFixture.set(rank, "rankService", f.ranks);
        var players = PresentationFixture.allocate(PlayerModule.class);
        PresentationFixture.set(players, "playerSettingsService", f.settings);
        var economy = PresentationFixture.allocate(EconomyModule.class);
        PresentationFixture.set(economy, "economyService", f.economy);
        var lobby = PresentationFixture.allocate(LobbyModule.class);
        PresentationFixture.set(lobby, "lobbyService", f.lobby);
        return new PresentationModule(
                f.plugin, f.config, f.messages, permission, rank, players, economy, lobby);
    }

    private static void expectFailure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected external failure");
        } catch (IllegalStateException expected) {
            check(true, "external failure remains observable");
        }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
