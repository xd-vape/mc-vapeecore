package dev.vapee.core.utility.command;

import dev.vapee.core.command.StaffTargetTestFixture;
import dev.vapee.core.utility.OnlinePlayerResolver;
import dev.vapee.core.utility.UtilityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.PlayerInventory;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

public final class UtilityCommandHarness {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    private UtilityCommandHarness() {
    }

    public static void main(String[] args) {
        testOnlinePlayerResolver();
        testFly();
        testSpeed();
        testGameMode();
        testTeleport();
        testTeleportHere();
        testHealAndFeed();
        testPingClearAndEnderChest();
        testExplicitSelfAndCommandBoundaries();
        testStaffTargetProtection();
        testCompletionContract();
        System.out.println("UtilityCommandHarness passed " + checks + " checks.");
    }

    private static void testOnlinePlayerResolver() {
        Fixture fixture = new Fixture();
        MutablePlayer player = fixture.player("_ImVentex_", GameMode.SURVIVAL);
        OnlinePlayerResolver resolver = new OnlinePlayerResolver(fixture::onlinePlayers);
        check(resolver.resolveExact("_imventex_") == player.player,
                "online resolver is case-insensitive for complete names");
        check(resolver.resolveExact("Vent") == null,
                "online resolver never guesses a partial mutation target");
        player.online = false;
        check(resolver.resolveExact("_ImVentex_") == null,
                "online resolver never falls back to an offline player");
    }

