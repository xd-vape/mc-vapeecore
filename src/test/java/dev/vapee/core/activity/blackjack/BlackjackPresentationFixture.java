package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.ActivitySession;
import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.card.BlackjackShoe;
import dev.vapee.core.activity.blackjack.presentation.BlackjackInventoryService;
import dev.vapee.core.activity.blackjack.presentation.BlackjackWorldViewService;
import dev.vapee.core.activity.blackjack.table.*;
import dev.vapee.core.activity.location.*;
import dev.vapee.core.activity.player.ActivityParticipant;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.quest.QuestCompletionFixture;
import dev.vapee.core.worlddisplay.WorldDisplayKey;
import dev.vapee.core.worlddisplay.WorldDisplayService;
import io.papermc.paper.registry.RegistryAccess;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ItemType;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;
import java.util.function.*;
import java.util.logging.Logger;

/** Real presentation and ownership services, with only the external Paper surfaces substituted. */
final class BlackjackPresentationFixture {

    private static final Object DEFAULT = QuestCompletionFixture.DEFAULT;
    final UUID playerId = UUID.randomUUID();
    final ItemStack[] items = new ItemStack[41];
    final Map<WorldDisplayKey, UUID> entities = new HashMap<>();
    final Map<UUID, Component> texts = new HashMap<>();
    final List<String> warnings = new ArrayList<>();
    int creates;
    int removals;
    int restores;
    int relinquishes;
    int closes;
    int selectedSlot = -1;
    final Player player;
    final BlackjackSession session;
    final BlackjackInventoryService inventory;
    final BlackjackWorldViewService worldView;
    final WorldDisplayService displays;
    final BlackjackTableDefinition definition;
    final LobbyPlayerStateService lobby;

