package dev.vapee.core.settings;

import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.*;

/** Local support dispatches real close handlers and retains exact inventory identity. */
public final class SettingsMenuFixture {
    final MemoryRepository repository = new MemoryRepository();
    final Logger logger = Logger.getAnonymousLogger();
    final List<LogRecord> logs = new ArrayList<>();
    final PlayerService players = new PlayerService(repository, logger);
    final PlayerSettingsService settings = new PlayerSettingsService(players);
    final MessageService messages;
    final Map<UUID, TestPlayer> online = new HashMap<>();
    final SettingsMenu menu;
    final SettingsListener listener;
    int presentationCalls;
    int visibilityCalls;
    int soundCalls;

    SettingsMenuFixture() throws Exception {
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override public void publish(LogRecord record) { logs.add(record); }
            @Override public void flush() { }
            @Override public void close() { }
        });
        var constructor = MessageService.class.getDeclaredConstructor(java.util.function.Supplier.class);
        constructor.setAccessible(true);
        messages = constructor.newInstance((java.util.function.Supplier<String>) () -> "");
        menu = createMenu(settings, messages, id -> {
            TestPlayer player = online.get(id);
            return player != null && player.online ? player.player : null;
        }, SettingsMenuFixture::inventory);
        listener = createListener(menu, settings, ignored -> presentationCalls++,
                ignored -> visibilityCalls++, ignored -> soundCalls++, messages, logger);
    }

    public static SettingsMenu createMenu(PlayerSettingsService settings, MessageService messages,
                                         Function<UUID, Player> online,
                                         java.util.function.BiFunction<SettingsInventoryHolder, Integer, Inventory> factory) {
        return new SettingsMenu(settings, messages, online,
                (holder, size, title) -> factory.apply(holder, size), SpecItem::new, Logger.getAnonymousLogger());
    }

    public static SettingsListener createListener(SettingsMenu menu, PlayerSettingsService settings,
                                                  Consumer<Player> presentation, Consumer<Player> visibility,
                                                  Consumer<Player> sound, MessageService messages, Logger logger) {
        return new SettingsListener(menu, settings, presentation, visibility, sound, messages, logger);
    }

    TestPlayer player(String name) {
        UUID id = UUID.nameUUIDFromBytes((name + online.size()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        players.loadPlayer(id, name);
        TestPlayer player = new TestPlayer(id);
        online.put(id, player);
        return player;
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
            case "getInventory" -> (int) args[0] < 0 ? null
                    : (int) args[0] < top.getSize() ? top : player.bottom;
            case "convertSlot" -> (int) args[0] < top.getSize() ? args[0] : (int) args[0] - top.getSize();
            case "getSlotType" -> InventoryType.SlotType.CONTAINER;
            case "countSlots" -> top.getSize() + player.bottom.getSize();
            default -> DEFAULT;
        });
    }

    static UiItemSpec spec(Inventory inventory, int slot) { return ((SpecItem) inventory.getItem(slot)).spec; }
    static String text(Inventory inventory, int slot) { return plain(spec(inventory, slot).name()); }
    static String plain(Component value) { return PlainTextComponentSerializer.plainText().serialize(value); }

    final class TestPlayer {
        final UUID id;
        final Inventory bottom = inventory(null, 36);
        final List<Component> received = new ArrayList<>();
        final Player player;
        Inventory open = bottom;
        int opens;
        int closes;
        boolean online = true;
        boolean permitted = true;
        boolean cancelNextOpen;

        TestPlayer(UUID id) {
            this.id = id;
            player = proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id;
                case "isOnline" -> online;
                case "hasPermission" -> permitted;
                case "openInventory" -> {
                    if (cancelNextOpen) { cancelNextOpen = false; yield null; }
                    Inventory old = open;
                    if (listener != null) listener.onInventoryClose(new InventoryCloseEvent(view(this, old)));
                    open = (Inventory) args[0];
                    opens++;
                    yield view(this, open);
                }
                case "getOpenInventory" -> view(this, open);
                case "closeInventory" -> {
                    Inventory old = open;
                    open = bottom;
                    closes++;
                    if (listener != null) listener.onInventoryClose(new InventoryCloseEvent(view(this, old)));
                    yield null;
                }
                case "sendMessage" -> {
                    for (Object value : args) if (value instanceof Component component) received.add(component);
                    yield null;
                }
                default -> DEFAULT;
            });
        }
    }

    private static final class SpecItem extends ItemStack {
        final UiItemSpec spec;
        SpecItem(UiItemSpec spec) { super(); this.spec = spec; }
    }

    static final class MemoryRepository implements PlayerRepository {
        final Map<UUID, CorePlayer> data = new HashMap<>();
        int saves;
        boolean failNext;
        @Override public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(data.get(id)); }
        @Override public void save(CorePlayer player) {
            if (failNext) { failNext = false; throw new IllegalStateException("simulated persistence failure"); }
            saves++;
            data.put(player.getUniqueId(), player);
        }
        @Override public boolean exists(UUID id) { return data.containsKey(id); }
    }

    private static final Object DEFAULT = new Object();
    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, ProxyAction action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(instance);
                case "equals" -> instance == args[0];
                case "toString" -> type.getSimpleName() + "Fixture";
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

    @FunctionalInterface interface ProxyAction { Object invoke(String method, Object[] arguments); }
}
