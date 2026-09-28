package dev.vapee.core.utility.command;

import dev.vapee.core.utility.UtilityService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class TeleportCommandHarness {
    private static int checks;

    public static void main(String[] args) {
        commands();
        permissionsAndGuards();
        completion();
        System.out.println("TeleportCommandHarness passed " + checks + " checks.");
    }

    private static void commands() {
        Fixture f = new Fixture();
        f.self.permissions.add(TeleportCommand.PERMISSION);
        run(f, f.self.player, "Alex");
        check(f.self.last.getWorld() == f.nether && f.last().equals("Teleported to Alex."), "crossworld player no world right");
        f.self.last = new Location(f.world, 100, 64, -20);
        run(f, f.self.player, "100", "64", "-250");
        pos(f.self.last, 100, 64, -250, f.world, "self absolute");
        check(f.last().equals("Teleported to 100.00, 64.00, -250.00."), "formatted coordinates");
        run(f, f.self.player, "~5", "~10", "~-2");
        pos(f.self.last, 105, 74, -252, f.world, "self relative source basis");
        run(f, f.self.player, "^", "^2", "^5");
        pos(f.self.last, 105, 76, -247, f.world, "self local source yaw");
        run(f, f.self.player, "0", "80", "0", "~90", "~-10");
        check(f.self.last.getYaw() == 90 && f.self.last.getPitch() == -10, "self relative rotation");
        f.self.permissions.add(TeleportCommand.OTHERS_PERMISSION);
        run(f, f.self.player, "Steve", "Alex");
        check(f.steve.last.getWorld() == f.nether && f.last().equals("Teleported Steve to Alex."), "other player crossworld");
        f.steve.last = new Location(f.world, 0, 70, 0);
        run(f, f.self.player, "Steve", "1", "2", "3");
        pos(f.steve.last, 1, 2, 3, f.world, "other absolute");
        run(f, f.self.player, "Steve", "~5", "~", "~-2");
        pos(f.steve.last, 6, 2, 1, f.world, "other relative uses Steve");
        f.steve.last.setYaw(90);
        run(f, f.self.player, "Steve", "^", "^", "^5");
        pos(f.steve.last, 1, 2, 1, f.world, "other local uses Steve yaw");
        run(f, f.self.player, "Steve", "0", "70", "0", "180", "100");
        check(f.steve.last.getYaw() == 180 && f.steve.last.getPitch() == 90, "other rotation pitch clamp");
        f.self.permissions.add(TeleportCommand.WORLD_PERMISSION);
        run(f, f.self.player, "world", "WORLD_NETHER", "10", "20", "30");
        pos(f.self.last, 10, 20, 30, f.nether, "case-insensitive explicit self world");
        f.self.permissions.add(TeleportCommand.OTHERS_WORLD_PERMISSION);
        run(f, f.self.player, "Steve", "world", "world_nether", "~", "~10", "~");
        pos(f.steve.last, 0, 80, 0, f.nether, "other explicit relative no scaling");
        f.steve.last.setPitch(0);
        run(f, f.self.player, "Steve", "world", "world_nether", "^", "^", "^5", "~", "~");
        pos(f.steve.last, 0, 80, -5, f.nether, "other explicit local and rotation");
        run(f, f.console(Set.of(TeleportCommand.OTHERS_PERMISSION)), "Steve", "Alex");
        check(f.last().equals("Teleported Steve to Alex."), "console other player");
        run(f, f.console(Set.of(TeleportCommand.OTHERS_PERMISSION)), "Steve", "~", "~5", "~");
        check(f.last().startsWith("Teleported Steve to"), "console other coordinates");
        run(f, f.console(Set.of(TeleportCommand.OTHERS_WORLD_PERMISSION)), "Steve", "world", "world_nether", "1", "2", "3");
        pos(f.steve.last, 1, 2, 3, f.nether, "console explicit world");
        run(f, f.console(Set.of(TeleportCommand.PERMISSION)), "Alex");
        check(f.last().contains("source player is required"), "console implicit player denied");
        run(f, f.console(Set.of(TeleportCommand.PERMISSION)), "1", "2", "3");
        check(f.last().contains("source player is required"), "console implicit coords denied");
        run(f, f.console(Set.of(TeleportCommand.WORLD_PERMISSION)), "world", "world", "1", "2", "3");
        check(f.last().contains("source player is required"), "console implicit world denied");
    }

    private static void permissionsAndGuards() {
        Fixture f = new Fixture();
        run(f, f.self.player, "Alex");
        check(f.last().contains("permission") && f.self.calls == 0, "self permission");
        f.self.permissions.add(TeleportCommand.PERMISSION);
        run(f, f.self.player, "Steve", "Alex");
        check(f.last().contains("permission") && f.steve.calls == 0, "others permission");
        run(f, f.self.player, "world", "world_nether", "1", "2", "3");
        check(f.last().contains("permission") && f.self.calls == 0, "world permission");
        f.self.permissions.add(TeleportCommand.OTHERS_PERMISSION);
        run(f, f.self.player, "Steve", "world", "world_nether", "1", "2", "3");
        check(f.last().contains("permission") && f.steve.calls == 0, "others world permission");
        f.self.permissions.add(TeleportCommand.OTHERS_WORLD_PERMISSION);
        run(f, f.self.player, "world", "missing", "1", "2", "3");
        check(f.last().equals("You do not have permission to teleport."), "others world does not imply self world in direct mock");
        f.self.permissions.add(TeleportCommand.WORLD_PERMISSION);
        run(f, f.self.player, "world", "missing", "1", "2", "3");
        check(f.last().equals("Unknown world 'missing'."), "unknown world distinct");
        run(f, f.self.player, "Missing", "Alex");
        check(f.last().equals("Player 'Missing' is not online."), "unknown source");
        run(f, f.self.player, "Steve", "Missing");
        check(f.last().equals("Player 'Missing' is not online."), "unknown target");
        run(f, f.self.player, "Ste", "Alex");
        check(f.last().contains("not online"), "partial source rejected");
        run(f, f.self.player, "Steve", "Ste");
        check(f.last().contains("not online"), "partial target rejected");
        run(f, f.self.player, "Steve", "steve");
        check(f.last().contains("same player"), "same player");
        int calls = f.self.calls;
        for (String token : List.of("NaN", "Infinity", "1e999999", "bad")) {
            run(f, f.self.player, token, "0", "0");
            check(f.last().contains("Invalid coordinate") && f.self.calls == calls, "invalid coordinate no teleport");
        }
        run(f, f.self.player, "^", "~", "10");
        check(f.last().contains("cannot be mixed") && f.self.calls == calls, "local mix rejected");
        run(f, f.self.player, "1", "2", "3", "NaN", "0");
        check(f.last().contains("Invalid rotation") && f.self.calls == calls, "invalid rotation rejected");
        f.activity.add(f.self.id);
        run(f, f.self.player, "1", "2", "3");
        check(f.last().contains("participating in an activity"), "coordinate activity source");
        run(f, f.self.player, "Alex");
        check(f.last().contains("participating in an activity"), "player activity source");
        f.activity.clear(); f.activity.add(f.alex.id);
        run(f, f.self.player, "Alex");
        check(f.last().contains("destination player is participating"), "player activity destination");
        f.self.permissions.add(TeleportCommand.BYPASS_PERMISSION);
        run(f, f.self.player, "Alex");
        check(f.last().equals("Teleported to Alex."), "bypass internal guard");
        f.self.acceptTeleport = false;
        run(f, f.self.player, "Alex");
        check(f.last().equals("Teleport failed or was cancelled."), "bypass does not bypass cancellation");
        f.self.acceptTeleport = true;
        f.alex.online = false;
        run(f, f.self.player, "Alex");
        check(f.last().contains("not online"), "bypass does not bypass offline lookup");
        run(f, f.self.player, "world", "missing", "1", "2", "3");
        check(f.last().contains("Unknown world"), "bypass does not bypass world lookup");
        run(f, f.self.player, "x");
        check(f.last().contains("not online"), "one argument is player not coordinate");
        run(f, f.self.player);
        check(f.last().contains("Invalid usage"), "wrong argument count gives usage");
    }

    private static void completion() {
        Fixture f = new Fixture();
        check(f.complete(f.self.player, "").isEmpty(), "no permission no suggestions");
        f.self.permissions.add(TeleportCommand.PERMISSION);
        check(f.complete(f.self.player, "").equals(List.of("^", "~", "Alex", "Steve")), "self completions sorted");
        check(f.complete(f.self.player, "a").equals(List.of("Alex")), "case-insensitive player prefix");
        check(f.complete(f.self.player, "~", "").equals(List.of("^", "~")), "self xyz completion");
        check(f.complete(f.self.player, "~", "~", "~", "").equals(List.of("~")), "yaw completion");
        check(!f.complete(f.self.player, "").contains("world"), "world keyword hidden");
        f.self.permissions.add(TeleportCommand.WORLD_PERMISSION);
        check(f.complete(f.self.player, "w").equals(List.of("world")), "world keyword");
        check(f.complete(f.self.player, "world", "WORLD_").equals(List.of("world_nether")), "loaded worlds prefix");
        f.self.permissions.add(TeleportCommand.OTHERS_PERMISSION);
        check(f.complete(f.self.player, "Steve", "").equals(List.of("^", "~", "Alex", "Self")), "other target suggestions");
        check(!f.complete(f.self.player, "Steve", "").contains("world"), "other world hidden");
        f.self.permissions.add(TeleportCommand.OTHERS_WORLD_PERMISSION);
        check(f.complete(f.self.player, "Steve", "w").equals(List.of("world")), "other world keyword");
        check(f.complete(f.self.player, "Steve", "world", "W").equals(List.of("world", "world_nether")), "other world names sorted");
        check(f.complete(f.console(Set.of()), "").isEmpty(), "console no permission hidden");
        CommandSender console = f.console(Set.of(TeleportCommand.OTHERS_PERMISSION));
        check(f.complete(console, "").equals(List.of("Alex", "Self", "Steve")), "console sources only");
        check(f.complete(console, "Steve", "").contains("Alex"), "console target names");
        check(!f.complete(console, "Steve", "").contains("world"), "console world hidden");
    }

    private static void run(Fixture fixture, CommandSender sender, String... tokens) {
        fixture.command.onCommand(sender, null, "tp", tokens);
    }
    private static void pos(Location actual, double x, double y, double z, World world, String message) {
        check(actual != null && actual.getWorld() == world && Math.abs(actual.getX() - x) < 0.00001
                && Math.abs(actual.getY() - y) < 0.00001 && Math.abs(actual.getZ() - z) < 0.00001, message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }

    private static final class Fixture {
        private final World world = world("world"), nether = world("world_nether");
        private final State self = new State("Self", world, 100, 64, -20);
        private final State steve = new State("Steve", world, 0, 70, 0);
        private final State alex = new State("Alex", nether, 5, 80, 5);
        private final List<State> players = List.of(self, steve, alex);
        private final Set<UUID> activity = new HashSet<>();
        private final List<Component> output = new ArrayList<>();
        private final UtilityService service = new UtilityService(() -> true,
                () -> players.stream().map(state -> state.player).toList(), ignored -> 20);
        private final TeleportCommand command = new TeleportCommand(service, this::lookup,
                () -> players.stream().filter(state -> state.online).map(state -> state.player).toList(),
                () -> List.of(world, nether), activity::contains, (sender, message) -> output.add(message));

        private Player lookup(String name) {
            return players.stream().filter(state -> state.online && state.name.equalsIgnoreCase(name))
                    .map(state -> state.player).findFirst().orElse(null);
        }
        private String last() { return PlainTextComponentSerializer.plainText().serialize(output.getLast()); }
        private List<String> complete(CommandSender sender, String... args) {
            return command.onTabComplete(sender, null, "tp", args);
        }
        private CommandSender console(Set<String> rights) {
            return (CommandSender) Proxy.newProxyInstance(CommandSender.class.getClassLoader(),
                    new Class<?>[]{CommandSender.class}, (proxy, method, args) -> {
                        if (method.getName().equals("hasPermission")) return rights.contains(args[0]);
                        if (method.getName().equals("getName")) return "CONSOLE";
                        return defaultValue(method.getReturnType());
                    });
        }
    }

    private static final class State {
        private final String name;
        private final UUID id = UUID.randomUUID();
        private final Set<String> permissions = new HashSet<>();
        private final Player player;
        private boolean online = true, acceptTeleport = true;
        private int calls;
        private Location last;

        private State(String name, World world, double x, double y, double z) {
            this.name = name;
            this.last = new Location(world, x, y, z);
            this.player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(),
                    new Class<?>[]{Player.class}, (proxy, method, args) -> switch (method.getName()) {
                        case "getUniqueId" -> id;
                        case "getName" -> name;
                        case "isOnline" -> online;
                        case "hasPermission" -> permissions.contains(args[0]);
                        case "getLocation" -> last.clone();
                        case "teleport" -> {
                            calls++;
                            if (acceptTeleport) last = ((Location) args[0]).clone();
                            yield acceptTeleport;
                        }
                        case "equals" -> proxy == args[0];
                        case "hashCode" -> System.identityHashCode(proxy);
                        default -> defaultValue(method.getReturnType());
                    });
        }
    }

    private static World world(String name) {
        return (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (proxy, method, args) -> method.getName().equals("getName") ? name : defaultValue(method.getReturnType()));
    }
    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }
}
