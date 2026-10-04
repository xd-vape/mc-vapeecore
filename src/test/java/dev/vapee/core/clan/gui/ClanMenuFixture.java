package dev.vapee.core.clan.gui;

import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.clan.*;
import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;

import java.lang.reflect.Proxy;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Logger;

final class ClanMenuFixture {
    final MemoryClans repository = new MemoryClans();
    final Map<UUID, CorePlayer> known = new HashMap<>();
    final Map<UUID, TestPlayer> players = new HashMap<>();
    final MessageService messages;
    final PlayerIdentityService identity;
    final ClanService clans;
    final ClanMenu menu;
    final ClanMenuListener listener;

    ClanMenuFixture() throws Exception { this(ClanLimits.defaults()); }

    ClanMenuFixture(ClanLimits limits) throws Exception {
        var constructor = MessageService.class.getDeclaredConstructor(Supplier.class);
        constructor.setAccessible(true);
        messages = constructor.newInstance((Supplier<String>) () -> "");
        PlayerRepository playerRepository = new PlayerRepository() {
            @Override public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(known.get(id)); }
            @Override public void save(CorePlayer player) { known.put(player.getUniqueId(), player); }
            @Override public boolean exists(UUID id) { return known.containsKey(id); }
            @Override public Set<UUID> findUniqueIdsByName(String name) {
                Set<UUID> result = new HashSet<>();
                known.values().stream().filter(player -> player.getName().equalsIgnoreCase(name))
                        .forEach(player -> result.add(player.getUniqueId()));
                return result;
            }
        };
        identity = new PlayerIdentityService(new PlayerService(playerRepository, logger()));
        clans = new ClanService(repository, limits, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC), UUID::randomUUID);
        menu = new ClanMenu(clans, identity, messages, id -> {
            TestPlayer player = players.get(id);
            return player != null && player.online ? player.player : null;
        }, this::inventory, SpecItem::new, logger());
        listener = new ClanMenuListener(menu, clans, new ClanMessages(messages, id -> {
            TestPlayer player = players.get(id);
            return player != null && player.online ? player.player : null;
        }), messages, logger());
    }

    TestPlayer player(String name, boolean online) {
        UUID id = UUID.randomUUID();
        known.put(id, new CorePlayer(id, name, Instant.EPOCH, Instant.EPOCH,
                PlayerSettings.defaults(), CoinWallet.empty(), PlayerSocial.empty()));
        TestPlayer player = new TestPlayer(id, name, online);
        players.put(id, player);
        return player;
    }

    TestPlayer unknown(UUID id) {
        TestPlayer player = new TestPlayer(id, id.toString(), false);
        players.put(id, player);
        return player;
    }

    Inventory inventory(ClanMenuHolder holder, int size, Component title) {
        return new TestInventory(holder, size).inventory;
    }

    InventoryClickEvent click(TestPlayer player, Inventory top, int slot, ClickType click) {
        InventoryAction action = switch (click) {
            case SHIFT_LEFT, SHIFT_RIGHT -> InventoryAction.MOVE_TO_OTHER_INVENTORY;
            case NUMBER_KEY -> InventoryAction.HOTBAR_SWAP;
            case DOUBLE_CLICK -> InventoryAction.COLLECT_TO_CURSOR;
            default -> InventoryAction.PICKUP_ALL;
        };
        InventoryClickEvent event = new InventoryClickEvent(view(player, top), InventoryType.SlotType.CONTAINER,
                slot, click, action);
        listener.onInventoryClick(event);
        return event;
    }

    InventoryView view(TestPlayer player, Inventory top) {
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
            case "getTitle", "getOriginalTitle" -> "Harness";
            default -> null;
        });
    }

    static ClanMenuHolder holder(Inventory inventory) { return (ClanMenuHolder) inventory.getHolder(); }
    static SpecItem spec(Inventory inventory, int slot) { return (SpecItem) inventory.getItem(slot); }

    final class TestPlayer {
        final UUID id;
        final String name;
        final Player player;
        final Inventory bottom = new TestInventory(null, 36).inventory;
        final List<Component> received = new ArrayList<>();
        boolean online;
        boolean permitted = true;
        Inventory open;
        int closeCalls;

        TestPlayer(UUID id, String name, boolean online) {
            this.id = id;
            this.name = name;
            this.online = online;
            player = proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> this.online;
                case "hasPermission" -> permitted;
                case "openInventory" -> { open = (Inventory) args[0]; yield view(this, open); }
                case "getOpenInventory" -> view(this, open == null ? bottom : open);
                case "closeInventory" -> { closeCalls++; open = bottom; yield null; }
                case "sendMessage" -> {
                    if (args != null) for (Object arg : args) if (arg instanceof Component c) received.add(c);
                    yield null;
                }
                default -> null;
            });
        }
    }

    static final class SpecItem extends ItemStack {
        final UiItemSpec spec;
        SpecItem(UiItemSpec spec) { super(); this.spec = spec; }
    }
    private static final class TestInventory {
        final ItemStack[] items;
        final Inventory inventory;
        TestInventory(InventoryHolder holder, int size) {
            items = new ItemStack[size];
            inventory = proxy(Inventory.class, (method, args) -> switch (method) {
                case "getHolder" -> holder;
                case "getSize" -> size;
                case "getItem" -> items[(int) args[0]];
                case "setItem" -> { items[(int) args[0]] = (ItemStack) args[1]; yield null; }
                case "getType" -> InventoryType.CHEST;
                default -> null;
            });
        }
    }

    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, ProxyAction action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getName().equals("hashCode")) return System.identityHashCode(instance);
            if (method.getName().equals("equals")) return instance == args[0];
            if (method.getName().equals("toString")) return type.getSimpleName() + "Proxy";
            return action.invoke(method.getName(), args);
        });
    }
    interface ProxyAction { Object invoke(String method, Object[] args); }
    static Logger logger() {
        Logger logger = Logger.getLogger("ClanMenuFixture-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }
    static final class MemoryClans implements ClanRepository {
        ClanSnapshot snapshot = ClanSnapshot.empty();
        boolean failNext;
        @Override public ClanSnapshot initialize() { return snapshot; }
        @Override public void save(ClanSnapshot next) {
            if (failNext) { failNext = false; throw new ClanRepositoryException("simulated failure"); }
            snapshot = next;
        }
    }
}
