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

    public static void main(String[] args) {
        testKeyedLifecycle();
        testTypeSafetyAndMissingEntities();
        testOwnerAndStaleCleanup();
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
            return true;
        }

        @Override
        public void remove(UUID entityId) {
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
