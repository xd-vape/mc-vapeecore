package dev.vapee.core.settings.visibility;

import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.settings.SettingsMenu;
import dev.vapee.core.settings.SettingsListener;
import dev.vapee.core.settings.SettingsMenuFixture;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

final class VisibilityMenuFixture {
    final MemoryRepository repository = new MemoryRepository();
    final PlayerService players = new PlayerService(repository, logger());
    final PlayerSettingsService settings = new PlayerSettingsService(players);
    final PlayerIdentityService identities = new PlayerIdentityService(players);
    final MessageService messages;
    final Map<UUID, TestPlayer> online = new HashMap<>();
    final VisibilitySettingsMenu visibilityMenu;
    final VisiblePlayersMenu visiblePlayersMenu;
    final VisibilitySettingsListener visibilityListener;
    final VisiblePlayersListener visiblePlayersListener;
    final SettingsMenu rootMenu;
    final SettingsListener rootListener;
    int applyCalls;
    int hotbarCalls;
    int settingsBackCalls;

    VisibilityMenuFixture() throws Exception {
        var constructor = MessageService.class.getDeclaredConstructor(java.util.function.Supplier.class);
        constructor.setAccessible(true);
        messages = constructor.newInstance((java.util.function.Supplier<String>) () -> "");
        visibilityMenu = new VisibilitySettingsMenu(settings, messages, this::onlinePlayer,
                this::visibilityInventory, VisibilitySpecItem::new, logger());
        visiblePlayersMenu = new VisiblePlayersMenu(settings, identities, messages, this::onlinePlayer,
                this::visibleInventory, VisibleSpecItem::new, logger());
        rootMenu = SettingsMenuFixture.createMenu(settings, messages, this::onlinePlayer,
                (holder, size) -> inventory(holder, size));
        rootListener = SettingsMenuFixture.createListener(rootMenu, settings, ignored -> { },
                visibilityMenu::open, this::sound, messages, logger());
        visibilityListener = new VisibilitySettingsListener(visibilityMenu, visiblePlayersMenu,
                player -> { settingsBackCalls++; rootMenu.open(player); }, settings, ignored -> applyCalls++,
                ignored -> hotbarCalls++, this::sound, messages, logger());
        visiblePlayersListener = new VisiblePlayersListener(visiblePlayersMenu, visibilityMenu,
                settings, ignored -> applyCalls++, this::sound, messages, logger());
    }

