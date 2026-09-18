package dev.vapee.core.utility.command;

import dev.vapee.core.utility.UtilityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
        testFly();
        testSpeed();
        testGameMode();
        testTeleport();
        testTeleportHere();
        testHealAndFeed();
        System.out.println("UtilityCommandHarness passed " + checks + " checks.");
    }

    private static void testFly() {
        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL, FlyCommand.PERMISSION);
        MutablePlayer other = fixture.player("Other", GameMode.ADVENTURE);
        FlyCommand command = new FlyCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.buildPlayers::contains, fixture.activityPlayers::contains, fixture.messages
        );

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
        );
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
        );
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
        TeleportCommand command = new TeleportCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.activityPlayers::contains, fixture.messages
        );
        command.onCommand(fixture.console(Set.of(TeleportCommand.PERMISSION)), null, "tp", new String[]{"Other"});
        check(fixture.last().contains("only be used by a player"), "tp is player-only");
        command.onCommand(self.player, null, "tp", new String[]{"Missing"});
        check(fixture.last().equals("Player 'Missing' is not online."), "tp rejects offline targets");
        command.onCommand(self.player, null, "tp", new String[]{"Self"});
        check(fixture.last().equals("You are already that player."), "tp handles self without teleporting");
        fixture.activityPlayers.add(self.id);
        command.onCommand(self.player, null, "tp", new String[]{"Other"});
        check(fixture.last().contains("participating in an activity"), "tp blocks activity senders");
        fixture.activityPlayers.clear();
        self.teleportResult = false;
        command.onCommand(self.player, null, "tp", new String[]{"Other"});
        check(fixture.last().equals("Teleport failed or was cancelled."), "tp reports a false teleport result");
        self.teleportResult = true;
        command.onCommand(self.player, null, "tp", new String[]{"Other"});
        check(fixture.last().equals("Teleported to Other."), "tp reports successful teleport");
        check(command.onTabComplete(self.player, null, "tp", new String[]{""}).equals(List.of("Other")),
                "tp completion filters self");
    }

    private static void testTeleportHere() {
        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL, TeleportHereCommand.PERMISSION);
        MutablePlayer other = fixture.player("Other", GameMode.SURVIVAL);
        TeleportHereCommand command = new TeleportHereCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.activityPlayers::contains, fixture.messages
        );
        command.onCommand(fixture.console(Set.of(TeleportHereCommand.PERMISSION)), null,
                "tphere", new String[]{"Other"});
        check(fixture.last().contains("only be used by a player"), "tphere is player-only");
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
        check(command.onTabComplete(self.player, null, "tphere", new String[]{""}).equals(List.of("Other")),
                "tphere completion filters self");
    }

    private static void testHealAndFeed() {
        Fixture fixture = new Fixture();
        MutablePlayer self = fixture.player("Self", GameMode.SURVIVAL,
                HealCommand.PERMISSION, FeedCommand.PERMISSION);
        MutablePlayer other = fixture.player("Other", GameMode.SURVIVAL);
        HealCommand heal = new HealCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.activityPlayers::contains, fixture.messages
        );
        FeedCommand feed = new FeedCommand(
                fixture.service, fixture::lookup, fixture::onlinePlayers,
                fixture.activityPlayers::contains, fixture.messages
        );
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
        check(heal.onTabComplete(self.player, null, "heal", new String[]{"o"}).equals(List.of("Other")),
                "heal completion is permission-aware and filtered");
        check(feed.onTabComplete(self.player, null, "feed", new String[]{"o"}).equals(List.of("Other")),
                "feed completion is permission-aware and filtered");
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
        private final List<Component> output = new ArrayList<>();
        private final List<MutablePlayer> players = new ArrayList<>();
        private final Map<String, MutablePlayer> playersByExactName = new HashMap<>();
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
            playersByExactName.put(name, player);
            return player;
        }

        private Player lookup(String name) {
            MutablePlayer player = playersByExactName.get(name);
            return player == null || !player.online ? null : player.player;
        }

        private Collection<Player> onlinePlayers() {
            return players.stream().filter(player -> player.online).map(player -> player.player).toList();
        }

        private CommandSender console(Set<String> permissions) {
            return proxy(CommandSender.class, (method, arguments) -> switch (method.getName()) {
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

        private MutablePlayer(String name, GameMode gameMode, Set<String> permissions) {
            this.name = name;
            this.gameMode = gameMode;
            this.permissions = new HashSet<>(permissions);
            this.player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> online;
                case "hasPermission" -> this.permissions.contains((String) arguments[0]);
                case "getGameMode" -> this.gameMode;
                case "setGameMode" -> set(() -> this.gameMode = (GameMode) arguments[0]);
                case "getAllowFlight" -> allowFlight;
                case "setAllowFlight" -> set(() -> allowFlight = (boolean) arguments[0]);
                case "isFlying" -> flying;
                case "setFlying" -> set(() -> flying = (boolean) arguments[0]);
                case "getWalkSpeed" -> walkSpeed;
                case "setWalkSpeed" -> set(() -> walkSpeed = (float) arguments[0]);
                case "getFlySpeed" -> flySpeed;
                case "setFlySpeed" -> set(() -> flySpeed = (float) arguments[0]);
                case "getHealth" -> health;
                case "setHealth" -> set(() -> health = (double) arguments[0]);
                case "getMaxHealth" -> maxHealth;
                case "getFoodLevel" -> food;
                case "setFoodLevel" -> set(() -> food = (int) arguments[0]);
                case "getSaturation" -> saturation;
                case "setSaturation" -> set(() -> saturation = (float) arguments[0]);
                case "getExhaustion" -> exhaustion;
                case "setExhaustion" -> set(() -> exhaustion = (float) arguments[0]);
                case "setFireTicks" -> set(() -> fireTicks = (int) arguments[0]);
                case "setFreezeTicks" -> set(() -> freezeTicks = (int) arguments[0]);
                case "getLocation" -> new Location(null, 1.0D, 2.0D, 3.0D);
                case "teleport" -> teleportResult;
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
