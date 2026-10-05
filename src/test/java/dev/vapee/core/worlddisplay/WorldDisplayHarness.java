package dev.vapee.core.worlddisplay;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public final class WorldDisplayHarness {

    private static int checks;

    private WorldDisplayHarness() {
    }

    public static void main(String[] args) throws Exception {
        testKeyedLifecycle();
        testTypeSafetyAndMissingEntities();
        testOwnerAndStaleCleanup();
        testRejectedTeleport();
        testCleanupFaults();
        testBukkitAliveLookup();
        System.out.println("WorldDisplayHarness passed " + checks + " checks.");
    }

    private static void testKeyedLifecycle() {
        Fixture fixture = new Fixture();
        WorldDisplayKey textKey = new WorldDisplayKey("feature", "status");
        WorldDisplayKey itemKey = new WorldDisplayKey("feature", "card");
        Component firstText = Component.text("Ready");
        WorldDisplayHandle text = fixture.service.createText(textKey, fixture.location(), firstText, null);
        check(text.key().equals(textKey), "text handle retains immutable key");
        check(text.type() == WorldDisplayHandle.Type.TEXT, "text handle is typed");
        check(fixture.gateway.texts.get(text.entityId()).equals(firstText), "text display is created");
        check(fixture.service.getDisplayCount() == 1, "text create increments registry count");
        expectThrows(() -> fixture.service.createText(textKey, fixture.location(), firstText, null),
                "duplicate key is rejected");

        ItemStack original = new FakeItemStack("diamond", 2);
        WorldDisplayHandle item = fixture.service.createItem(itemKey, fixture.location(), original, null);
        original.setAmount(7);
        check(item.type() == WorldDisplayHandle.Type.ITEM, "item handle is typed");
        check(fixture.gateway.items.get(item.entityId()).getAmount() == 2,
                "create defensively clones item stack");
        ItemStack update = new FakeItemStack("emerald", 3);
        check(fixture.service.updateItem(itemKey, update), "item display updates");
        update.setAmount(9);
        check(fixture.gateway.items.get(item.entityId()).getAmount() == 3,
                "update defensively clones item stack");
        Component secondText = Component.text("Playing");
        check(fixture.service.updateText(textKey, secondText), "text display updates");
        check(fixture.gateway.texts.get(text.entityId()).equals(secondText), "updated text reaches entity");
        check(fixture.service.teleport(textKey, new Location(fixture.world, 4, 5, 6)),
                "registered display teleports");
        check(fixture.gateway.teleports == 1, "teleport delegates once");
    }

    private static void testTypeSafetyAndMissingEntities() {
        Fixture fixture = new Fixture();
        WorldDisplayKey textKey = new WorldDisplayKey("types", "text");
        WorldDisplayKey itemKey = new WorldDisplayKey("types", "item");
        WorldDisplayHandle text = fixture.service.createText(
                textKey, fixture.location(), Component.empty(), ignored -> { }
        );
        fixture.service.createItem(itemKey, fixture.location(), new FakeItemStack("paper", 1), ignored -> { });
        expectThrows(() -> fixture.service.updateItem(textKey, new FakeItemStack("paper", 1)),
                "item update on text is rejected");
        expectThrows(() -> fixture.service.updateText(itemKey, Component.empty()),
                "text update on item is rejected");
        fixture.gateway.remove(text.entityId());
        check(!fixture.service.updateText(textKey, Component.text("missing")),
                "missing entity update returns false");
        check(fixture.service.getHandle(textKey).isEmpty(), "missing entity update clears stale registry entry");
        check(!fixture.service.teleport(new WorldDisplayKey("missing", "key"), fixture.location()),
                "unknown key teleport is controlled");
        check(!fixture.service.remove(new WorldDisplayKey("missing", "key")), "remove is idempotent for unknown key");
    }

    private static void testOwnerAndStaleCleanup() {
        Fixture fixture = new Fixture();
        fixture.service.createText(new WorldDisplayKey("one", "a"), fixture.location(), Component.empty(), null);
        fixture.service.createItem(new WorldDisplayKey("one", "b"), fixture.location(),
                new FakeItemStack("paper", 1), null);
        fixture.service.createText(new WorldDisplayKey("two", "a"), fixture.location(), Component.empty(), null);
        check(fixture.service.removeOwner("one") == 2, "removeOwner removes its complete group");
        check(fixture.service.getDisplayCount() == 1, "removeOwner leaves foreign owner registered");
        check(fixture.service.removeOwner("one") == 0, "removeOwner is idempotent");
        fixture.gateway.staleCount = 2;
        check(fixture.service.cleanupStaleDisplays() == 2,
                "marked text and item stale cleanup delegates without foreign display removal");
        fixture.service.cleanup();
        check(fixture.service.getDisplayCount() == 0, "shutdown cleanup clears registry");
        check(fixture.gateway.texts.isEmpty() && fixture.gateway.items.isEmpty(),
                "shutdown cleanup removes remaining entities");
        check(new WorldDisplayKey(" owner ", " id ").equals(new WorldDisplayKey("owner", "id")),
                "display key normalizes immutable values");
        expectThrows(() -> new WorldDisplayKey(" ", "id"), "blank owner is rejected");
    }

    private static void testBukkitAliveLookup() throws Exception {
        // Only the external server lookup is substituted; execute the real private gateway read.
        var type = Class.forName(WorldDisplayService.class.getName() + "$BukkitDisplayGateway");
        var unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        Object unsafe = unsafeField.get(null);
        var gateway = (WorldDisplayService.DisplayGateway) unsafe.getClass()
                .getMethod("allocateInstance", Class.class).invoke(unsafe, type);
        var serverField = type.getDeclaredField("server");
        serverField.setAccessible(true);
        UUID id = UUID.randomUUID();
        for (int state = 0; state < 4; state++) {
            int current = state;
            org.bukkit.entity.Entity entity = state == 0 ? null : proxy(org.bukkit.entity.Entity.class,
                    (method, args) -> switch (method.getName()) {
                        case "isValid" -> current != 3;
                        case "isDead" -> current == 2;
                        default -> defaultValue(method.getReturnType());
                    });
            serverField.set(gateway, proxy(org.bukkit.Server.class, (method, args) -> {
                if (method.getName().equals("getEntity")) {
                    check(args[0].equals(id), "Bukkit alive lookup targets exact owned UUID");
                    return entity;
                }
                throw new AssertionError("Unexpected Bukkit lookup " + method.getName());
            }));
            check(gateway.isAlive(id) == (state == 1), "Bukkit gateway distinguishes missing live dead invalid entity " + state);
        }
    }

    private static void testRejectedTeleport() {
        Fixture fixture = new Fixture();
        WorldDisplayKey key = new WorldDisplayKey("move", "original");
        WorldDisplayKey other = new WorldDisplayKey("move", "other");
        WorldDisplayHandle original = fixture.service.createText(key, fixture.location(), Component.empty(), null);
        fixture.service.createText(other, fixture.location(), Component.empty(), null);
        fixture.gateway.rejected.add(original.entityId());
        check(!fixture.service.teleport(key, fixture.location()), "rejected movement reports false");
        check(fixture.service.getHandle(key).orElse(null) == original, "rejected live teleport retains original ownership");
        check(fixture.service.teleport(other, fixture.location()), "one rejected movement leaves other display usable");
        expectThrows(() -> fixture.service.createText(key, fixture.location(), Component.empty(), null),
                "live rejected handle prevents duplicate creation");
        fixture.gateway.rejected.clear();
        check(fixture.service.teleport(key, fixture.location()), "later movement succeeds on original");
        check(fixture.service.getHandle(key).orElseThrow() == original, "later movement keeps same handle");
        fixture.gateway.rejected.add(original.entityId());
        check(!fixture.service.teleport(key, fixture.location()), "second rejected movement remains false");
        check(fixture.service.removeOwner("move") == 2 && fixture.gateway.texts.isEmpty(),
                "owner cleanup removes rejected original and other display");
        WorldDisplayHandle missing = fixture.service.createText(key, fixture.location(), Component.empty(), null);
        fixture.gateway.texts.remove(missing.entityId());
        check(!fixture.service.teleport(key, fixture.location()) && fixture.service.getHandle(key).isEmpty(),
                "missing entity reconciles stale ownership");
        WorldDisplayHandle replacement = fixture.service.createText(key, fixture.location(), Component.empty(), null);
        check(!replacement.entityId().equals(missing.entityId()), "missing key permits controlled recreation");
    }

    private static void testCleanupFaults() {
        for (boolean all : new boolean[]{false, true}) {
            for (int mask : new int[]{0, 1, 2, 4, 5, 7}) {
                Fixture f = new Fixture();
                java.util.List<WorldDisplayHandle> owned = new java.util.ArrayList<>();
                for (int i = 0; i < 3; i++) owned.add(f.service.createText(
                        new WorldDisplayKey(all && i == 2 ? "second" : "owner", "resource-" + i),
                        f.location(), Component.empty(), null));
                WorldDisplayHandle outside = all ? null : f.service.createText(
                        new WorldDisplayKey("second", "outside"), f.location(), Component.empty(), null);
                UUID foreign = UUID.randomUUID();
                f.gateway.texts.put(foreign, Component.text("foreign"));
                f.gateway.failMask = mask;
                RuntimeException failure = capture(() -> { if (all) f.service.cleanup(); else f.service.removeOwner("owner"); });
                check(f.gateway.attempts.size() == 3, "display cleanup attempts every owned resource despite failure");
                check((failure == null) == (mask == 0), "display cleanup reports incomplete release");
                if (failure != null) {
                    check(failure.getSuppressed().length == Integer.bitCount(mask), "display aggregate contains every failure");
                    for (Throwable item : failure.getSuppressed()) {
                        check(item.getMessage().contains("WorldDisplay remove") && item.getMessage().contains("owner=")
                                        && item.getMessage().contains("id=") && item.getMessage().contains("entity=")
                                        && f.gateway.failures.containsValue(item.getCause()),
                                "display failure reports service owner key operation and original cause");
                    }
                }
                for (WorldDisplayHandle handle : owned) {
                    boolean failed = f.gateway.failures.containsKey(handle.entityId());
                    check(f.service.getHandle(handle.key()).isPresent() == failed, "failed display retains owned handle");
                    check(f.gateway.texts.containsKey(handle.entityId()) == failed, "display physical outcome matches ownership");
                }
                check(f.gateway.texts.containsKey(foreign), "display cleanup leaves unowned entity untouched");
                check(outside == null || f.service.getHandle(outside.key()).isPresent(), "owner cleanup leaves second owner untouched");
                f.gateway.failMask = 0;
                f.gateway.attempts.clear();
                if (all) f.service.cleanup(); else f.service.removeOwner("owner");
                check(f.gateway.attempts.size() == Integer.bitCount(mask), "display retry targets only failed handles");
                if (all) f.service.cleanup(); else f.service.removeOwner("owner");
                check(f.gateway.attempts.size() == Integer.bitCount(mask), "display repeated cleanup is idempotent");
                check(f.gateway.texts.containsKey(foreign), "display retry never touches foreign entity");
            }
        }
    }

    private static RuntimeException capture(Runnable action) {
        try { action.run(); return null; } catch (RuntimeException failure) { return failure; }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectThrows(Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message);
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

    private static final class Fixture {
        private final World world = proxy(World.class, (method, args) -> switch (method.getName()) {
            case "getUID" -> UUID.randomUUID();
            case "getName" -> "world";
            default -> defaultValue(method.getReturnType());
        });
        private final FakeGateway gateway = new FakeGateway();
        private final WorldDisplayService service = new WorldDisplayService(() -> true, gateway);

        private Location location() {
            return new Location(world, 1, 2, 3, 4, 5);
        }
    }

    private static final class FakeGateway implements WorldDisplayService.DisplayGateway {
        private final Map<UUID, Component> texts = new HashMap<>();
        private final Map<UUID, ItemStack> items = new HashMap<>();
        private int teleports;
        private int staleCount;
        private final java.util.Set<UUID> rejected = new java.util.HashSet<>();
        private final java.util.List<UUID> attempts = new java.util.ArrayList<>();
        private final Map<UUID, RuntimeException> failures = new HashMap<>();
        private int failMask;

        @Override
        public UUID createText(WorldDisplayKey key, Location location, Component text,
                               Consumer<TextDisplay> configurator) {
            UUID id = UUID.randomUUID();
            texts.put(id, text);
            return id;
        }

        @Override
        public UUID createItem(WorldDisplayKey key, Location location, ItemStack item,
                               Consumer<ItemDisplay> configurator) {
            UUID id = UUID.randomUUID();
            items.put(id, item.clone());
            return id;
        }

        @Override
        public boolean updateText(UUID entityId, Component text) {
            if (!texts.containsKey(entityId)) return false;
            texts.put(entityId, text);
            return true;
        }

        @Override
        public boolean updateItem(UUID entityId, ItemStack item) {
            if (!items.containsKey(entityId)) return false;
            items.put(entityId, item.clone());
            return true;
        }

        @Override
        public boolean teleport(UUID entityId, Location location) {
            if (!texts.containsKey(entityId) && !items.containsKey(entityId)) return false;
            teleports++;
            return !rejected.contains(entityId);
        }

        @Override
        public boolean isAlive(UUID entityId) {
            return texts.containsKey(entityId) || items.containsKey(entityId);
        }

        @Override
        public void remove(UUID entityId) {
            attempts.add(entityId);
            if ((failMask & (1 << (attempts.size() - 1))) != 0) {
                RuntimeException failure = new IllegalStateException("injected display remove");
                failures.put(entityId, failure);
                throw failure;
            }
            texts.remove(entityId);
            items.remove(entityId);
        }

        @Override
        public int cleanupStaleDisplays() {
            texts.clear();
            items.clear();
            return staleCount;
        }
    }

    private static final class FakeItemStack extends ItemStack {
        private final String kind;
        private int amount;

        private FakeItemStack(String kind, int amount) {
            super();
            this.kind = kind;
            this.amount = amount;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public void setAmount(int amount) {
            this.amount = amount;
        }

        @Override
        public FakeItemStack clone() {
            return new FakeItemStack(kind, amount);
        }
    }
}