    BlackjackPresentationFixture() throws Exception {
        installItemRegistry();
        World world = proxy(World.class, (method, args) -> method.equals("getName") ? "world" : DEFAULT);
        PlayerInventory playerInventory = proxy(PlayerInventory.class, (method, args) -> switch (method) {
            case "getContents" -> items.clone();
            case "getItem" -> items[(int) args[0]];
            case "setItem" -> { items[(int) args[0]] = (ItemStack) args[1]; yield null; }
            case "clear" -> { Arrays.fill(items, null); yield null; }
            case "setHeldItemSlot" -> { selectedSlot = (int) args[0]; yield null; }
            default -> DEFAULT;
        });
        player = proxy(Player.class, (method, args) -> switch (method) {
            case "getUniqueId" -> playerId;
            case "getName" -> "Tester";
            case "getInventory" -> playerInventory;
            case "getWorld" -> world;
            case "isOnline" -> true;
            case "closeInventory" -> { closes++; yield null; }
            default -> DEFAULT;
        });
        Server server = proxy(Server.class, (method, args) -> switch (method) {
            case "getPlayer" -> playerId.equals(args[0]) ? player : null;
            case "getWorld" -> world;
            case "isPrimaryThread" -> true;
            default -> DEFAULT;
        });
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) { warnings.add(record.getMessage()); }
            public void flush() { }
            public void close() { }
        });
        JavaPlugin plugin = allocate(TestPlugin.class);
        PluginDescriptionFile description = new PluginDescriptionFile("VapeeCore", "test", "test.Plugin");
        for (var entry : Map.of("server", server, "description", description,
                "pluginMeta", description, "logger", logger).entrySet()) {
            Field field = JavaPlugin.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(plugin, entry.getValue());
        }
        var lobbyConstructor = LobbyPlayerStateService.class.getDeclaredConstructor(BooleanSupplier.class,
                Predicate.class, Supplier.class, Consumer.class, Consumer.class, Supplier.class);
        lobbyConstructor.setAccessible(true);
        lobby = lobbyConstructor.newInstance((BooleanSupplier) () -> true, (Predicate<Player>) p -> true,
                (Supplier<GameMode>) () -> GameMode.ADVENTURE, (Consumer<Player>) p -> restores++,
                (Consumer<Player>) p -> relinquishes++, (Supplier<Collection<Player>>) () -> List.of(player));
        inventory = new BlackjackInventoryService(plugin, lobby);
        ActivityService activity = new ActivityService(server,
                new PlayerService(new QuestCompletionFixture.Repository(), logger), logger);
        definition = new BlackjackTableDefinition("read-parity", new ActivityArea("world", 0, 0, 0, 10, 10, 10),
                new ActivityPosition("world", 5, 5, 2, 180, 0), new BlackjackBlockPosition("world", 5, 4, 5),
                List.of(new BlackjackSeat(1, new ActivityPosition("world", 5, 5, 8, 0, 0))));
        BlackjackService service = new BlackjackService(activity, new BlackjackService.TableAccess() {
            public Optional<BlackjackTableDefinition> getDefinition(String id) { return Optional.of(definition); }
            public Optional<BlackjackSession> getSession(String id) { return Optional.empty(); }
        }, new BlackjackService.SeatAccess() {
            public boolean reserveLowestFreeSeat(BlackjackTableDefinition table, UUID id) { return true; }
            public void mountReservedPlayer(Player p) { }
            public void releaseSeat(UUID id) { }
            public Optional<Integer> getSeatNumber(UUID id) { return Optional.of(1); }
        }, id -> playerId.equals(id) ? player : null, (p, message) -> { },
                (action, ticks) -> proxy(BukkitTask.class, (method, args) -> DEFAULT),
                () -> BlackjackShoe.sixDecks(new Random(1)), logger);
        session = new BlackjackSession(UUID.randomUUID(),
                new ActivityVenue(definition.id(), BlackjackActivityType.KEY, definition.area(), definition.dealer()), service);
        Method add = ActivitySession.class.getDeclaredMethod("addParticipant", ActivityParticipant.class);
        add.setAccessible(true);
        add.invoke(session, new ActivityParticipant(playerId, Instant.EPOCH));

        Class<?> gatewayType = Class.forName(WorldDisplayService.class.getName() + "$DisplayGateway");
        Object gateway = Proxy.newProxyInstance(gatewayType.getClassLoader(), new Class<?>[]{gatewayType},
                (instance, method, args) -> displayCall(method.getName(), args));
        var displayConstructor = WorldDisplayService.class.getDeclaredConstructor(BooleanSupplier.class, gatewayType);
        displayConstructor.setAccessible(true);
        displays = displayConstructor.newInstance((BooleanSupplier) () -> true, gateway);
        var tableConstructor = BlackjackTableService.class.getDeclaredConstructor(ActivityService.class,
                BlackjackTableConfig.class, Consumer.class, Predicate.class, Logger.class);
        tableConstructor.setAccessible(true);
        BlackjackTableService tables = tableConstructor.newInstance(activity,
                new BlackjackTableConfig(Path.of("target/harness-temp/unused-blackjack.yml"), logger),
                (Consumer<String>) id -> { }, (Predicate<String>) id -> true, logger);
        worldView = new BlackjackWorldViewService(plugin, displays, tables, service);
    }

    void activate() throws Exception {
        Method transition = ActivitySession.class.getDeclaredMethod("transitionTo", ActivityState.class);
        transition.setAccessible(true);
        transition.invoke(session, ActivityState.ACTIVE);
        session.beginRound(BlackjackShoe.sixDecks(new Random(2)));
        session.beginTurn(playerId);
    }

    void render() { worldView.onEnabled(definition, session); }

    String displayText(String key) {
        return QuestCompletionFixture.plain(texts.get(entities.get(new WorldDisplayKey("blackjack:read-parity", key))));
    }

    @SuppressWarnings("unchecked")
    private Object displayCall(String method, Object[] args) {
        return switch (method) {
            case "createText" -> {
                UUID id = UUID.randomUUID();
                creates++;
                entities.put((WorldDisplayKey) args[0], id);
                texts.put(id, (Component) args[2]);
                ((Consumer<TextDisplay>) args[3]).accept(proxy(TextDisplay.class, (name, values) -> DEFAULT));
                yield id;
            }
            case "updateText" -> { texts.put((UUID) args[0], (Component) args[1]); yield true; }
            case "teleport" -> true;
            case "remove" -> { texts.remove(args[0]); entities.values().remove(args[0]); removals++; yield null; }
            default -> throw new AssertionError("Unexpected display operation: " + method);
        };
    }

    private static <T> T proxy(Class<T> type, QuestCompletionFixture.ProxyAction action) {
        return QuestCompletionFixture.proxy(type, action);
    }

    /* Paper ItemStack delegates to the item registry. Install a process-local test provider before
       its first use so the real inventory code can construct items without a running server.
       This reflection stays in the harness; production gets no test-only API or registry change. */
    private static boolean registryInstalled;

    private static void installItemRegistry() throws Exception {
        if (registryInstalled) return;
        RegistryAccess access = proxy(RegistryAccess.class, (method, args) -> {
            if (!method.equals("getRegistry")) return DEFAULT;
            boolean items = args[0].toString().contains("item");
            return proxy(Registry.class, (operation, values) -> {
                if (items && (operation.equals("get") || operation.equals("getOrThrow"))) {
                    String key = values[0].toString();
                    return proxy(ItemType.Typed.class, (itemMethod, itemArgs) -> switch (itemMethod) {
                        case "createItemStack" -> new TestItem(Material.valueOf(key.substring(key.indexOf(':') + 1).toUpperCase(Locale.ROOT)));
                        case "getKey" -> NamespacedKey.fromString(key);
                        default -> DEFAULT;
                    });
                }
                return DEFAULT;
            });
        });
        Field field = Class.forName("io.papermc.paper.registry.RegistryAccessHolder").getDeclaredField("INSTANCE");
        Object unsafe = unsafe();
        Class<?> type = unsafe.getClass();
        Object base = type.getMethod("staticFieldBase", Field.class).invoke(unsafe, field);
        long offset = (long) type.getMethod("staticFieldOffset", Field.class).invoke(unsafe, field);
        type.getMethod("putObject", Object.class, long.class, Object.class).invoke(unsafe, base, offset, Optional.of(access));
        registryInstalled = true;
    }

    private static Object unsafe() throws Exception {
        Field field = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return field.get(null);
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        Object unsafe = unsafe();
        return type.cast(unsafe.getClass().getMethod("allocateInstance", Class.class).invoke(unsafe, type));
    }

    private static final class TestPlugin extends JavaPlugin { }

    private static final class TestItem extends ItemStack {
        private final Material material;
        private ItemMeta meta;

        private TestItem(Material material) {
            super();
            this.material = material;
            Map<NamespacedKey, Object> pdcValues = new HashMap<>();
            PersistentDataContainer pdc = proxy(PersistentDataContainer.class, (method, args) -> switch (method) {
                case "set" -> { pdcValues.put((NamespacedKey) args[0], args[2]); yield null; }
                case "get" -> pdcValues.get(args[0]);
                case "has" -> pdcValues.containsKey(args[0]);
                default -> DEFAULT;
            });
            Map<String, Object> values = new HashMap<>();
            meta = proxy(ItemMeta.class, (method, args) -> {
                if (method.equals("getPersistentDataContainer")) return pdc;
                if (method.equals("displayName") || method.equals("lore")) {
                    if (args.length == 0) return values.get(method);
                    values.put(method, args[0]);
                    return null;
                }
                return DEFAULT;
            });
        }

        @Override public Material getType() { return material; }
        @Override public ItemMeta getItemMeta() { return meta; }
        @Override public boolean hasItemMeta() { return true; }
        @Override public boolean setItemMeta(ItemMeta meta) { this.meta = meta; return true; }
    }
}