    private static void testFly() {
        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL, FlyCommand.PERMISSION);
        MutablePlayer other = fixture.player("Other", GameMode.ADVENTURE);
        FlyCommand command = new FlyCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.buildPlayers::contains, fixture.activityPlayers::contains, fixture.messages
        , fixture.staff.guard);

        command.onCommand(fixture.console(Set.of(FlyCommand.OTHERS_PERMISSION)), null, "fly", new String[0]);
        check(fixture.last().contains("target is required"), "fly console requires a target");
        command.onCommand(self.player, null, "fly", new String[]{"missing"});
        check(fixture.last().equals("Player 'missing' is not online."), "fly uses exact online lookup");

        fixture.activityPlayers.add(self.id);
        command.onCommand(self.player, null, "fly", new String[0]);
        check(fixture.last().contains("participating in an activity") && !self.allowFlight,
                "fly blocks an activity participant");
        fixture.activityPlayers.clear();
        fixture.buildPlayers.add(self.id);
        command.onCommand(self.player, null, "fly", new String[0]);
        check(fixture.last().contains("controlled by build mode") && !self.allowFlight,
                "fly blocks BUILD ownership");
        fixture.buildPlayers.clear();

        self.gameMode = GameMode.CREATIVE;
        self.allowFlight = true;
        self.flying = true;
        command.onCommand(self.player, null, "fly", new String[0]);
        check(fixture.last().contains("current game mode") && self.allowFlight && self.flying,
                "fly preserves creative native flight");
        self.gameMode = GameMode.SPECTATOR;
        command.onCommand(self.player, null, "fly", new String[0]);
        check(fixture.last().contains("current game mode") && self.allowFlight,
                "fly preserves spectator native flight");

        self.gameMode = GameMode.SURVIVAL;
        self.allowFlight = false;
        self.flying = false;
        command.onCommand(self.player, null, "fly", new String[0]);
        check(self.allowFlight && fixture.service.hasManagedFlight(self.id)
                        && fixture.last().equals("Flight enabled."),
                "fly enables and reports self managed flight");
        self.flying = true;
        command.onCommand(self.player, null, "fly", new String[0]);
        check(!self.allowFlight && !self.flying && fixture.last().equals("Flight disabled."),
                "fly disables managed flight cleanly");

        self.gameMode = GameMode.ADVENTURE;
        command.onCommand(self.player, null, "fly", new String[0]);
        check(self.allowFlight && fixture.service.hasManagedFlight(self.id),
                "fly supports adventure managed flight");
        command.onCommand(self.player, null, "fly", new String[0]);

        self.permissions.remove(FlyCommand.OTHERS_PERMISSION);
        command.onCommand(self.player, null, "fly", new String[]{"Other"});
        check(fixture.last().contains("another player's flight") && !other.allowFlight,
                "fly others requires its dedicated permission");
        self.permissions.add(FlyCommand.OTHERS_PERMISSION);
        command.onCommand(self.player, null, "fly", new String[]{"Other"});
        check(other.allowFlight && fixture.last().equals("Flight enabled for Other."),
                "fly others mutates the exact target");
        command.onCommand(fixture.console(Set.of(FlyCommand.OTHERS_PERMISSION)), null,
                "fly", new String[]{"Other"});
        check(!other.allowFlight, "fly console with others permission mutates only its target");

        self.permissions.remove(FlyCommand.OTHERS_PERMISSION);
        check(command.onTabComplete(self.player, null, "fly", new String[]{""}).isEmpty(),
                "fly hides player completion without others permission");
        self.permissions.add(FlyCommand.OTHERS_PERMISSION);
        check(command.onTabComplete(self.player, null, "fly", new String[]{""})
                        .equals(List.of("Other", "Self")),
                "fly player completion is stable and sorted");
    }

    private static void testSpeed() {
        check(SpeedCommand.parseLevel("1") == 1 && SpeedCommand.parseLevel("10") == 10,
                "speed accepts range endpoints");
        check(SpeedCommand.parseLevel("0") == -1 && SpeedCommand.parseLevel("11") == -1
                        && SpeedCommand.parseLevel("abc") == -1,
                "speed rejects out-of-range and non-whole input");

        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL, SpeedCommand.PERMISSION);
        MutablePlayer other = fixture.player("Other", GameMode.CREATIVE);
        SpeedCommand command = new SpeedCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.activityPlayers::contains, fixture.messages
        , fixture.staff.guard);
        command.onCommand(self.player, null, "speed", new String[]{"0"});
        check(fixture.last().contains("whole number between 1 and 10"), "speed gives a concrete range error");
        command.onCommand(fixture.console(Set.of(SpeedCommand.OTHERS_PERMISSION)), null, "speed", new String[]{"5"});
        check(fixture.last().contains("target is required"), "speed console requires a target");
        command.onCommand(self.player, null, "speed", new String[]{"5"});
        check(fixture.last().equals("Walk speed set to 5/10.") && self.walkSpeed > 0.2F,
                "speed reports and changes the walk channel");
        self.permissions.remove(SpeedCommand.OTHERS_PERMISSION);
        command.onCommand(self.player, null, "speed", new String[]{"6", "Other"});
        check(fixture.last().contains("another player's speed"), "speed others permission is enforced");
        self.permissions.add(SpeedCommand.OTHERS_PERMISSION);
        command.onCommand(self.player, null, "speed", new String[]{"10", "Other"});
        check(fixture.last().equals("Flight speed for Other set to 10/10.") && close(other.flySpeed, 1.0F),
                "speed changes a creative target's flight channel");
        command.onCommand(fixture.console(Set.of(SpeedCommand.OTHERS_PERMISSION)), null,
                "speed", new String[]{"5", "Other"});
        check(other.flySpeed < 1.0F, "speed console requires and honors the others permission");
        fixture.activityPlayers.add(other.id);
        command.onCommand(self.player, null, "speed", new String[]{"4", "Other"});
        check(fixture.last().contains("participating in an activity"), "speed blocks activity targets");

        check(command.onTabComplete(self.player, null, "speed", new String[]{"1"})
                        .equals(List.of("1", "10")),
                "speed completes all matching levels");
        check(command.onTabComplete(self.player, null, "speed", new String[]{"5", "o"})
                        .equals(List.of("Other")),
                "speed completes targets only in its target argument");
    }

    private static void testGameMode() {
        Map<String, GameMode> aliases = Map.ofEntries(
                Map.entry("survival", GameMode.SURVIVAL), Map.entry("s", GameMode.SURVIVAL),
                Map.entry("0", GameMode.SURVIVAL), Map.entry("creative", GameMode.CREATIVE),
                Map.entry("c", GameMode.CREATIVE), Map.entry("1", GameMode.CREATIVE),
                Map.entry("adventure", GameMode.ADVENTURE), Map.entry("a", GameMode.ADVENTURE),
                Map.entry("2", GameMode.ADVENTURE), Map.entry("spectator", GameMode.SPECTATOR),
                Map.entry("sp", GameMode.SPECTATOR), Map.entry("3", GameMode.SPECTATOR)
        );
        aliases.forEach((alias, expected) -> check(GameModeCommand.parseGameMode(alias) == expected,
                "gamemode parses " + alias));
        check(GameModeCommand.parseGameMode("banana") == null, "gamemode rejects unknown input");

        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL, GameModeCommand.PERMISSION);
        MutablePlayer other = fixture.player("Other", GameMode.ADVENTURE);
        GameModeCommand command = new GameModeCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.buildPlayers::contains, fixture.activityPlayers::contains,
                player -> player == self.player, fixture.messages
        , fixture.staff.guard);
        command.onCommand(fixture.console(Set.of(GameModeCommand.OTHERS_PERMISSION)), null,
                "gamemode", new String[]{"creative"});
        check(fixture.last().contains("target is required"), "gamemode console requires a target");
        fixture.service.toggleFlight(self.player);
        command.onCommand(self.player, null, "gm", new String[]{"creative"});
        check(self.gameMode == GameMode.CREATIVE && !fixture.service.hasManagedFlight(self.id),
                "gamemode releases command-managed flight");
        check(fixture.last().contains("Lobby protection remains active"),
                "creative in the lobby explains BUILD protection");
        command.onCommand(self.player, null, "gm", new String[]{"survival"});
        check(self.gameMode == GameMode.SURVIVAL && !self.allowFlight,
                "returning to survival leaves no managed allowFlight leak");

        self.permissions.add(GameModeCommand.OTHERS_PERMISSION);
        other.gameMode = GameMode.CREATIVE;
        fixture.buildPlayers.add(other.id);
        command.onCommand(self.player, null, "gamemode", new String[]{"survival", "Other"});
        check(fixture.last().contains("Exit build mode") && other.gameMode == GameMode.CREATIVE,
                "gamemode preserves the BUILD invariant");
        fixture.buildPlayers.clear();
        fixture.activityPlayers.add(other.id);
        command.onCommand(self.player, null, "gamemode", new String[]{"spectator", "Other"});
        check(fixture.last().contains("participating in an activity"), "gamemode blocks activity targets");
        fixture.activityPlayers.clear();
        command.onCommand(self.player, null, "gamemode", new String[]{"sp", "Other"});
        check(other.gameMode == GameMode.SPECTATOR
                        && fixture.last().equals("Other's game mode changed to Spectator."),
                "gamemode changes an authorized online target");
        command.onCommand(fixture.console(Set.of(GameModeCommand.OTHERS_PERMISSION)), null,
                "gamemode", new String[]{"survival", "Other"});
        check(other.gameMode == GameMode.SURVIVAL,
                "gamemode console requires and honors the others permission");

        check(command.onTabComplete(self.player, null, "gamemode", new String[]{""})
                        .equals(List.of("survival", "creative", "adventure", "spectator")),
                "gamemode completes only full mode names");
        check(command.onTabComplete(self.player, null, "gamemode", new String[]{"s"})
                        .equals(List.of("survival", "spectator")),
                "gamemode mode completion is prefix-filtered");
    }

    private static void testTeleport() {
        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL, TeleportCommand.PERMISSION);
        MutablePlayer other = fixture.player("Other", GameMode.SURVIVAL);
        MutablePlayer third = fixture.player("Third", GameMode.SURVIVAL);
        TeleportCommand command = new TeleportCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.activityPlayers::contains, fixture.messages
        , fixture.staff.guard);
        self.permissions.remove(TeleportCommand.PERMISSION);
        command.onCommand(self.player, null, "tp", new String[]{"Other"});
        check(fixture.last().contains("do not have permission") && !self.teleported,
                "tp self form rejects a missing permission before mutation");
        self.permissions.add(TeleportCommand.PERMISSION);
        command.onCommand(fixture.console(Set.of(TeleportCommand.PERMISSION)), null, "tp", new String[]{"Other"});
        check(fixture.last().contains("source player is required"), "tp one-argument console form is denied");
        command.onCommand(self.player, null, "tp", new String[]{"Missing"});
        check(fixture.last().equals("Player 'Missing' is not online."), "tp rejects offline targets");
        command.onCommand(self.player, null, "tp", new String[]{"selF"});
        check(fixture.last().contains("already at your own location"), "tp handles case-insensitive self without mutation");
        command.onCommand(self.player, null, "tp", new String[]{"Oth"});
        check(fixture.last().equals("Player 'Oth' is not online."), "tp rejects partial target names");
        fixture.activityPlayers.add(self.id);
        command.onCommand(self.player, null, "tp", new String[]{"Other"});
        check(fixture.last().contains("participating in an activity"), "tp blocks activity senders");
        fixture.activityPlayers.clear();
        fixture.activityPlayers.add(other.id);
        command.onCommand(self.player, null, "tp", new String[]{"Other"});
        check(fixture.last().contains("destination player is participating"),
                "tp blocks activity destinations");
        self.permissions.add(TeleportCommand.BYPASS_PERMISSION);
        command.onCommand(self.player, null, "tp", new String[]{"Other"});
        check(fixture.last().equals("Teleported to Other."), "tp bypass skips only internal activity guards");
        self.permissions.remove(TeleportCommand.BYPASS_PERMISSION);
        fixture.activityPlayers.clear();
        self.teleportResult = false;
        command.onCommand(self.player, null, "tp", new String[]{"Other"});
        check(fixture.last().equals("Teleport failed or was cancelled."), "tp reports a false teleport result");
        self.teleportResult = true;
        command.onCommand(self.player, null, "tp", new String[]{"Other"});
        check(fixture.last().equals("Teleported to Other."), "tp reports successful teleport");

        command.onCommand(self.player, null, "tp", new String[]{"Other", "Third"});
        check(fixture.last().contains("one player to another") && !other.teleported,
                "tp source-target form never inherits the self permission");
        self.permissions.add(TeleportCommand.OTHERS_PERMISSION);
        command.onCommand(self.player, null, "tp", new String[]{"Other", "Third"});
        check(other.teleported && fixture.last().equals("Teleported Other to Third."),
                "tp source-target form mutates the named source only with others permission");
        other.teleported = false;
        other.online = false;
        command.onCommand(self.player, null, "tp", new String[]{"Other", "Third"});
        check(fixture.last().equals("Player 'Other' is not online.") && !other.teleported,
                "tp source-target rejects offline sources");
        other.online = true;
        third.online = false;
        command.onCommand(self.player, null, "tp", new String[]{"Other", "Third"});
        check(fixture.last().equals("Player 'Third' is not online.") && !other.teleported,
                "tp source-target rejects offline destinations");
        third.online = true;
        command.onCommand(self.player, null, "tp", new String[]{"Other", "other"});
        check(fixture.last().contains("same player") && !other.teleported,
                "tp source-target handles an identical source and target");
        fixture.activityPlayers.add(other.id);
        command.onCommand(self.player, null, "tp", new String[]{"Other", "Third"});
        check(fixture.last().contains("source player is participating") && !other.teleported,
                "tp source-target blocks an activity source");
        self.permissions.add(TeleportCommand.BYPASS_PERMISSION);
        command.onCommand(self.player, null, "tp", new String[]{"Other", "Third"});
        check(other.teleported, "tp source-target bypass permits an activity source");
        fixture.activityPlayers.clear();

        self.permissions.remove(TeleportCommand.OTHERS_PERMISSION);
        self.permissions.remove(TeleportCommand.BYPASS_PERMISSION);
        check(command.onTabComplete(self.player, null, "tp", new String[]{""})
                        .equals(List.of("^", "~", "Other", "Third")),
                "tp self-form completion filters the sender");
        check(command.onTabComplete(fixture.console(Set.of()), null, "tp", new String[]{""}).isEmpty(),
                "tp completion is hidden without permission");
        self.permissions.add(TeleportCommand.OTHERS_PERMISSION);
        check(command.onTabComplete(self.player, null, "tp", new String[]{"Other", ""})
                        .equals(List.of("^", "~", "Self", "Third")),
                "tp second-argument completion excludes the selected source");
    }

    private static void testTeleportHere() {
        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL, TeleportHereCommand.PERMISSION);
        MutablePlayer other = fixture.player("Other", GameMode.SURVIVAL);
        TeleportHereCommand command = new TeleportHereCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.activityPlayers::contains, fixture.messages
        , fixture.staff.guard);
        command.onCommand(fixture.console(Set.of(TeleportHereCommand.PERMISSION)), null,
                "tphere", new String[]{"Other"});
        check(fixture.last().contains("console has no location"), "tphere is player-only");
        command.onCommand(self.player, null, "tphere", new String[]{"Self"});
        check(fixture.last().equals("You are already here."), "tphere handles self");
        command.onCommand(self.player, null, "tphere", new String[]{"Missing"});
        check(fixture.last().equals("Player 'Missing' is not online."), "tphere rejects offline targets");
        fixture.activityPlayers.add(self.id);
        command.onCommand(self.player, null, "tphere", new String[]{"Other"});
        check(fixture.last().contains("participating in an activity"), "tphere blocks activity senders");
        fixture.activityPlayers.clear();
        fixture.activityPlayers.add(other.id);
        command.onCommand(self.player, null, "tphere", new String[]{"Other"});
        check(fixture.last().contains("That player is participating"), "tphere blocks activity targets");
        fixture.activityPlayers.clear();
        other.teleportResult = false;
        command.onCommand(self.player, null, "tphere", new String[]{"Other"});
        check(fixture.last().equals("Teleport failed or was cancelled."),
                "tphere reports a false teleport result");
        other.teleportResult = true;
        command.onCommand(self.player, null, "tphere", new String[]{"Other"});
        check(fixture.last().equals("Teleported Other to you."), "tphere reports successful teleport");
        fixture.activityPlayers.add(other.id);
        self.permissions.add(TeleportHereCommand.BYPASS_PERMISSION);
        command.onCommand(self.player, null, "tphere", new String[]{"Other"});
        check(fixture.last().equals("Teleported Other to you."), "tphere bypass skips its activity guard");
        fixture.activityPlayers.clear();
        check(command.onTabComplete(self.player, null, "tphere", new String[]{""}).equals(List.of("Other")),
                "tphere completion filters self");
        self.permissions.remove(TeleportHereCommand.PERMISSION);
        check(command.onTabComplete(self.player, null, "tphere", new String[]{""}).isEmpty(),
                "tphere completion is hidden without permission");
        other.teleported = false;
        command.onCommand(self.player, null, "tphere", new String[]{"Other"});
        check(fixture.last().contains("do not have permission") && !other.teleported,
                "tphere rejects a missing permission before mutation");
    }

    private static void testHealAndFeed() {
        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL,
                HealCommand.PERMISSION, FeedCommand.PERMISSION);
        MutablePlayer other = fixture.player("Other", GameMode.SURVIVAL);
        HealCommand heal = new HealCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.activityPlayers::contains, fixture.messages
        , fixture.staff.guard);
        FeedCommand feed = new FeedCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.activityPlayers::contains, fixture.messages
        , fixture.staff.guard);
        heal.onCommand(fixture.console(Set.of(HealCommand.OTHERS_PERMISSION)), null, "heal", new String[0]);
        check(fixture.last().contains("target is required"), "heal console requires a target");
        feed.onCommand(fixture.console(Set.of(FeedCommand.OTHERS_PERMISSION)), null, "feed", new String[0]);
        check(fixture.last().contains("target is required"), "feed console requires a target");

        self.health = 2.0D;
        self.maxHealth = 28.0D;
        self.food = 3;
        heal.onCommand(self.player, null, "heal", new String[0]);
        check(close(self.health, 28.0D) && self.food == 3 && fixture.last().equals("Healed."),
                "heal restores health without changing food");
        double health = self.health;
        feed.onCommand(self.player, null, "feed", new String[0]);
        check(self.food == 20 && close(self.health, health) && fixture.last().equals("Fed."),
                "feed restores food without changing health");

        heal.onCommand(self.player, null, "heal", new String[]{"Other"});
        check(fixture.last().contains("heal another player"), "heal others requires its dedicated permission");
        feed.onCommand(self.player, null, "feed", new String[]{"Other"});
        check(fixture.last().contains("feed another player"), "feed others requires its dedicated permission");
        self.permissions.addAll(Set.of(HealCommand.OTHERS_PERMISSION, FeedCommand.OTHERS_PERMISSION));
        fixture.activityPlayers.add(other.id);
        heal.onCommand(self.player, null, "heal", new String[]{"Other"});
        check(fixture.last().contains("participating in an activity"), "heal blocks activity targets");
        feed.onCommand(self.player, null, "feed", new String[]{"Other"});
        check(fixture.last().contains("participating in an activity"), "feed blocks activity targets");
        fixture.activityPlayers.clear();
        other.health = 1.0D;
        other.food = 1;
        heal.onCommand(self.player, null, "heal", new String[]{"Other"});
        check(close(other.health, other.maxHealth) && fixture.last().equals("Healed Other."),
                "heal others mutates the authorized target");
        feed.onCommand(self.player, null, "feed", new String[]{"Other"});
        check(other.food == 20 && fixture.last().equals("Fed Other."),
                "feed others mutates the authorized target");
        other.health = 2.0D;
        other.food = 2;
        heal.onCommand(fixture.console(Set.of(HealCommand.OTHERS_PERMISSION)), null,
                "heal", new String[]{"Other"});
        feed.onCommand(fixture.console(Set.of(FeedCommand.OTHERS_PERMISSION)), null,
                "feed", new String[]{"Other"});
        check(close(other.health, other.maxHealth) && other.food == 20,
                "heal and feed console forms require and honor their others permissions");
        check(heal.onTabComplete(self.player, null, "heal", new String[]{"o"}).equals(List.of("Other")),
                "heal completion is permission-aware and filtered");
        check(feed.onTabComplete(self.player, null, "feed", new String[]{"o"}).equals(List.of("Other")),
                "feed completion is permission-aware and filtered");
    }

    private static void testPingClearAndEnderChest() {
        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL,
                PingCommand.PERMISSION, ClearCommand.PERMISSION, EnderChestCommand.PERMISSION);
        MutablePlayer other = fixture.player("Other", GameMode.SURVIVAL);
        self.ping = 24;
        other.ping = 42;

        PingCommand ping = new PingCommand(fixture::lookup, fixture::onlinePlayers, fixture.messages);
        ping.onCommand(self.player, null, "ping", new String[0]);
        check(fixture.last().equals("Your ping: 24 ms"), "ping reports self latency");
        ping.onCommand(self.player, null, "ping", new String[]{"Other"});
        check(fixture.last().contains("permission"), "ping others requires its dedicated permission");
        self.permissions.add(PingCommand.OTHERS_PERMISSION);
        ping.onCommand(self.player, null, "ping", new String[]{"oThEr"});
        check(fixture.last().equals("Other's ping: 42 ms"), "ping reports a case-insensitive online target");
        ping.onCommand(fixture.console(Set.of(PingCommand.OTHERS_PERMISSION)), null,
                "ping", new String[]{"Other"});
        check(fixture.last().equals("Other's ping: 42 ms"), "ping supports an authorized console target");
        ping.onCommand(fixture.console(Set.of()), null, "ping", new String[0]);
        check(fixture.last().contains("target is required"), "ping console requires a target");
        ping.onCommand(self.player, null, "ping", new String[]{"Missing"});
        check(fixture.last().equals("Player 'Missing' is not online."), "ping rejects unknown targets");
        self.permissions.remove(PingCommand.OTHERS_PERMISSION);
        check(ping.onTabComplete(self.player, null, "ping", new String[]{""}).isEmpty(),
                "ping hides completion without others permission");

        ClearCommand clear = new ClearCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.activityPlayers::contains, fixture.buildPlayers::contains, fixture.messages
        , fixture.staff.guard);
        clear.onCommand(fixture.console(Set.of(ClearCommand.OTHERS_PERMISSION)), null,
                "clear", new String[0]);
        check(fixture.last().contains("target is required"), "clear console requires a target");
        clear.onCommand(self.player, null, "clear", new String[]{"Other"});
        check(fixture.last().contains("another player's inventory") && other.inventoryClearCalls == 0,
                "clear self permission never mutates another inventory");
        self.permissions.add(ClearCommand.OTHERS_PERMISSION);
        fixture.activityPlayers.add(other.id);
        clear.onCommand(self.player, null, "clear", new String[]{"Other"});
        check(fixture.last().contains("controlled by an activity") && other.inventoryClearCalls == 0,
                "clear protects activity-owned inventories");
        fixture.activityPlayers.clear();
        fixture.buildPlayers.add(other.id);
        clear.onCommand(self.player, null, "clear", new String[]{"Other"});
        check(fixture.last().contains("controlled by build mode") && other.inventoryClearCalls == 0,
                "clear protects build-owned inventories");
        fixture.buildPlayers.clear();
        clear.onCommand(self.player, null, "clear", new String[]{"Other"});
        check(other.inventoryClearCalls == 1 && other.armorCleared && other.offhandCleared
                        && fixture.last().equals("Cleared Other's inventory."),
                "clear safely clears storage, armor and offhand after all guards");

        EnderChestCommand enderChest = new EnderChestCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers, fixture.messages
        , fixture.staff.guard);
        enderChest.onCommand(fixture.console(Set.of(EnderChestCommand.OTHERS_PERMISSION)), null,
                "enderchest", new String[]{"Other"});
        check(fixture.last().contains("only be used by a player"), "enderchest denies console GUI access");
        enderChest.onCommand(self.player, null, "enderchest", new String[0]);
        check(self.openedInventory == self.enderChest, "enderchest opens the player's own live chest");
        enderChest.onCommand(self.player, null, "enderchest", new String[]{"Other"});
        check(fixture.last().contains("another player's ender chest")
                        && self.openedInventory != other.enderChest,
                "enderchest others requires its dedicated permission");
        self.permissions.add(EnderChestCommand.OTHERS_PERMISSION);
        enderChest.onCommand(self.player, null, "enderchest", new String[]{"other"});
        check(self.openedInventory == other.enderChest
                        && fixture.last().equals("Opened Other's ender chest."),
                "enderchest opens only an exact online target's live chest");
        other.online = false;
        enderChest.onCommand(self.player, null, "enderchest", new String[]{"Other"});
        check(fixture.last().equals("Player 'Other' is not online."),
                "enderchest performs no offline-player lookup");
    }

    private static void testExplicitSelfAndCommandBoundaries() {
        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL, FlyCommand.PERMISSION, SpeedCommand.PERMISSION,
                GameModeCommand.PERMISSION, HealCommand.PERMISSION, FeedCommand.PERMISSION, PingCommand.PERMISSION,
                ClearCommand.PERMISSION, EnderChestCommand.PERMISSION);
        List<TabExecutor> commands = List.of(
                new FlyCommand(fixture.service, fixture::lookup, fixture::onlinePlayers,
                        fixture.buildPlayers::contains, fixture.activityPlayers::contains, fixture.messages, fixture.staff.guard),
                new SpeedCommand(fixture.service, fixture::lookup, fixture::onlinePlayers,
                        fixture.activityPlayers::contains, fixture.messages, fixture.staff.guard),
                new GameModeCommand(fixture.service, fixture::lookup, fixture::onlinePlayers,
                        fixture.buildPlayers::contains, fixture.activityPlayers::contains, player -> false, fixture.messages, fixture.staff.guard),
                new HealCommand(fixture.service, fixture::lookup, fixture::onlinePlayers, fixture.activityPlayers::contains, fixture.messages, fixture.staff.guard),
                new FeedCommand(fixture.service, fixture::lookup, fixture::onlinePlayers, fixture.activityPlayers::contains, fixture.messages, fixture.staff.guard),
                new PingCommand(fixture::lookup, fixture::onlinePlayers, fixture.messages),
                new ClearCommand(fixture.service, fixture::lookup, fixture::onlinePlayers,
                        fixture.activityPlayers::contains, fixture.buildPlayers::contains, fixture.messages, fixture.staff.guard),
                new EnderChestCommand(fixture.service, fixture::lookup, fixture::onlinePlayers, fixture.messages, fixture.staff.guard));
        List<String[]> explicitSelf = List.of(new String[]{"sElF"}, new String[]{"5", "sElF"},
                new String[]{"survival", "sElF"}, new String[]{"sElF"}, new String[]{"sElF"},
                new String[]{"sElF"}, new String[]{"sElF"}, new String[]{"sElF"});
        List<String> expected = List.of("Flight enabled.", "Walk speed set to 5/10.", "Game mode changed to Survival.",
                "Healed.", "Fed.", "Your ping:", "Inventory cleared.", "");
        List<String> permissions = List.of(FlyCommand.PERMISSION, SpeedCommand.PERMISSION, GameModeCommand.PERMISSION,
                HealCommand.PERMISSION, FeedCommand.PERMISSION, PingCommand.PERMISSION, ClearCommand.PERMISSION, EnderChestCommand.PERMISSION);
        for (int i = 0; i < commands.size(); i++) {
            TabExecutor command = commands.get(i);
            String[] selfArgs = explicitSelf.get(i);
            command.onCommand(self.player, null, "utility", selfArgs);
            check(command instanceof EnderChestCommand ? self.openedInventory == self.enderChest : fixture.last().contains(expected.get(i)),
                    command.getClass().getSimpleName() + " explicit same-UUID target needs only base permission; got " + fixture.last());
            self.permissions.remove(permissions.get(i));
            command.onCommand(self.player, null, "utility", selfArgs);
            check(fixture.last().contains("permission"), command.getClass().getSimpleName() + " executor denies missing base permission");
            check(command.onTabComplete(self.player, null, "utility", new String[]{""}).isEmpty(),
                    command.getClass().getSimpleName() + " denied completion leaks no values");
            self.permissions.add(permissions.get(i));
            String[] extra = java.util.Arrays.copyOf(selfArgs, selfArgs.length + 1);
            extra[extra.length - 1] = "extra";
            command.onCommand(self.player, null, "utility", extra);
            check(fixture.last().contains("Invalid usage."), command.getClass().getSimpleName() + " rejects extra arguments");
            command.onCommand(fixture.console(Set.of(permissions.get(i))), null, "utility", selfArgs);
            check(fixture.last().contains("permission") || fixture.last().contains("only be used by a player"),
                    command.getClass().getSimpleName() + " console cannot use base permission as others permission");
        }
    }


    private static void testStaffTargetProtection() {
        List<String> groups = java.util.Arrays.asList("default", "vip", "builder", "moderator", "admin", "owner", null);
        for (String actorGroup : groups) for (String targetGroup : groups) {
            for (int index = 0; index < 8; index++) {
                Fixture f = new Fixture();
                MutablePlayer actor = f.player("Actor", GameMode.SURVIVAL);
                MutablePlayer target = f.player("Target", GameMode.SURVIVAL);
                actor.permissions.addAll(staffPermissions());
                f.staff.groups.put(actor.id, actorGroup); f.staff.groups.put(target.id, targetGroup);
                target.health = 1; target.food = 1;
                List<TabExecutor> commands = staffCommands(f);
                String[] args = staffArguments(index, "Target");
                int al = (actorGroup == null ? -1 : dev.vapee.core.rank.staff.StaffHierarchyConfig.DEFAULT_GROUPS.indexOf(actorGroup));
                int tl = (targetGroup == null ? -1 : dev.vapee.core.rank.staff.StaffHierarchyConfig.DEFAULT_GROUPS.indexOf(targetGroup));
                boolean allowed = targetGroup != null && (tl < 0 || actorGroup != null && al > tl);
                commands.get(index).onCommand(actor.player, null, "staff", args);
                check(allowed ? target.writes + actor.writes > 0 : target.writes + actor.writes == 0,
                        commands.get(index).getClass().getSimpleName() + " real side effects " + actorGroup + " -> " + targetGroup);
                if (!allowed) check(f.last().equals(targetGroup == null || actorGroup == null && tl >= 0
                        ? dev.vapee.core.command.OnlineStaffTargetGuard.UNAVAILABLE_MESSAGE
                        : dev.vapee.core.command.OnlineStaffTargetGuard.DENIED_MESSAGE), "safe staff denial");
                String[] completion = index == 1 ? new String[]{"5", ""} : index == 2
                        ? new String[]{"creative", ""} : new String[]{""};
                check(commands.get(index).onTabComplete(actor.player, null, "staff", completion).contains("Target") == allowed,
                        "completion follows scoped target decision");
            }
        }
        for (int index = 0; index < 8; index++) {
            Fixture f = new Fixture();
            MutablePlayer actor = f.player("Actor", GameMode.SURVIVAL);
            MutablePlayer target = f.player("Target", GameMode.SURVIVAL);
            actor.permissions.addAll(staffPermissions());
            f.staff.groups.put(actor.id, "admin"); f.staff.groups.put(target.id, "owner");
            // Existing Activity/BUILD guards must not mask hierarchy; bypass is only a state bypass.
            f.activityPlayers.add(target.id); f.buildPlayers.add(target.id);
            List<TabExecutor> commands = staffCommands(f);
            commands.get(index).onCommand(actor.player, null, "staff", staffArguments(index, "Target"));
            check(f.last().equals(dev.vapee.core.command.OnlineStaffTargetGuard.DENIED_MESSAGE)
                    && target.writes + actor.writes == 0, "hierarchy before Activity/BUILD/open/mutation " + index);
            actor.permissions.clear();
            int reads = f.staff.reads;
            commands.get(index).onCommand(actor.player, null, "staff", staffArguments(index, "Target"));
            check(f.last().contains("permission") && f.staff.reads == reads, "capability before loaded hierarchy " + index);
            f.activityPlayers.clear(); f.buildPlayers.clear();
            target.health = 1; target.food = 1;
            CommandSender console = f.console(staffPermissions());
            commands.get(index).onCommand(console, null, "staff", staffArguments(index, "Target"));
            boolean consoleSupported = index < 6;
            check(consoleSupported ? target.writes > 0 : target.writes == 0 && actor.writes == 0,
                    "console command-specific authority " + index);
            if (!consoleSupported) check(commands.get(index).onTabComplete(console, null, "staff", new String[]{""}).isEmpty(),
                    "player-only completion never offers console GUI/destination");
            int writes = target.writes + actor.writes;
            CommandSender unsupported = proxy(CommandSender.class, (method, values) -> method.getName().equals("hasPermission")
                    ? true : defaultValue(method.getReturnType()));
            commands.get(index).onCommand(unsupported, null, "staff", staffArguments(index, "Target"));
            check(target.writes + actor.writes == writes, "unsupported sender never gets console authority " + index);
        }
        Fixture f = new Fixture();
        MutablePlayer actor = f.player("Actor", GameMode.SURVIVAL);
        MutablePlayer target = f.player("Target", GameMode.SURVIVAL);
        actor.permissions.addAll(staffPermissions());
        f.staff.groups.put(actor.id, "admin"); f.staff.groups.put(target.id, "owner");
        f.service.toggleFlight(target.player);
        target.gameMode = GameMode.CREATIVE;
        int writes = target.writes;
        staffCommands(f).getFirst().onCommand(actor.player, null, "fly", new String[]{"Target"});
        check(f.service.hasManagedFlight(target.id) && target.writes == writes,
                "denied creative fly preserves managed ownership without cleanup");
        staffCommands(f).get(2).onCommand(actor.player, null, "gamemode", new String[]{"survival", "Target"});
        check(f.service.hasManagedFlight(target.id) && target.writes == writes && target.gameMode == GameMode.CREATIVE,
                "denied gamemode preserves managed flight and native mode");

        f.staff.groups.put(actor.id, null);
        for (int index = 0; index < 8; index++) {
            int reads = f.staff.reads;
            staffCommands(f).get(index).onCommand(actor.player, null, "self", staffArguments(index, "Actor"));
            check(f.staff.reads == reads && !f.last().contains("hierarchy"), "explicit self never queries staff " + index);
        }
        f.staff.fail = true;
        target.writes = 0; actor.writes = 0;
        for (int index = 0; index < 8; index++) {
            staffCommands(f).get(index).onCommand(actor.player, null, "exception", staffArguments(index, "Target"));
            check(f.last().equals(dev.vapee.core.command.OnlineStaffTargetGuard.UNAVAILABLE_MESSAGE)
                    && actor.writes + target.writes == 0, "loaded lookup exception no effect " + index);
        }
        // Ping is intentionally a non-sensitive read, even for unavailable/protected staff.
        actor.permissions.add(PingCommand.OTHERS_PERMISSION);
        new PingCommand(f::lookup, f::onlinePlayers, f.messages).onCommand(actor.player, null, "ping", new String[]{"Target"});
        check(f.last().contains("ping:"), "ping remains capability-only, not hierarchy protected");
    }

    private static void testCompletionContract() {
        Fixture f = new Fixture();
        MutablePlayer self = f.player("Zulu", GameMode.SURVIVAL);
        self.permissions.addAll(staffPermissions());
        f.player("Beta", GameMode.SURVIVAL);
        f.player("alpha", GameMode.SURVIVAL);
        f.player("Alpine", GameMode.SURVIVAL);
        f.player("Absent", GameMode.SURVIVAL).online = false;
        List<TabExecutor> commands = staffCommands(f).subList(0, 5);
        List<String> roots = List.of("fly", "speed", "gamemode", "heal", "feed");
        List<String> usages = List.of("/fly [player]", "/speed <1-10> [player]",
                "/gamemode <mode> [player]", "/heal [player]", "/feed [player]");
        List<String> others = List.of(FlyCommand.OTHERS_PERMISSION, SpeedCommand.OTHERS_PERMISSION,
                GameModeCommand.OTHERS_PERMISSION, HealCommand.OTHERS_PERMISSION, FeedCommand.OTHERS_PERMISSION);
        for (int i = 0; i < commands.size(); i++) {
            TabExecutor command = commands.get(i);
            String root = roots.get(i);
            for (CommandSender sender : List.of(self.player, f.console(staffPermissions()))) {
                for (String prefix : List.of("", "aL", "bE", "nothing")) {
                    List<String> expected = switch (prefix) {
                        case "" -> List.of("alpha", "Alpine", "Beta", "Zulu");
                        case "aL" -> List.of("alpha", "Alpine");
                        case "bE" -> List.of("Beta");
                        default -> List.of();
                    };
                    check(command.onTabComplete(sender, null, root, staffArguments(i, prefix)).equals(expected),
                            root + " actual caller sorted online candidates, prefix=" + prefix);
                }
            }
            self.permissions.remove(others.get(i));
            check(command.onTabComplete(self.player, null, root, staffArguments(i, "")).isEmpty(),
                    root + " missing others hides even self suggestions");
            check(command.onTabComplete(f.console(Set.of()), null, root, staffArguments(i, "")).isEmpty(),
                    root + " console missing others has no suggestions");
            command.onCommand(self.player, null, root, staffArguments(i, "Absent"));
            check(f.last().equals("Player 'Absent' is not online."), root + " lookup failure precedes permission");
            command.onCommand(self.player, null, root, staffArguments(i, "missing"));
            check(f.last().equals("Player 'missing' is not online."), root + " exact unknown feedback");
            self.permissions.add(others.get(i));
            String[] extra = java.util.Arrays.copyOf(staffArguments(i, "Beta"), staffArguments(i, "Beta").length + 1);
            extra[extra.length - 1] = "extra";
            command.onCommand(self.player, null, root, extra);
            check(f.last().equals("Invalid usage.\nUse: " + usages.get(i)), root + " exact arity/usage preserved");
            check(command.onTabComplete(self.player, null, root, extra).isEmpty(), root + " extra completion arity");
            check(command.onTabComplete(self.player, null, root, new String[0]).isEmpty(), root + " zero completion arity");
            Fixture empty = new Fixture();
            check(staffCommands(empty).get(i).onTabComplete(empty.console(staffPermissions()), null, root,
                    staffArguments(i, "")).isEmpty(), root + " empty online universe");
        }
        check(f.players.stream().allMatch(player -> player.writes == 0), "completion and invalid input never mutate players");
    }

    private static Set<String> staffPermissions() {
        return Set.of(FlyCommand.PERMISSION, FlyCommand.OTHERS_PERMISSION, SpeedCommand.PERMISSION, SpeedCommand.OTHERS_PERMISSION,
                GameModeCommand.PERMISSION, GameModeCommand.OTHERS_PERMISSION, HealCommand.PERMISSION, HealCommand.OTHERS_PERMISSION,
                FeedCommand.PERMISSION, FeedCommand.OTHERS_PERMISSION, ClearCommand.PERMISSION, ClearCommand.OTHERS_PERMISSION,
                EnderChestCommand.PERMISSION, EnderChestCommand.OTHERS_PERMISSION, TeleportHereCommand.PERMISSION,
                TeleportHereCommand.BYPASS_PERMISSION);
    }

    private static List<TabExecutor> staffCommands(Fixture f) {
        return List.of(
                new FlyCommand(f.service, f::lookup, f::onlinePlayers, f.buildPlayers::contains, f.activityPlayers::contains, f.messages, f.staff.guard),
                new SpeedCommand(f.service, f::lookup, f::onlinePlayers, f.activityPlayers::contains, f.messages, f.staff.guard),
                new GameModeCommand(f.service, f::lookup, f::onlinePlayers, f.buildPlayers::contains, f.activityPlayers::contains, p -> false, f.messages, f.staff.guard),
                new HealCommand(f.service, f::lookup, f::onlinePlayers, f.activityPlayers::contains, f.messages, f.staff.guard),
                new FeedCommand(f.service, f::lookup, f::onlinePlayers, f.activityPlayers::contains, f.messages, f.staff.guard),
                new ClearCommand(f.service, f::lookup, f::onlinePlayers, f.activityPlayers::contains, f.buildPlayers::contains, f.messages, f.staff.guard),
                new TeleportHereCommand(f.service, f::lookup, f::onlinePlayers, f.activityPlayers::contains, f.messages, f.staff.guard),
                new EnderChestCommand(f.service, f::lookup, f::onlinePlayers, f.messages, f.staff.guard));
    }

    private static String[] staffArguments(int index, String target) {
        return index == 1 ? new String[]{"5", target} : index == 2
                ? new String[]{"creative", target} : new String[]{target};
    }

    private static boolean close(double actual, double expected) {
        return Math.abs(actual - expected) < 0.00001D;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    private static final class Fixture {
        private final StaffTargetTestFixture staff = new StaffTargetTestFixture();
        private final List<Component> output = new ArrayList<>();
        private final List<MutablePlayer> players = new ArrayList<>();
        private final Set<UUID> buildPlayers = new HashSet<>();
        private final Set<UUID> activityPlayers = new HashSet<>();
        private final BiConsumer<CommandSender, Component> messages = (sender, component) -> output.add(component);
        private final UtilityService service = new UtilityService(
                () -> true,
                this::onlinePlayers,
                player -> players.stream()
                        .filter(candidate -> candidate.player == player)
                        .findFirst()
                        .map(candidate -> candidate.maxHealth)
                        .orElse(20.0D)
        );

        private MutablePlayer player(String name, GameMode gameMode, String... permissions) {
            MutablePlayer player = new MutablePlayer(name, gameMode, Set.of(permissions));
            players.add(player);
            return player;
        }

        private Player lookup(String name) {
            return players.stream()
                    .filter(candidate -> candidate.online && candidate.name.equalsIgnoreCase(name))
                    .map(candidate -> candidate.player)
                    .findFirst()
                    .orElse(null);
        }

        private Collection<Player> onlinePlayers() {
            return players.stream().filter(player -> player.online).map(player -> player.player).toList();
        }

        private CommandSender console(Set<String> permissions) {
            return proxy(ConsoleCommandSender.class, (method, arguments) -> switch (method.getName()) {
                case "hasPermission" -> permissions.contains((String) arguments[0]);
                case "getName" -> "CONSOLE";
                default -> defaultValue(method.getReturnType());
            });
        }

        private String last() {
            return PLAIN.serialize(output.getLast());
        }
    }

    private static final class MutablePlayer {
        private final UUID id = UUID.randomUUID();
        private final String name;
        private final Set<String> permissions;
        private final Player player;
        private GameMode gameMode;
        private boolean online = true;
        private boolean allowFlight;
        private boolean flying;
        private float walkSpeed = 0.2F;
        private float flySpeed = 0.1F;
        private double health = 20.0D;
        private double maxHealth = 20.0D;
        private int food = 20;
        private float saturation = 5.0F;
        private float exhaustion;
        private int fireTicks;
        private int freezeTicks;
        private boolean teleportResult = true;
        private boolean teleported;
        private int ping;
        private int writes;
        private int inventoryClearCalls;
        private boolean armorCleared;
        private boolean offhandCleared;
        private final Inventory enderChest;
        private final PlayerInventory inventory;
        private Inventory openedInventory;

        private MutablePlayer(String name, GameMode gameMode, Set<String> permissions) {
            this.name = name;
            this.gameMode = gameMode;
            this.permissions = new HashSet<>(permissions);
            this.enderChest = proxy(Inventory.class, (method, arguments) -> defaultValue(method.getReturnType()));
            this.inventory = proxy(PlayerInventory.class, (method, arguments) -> switch (method.getName()) {
                case "clear" -> set(() -> { writes++;  inventoryClearCalls++; });
                case "setArmorContents" -> set(() -> { writes++;  armorCleared = true; });
                case "setItemInOffHand" -> set(() -> { writes++;  offhandCleared = arguments[0] == null; });
                default -> defaultValue(method.getReturnType());
            });
            this.player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> online;
                case "hasPermission" -> this.permissions.contains((String) arguments[0]);
                case "getGameMode" -> this.gameMode;
                case "setGameMode" -> set(() -> { writes++;  this.gameMode = (GameMode) arguments[0]; });
                case "getAllowFlight" -> allowFlight;
                case "setAllowFlight" -> set(() -> { writes++;  allowFlight = (boolean) arguments[0]; });
                case "isFlying" -> flying;
                case "setFlying" -> set(() -> { writes++;  flying = (boolean) arguments[0]; });
                case "getWalkSpeed" -> walkSpeed;
                case "setWalkSpeed" -> set(() -> { writes++;  walkSpeed = (float) arguments[0]; });
                case "getFlySpeed" -> flySpeed;
                case "setFlySpeed" -> set(() -> { writes++;  flySpeed = (float) arguments[0]; });
                case "getHealth" -> health;
                case "setHealth" -> set(() -> { writes++;  health = (double) arguments[0]; });
                case "getMaxHealth" -> maxHealth;
                case "getFoodLevel" -> food;
                case "setFoodLevel" -> set(() -> { writes++;  food = (int) arguments[0]; });
                case "getSaturation" -> saturation;
                case "setSaturation" -> set(() -> { writes++;  saturation = (float) arguments[0]; });
                case "getExhaustion" -> exhaustion;
                case "setExhaustion" -> set(() -> { writes++;  exhaustion = (float) arguments[0]; });
                case "setFireTicks" -> set(() -> { writes++;  fireTicks = (int) arguments[0]; });
                case "setFreezeTicks" -> set(() -> { writes++;  freezeTicks = (int) arguments[0]; });
                case "getPing" -> ping;
                case "getInventory" -> inventory;
                case "getEnderChest" -> enderChest;
                case "openInventory" -> set(() -> { writes++;  openedInventory = (Inventory) arguments[0]; });
                case "getLocation" -> new Location(null, 1.0D, 2.0D, 3.0D);
                case "teleport" -> {
                    writes++;
                    teleported = teleportResult;
                    yield teleportResult;
                }
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static Object set(Runnable action) {
        action.run();
        return null;
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "HarnessProxy";
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    default -> null;
                };
            }
            return handler.invoke(method, args == null ? new Object[0] : args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == float.class) {
            return 0.0F;
        }
        if (type == double.class) {
            return 0.0D;
        }
        if (type == long.class) {
            return 0L;
        }
        return 0;
    }

    @FunctionalInterface
    private interface ProxyHandler {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }
}
