package dev.vapee.core.visibility;

import dev.vapee.core.player.settings.PlayerVisibilitySettings;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.logging.Logger;

public final class VisibilityServiceHarness {
    private static int checks;

    public static void main(String[] args) {
        World lobby = proxy(World.class, (method, arguments) -> null);
        World elsewhere = proxy(World.class, (method, arguments) -> null);
        Plugin plugin = proxy(Plugin.class, (method, arguments) -> null);
        Map<UUID, PlayerVisibilitySettings> settings = new HashMap<>();
        List<TestPlayer> players = new ArrayList<>();
        TestPlayer alice = player("Alice", lobby, settings, players);
        TestPlayer bob = player("Bob", lobby, settings, players);
        TestPlayer outside = player("Outside", elsewhere, settings, players);
        VisibilityPolicy policy = new VisibilityPolicy(settings::get,
                (viewer, target) -> false, (viewer, target) -> false, (viewer, target) -> false);
        VisibilityService service = new VisibilityService(plugin,
                () -> players.stream().map(TestPlayer::player).toList(),
                world -> world == lobby, policy, logger());

        service.applyViewerPreference(outside.player);
        check(outside.actions.isEmpty(), "non-lobby viewer is ignored");
        service.applyViewerPreference(alice.player);
        check(alice.shown(bob.id) && !alice.mentions(outside.id),
                "lobby viewer applies only to lobby target");
        check(!alice.mentions(alice.id), "viewer is never applied to itself");

        settings.get(alice.id).setAllPlayersVisible(false);
        alice.actions.clear();
        service.applyViewerPreference(alice.player);
        check(alice.hidden(bob.id) && !alice.mentions(outside.id),
                "filtered unmatched target is hidden");
        bob.actions.clear();
        service.synchronizePlayer(bob.player);
        check(bob.shown(alice.id) && alice.hidden(bob.id),
                "synchronize updates both directions asymmetrically");

        settings.get(alice.id).setShowStaff(true);
        bob.staff = true;
        alice.actions.clear();
        service.applyViewerPreference(alice.player);
        check(alice.shown(bob.id), "staff marker is read from current online target");
        bob.staff = false;
        alice.actions.clear();
        service.applyViewerPreference(alice.player);
        check(alice.hidden(bob.id), "removed staff marker is read on next recalculation");

        alice.actions.clear();
        service.restorePlayer(bob.player);
        check(alice.shown(bob.id), "restore player releases incoming VapeeCore hide state");
        service.applyViewerPreference(alice.player);
        alice.actions.clear();
        service.restoreAll();
        check(alice.shown(bob.id), "restore all releases every tracked hide state");
        alice.actions.clear();
        service.restoreAll();
        check(alice.actions.isEmpty(), "restore all does not touch unowned states twice");
        System.out.println("VisibilityServiceHarness passed " + checks + " checks.");
    }

    private static TestPlayer player(String name, World world,
                                     Map<UUID, PlayerVisibilitySettings> settings,
                                     List<TestPlayer> players) {
        TestPlayer created = new TestPlayer(name, world);
        settings.put(created.id, PlayerVisibilitySettings.defaults());
        players.add(created);
        return created;
    }

    private static final class TestPlayer {
        private final UUID id = UUID.randomUUID();
        private final String name;
        private final World world;
        private final List<String> actions = new ArrayList<>();
        private final Player player;
        private boolean staff;

        private TestPlayer(String name, World world) {
            this.name = name;
            this.world = world;
            player = proxy(Player.class, (method, arguments) -> switch (method) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "getWorld" -> world;
                case "isOnline" -> true;
                case "hasPermission" -> staff;
                case "hidePlayer" -> { actions.add("hide:" + ((Player) arguments[1]).getUniqueId()); yield null; }
                case "showPlayer" -> { actions.add("show:" + ((Player) arguments[1]).getUniqueId()); yield null; }
                default -> DEFAULT;
            });
        }
        private Player player() { return player; }
        private boolean hidden(UUID target) { return actions.contains("hide:" + target); }
        private boolean shown(UUID target) { return actions.contains("show:" + target); }
        private boolean mentions(UUID target) {
            return hidden(target) || shown(target);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    case "toString" -> type.getSimpleName() + "Proxy";
                    default -> null;
                };
            }
            Object result = handler.invoke(method.getName(), args == null ? new Object[0] : args);
            return result == DEFAULT ? defaultValue(method.getReturnType()) : result;
        });
    }

    private static final Object DEFAULT = new Object();
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
    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }
    @FunctionalInterface private interface Handler { Object invoke(String method, Object[] arguments); }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
