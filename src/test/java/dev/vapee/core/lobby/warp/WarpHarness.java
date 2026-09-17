package dev.vapee.core.lobby.warp;

import dev.vapee.core.lobby.experience.navigator.NavigatorInventoryHolder;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.util.Vector;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class WarpHarness {

    private static int checks;

    private WarpHarness() {
    }

    public static void main(String[] args) throws Exception {
        testZeroWarpAndPersistence();
        testMutationSortingAndCustomIds();
        testTeleportAndMissingWorld();
        testInvalidWarpIsolation();
        testNavigatorHolderAndPaginationContract();
        System.out.println("WarpHarness passed " + checks + " checks.");
    }

    private static void testZeroWarpAndPersistence() throws Exception {
        Path file = workspaceTempDirectory("vapeecore-warps-zero-").resolve("warps.yml");
        WarpConfig config = new WarpConfig(file, logger());
        NavigableMap<String, WarpPoint> loaded = config.initialize();
        check(loaded.isEmpty(), "default warp registry is empty");
        check(Files.readString(file).contains("warps: {}"), "default warps.yml has no predefined warps");

        World world = world("world");
        WarpService service = new WarpService(config, name -> name.equals("world") ? world : null, loaded);
        Location position = new Location(world, 10.25, 65.5, -4.75, 123.5F, -12.25F);
        check(service.setWarp("test", position) == WarpResult.SUCCESS, "custom warp saves");
        WarpPoint created = service.getWarp("test").orElseThrow();
        check(created.displayName().equals("Test"), "new warp receives generic display name");
        check(created.icon() == Material.ENDER_PEARL, "new warp receives neutral default icon");

        WarpPoint reloaded = new WarpConfig(file, logger()).initialize().get("test");
        check(reloaded != null, "saved warp reloads");
        check(reloaded.position().worldName().equals("world"), "world survives persistence");
        check(reloaded.position().x() == 10.25 && reloaded.position().y() == 65.5
                && reloaded.position().z() == -4.75, "XYZ survive persistence exactly");
        check(reloaded.position().yaw() == 123.5F && reloaded.position().pitch() == -12.25F,
                "yaw and pitch survive persistence exactly");
    }

    private static void testMutationSortingAndCustomIds() throws Exception {
        Path file = Files.createTempDirectory("vapeecore-warps-mutation-").resolve("warps.yml");
        WarpConfig config = new WarpConfig(file, logger());
        World world = world("world");
        WarpService service = new WarpService(config, ignored -> world, config.initialize());
        for (String id : List.of("zeta", "alpha", "casino", "foo", "totally-custom-destination")) {
            check(service.setWarp(id, new Location(world, id.length(), 64, 0)) == WarpResult.SUCCESS,
                    "arbitrary valid warp id saves");
        }
        check(service.getWarps().stream().map(WarpPoint::id).toList().equals(
                        List.of("alpha", "casino", "foo", "totally-custom-destination", "zeta")),
                "warps are stable alphabetically sorted by id");
        check(service.hasWarp("TOTALLY-CUSTOM-DESTINATION"), "lookup normalizes id case");
        check(service.setDisplayName("foo", "Foo Hall With Spaces") == WarpResult.SUCCESS,
                "plain display name with spaces updates");
        check(service.setIcon("foo", Material.GOLD_INGOT) == WarpResult.SUCCESS, "valid item icon updates");
        check(service.setIcon("foo", Material.AIR) == WarpResult.INVALID_ICON, "air icon is controlled invalid input");
        WarpPoint beforeMove = service.getWarp("foo").orElseThrow();
        check(service.setWarp("foo", new Location(world, 99, 70, 101, 90, 5)) == WarpResult.SUCCESS,
                "existing warp position updates");
        WarpPoint moved = service.getWarp("foo").orElseThrow();
        check(moved.displayName().equals(beforeMove.displayName()), "position update preserves display name");
        check(moved.icon() == beforeMove.icon(), "position update preserves icon");
        check(moved.position().x() == 99 && moved.position().yaw() == 90, "position update takes effect");
        check(service.setDisplayName("foo", "   ") == WarpResult.INVALID_NAME, "blank display name is rejected");
        check(service.setWarp("invalid id", new Location(world, 0, 0, 0)) == WarpResult.INVALID_ID,
                "invalid warp id is rejected");
        check(service.removeWarp("casino") == WarpResult.SUCCESS, "warp removes from runtime");
        check(!service.hasWarp("casino"), "removed warp is absent at runtime");
        check(!new WarpConfig(file, logger()).initialize().containsKey("casino"),
                "removed warp is absent after reload");
        check(service.removeWarp("casino") == WarpResult.NOT_FOUND, "second removal is controlled");
    }

    private static void testTeleportAndMissingWorld() throws Exception {
        Path file = Files.createTempDirectory("vapeecore-warps-teleport-").resolve("warps.yml");
        WarpConfig config = new WarpConfig(file, logger());
        World world = world("world");
        WarpService service = new WarpService(config, name -> name.equals("world") ? world : null, config.initialize());
        service.setWarp("destination", new Location(world, 3, 70, 4, 45, 2));
        AtomicReference<Location> teleported = new AtomicReference<>();
        AtomicReference<PlayerTeleportEvent.TeleportCause> cause = new AtomicReference<>();
        AtomicReference<Vector> velocity = new AtomicReference<>();
        AtomicBoolean fallReset = new AtomicBoolean();
        Player player = proxy(Player.class, (method, args) -> switch (method.getName()) {
            case "isOnline" -> true;
            case "teleport" -> {
                teleported.set((Location) args[0]);
                cause.set((PlayerTeleportEvent.TeleportCause) args[1]);
                yield true;
            }
            case "setFallDistance" -> {
                fallReset.set(((Float) args[0]) == 0.0F);
                yield null;
            }
            case "setVelocity" -> {
                velocity.set((Vector) args[0]);
                yield null;
            }
            default -> defaultValue(method.getReturnType());
        });
        check(service.teleport(player, "destination") == WarpResult.SUCCESS, "configured warp teleports");
        check(teleported.get() != null && teleported.get().getX() == 3, "teleport uses configured target");
        check(cause.get() == PlayerTeleportEvent.TeleportCause.PLUGIN, "teleport uses PLUGIN cause");
        check(fallReset.get(), "successful teleport resets fall distance");
        check(velocity.get() != null && velocity.get().lengthSquared() == 0, "successful teleport resets velocity");

        WarpService missingWorld = new WarpService(config, ignored -> null, config.loadWarps());
        check(missingWorld.teleport(player, "destination") == WarpResult.WORLD_NOT_LOADED,
                "missing world is rejected without loading it");
        Player offline = proxy(Player.class, (method, args) -> method.getName().equals("isOnline")
                ? false : defaultValue(method.getReturnType()));
        check(service.teleport(offline, "destination") == WarpResult.PLAYER_OFFLINE,
                "offline player is rejected");
    }

    private static void testInvalidWarpIsolation() throws Exception {
        Path file = Files.createTempDirectory("vapeecore-warps-invalid-").resolve("warps.yml");
        Files.writeString(file, """
                warps:
                  valid:
                    display-name: Valid
                    icon: ENDER_PEARL
                    location:
                      world: world
                      x: 1.0
                      y: 2.0
                      z: 3.0
                      yaw: 4.0
                      pitch: 5.0
                  broken:
                    display-name: Broken
                    icon: NOT_A_MATERIAL
                    location:
                      world: world
                      x: 0
                      y: 0
                      z: 0
                      yaw: 0
                      pitch: 0
                """);
        String before = Files.readString(file);
        NavigableMap<String, WarpPoint> loaded = new WarpConfig(file, logger()).initialize();
        check(loaded.size() == 1 && loaded.containsKey("valid"), "invalid warp is skipped independently");
        check(Files.readString(file).equals(before), "invalid warp load leaves source file unchanged");
    }

    private static void testNavigatorHolderAndPaginationContract() throws Exception {
        UUID owner = UUID.randomUUID();
        Map<Integer, String> mapping = new LinkedHashMap<>();
        mapping.put(0, "totally-custom-destination");
        mapping.put(44, "zeta");
        NavigatorInventoryHolder holder = new NavigatorInventoryHolder(owner, 2, mapping);
        check(holder.getOwnerUniqueId().equals(owner), "navigator holder owns one player");
        check(holder.getPage() == 2, "navigator holder stores page");
        check(holder.getWarpId(0).orElseThrow().equals("totally-custom-destination"),
                "navigator maps slot to arbitrary warp id");
        check(holder.getWarpId(1).isEmpty(), "unmapped navigator slot has no destination");
        check(NavigatorMenu.INVENTORY_SIZE == 54 && NavigatorMenu.CONTENT_SIZE == 45,
                "navigator uses 54 slots with 45 paginated destinations");
        check(NavigatorMenu.PREVIOUS_SLOT >= NavigatorMenu.CONTENT_SIZE
                        && NavigatorMenu.NEXT_SLOT < NavigatorMenu.INVENTORY_SIZE,
                "pagination controls are outside content slots");

        AtomicReference<NavigatorInventoryHolder> holderReference = new AtomicReference<>(holder);
        Inventory inventory = proxy(Inventory.class, (method, args) -> method.getName().equals("getHolder")
                ? holderReference.get() : defaultValue(method.getReturnType()));
        Method bind = NavigatorInventoryHolder.class.getDeclaredMethod("bindInventory", Inventory.class);
        bind.setAccessible(true);
        bind.invoke(holder, inventory);
        check(holder.getInventory() == inventory, "navigator holder binds exact inventory identity");
    }

    private static World world(String name) {
        return proxy(World.class, (method, args) -> method.getName().equals("getName")
                ? name : defaultValue(method.getReturnType()));
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static Path workspaceTempDirectory(String prefix) throws Exception {
        Path root = Path.of("target", "harness-temp").toAbsolutePath().normalize();
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Proxy";
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    default -> null;
                };
            }
            return handler.invoke(method, args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    @FunctionalInterface
    private interface Handler {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }
}
