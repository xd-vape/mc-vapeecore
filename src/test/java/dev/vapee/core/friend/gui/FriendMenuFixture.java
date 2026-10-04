package dev.vapee.core.friend.gui;

import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.friend.FriendLimits;
import dev.vapee.core.friend.FriendMessages;
import dev.vapee.core.friend.FriendRepository;
import dev.vapee.core.friend.FriendRepositoryException;
import dev.vapee.core.friend.FriendRequestPolicy;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.friend.FriendSnapshot;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import net.kyori.adventure.text.Component;
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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

final class FriendMenuFixture {

    final MemoryFriends repository = new MemoryFriends();
    final AtomicReference<FriendLimits> limits = new AtomicReference<>(new FriendLimits(100, 100, 100));
    final Map<UUID, TestPlayer> players = new HashMap<>();
    final Map<UUID, CorePlayer> known = new HashMap<>();
    final MessageService messages;
    final FriendService friends;
    final PlayerIdentityService identities;
    final FriendMenu menu;
    final FriendMenuListener listener;

    FriendMenuFixture() throws Exception {
        var constructor = MessageService.class.getDeclaredConstructor(java.util.function.Supplier.class);
        constructor.setAccessible(true);
        messages = constructor.newInstance((java.util.function.Supplier<String>) () -> "");
        PlayerRepository playerRepository = new PlayerRepository() {
            @Override
            public Optional<CorePlayer> findByUniqueId(UUID id) {
                return Optional.ofNullable(known.get(id));
            }

            @Override
            public void save(CorePlayer player) {
                known.put(player.getUniqueId(), player);
            }

            @Override
            public boolean exists(UUID id) {
                return known.containsKey(id);
            }

            @Override
            public Set<UUID> findUniqueIdsByName(String name) {
                return known.values().stream()
                        .filter(player -> player.getName().equalsIgnoreCase(name))
                        .map(CorePlayer::getUniqueId)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
            }
        };
        identities = new PlayerIdentityService(new PlayerService(playerRepository, logger()));
        friends = new FriendService(repository, limits::get, FriendRequestPolicy.allowAll(), Clock.systemUTC());
        menu = new FriendMenu(friends, identities, messages,
                id -> {
                    TestPlayer player = players.get(id);
                    return player != null && player.online ? player.player : null;
                }, this::inventory, SpecItem::new, logger());
        listener = new FriendMenuListener(menu, friends,
                new FriendMessages(messages, id -> {
                    TestPlayer player = players.get(id);
                    return player != null && player.online ? player.player : null;
                }), messages, logger());
    }

    TestPlayer player(String name, boolean online) {
        UUID id = UUID.nameUUIDFromBytes(("friend-menu-" + name).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return player(id, name, online, true);
    }

    TestPlayer player(UUID id, String name, boolean online, boolean knownPlayer) {
        if (knownPlayer) {
            known.put(id, new CorePlayer(id, name, Instant.EPOCH, Instant.EPOCH,
                    PlayerSettings.defaults(), CoinWallet.empty(), PlayerSocial.empty()));
        }
        TestPlayer player = new TestPlayer(id, name, online);
        players.put(id, player);
        return player;
    }

    Inventory inventory(FriendMenuHolder holder, int size, Component title) {
        return new TestInventory(holder, size).inventory;
    }

    InventoryClickEvent click(TestPlayer player, Inventory top, int rawSlot, ClickType click) {
        InventoryView view = view(player, top);
        InventoryAction action = switch (click) {
            case SHIFT_LEFT, SHIFT_RIGHT -> InventoryAction.MOVE_TO_OTHER_INVENTORY;
            case NUMBER_KEY -> InventoryAction.HOTBAR_SWAP;
            case DOUBLE_CLICK -> InventoryAction.COLLECT_TO_CURSOR;
            default -> InventoryAction.PICKUP_ALL;
        };
        InventoryClickEvent event = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER,
                rawSlot, click, action);
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

    static FriendMenuHolder holder(Inventory inventory) {
        return (FriendMenuHolder) inventory.getHolder();
    }

    static SpecItem spec(Inventory inventory, int slot) {
        return (SpecItem) inventory.getItem(slot);
    }

    private Inventory inventory(InventoryHolder holder, int size) {
        return new TestInventory(holder, size).inventory;
    }

    final class TestPlayer {
        final UUID id;
        final String name;
        final Player player;
        final Inventory bottom = inventory(null, 36);
        final List<Component> received = new ArrayList<>();
        boolean online;
        Inventory open;
        int closeCalls;

        TestPlayer(UUID id, String name, boolean online) {
            this.id = id;
            this.name = name;
            this.online = online;
            this.player = proxy(Player.class, (method, args) -> switch (method) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> this.online;
                case "openInventory" -> {
                    open = (Inventory) args[0];
                    yield view(this, open);
                }
                case "getOpenInventory" -> view(this, open == null ? bottom : open);
                case "closeInventory" -> {
                    closeCalls++;
                    open = bottom;
                    yield null;
                }
                case "sendMessage" -> {
                    if (args != null) {
                        for (Object argument : args) {
                            if (argument instanceof Component component) received.add(component);
                        }
                    }
                    yield null;
                }
                default -> null;
            });
        }
    }

    static final class SpecItem extends ItemStack {
        final UiItemSpec spec;

        SpecItem(UiItemSpec spec) {
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
            this.inventory = proxy(Inventory.class, (method, args) -> switch (method) {
                case "getHolder" -> holder;
                case "getSize" -> size;
                case "getItem" -> items[(int) args[0]];
                case "setItem" -> {
                    items[(int) args[0]] = (ItemStack) args[1];
                    yield null;
                }
                case "getType" -> InventoryType.CHEST;
                default -> null;
            });
        }
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, ProxyAction action) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getName().equals("hashCode")) return System.identityHashCode(instance);
            if (method.getName().equals("equals")) return instance == args[0];
            if (method.getName().equals("toString")) return type.getSimpleName() + "FixtureProxy";
            return action.invoke(method.getName(), args);
        });
    }

    interface ProxyAction {
        Object invoke(String method, Object[] args);
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("FriendMenuFixture-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    static final class MemoryFriends implements FriendRepository {
        FriendSnapshot snapshot = FriendSnapshot.empty();
        boolean failNext;

        @Override
        public FriendSnapshot initialize() {
            return snapshot;
        }

        @Override
        public void save(FriendSnapshot snapshot) {
            if (failNext) {
                failNext = false;
                throw new FriendRepositoryException("simulated persistence failure");
            }
            this.snapshot = snapshot;
        }
    }
}
