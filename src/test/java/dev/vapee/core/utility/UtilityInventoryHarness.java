package dev.vapee.core.utility;

import net.kyori.adventure.text.Component;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class UtilityInventoryHarness {

    private static int checks;

    private UtilityInventoryHarness() {
    }

    public static void main(String[] args) throws Exception {
        testSnapshotAndHolder();
        testReadOnlyEvents();
        testLifecycle();
        testPluginYaml();
        System.out.println("UtilityInventoryHarness passed " + checks + " checks.");
    }

    private static void testSnapshotAndHolder() {
        Fixture fixture = new Fixture();
        MutablePlayer viewer = fixture.player("Viewer");
        MutablePlayer target = fixture.player("Target");
        target.inventoryState.items[0] = item("hotbar", 3);
        target.inventoryState.items[9] = item("main", 12);
        target.inventoryState.helmet = item("helmet");
        target.inventoryState.chestplate = item("chestplate");
        target.inventoryState.leggings = item("leggings");
        target.inventoryState.boots = item("boots");
        target.inventoryState.offhand = item("offhand");

        Inventory snapshot = fixture.service.openSnapshot(viewer.player, target.player);
        check(snapshot.getHolder() instanceof InvseeInventoryHolder,
                "invsee snapshot has its own holder type");
        InvseeInventoryHolder holder = (InvseeInventoryHolder) snapshot.getHolder();
        check(holder.getViewerUniqueId().equals(viewer.id)
                        && holder.getTargetUniqueId().equals(target.id),
                "invsee holder binds viewer and target UUIDs");
        check(holder.getInventory() == snapshot && viewer.openInventory == snapshot,
                "invsee holder and viewer bind the exact generated inventory");
        check(marker(snapshot.getItem(0)).equals("hotbar")
                        && snapshot.getItem(0).getAmount() == 3
                        && marker(snapshot.getItem(9)).equals("main"),
                "invsee snapshot copies hotbar and main inventory slots");
        check(marker(snapshot.getItem(InvseeService.HELMET_SLOT)).equals("helmet")
                        && marker(snapshot.getItem(InvseeService.CHESTPLATE_SLOT)).equals("chestplate")
                        && marker(snapshot.getItem(InvseeService.LEGGINGS_SLOT)).equals("leggings")
                        && marker(snapshot.getItem(InvseeService.BOOTS_SLOT)).equals("boots")
                        && marker(snapshot.getItem(InvseeService.OFFHAND_SLOT)).equals("offhand"),
                "invsee snapshot copies armor and offhand into documented slots");
        check(snapshot.getItem(0) != target.inventoryState.items[0]
                        && snapshot.getItem(InvseeService.OFFHAND_SLOT) != target.inventoryState.offhand,
                "invsee stores clones instead of target-owned item references");
        snapshot.setItem(0, null);
        check(marker(target.inventoryState.items[0]).equals("hotbar"),
                "snapshot changes cannot mutate the target inventory");
        check(fixture.service.activeViewCount() == 1,
                "opening a snapshot records exactly one UUID-based runtime view");
    }

    private static void testReadOnlyEvents() {
        Fixture fixture = new Fixture();
        MutablePlayer viewer = fixture.player("Viewer");
        MutablePlayer target = fixture.player("Target");
        Inventory snapshot = fixture.service.openSnapshot(viewer.player, target.player);
        InventoryView view = fixture.view(viewer.player, snapshot, viewer.inventory);

        List<InventoryClickEvent> clicks = List.of(
                click(view, ClickType.LEFT, InventoryAction.PICKUP_ALL),
                click(view, ClickType.SHIFT_LEFT, InventoryAction.MOVE_TO_OTHER_INVENTORY),
                new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0,
                        ClickType.NUMBER_KEY, InventoryAction.HOTBAR_SWAP, 1),
                click(view, ClickType.DOUBLE_CLICK, InventoryAction.COLLECT_TO_CURSOR),
                click(view, ClickType.DROP, InventoryAction.DROP_ONE_SLOT),
                click(view, ClickType.SWAP_OFFHAND, InventoryAction.HOTBAR_SWAP)
        );
        for (InventoryClickEvent event : clicks) {
            fixture.service.onInventoryClick(event);
            check(event.isCancelled(), "invsee cancels click path " + event.getClick());
        }

        InventoryClickEvent bottomClick = new InventoryClickEvent(
                view, InventoryType.SlotType.CONTAINER, snapshot.getSize(),
                ClickType.LEFT, InventoryAction.PLACE_ALL
        );
        fixture.service.onInventoryClick(bottomClick);
        check(bottomClick.isCancelled(), "invsee cancels bottom-inventory transfer setup");

        InventoryCreativeEvent creative = new InventoryCreativeEvent(
                view, InventoryType.SlotType.CONTAINER, 0, item("creative")
        );
        fixture.service.onInventoryClick(creative);
        check(creative.isCancelled(), "invsee cancels creative inventory clicks");

        InventoryDragEvent drag = new InventoryDragEvent(
                view,
                item("cursor"),
                item("old-cursor"),
                false,
                Map.of(0, item("drag"))
        );
        fixture.service.onInventoryDrag(drag);
        check(drag.isCancelled(), "invsee cancels inventory drags");

        PlayerSwapHandItemsEvent swap = new PlayerSwapHandItemsEvent(
                viewer.player, item("main-hand"), item("off-hand")
        );
        fixture.service.onSwap(swap);
        check(swap.isCancelled(), "invsee cancels offhand swaps while the view is active");

        Item droppedItem = proxy(Item.class, (method, arguments) -> defaultValue(method.getReturnType()));
        PlayerDropItemEvent drop = new PlayerDropItemEvent(viewer.player, droppedItem);
        fixture.service.onDrop(drop);
        check(drop.isCancelled(), "invsee cancels drops while the view is active");

        MutablePlayer wrongViewer = fixture.player("WrongViewer");
        InventoryClickEvent wrongViewerClick = click(
                fixture.view(wrongViewer.player, snapshot, wrongViewer.inventory),
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        fixture.service.onInventoryClick(wrongViewerClick);
        check(wrongViewerClick.isCancelled(), "invsee cancels a wrong-viewer forged view");

        Inventory staleSnapshot = snapshot;
        Inventory currentSnapshot = fixture.service.openSnapshot(viewer.player, target.player);
        InventoryClickEvent staleClick = click(
                fixture.view(viewer.player, staleSnapshot, viewer.inventory),
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        fixture.service.onInventoryClick(staleClick);
        check(staleClick.isCancelled() && fixture.service.isActiveView(viewer.id, currentSnapshot),
                "invsee cancels stale inventory clicks without replacing the active binding");

        InvseeInventoryHolder forgedHolder = new InvseeInventoryHolder(viewer.id, target.id);
        Inventory forgedInventory = fixture.inventory(forgedHolder, InvseeService.INVENTORY_SIZE);
        InventoryClickEvent forgedClick = click(
                fixture.view(viewer.player, forgedInventory, viewer.inventory),
                ClickType.LEFT,
                InventoryAction.PICKUP_ALL
        );
        fixture.service.onInventoryClick(forgedClick);
        check(forgedClick.isCancelled(), "invsee cancels an unbound forged holder safely");
    }

    private static void testLifecycle() {
        Fixture fixture = new Fixture();
        MutablePlayer viewer = fixture.player("Viewer");
        MutablePlayer target = fixture.player("Target");

        Inventory snapshot = fixture.service.openSnapshot(viewer.player, target.player);
        fixture.service.onInventoryClose(new InventoryCloseEvent(
                fixture.view(viewer.player, snapshot, viewer.inventory)
        ));
        check(fixture.service.activeViewCount() == 0, "viewer close removes runtime metadata");

        fixture.service.openSnapshot(viewer.player, target.player);
        fixture.service.onPlayerQuit(new PlayerQuitEvent(
                viewer.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED
        ));
        check(fixture.service.activeViewCount() == 0, "viewer quit removes runtime metadata");

        fixture.service.openSnapshot(viewer.player, target.player);
        int closesBeforeTargetQuit = viewer.closeCalls;
        fixture.service.onPlayerQuit(new PlayerQuitEvent(
                target.player, Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED
        ));
        check(fixture.service.activeViewCount() == 0 && viewer.closeCalls == closesBeforeTargetQuit + 1,
                "target quit closes and removes every matching snapshot");

        fixture.service.openSnapshot(viewer.player, target.player);
        int closesBeforeDisable = viewer.closeCalls;
        fixture.service.disable();
        check(fixture.service.activeViewCount() == 0 && viewer.closeCalls == closesBeforeDisable + 1,
                "utility disable closes snapshots and clears runtime metadata");

        viewer.inventoryOpenAllowed = false;
        check(fixture.service.openSnapshot(viewer.player, target.player) == null
                        && fixture.service.activeViewCount() == 0,
                "a cancelled inventory open leaves no stale invsee metadata");

        InvseeService offThread = new InvseeService(
                () -> false,
                ignored -> null,
                fixture::createInventory
        );
        boolean rejected = false;
        try {
            offThread.openSnapshot(viewer.player, target.player);
        } catch (IllegalStateException exception) {
            rejected = exception.getMessage().contains("primary server thread");
        }
        check(rejected, "invsee lifecycle rejects off-main-thread ownership");
    }

    private static void testPluginYaml() throws Exception {
        YamlConfiguration yaml;
        try (InputStream stream = UtilityInventoryHarness.class.getClassLoader()
                .getResourceAsStream("plugin.yml")) {
            if (stream == null) {
                throw new AssertionError("Missing plugin.yml resource");
            }
            yaml = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
        }

        Set<String> utilityCommands = Set.of(
                "build", "fly", "speed", "gamemode", "tp", "tphere", "heal", "feed",
                "ping", "clear", "invsee", "enderchest"
        );
        check(utilityCommands.stream().allMatch(name -> yaml.contains("commands." + name)),
                "plugin.yml registers all twelve utility commands");
        check("/tp <target> | /tp <player> <target>".equals(yaml.getString("commands.tp.usage")),
                "plugin.yml publishes both teleport forms");

        Set<String> newPermissions = Set.of(
                "vapeecore.utility.teleport.others",
                "vapeecore.utility.teleport.bypass",
                "vapeecore.utility.ping",
                "vapeecore.utility.ping.others",
                "vapeecore.utility.clear",
                "vapeecore.utility.clear.others",
                "vapeecore.utility.invsee",
                "vapeecore.utility.invsee.modify",
                "vapeecore.utility.enderchest",
                "vapeecore.utility.enderchest.others"
        );
        check(newPermissions.stream().allMatch(node -> yaml.contains("permissions." + node)),
                "plugin.yml registers every new utility permission");
        check(newPermissions.stream().allMatch(node ->
                        "op".equalsIgnoreCase(yaml.getString("permissions." + node + ".default"))),
                "every new utility permission defaults to op");
        check(yaml.getConfigurationSection("permissions").getKeys(false).stream()
                        .filter(node -> node.startsWith("vapeecore.utility."))
                        .noneMatch(node -> "true".equalsIgnoreCase(
                                yaml.getString("permissions." + node + ".default"))),
                "no utility staff permission accidentally defaults to true");
    }

    private static InventoryClickEvent click(
            InventoryView view,
            ClickType clickType,
            InventoryAction action
    ) {
        return new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, 0, clickType, action);
    }

    private static TestItemStack item(String marker) {
        return item(marker, 1);
    }

    private static TestItemStack item(String marker, int amount) {
        return new TestItemStack(marker, amount);
    }

    private static String marker(ItemStack item) {
        return ((TestItemStack) item).marker;
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
        checks++;
    }

    private static final class Fixture {
        private final Map<UUID, MutablePlayer> players = new HashMap<>();
        private final InvseeService service = new InvseeService(
                () -> true,
                id -> {
                    MutablePlayer player = players.get(id);
                    return player == null || !player.online ? null : player.player;
                },
                this::createInventory
        );

        private MutablePlayer player(String name) {
            MutablePlayer player = new MutablePlayer(this, name);
            players.put(player.id, player);
            return player;
        }

        private Inventory createInventory(InvseeInventoryHolder holder, int size, Component title) {
            return inventory(holder, size);
        }

        private Inventory inventory(InventoryHolder holder, int size) {
            MutableInventory state = new MutableInventory(holder, size);
            return state.inventory;
        }

        private InventoryView view(HumanEntity viewer, Inventory top, Inventory bottom) {
            return proxy(InventoryView.class, (method, arguments) -> switch (method.getName()) {
                case "getTopInventory" -> top;
                case "getBottomInventory" -> bottom;
                case "getPlayer" -> viewer;
                case "getType" -> InventoryType.CHEST;
                case "getInventory" -> (int) arguments[0] < top.getSize() ? top : bottom;
                case "convertSlot" -> (int) arguments[0] < top.getSize()
                        ? arguments[0]
                        : (int) arguments[0] - top.getSize();
                case "getSlotType" -> InventoryType.SlotType.CONTAINER;
                case "countSlots" -> top.getSize() + bottom.getSize();
                case "getTitle", "getOriginalTitle" -> "Harness";
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static final class MutablePlayer {
        private final UUID id = UUID.randomUUID();
        private final String name;
        private final MutableInventory inventoryState = new MutableInventory(null, 41);
        private final PlayerInventory inventory;
        private final Player player;
        private boolean online = true;
        private boolean inventoryOpenAllowed = true;
        private Inventory openInventory;
        private int closeCalls;

        private MutablePlayer(Fixture fixture, String name) {
            this.name = name;
            this.inventory = proxy(PlayerInventory.class, (method, arguments) ->
                    inventoryState.invoke(method.getName(), arguments));
            Player[] playerReference = new Player[1];
            this.player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> online;
                case "getInventory" -> inventory;
                case "openInventory" -> {
                    if (!inventoryOpenAllowed) {
                        yield null;
                    }
                    openInventory = (Inventory) arguments[0];
                    yield fixture.view(playerReference[0], openInventory, inventory);
                }
                case "getOpenInventory" -> fixture.view(playerReference[0], openInventory, inventory);
                case "closeInventory" -> set(() -> {
                    closeCalls++;
                    openInventory = inventory;
                });
                default -> defaultValue(method.getReturnType());
            });
            playerReference[0] = this.player;
            this.inventoryState.inventory = inventory;
            this.openInventory = inventory;
        }
    }

    private static final class MutableInventory {
        private final InventoryHolder holder;
        private final ItemStack[] items;
        private Inventory inventory;
        private ItemStack helmet;
        private ItemStack chestplate;
        private ItemStack leggings;
        private ItemStack boots;
        private ItemStack offhand;

        private MutableInventory(InventoryHolder holder, int size) {
            this.holder = holder;
            this.items = new ItemStack[size];
            if (!(holder == null && size == 41)) {
                this.inventory = proxy(Inventory.class, (method, arguments) ->
                        invoke(method.getName(), arguments));
            }
        }

        private Object invoke(String method, Object[] arguments) {
            return switch (method) {
                case "getSize" -> items.length;
                case "getHolder" -> holder;
                case "getType" -> InventoryType.CHEST;
                case "getItem" -> items[(int) arguments[0]];
                case "setItem" -> set(() -> items[(int) arguments[0]] = (ItemStack) arguments[1]);
                case "clear" -> set(() -> java.util.Arrays.fill(items, null));
                case "getHelmet" -> helmet;
                case "getChestplate" -> chestplate;
                case "getLeggings" -> leggings;
                case "getBoots" -> boots;
                case "getItemInOffHand" -> offhand;
                default -> defaultValue(findReturnType(method));
            };
        }

        private static Class<?> findReturnType(String methodName) {
            return java.util.Arrays.stream(PlayerInventory.class.getMethods())
                    .filter(method -> method.getName().equals(methodName))
                    .findFirst()
                    .map(java.lang.reflect.Method::getReturnType)
                    .orElse(Object.class);
        }
    }

    private static final class TestItemStack extends ItemStack {
        private final String marker;
        private final int amount;

        private TestItemStack(String marker, int amount) {
            super();
            this.marker = marker;
            this.amount = amount;
        }

        @Override
        public int getAmount() {
            return amount;
        }

        @Override
        public TestItemStack clone() {
            return new TestItemStack(marker, amount);
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
