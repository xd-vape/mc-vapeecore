package dev.vapee.core.settings;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettingsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SettingsMenuHarness {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    public static void main(String[] args) throws Exception {
        MemoryRepository repository = new MemoryRepository();
        PlayerService players = new PlayerService(repository, logger());
        PlayerSettingsService settings = new PlayerSettingsService(players);
        MessageService messages = messages();
        UUID ownerId = UUID.randomUUID();
        players.loadPlayer(ownerId, "Owner");
        TestPlayer owner = new TestPlayer(ownerId);
        SettingsMenu menu = new SettingsMenu(settings, messages,
                (holder, size, title) -> inventory(holder, size), SpecItem::new);
        int[] presentationRefresh = {0};
        int[] visibilityOpens = {0};
        SettingsListener listener = new SettingsListener(menu, settings,
                ignored -> presentationRefresh[0]++, ignored -> visibilityOpens[0]++,
                ignored -> { },
                messages, logger());

        menu.open(owner.player);
        Inventory inventory = owner.open;
        check(inventory.getSize() == 27
                        && ((SettingsInventoryHolder) inventory.getHolder()).getOwnerUniqueId().equals(ownerId),
                "existing settings menu size and owner binding remain");
        check(spec(inventory, SettingsMenu.VISIBILITY_SLOT).material() == Material.SPYGLASS
                        && text(inventory, SettingsMenu.VISIBILITY_SLOT).equals("Player Visibility")
                        && lore(inventory, SettingsMenu.VISIBILITY_SLOT)
                        .contains("Manage which lobby players you can see."),
                "main menu has minimal player visibility entry");
        check(text(inventory, SettingsMenu.SCOREBOARD_SLOT).equals("Scoreboard")
                        && text(inventory, SettingsMenu.SOUNDS_SLOT).equals("Sounds")
                        && text(inventory, SettingsMenu.PRIVATE_MESSAGES_SLOT).equals("Private Messages")
                        && text(inventory, SettingsMenu.FRIEND_REQUESTS_SLOT).equals("Friend Requests"),
                "existing settings entries remain in their slots");

        click(listener, owner, inventory, SettingsMenu.VISIBILITY_SLOT, ClickType.LEFT);
        check(visibilityOpens[0] == 1, "visibility entry opens the dedicated visibility menu");
        click(listener, owner, inventory, SettingsMenu.SCOREBOARD_SLOT, ClickType.LEFT);
        check(!settings.isScoreboardEnabled(ownerId).orElseThrow() && presentationRefresh[0] == 1,
                "scoreboard toggle still saves and refreshes presentation");
        click(listener, owner, inventory, SettingsMenu.PRIVATE_MESSAGES_SLOT, ClickType.LEFT);
        check(!settings.arePrivateMessagesEnabled(ownerId).orElseThrow(),
                "private-message toggle still works");
        click(listener, owner, inventory, SettingsMenu.FRIEND_REQUESTS_SLOT, ClickType.LEFT);
        check(!settings.areFriendRequestsEnabled(ownerId).orElseThrow(),
                "friend-request toggle still works");
        click(listener, owner, inventory, SettingsMenu.SOUNDS_SLOT, ClickType.LEFT);
        check(!settings.areSoundsEnabled(ownerId).orElseThrow(), "sounds toggle still works");
        click(listener, owner, inventory, SettingsMenu.CLOSE_SLOT, ClickType.LEFT);
        check(owner.closeCalls == 1, "close button remains functional");

        menu.open(owner.player);
        boolean before = settings.isScoreboardEnabled(ownerId).orElseThrow();
        repository.failNext = true;
        click(listener, owner, owner.open, SettingsMenu.SCOREBOARD_SLOT, ClickType.LEFT);
        check(settings.isScoreboardEnabled(ownerId).orElseThrow() == before
                        && plain(owner.received.getLast()).contains("could not be saved"),
                "existing save failure remains controlled and rolled back");
        System.out.println("SettingsMenuHarness passed " + checks + " checks.");
    }

    private static InventoryClickEvent click(SettingsListener listener, TestPlayer player,
                                             Inventory top, int rawSlot, ClickType click) {
        InventoryAction action = click == ClickType.SHIFT_LEFT
                ? InventoryAction.MOVE_TO_OTHER_INVENTORY : InventoryAction.PICKUP_ALL;
        InventoryClickEvent event = new InventoryClickEvent(view(player, top),
                InventoryType.SlotType.CONTAINER, rawSlot, click, action);
        listener.onInventoryClick(event);
        check(event.isCancelled(), "settings menu interaction is cancelled");
        return event;
    }

    private static Inventory inventory(InventoryHolder holder, int size) {
        ItemStack[] items = new ItemStack[size];
        return proxy(Inventory.class, (method, arguments) -> switch (method) {
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

    private static InventoryView view(TestPlayer player, Inventory top) {
        return proxy(InventoryView.class, (method, arguments) -> switch (method) {
            case "getTopInventory" -> top;
            case "getBottomInventory" -> player.bottom;
            case "getPlayer" -> player.player;
            case "getType" -> InventoryType.CHEST;
            case "getInventory" -> (int) arguments[0] < top.getSize() ? top : player.bottom;
            case "convertSlot" -> (int) arguments[0] < top.getSize()
                    ? arguments[0] : (int) arguments[0] - top.getSize();
            case "getSlotType" -> InventoryType.SlotType.CONTAINER;
            case "countSlots" -> top.getSize() + player.bottom.getSize();
            default -> DEFAULT;
        });
    }

    private static SettingsMenu.ItemSpec spec(Inventory inventory, int slot) {
        return ((SpecItem) inventory.getItem(slot)).spec;
    }

    private static String text(Inventory inventory, int slot) {
        return plain(spec(inventory, slot).name());
    }

    private static String lore(Inventory inventory, int slot) {
        return String.join("\n", spec(inventory, slot).lore().stream().map(PLAIN::serialize).toList());
    }

    private static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    private static MessageService messages() throws Exception {
        var constructor = MessageService.class.getDeclaredConstructor(java.util.function.Supplier.class);
        constructor.setAccessible(true);
        return constructor.newInstance((java.util.function.Supplier<String>) () -> "");
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static final class TestPlayer {
        final UUID id;
        final Inventory bottom = inventory(null, 36);
        final List<Component> received = new ArrayList<>();
        final Player player;
        Inventory open;
        int closeCalls;

        TestPlayer(UUID id) {
            this.id = id;
            player = proxy(Player.class, (method, arguments) -> switch (method) {
                case "getUniqueId" -> id;
                case "isOnline", "hasPermission" -> true;
                case "openInventory" -> {
                    open = (Inventory) arguments[0];
                    yield view(this, open);
                }
                case "getOpenInventory" -> view(this, open == null ? bottom : open);
                case "closeInventory" -> {
                    closeCalls++;
                    open = bottom;
                    yield null;
                }
                case "sendMessage" -> {
                    if (arguments != null) for (Object argument : arguments) {
                        if (argument instanceof Component component) received.add(component);
                    }
                    yield null;
                }
                case "playSound" -> null;
                default -> DEFAULT;
            });
        }
    }

    private static final class SpecItem extends ItemStack {
        final SettingsMenu.ItemSpec spec;
        SpecItem(SettingsMenu.ItemSpec spec) {
            super();
            this.spec = spec;
        }
    }

    private static final class MemoryRepository implements PlayerRepository {
        final Map<UUID, CorePlayer> data = new HashMap<>();
        boolean failNext;
        @Override public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(data.get(id)); }
        @Override public void save(CorePlayer player) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("simulated persistence failure");
            }
            data.put(player.getUniqueId(), player);
        }
        @Override public boolean exists(UUID id) { return data.containsKey(id); }
    }

    private static final Object DEFAULT = new Object();

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyAction action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    case "toString" -> type.getSimpleName() + "HarnessProxy";
                    default -> null;
                };
            }
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

    @FunctionalInterface
    private interface ProxyAction { Object invoke(String method, Object[] arguments); }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