    TestPlayer player(String name, boolean onlineNow) {
        UUID id = UUID.nameUUIDFromBytes(("visibility-menu-" + name + "-" + this.online.size())
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return player(id, name, onlineNow, true);
    }

    TestPlayer player(UUID id, String name, boolean onlineNow, boolean loaded) {
        players.loadPlayer(id, name);
        TestPlayer result = new TestPlayer(id, name, onlineNow);
        online.put(id, result);
        if (!loaded) players.unloadPlayer(id);
        return result;
    }

    UUID knownOffline(String name) {
        TestPlayer player = player(name, false);
        players.unloadPlayer(player.id);
        return player.id;
    }

    Player onlinePlayer(UUID id) {
        TestPlayer result = online.get(id);
        return result != null && result.online ? result.player : null;
    }

    private void sound(Player player) {
        TestPlayer target = online.get(player.getUniqueId());
        if (target != null && settings.areSoundsEnabled(target.id).orElse(false)) target.soundCalls++;
    }

    Inventory visibilityInventory(VisibilitySettingsHolder holder, int size, Component title) {
        return inventory(holder, size);
    }

    Inventory visibleInventory(VisiblePlayersHolder holder, int size, Component title) {
        return inventory(holder, size);
    }

    Inventory inventory(InventoryHolder holder, int size) {
        return new TestInventory(holder, size).inventory;
    }

    InventoryClickEvent visibilityClick(TestPlayer player, Inventory top, int rawSlot, ClickType click) {
        InventoryClickEvent event = clickEvent(player, top, rawSlot, click);
        visibilityListener.onInventoryClick(event);
        return event;
    }

    InventoryClickEvent visibleClick(TestPlayer player, Inventory top, int rawSlot, ClickType click) {
        InventoryClickEvent event = clickEvent(player, top, rawSlot, click);
        visiblePlayersListener.onInventoryClick(event);
        return event;
    }

    InventoryClickEvent rootClick(TestPlayer player, Inventory top, int rawSlot, ClickType click) {
        InventoryClickEvent event = clickEvent(player, top, rawSlot, click);
        rootListener.onInventoryClick(event);
        return event;
    }

    private void closeEvent(TestPlayer player, Inventory top) {
        InventoryCloseEvent event = new InventoryCloseEvent(view(player, top));
        rootListener.onInventoryClose(event);
        visibilityListener.onInventoryClose(event);
        visiblePlayersListener.onInventoryClose(event);
    }

    private InventoryClickEvent clickEvent(TestPlayer player, Inventory top, int rawSlot, ClickType click) {
        InventoryAction action = switch (click) {
            case SHIFT_LEFT, SHIFT_RIGHT -> InventoryAction.MOVE_TO_OTHER_INVENTORY;
            case NUMBER_KEY -> InventoryAction.HOTBAR_SWAP;
            case DOUBLE_CLICK -> InventoryAction.COLLECT_TO_CURSOR;
            default -> InventoryAction.PICKUP_ALL;
        };
        return new InventoryClickEvent(view(player, top), InventoryType.SlotType.CONTAINER,
                rawSlot, click, action);
    }

    InventoryView view(TestPlayer player, Inventory top) {
        return proxy(InventoryView.class, (method, arguments) -> switch (method) {
            case "getTopInventory" -> top;
            case "getBottomInventory" -> player.bottom;
            case "getPlayer" -> player.player;
            case "getType" -> InventoryType.CHEST;
            case "getInventory" -> (int) arguments[0] < 0 ? null
                    : (int) arguments[0] < top.getSize() ? top : player.bottom;
            case "convertSlot" -> (int) arguments[0] < top.getSize()
                    ? arguments[0] : (int) arguments[0] - top.getSize();
            case "getSlotType" -> InventoryType.SlotType.CONTAINER;
            case "countSlots" -> top.getSize() + player.bottom.getSize();
            case "getTitle", "getOriginalTitle" -> "Harness";
            default -> DEFAULT;
        });
    }

    static VisibilitySettingsHolder visibilityHolder(Inventory inventory) {
        return (VisibilitySettingsHolder) inventory.getHolder();
    }

    static VisiblePlayersHolder visibleHolder(Inventory inventory) {
        return (VisiblePlayersHolder) inventory.getHolder();
    }

    static UiItemSpec visibilitySpec(Inventory inventory, int slot) {
        return ((VisibilitySpecItem) inventory.getItem(slot)).spec;
    }

    static UiItemSpec visibleSpec(Inventory inventory, int slot) {
        return ((VisibleSpecItem) inventory.getItem(slot)).spec;
    }

    final class TestPlayer {
        final UUID id;
        final String name;
        final Player player;
        final Inventory bottom = inventory(null, 36);
        final List<Component> received = new ArrayList<>();
        boolean online;
        boolean permitted = true;
        Inventory open;
        int closeCalls;
        int soundCalls;

        TestPlayer(UUID id, String name, boolean online) {
            this.id = id;
            this.name = name;
            this.online = online;
            this.player = proxy(Player.class, (method, arguments) -> switch (method) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> this.online;
                case "hasPermission" -> permitted;
                case "openInventory" -> {
                    if (open != null) closeEvent(this, open);
                    open = (Inventory) arguments[0];
                    yield view(this, open);
                }
                case "getOpenInventory" -> view(this, open == null ? bottom : open);
                case "closeInventory" -> {
                    if (open != null) closeEvent(this, open);
                    closeCalls++;
                    open = bottom;
                    yield null;
                }
                case "sendMessage" -> {
                    if (arguments != null) {
                        for (Object argument : arguments) {
                            if (argument instanceof Component component) received.add(component);
                        }
                    }
                    yield null;
                }
                case "playSound" -> {
                    soundCalls++;
                    yield null;
                }
                default -> DEFAULT;
            });
        }
    }

    static final class VisibilitySpecItem extends ItemStack {
        final UiItemSpec spec;
        VisibilitySpecItem(UiItemSpec spec) {
            super();
            this.spec = spec;
        }
    }

    static final class VisibleSpecItem extends ItemStack {
        final UiItemSpec spec;
        VisibleSpecItem(UiItemSpec spec) {
            super();
            this.spec = spec;
        }
    }

    private static final class TestInventory {
        final InventoryHolder holder;
        final ItemStack[] items;
        final Inventory inventory;

        TestInventory(InventoryHolder holder, int size) {
            this.holder = holder;
            this.items = new ItemStack[size];
            this.inventory = proxy(Inventory.class, (method, arguments) -> switch (method) {
                case "getHolder" -> holder;
                case "getSize" -> size;
                case "getItem" -> items[(int) arguments[0]];
                case "setItem" -> {
                    items[(int) arguments[0]] = (ItemStack) arguments[1];
                    yield null;
                }
                case "getType" -> InventoryType.CHEST;
                default -> DEFAULT;
            });
        }
    }

    static final class MemoryRepository implements PlayerRepository {
        final Map<UUID, CorePlayer> data = new HashMap<>();
        boolean failNext;
        int saves;

        @Override public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(data.get(id)); }
        @Override public void save(CorePlayer player) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("simulated persistence failure");
            }
            data.put(player.getUniqueId(), player);
            saves++;
        }
        @Override public boolean exists(UUID id) { return data.containsKey(id); }
        @Override public Set<UUID> findUniqueIdsByName(String name) {
            return data.values().stream().filter(player -> player.getName().equalsIgnoreCase(name))
                    .map(CorePlayer::getUniqueId).collect(Collectors.toUnmodifiableSet());
        }
    }

    private static final Object DEFAULT = new Object();

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, ProxyAction action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    case "toString" -> type.getSimpleName() + "FixtureProxy";
                    default -> null;
                };
            }
            Object result = action.invoke(method.getName(), args == null ? new Object[0] : args);
            return result == DEFAULT ? defaultValue(method.getReturnType()) : result;
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            if (Collection.class.isAssignableFrom(type)) return List.of();
            return null;
        }
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

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    @FunctionalInterface
    interface ProxyAction {
        Object invoke(String method, Object[] arguments);
    }
}
