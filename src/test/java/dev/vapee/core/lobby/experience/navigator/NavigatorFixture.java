package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.lobby.player.LobbyPlayerMode;
import dev.vapee.core.lobby.warp.*;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.util.Vector;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.*;

/** Real menu/listener execution with exact inventory identities and close-event dispatch. */
final class NavigatorFixture {
    final Logger logger = Logger.getAnonymousLogger();
    final List<LogRecord> logs = new ArrayList<>();
    final Map<UUID, TestPlayer> players = new LinkedHashMap<>();
    final Map<String, World> worlds = new HashMap<>();
    final World lobby = world("lobby-world");
    final World outside = world("other-world");
    final WarpService warps;
    final NavigatorAccessPolicy access;
    final NavigatorMenu menu;
    final NavigatorListener listener;
    Component title;

    NavigatorFixture() throws Exception {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { logs.add(record); }
            public void flush() { }
            public void close() { }
        });
        worlds.put(lobby.getName(), lobby);
        worlds.put(outside.getName(), outside);
        Path root = Path.of("target", "harness-temp").toAbsolutePath();
        Files.createDirectories(root);
        WarpConfig config = new WarpConfig(Files.createTempDirectory(root, "navigator-").resolve("warps.yml"), logger);
        warps = new WarpService(config, worlds::get, config.initialize());
        access = new NavigatorAccessPolicy(id -> players.get(id) != null && players.get(id).loaded,
                candidate -> candidate == lobby, id -> players.get(id).mode,
                id -> players.get(id).participating);
        menu = new NavigatorMenu(warps, access,
                id -> players.containsKey(id) ? players.get(id).player : null,
                (holder, size, heading) -> { title = heading; return inventory(holder, size); },
                SpecItem::new, logger);
        var constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        MessageService messages = constructor.newInstance((Supplier<String>) () -> "");
        listener = new NavigatorListener(warps, messages, menu);
    }

    TestPlayer player() {
        TestPlayer player = new TestPlayer();
        players.put(player.id, player);
        return player;
    }

    void destinations(int count) {
        for (int index = 0; index < count; index++) {
            String id = String.format(Locale.ROOT, "custom_%03d", index);
            warps.setWarp(id, new Location(lobby, index, 64, 0));
            warps.setDisplayName(id, "Friendly " + index);
            warps.setIcon(id, org.bukkit.Material.DIAMOND);
        }
    }

    InventoryClickEvent click(TestPlayer player, Inventory top, int slot, ClickType click) {
        InventoryAction action = switch (click) {
            case SHIFT_LEFT, SHIFT_RIGHT -> InventoryAction.MOVE_TO_OTHER_INVENTORY;
            case NUMBER_KEY -> InventoryAction.HOTBAR_SWAP;
            case DOUBLE_CLICK -> InventoryAction.COLLECT_TO_CURSOR;
            default -> InventoryAction.PICKUP_ALL;
        };
        InventoryClickEvent event = new InventoryClickEvent(view(player, top),
                InventoryType.SlotType.CONTAINER, slot, click, action);
        listener.onInventoryClick(event);
        return event;
    }

    InventoryDragEvent drag(TestPlayer player, Inventory top, int slot) {
        InventoryDragEvent event = new InventoryDragEvent(view(player, top), null, null, false,
                Map.of(slot, new ItemStack() { }));
        listener.onInventoryDrag(event);
        return event;
    }

    static Inventory inventory(InventoryHolder holder, int size) {
        ItemStack[] items = new ItemStack[size];
        return proxy(Inventory.class, (method, args) -> switch (method) {
            case "getHolder" -> holder;
            case "getSize" -> size;
            case "getItem" -> items[(int) args[0]];
            case "setItem" -> { items[(int) args[0]] = (ItemStack) args[1]; yield null; }
            case "getType" -> InventoryType.CHEST;
            default -> DEFAULT;
        });
    }

    static InventoryView view(TestPlayer player, Inventory top) {
        return proxy(InventoryView.class, (method, args) -> switch (method) {
            case "getTopInventory" -> top;
            case "getBottomInventory" -> player.bottom;
            case "getPlayer" -> player.player;
            case "getType" -> InventoryType.CHEST;
            case "getInventory" -> (int) args[0] < 0 ? null : (int) args[0] < top.getSize() ? top : player.bottom;
            case "convertSlot" -> (int) args[0] < top.getSize() ? args[0] : (int) args[0] - top.getSize();
            case "countSlots" -> top.getSize() + player.bottom.getSize();
            default -> DEFAULT;
        });
    }

    static NavigatorMenu.ItemSpec spec(Inventory inventory, int slot) {
        return ((SpecItem) inventory.getItem(slot)).spec;
    }

    static String text(Inventory inventory, int slot) { return plain(spec(inventory, slot).name()); }
    static String plain(Component component) { return PlainTextComponentSerializer.plainText().serialize(component); }

    final class TestPlayer {
        final UUID id = UUID.randomUUID();
        final Inventory bottom = inventory(null, 36);
        final List<Component> received = new ArrayList<>();
        final Player player;
        Inventory open = bottom;
        World currentWorld = lobby;
        LobbyPlayerMode mode = LobbyPlayerMode.NORMAL;
        boolean online = true;
        boolean loaded = true;
        boolean participating;
        boolean cancelNextOpen;
        boolean denyDuringOpen;
        boolean failClose;
        boolean teleportAccepted = true;
        boolean changeWorldOnTeleport;
        int opens;
        int closes;
        int teleports;
        Location target;
        org.bukkit.event.player.PlayerTeleportEvent.TeleportCause cause;
        Float fallDistance;
        Vector velocity;

        TestPlayer() {
            player = proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id;
                case "isOnline" -> online;
                case "getWorld" -> currentWorld;
                case "openInventory" -> {
                    if (cancelNextOpen) { cancelNextOpen = false; yield null; }
                    Inventory old = open;
                    listener.onInventoryClose(new InventoryCloseEvent(view(this, old)));
                    open = (Inventory) args[0];
                    opens++;
                    if (denyDuringOpen) loaded = false;
                    yield view(this, open);
                }
                case "getOpenInventory" -> view(this, open);
                case "closeInventory" -> {
                    if (failClose) throw new IllegalStateException("Injected close failure");
                    Inventory old = open;
                    open = bottom;
                    closes++;
                    listener.onInventoryClose(new InventoryCloseEvent(view(this, old)));
                    yield null;
                }
                case "teleport" -> {
                    teleports++;
                    target = (Location) args[0];
                    cause = (org.bukkit.event.player.PlayerTeleportEvent.TeleportCause) args[1];
                    if (teleportAccepted && changeWorldOnTeleport && currentWorld != target.getWorld()) {
                        World from = currentWorld;
                        currentWorld = target.getWorld();
                        listener.onPlayerChangedWorld(new org.bukkit.event.player.PlayerChangedWorldEvent(
                                currentPlayer(), from));
                    }
                    yield teleportAccepted;
                }
                case "setFallDistance" -> { fallDistance = (Float) args[0]; yield null; }
                case "setVelocity" -> { velocity = (Vector) args[0]; yield null; }
                case "sendMessage" -> {
                    for (Object value : args) if (value instanceof Component component) received.add(component);
                    yield null;
                }
                default -> DEFAULT;
            });
        }

        private Player currentPlayer() { return player; }
        String output() { return received.stream().map(NavigatorFixture::plain).reduce("", (a, b) -> a + b); }
    }

    private static final class SpecItem extends ItemStack {
        final NavigatorMenu.ItemSpec spec;
        SpecItem(NavigatorMenu.ItemSpec spec) { super(); this.spec = spec; }
    }

    private static World world(String name) {
        return proxy(World.class, (method, args) -> method.equals("getName") ? name : DEFAULT);
    }

    private static final Object DEFAULT = new Object();
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, ProxyAction action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(instance);
                case "equals" -> instance == args[0];
                case "toString" -> type.getSimpleName() + "NavigatorFixture";
                default -> null;
            };
            Object result = action.invoke(method.getName(), args == null ? new Object[0] : args);
            return result == DEFAULT ? defaultValue(method.getReturnType()) : result;
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

    @FunctionalInterface interface ProxyAction { Object invoke(String method, Object[] args); }
}
