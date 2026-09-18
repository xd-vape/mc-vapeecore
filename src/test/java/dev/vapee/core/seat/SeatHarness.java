package dev.vapee.core.seat;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class SeatHarness {

    private static int checks;

    private SeatHarness() {
    }

    public static void main(String[] args) {
        testServiceLifecycle();
        testDismountSemantics();
        testPositionResolver();
        testCasualPolicy();
        testLifecycleCleanup();
        System.out.println("SeatHarness passed " + checks + " checks.");
    }

    private static void testServiceLifecycle() {
        Fixture fixture = new Fixture();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        SeatKey one = new SeatKey("owner", "one");
        SeatKey two = new SeatKey("owner", "two");
        Location location = new Location(fixture.world, 1.5, 64.5, 2.5, 90, 0);

        check(fixture.service.reserve(one, SeatType.CASUAL, first, location, null), "seat reserves");
        check(!fixture.service.reserve(one, SeatType.CASUAL, second, location, null),
                "second player cannot reserve occupied key");
        check(!fixture.service.reserve(two, SeatType.CASUAL, first, location, null),
                "one player cannot reserve a second seat");
        check(fixture.service.getAssignment(first).orElseThrow().type() == SeatType.CASUAL,
                "casual type is retained");
        check(fixture.service.mountReserved(fixture.player(first, false, false, 30)), "reserved seat mounts");
        check(fixture.service.getAssignment(first).orElseThrow().isMounted(), "entity UUID is recorded");
        check(fixture.service.releasePlayer(first), "player release succeeds");
        check(!fixture.service.releasePlayer(first), "player release is idempotent");
        check(fixture.gateway.removed == 1, "release removes physical entity");

        check(fixture.service.reserve(one, SeatType.CASUAL, first, location, null), "owner seat one reserves");
        check(fixture.service.reserve(two, SeatType.MANAGED, second, location, ignored -> { }),
                "owner seat two reserves");
        check(fixture.service.releaseOwner("owner") == 2, "owner cleanup reports both seats");
        check(fixture.service.getAssignmentCount() == 0, "owner cleanup clears registry");
        fixture.gateway.staleCount = 2;
        check(fixture.service.cleanupStaleSeats() == 2, "generic and legacy stale cleanup delegates");
    }

    private static void testDismountSemantics() {
        Fixture fixture = new Fixture();
        UUID playerId = UUID.randomUUID();
        AtomicInteger callbacks = new AtomicInteger();
        SeatKey key = new SeatKey("feature", "1");
        Location location = new Location(fixture.world, 1, 2, 3);
        Player player = fixture.player(playerId, false, false, 0);

        check(fixture.service.reserve(key, SeatType.MANAGED, playerId, location,
                ignored -> callbacks.incrementAndGet()), "managed seat reserves");
        check(fixture.service.mountReserved(player), "managed seat mounts");
        UUID entityId = fixture.service.getAssignment(playerId).orElseThrow().seatEntity().orElseThrow();
        fixture.service.releasePlayer(playerId);
        fixture.service.handleDismount(player, fixture.entity(entityId));
        fixture.runDelayed();
        check(callbacks.get() == 0, "programmatic release never invokes voluntary callback");

        check(fixture.service.reserve(key, SeatType.MANAGED, playerId, location,
                ignored -> callbacks.incrementAndGet()), "managed seat can reserve again");
        check(fixture.service.mountReserved(player), "managed seat remounts");
        entityId = fixture.service.getAssignment(playerId).orElseThrow().seatEntity().orElseThrow();
        Entity entity = fixture.entity(entityId);
        fixture.service.handleDismount(player, entity);
        fixture.service.handleDismount(player, entity);
        check(fixture.delayed.size() == 2, "real dismount is delayed by the runtime executor");
        fixture.runDelayed();
        check(callbacks.get() == 1, "duplicate dismount notifications invoke callback once");
        check(!fixture.service.isSeated(playerId), "voluntary dismount clears assignment");

        UUID casualId = UUID.randomUUID();
        Player casual = fixture.player(casualId, false, false, 0);
        check(fixture.service.reserve(key, SeatType.CASUAL, casualId, location,
                ignored -> callbacks.incrementAndGet()), "casual seat reserves");
        check(fixture.service.mountReserved(casual), "casual seat mounts");
        UUID casualEntity = fixture.service.getAssignment(casualId).orElseThrow().seatEntity().orElseThrow();
        fixture.service.handleDismount(casual, fixture.entity(casualEntity));
        fixture.runDelayed();
        check(callbacks.get() == 1, "casual dismount has no managed callback");
    }

    private static void testPositionResolver() {
        Fixture fixture = new Fixture();
        SeatPositionResolver resolver = new SeatPositionResolver();
        checkY(resolver.resolve(fixture.block(fixture.slab(Slab.Type.BOTTOM)), 45), 64.5,
                "bottom slab uses half height");
        Location top = resolver.resolve(fixture.block(fixture.slab(Slab.Type.TOP)), 270).orElseThrow();
        check(top.getY() == 65.0, "top slab uses full height");
        check(top.getYaw() == -90.0F, "slab uses normalized player yaw");
        check(resolver.resolve(fixture.block(fixture.slab(Slab.Type.DOUBLE)), 0).isEmpty(),
                "double slab is rejected");
        checkYaw(resolver, fixture, BlockFace.NORTH, 0.0F);
        checkYaw(resolver, fixture, BlockFace.SOUTH, 180.0F);
        checkYaw(resolver, fixture, BlockFace.EAST, 90.0F);
        checkYaw(resolver, fixture, BlockFace.WEST, -90.0F);
        check(resolver.resolve(fixture.block(fixture.stairs(BlockFace.NORTH, Bisected.Half.TOP)), 0).isEmpty(),
                "top stairs are deliberately rejected");
        BlockData foreign = proxy(BlockData.class, (method, args) -> defaultValue(method.getReturnType()));
        check(resolver.resolve(fixture.block(foreign), 0).isEmpty(), "unsupported block data is rejected");
    }

    private static void testCasualPolicy() {
        PolicyFixture valid = new PolicyFixture();
        check(valid.click(EquipmentSlot.HAND, null), "valid empty-hand lobby stair click sits");
        check(valid.service.getAssignmentCount() == 1, "valid click creates one assignment");

        PolicyFixture offhand = new PolicyFixture();
        check(!offhand.click(EquipmentSlot.OFF_HAND, null), "offhand is ignored");
        PolicyFixture sneaking = new PolicyFixture();
        sneaking.sneaking = true;
        check(!sneaking.click(EquipmentSlot.HAND, null), "sneaking is ignored");
        PolicyFixture item = new PolicyFixture();
        check(!item.click(EquipmentSlot.HAND, false), "held item is ignored");
        PolicyFixture build = new PolicyFixture();
        build.build = true;
        check(!build.click(EquipmentSlot.HAND, null), "build mode is ignored");
        PolicyFixture activity = new PolicyFixture();
        activity.activity = true;
        check(!activity.click(EquipmentSlot.HAND, null), "activity participant is ignored");
        PolicyFixture venue = new PolicyFixture();
        venue.venue = true;
        check(!venue.click(EquipmentSlot.HAND, null), "activity venue click is ignored");
        PolicyFixture outsideLobby = new PolicyFixture();
        outsideLobby.lobby = false;
        check(!outsideLobby.click(EquipmentSlot.HAND, null), "outside lobby is ignored");
        PolicyFixture vehicle = new PolicyFixture();
        vehicle.vehicle = true;
        check(!vehicle.click(EquipmentSlot.HAND, null), "player already in vehicle is ignored");
        PolicyFixture blocked = new PolicyFixture();
        blocked.solidAbove = true;
        check(!blocked.click(EquipmentSlot.HAND, null), "solid block above seat is rejected");
    }

    private static void testLifecycleCleanup() {
        Fixture fixture = new Fixture();
        SeatListener listener = new SeatListener(fixture.service, new SeatPositionResolver(),
                ignored -> true, ignored -> false, ignored -> false, ignored -> false);
        Location location = new Location(fixture.world, 0, 64, 0);
        String[] paths = {"quit", "world change", "death"};
        for (String path : paths) {
            UUID id = UUID.randomUUID();
            Player player = fixture.player(id, false, false, 0);
            check(fixture.service.reserve(new SeatKey("lifecycle", path), SeatType.CASUAL, id, location, null),
                    path + " fixture reserves");
            listener.cleanupPlayer(player);
            check(!fixture.service.isSeated(id), path + " cleanup releases assignment");
        }
    }

    private static void checkYaw(SeatPositionResolver resolver, Fixture fixture, BlockFace facing, float expected) {
        Location location = resolver.resolve(
                fixture.block(fixture.stairs(facing, Bisected.Half.BOTTOM)),
                17
        ).orElseThrow();
        check(location.getYaw() == expected, "bottom stair " + facing + " maps to yaw " + expected);
    }

    private static void checkY(Optional<Location> result, double expected, String message) {
        check(result.orElseThrow().getY() == expected, message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "HarnessProxy";
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
        if (type == int.class) return 0;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        if (type == long.class) return 0L;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == char.class) return '\0';
        return null;
    }

    @FunctionalInterface
    private interface Handler {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }

    private static class Fixture {
        final UUID worldId = UUID.randomUUID();
        final World world = proxy(World.class, (method, args) -> switch (method.getName()) {
            case "getUID" -> worldId;
            case "getName" -> "world";
            default -> defaultValue(method.getReturnType());
        });
        final FakeGateway gateway = new FakeGateway();
        final List<Runnable> delayed = new ArrayList<>();
        final SeatService service = new SeatService(() -> true, () -> true, ignored -> true,
                delayed::add, gateway);

        Player player(UUID id, boolean sneaking, boolean vehicle, float yaw) {
            return proxy(Player.class, (method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "isSneaking" -> sneaking;
                case "isInsideVehicle" -> vehicle;
                case "isOnline" -> true;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 0, 64, 0, yaw, 0);
                default -> defaultValue(method.getReturnType());
            });
        }

        Entity entity(UUID id) {
            gateway.seatEntities.put(id, true);
            return proxy(Entity.class, (method, args) -> method.getName().equals("getUniqueId")
                    ? id : defaultValue(method.getReturnType()));
        }

        Slab slab(Slab.Type type) {
            return proxy(Slab.class, (method, args) -> method.getName().equals("getType")
                    ? type : defaultValue(method.getReturnType()));
        }

        Stairs stairs(BlockFace facing, Bisected.Half half) {
            return proxy(Stairs.class, (method, args) -> switch (method.getName()) {
                case "getFacing" -> facing;
                case "getHalf" -> half;
                default -> defaultValue(method.getReturnType());
            });
        }

        Block block(BlockData data) {
            return block(data, false);
        }

        Block block(BlockData data, boolean solidAbove) {
            Block above = proxy(Block.class, (method, args) -> method.getName().equals("isPassable")
                    ? !solidAbove : defaultValue(method.getReturnType()));
            return proxy(Block.class, (method, args) -> switch (method.getName()) {
                case "getBlockData" -> data;
                case "getWorld" -> world;
                case "getX" -> 10;
                case "getY" -> 64;
                case "getZ" -> 20;
                case "getLocation" -> new Location(world, 10, 64, 20);
                case "getRelative" -> above;
                default -> defaultValue(method.getReturnType());
            });
        }

        void runDelayed() {
            List<Runnable> pending = List.copyOf(delayed);
            delayed.clear();
            pending.forEach(Runnable::run);
        }
    }

    private static final class PolicyFixture extends Fixture {
        boolean lobby = true;
        boolean build;
        boolean activity;
        boolean venue;
        boolean sneaking;
        boolean vehicle;
        boolean solidAbove;
        final UUID playerId = UUID.randomUUID();
        final SeatListener listener = new SeatListener(service, new SeatPositionResolver(),
                ignored -> lobby, ignored -> build, ignored -> activity, ignored -> venue);

        boolean click(EquipmentSlot slot, ItemStack item) {
            return listener.handleCasualInteraction(
                    player(playerId, sneaking, vehicle, 25),
                    block(stairs(BlockFace.NORTH, Bisected.Half.BOTTOM), solidAbove),
                    Action.RIGHT_CLICK_BLOCK,
                    slot,
                    item
            );
        }

        boolean click(EquipmentSlot slot, boolean emptyHand) {
            return listener.handleCasualInteraction(
                    player(playerId, sneaking, vehicle, 25),
                    block(stairs(BlockFace.NORTH, Bisected.Half.BOTTOM), solidAbove),
                    Action.RIGHT_CLICK_BLOCK,
                    slot,
                    emptyHand
            );
        }
    }

    private static final class FakeGateway implements SeatService.SeatEntityGateway {
        final Map<UUID, Boolean> seatEntities = new HashMap<>();
        int removed;
        int staleCount;

        @Override
        public UUID spawnAndMount(SeatAssignment assignment, Player player) {
            UUID id = UUID.randomUUID();
            seatEntities.put(id, true);
            return id;
        }

        @Override
        public void remove(UUID entityId, UUID playerId) {
            seatEntities.remove(entityId);
            removed++;
        }

        @Override
        public boolean isSeatEntity(Entity entity) {
            return entity != null && seatEntities.containsKey(entity.getUniqueId());
        }

        @Override
        public int cleanupStaleSeats() {
            return staleCount;
        }
    }
}
