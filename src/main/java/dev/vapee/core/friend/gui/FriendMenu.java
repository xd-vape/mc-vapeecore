package dev.vapee.core.friend.gui;

import dev.vapee.core.ui.MenuLayout;
import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;
import dev.vapee.core.ui.Pagination;
import dev.vapee.core.friend.FriendRequest;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.friend.command.FriendCommand;
import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** A focused, holder-bound view of the existing friends service. */
public final class FriendMenu {

    public enum Action { PREVIOUS, ADD, FRIENDS, INCOMING, CLOSE, OUTGOING, REFRESH, NEXT }
    public static final MenuLayout<Action> DEFAULT_LAYOUT = new MenuLayout<>(54,
            List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44), 22,
            Map.ofEntries(
                    Map.entry(Action.PREVIOUS, List.of(45)),
                    Map.entry(Action.ADD, List.of(46)),
                    Map.entry(Action.FRIENDS, List.of(47)),
                    Map.entry(Action.INCOMING, List.of(48)),
                    Map.entry(Action.CLOSE, List.of(49)),
                    Map.entry(Action.OUTGOING, List.of(50)),
                    Map.entry(Action.REFRESH, List.of(52)),
                    Map.entry(Action.NEXT, List.of(53))));
    // Compatibility aliases describe the default only; runtime code reads the instance layout.
    public static final int INVENTORY_SIZE = DEFAULT_LAYOUT.size();
    public static final int CONTENT_SIZE = DEFAULT_LAYOUT.pageSize();
    public static final int EMPTY_SLOT = DEFAULT_LAYOUT.emptySlot();
    public static final int PREVIOUS_SLOT = DEFAULT_LAYOUT.slot(Action.PREVIOUS);
    public static final int ADD_SLOT = DEFAULT_LAYOUT.slot(Action.ADD);
    public static final int FRIENDS_SLOT = DEFAULT_LAYOUT.slot(Action.FRIENDS);
    public static final int INCOMING_SLOT = DEFAULT_LAYOUT.slot(Action.INCOMING);
    public static final int CLOSE_SLOT = DEFAULT_LAYOUT.slot(Action.CLOSE);
    public static final int OUTGOING_SLOT = DEFAULT_LAYOUT.slot(Action.OUTGOING);
    public static final int REFRESH_SLOT = DEFAULT_LAYOUT.slot(Action.REFRESH);
    public static final int NEXT_SLOT = DEFAULT_LAYOUT.slot(Action.NEXT);

    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.id().toString());

    private final MenuLayout<Action> layout;
    public MenuLayout<Action> layout() { return layout; }

    private final FriendService friends;
    private final PlayerIdentityService identities;
    private final MessageService messages;
    private final Function<UUID, Player> onlinePlayer;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private final Logger logger;
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public FriendMenu(JavaPlugin plugin, FriendService friends, PlayerIdentityService identities,
                      MessageService messages) {
        this(friends, identities, messages,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                UiItems::render, plugin.getLogger());
    }

    FriendMenu(FriendService friends, PlayerIdentityService identities, MessageService messages,
               Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
               ItemRenderer itemRenderer, Logger logger) {
        this(friends, identities, messages, onlinePlayer, inventoryFactory, itemRenderer, logger, DEFAULT_LAYOUT);
    }

    FriendMenu(FriendService friends, PlayerIdentityService identities, MessageService messages,
               Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
               ItemRenderer itemRenderer, Logger logger, MenuLayout<Action> menuLayout) {
        this.layout = Objects.requireNonNull(menuLayout, "layout").requireActions(DEFAULT_LAYOUT.actions());
        this.layout.requirePaged();
        this.friends = Objects.requireNonNull(friends, "friends");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean open(Player player) {
        return open(player, FriendMenuView.FRIENDS, 0);
    }

    public boolean open(Player player, FriendMenuView view, int requestedPage) {
        Player viewer = Objects.requireNonNull(player, "player");
        FriendMenuView selectedView = Objects.requireNonNull(view, "view");
        if (!viewer.hasPermission(FriendCommand.PERMISSION)) {
            messages.send(viewer, "<red>You do not have permission to use friends.</red>");
            return false;
        }
        UUID owner = viewer.getUniqueId();
        List<Entry> entries = entries(owner, selectedView);
        Pagination pagination = Pagination.of(entries.size(), requestedPage, layout.pageSize());
        int page = pagination.page();
        int first = pagination.fromIndex();
        int end = pagination.toIndex();
        Map<Integer, UUID> targets = new LinkedHashMap<>();
        for (int index = first; index < end; index++) {
            targets.put(layout.contentSlot(index - first), entries.get(index).id());
        }

        FriendMenuHolder holder = new FriendMenuHolder(owner, selectedView, page, targets, layout);
        Inventory inventory = inventoryFactory.create(holder, layout.size(), title(selectedView));
        holder.bindInventory(inventory);
        for (int index = first; index < end; index++) {
            inventory.setItem(layout.contentSlot(index - first), itemRenderer.render(entryItem(selectedView, entries.get(index))));
        }
        if (entries.isEmpty()) {
            String empty = switch (selectedView) {
                case FRIENDS -> "No friends yet";
                case INCOMING -> "No incoming requests";
                case OUTGOING -> "No outgoing requests";
            };
            inventory.setItem(layout.emptySlot(), item(Material.PAPER, empty, NamedTextColor.GRAY,
                    selectedView == FriendMenuView.FRIENDS
                            ? List.of("Use /friend add <player> to get started.") : List.of()));
        }
        if (pagination.hasPrevious()) {
            inventory.setItem(layout.slot(Action.PREVIOUS), item(Material.ARROW, "Previous Page", NamedTextColor.YELLOW,
                    List.of()));
        }
        inventory.setItem(layout.slot(Action.ADD), item(Material.LIME_DYE, "Add Friend", NamedTextColor.GREEN,
                List.of("Click to enter /friend add <player>.")));
        inventory.setItem(layout.slot(Action.FRIENDS), tabItem("Friends", FriendMenuView.FRIENDS == selectedView,
                friends.countFriends(owner) + " / " + friends.getLimits().maxFriends()));
        inventory.setItem(layout.slot(Action.INCOMING), tabItem("Incoming Requests", FriendMenuView.INCOMING == selectedView,
                Integer.toString(friends.getIncomingRequests(owner).size())));
        inventory.setItem(layout.slot(Action.CLOSE), item(Material.BARRIER, "Close", NamedTextColor.RED, List.of()));
        inventory.setItem(layout.slot(Action.OUTGOING), tabItem("Outgoing Requests", FriendMenuView.OUTGOING == selectedView,
                Integer.toString(friends.getOutgoingRequests(owner).size())));
        inventory.setItem(layout.slot(Action.REFRESH), item(Material.CLOCK, "Refresh", NamedTextColor.AQUA,
                List.of("Update friends, requests and online status.")));
        if (pagination.hasNext()) {
            inventory.setItem(layout.slot(Action.NEXT), item(Material.ARROW, "Next Page", NamedTextColor.YELLOW,
                    List.of()));
        }
        var opened = viewer.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory) {
            activeInventories.put(owner, inventory);
            return true;
        }
        return false;
    }

    public boolean isActive(Player viewer, Inventory inventory, FriendMenuHolder holder) {
        return viewer != null && inventory != null && holder != null
                && holder.getOwnerUniqueId().equals(viewer.getUniqueId())
                && inventory.getHolder() == holder && holder.isBoundTo(inventory)
                && activeInventories.get(viewer.getUniqueId()) == inventory;
    }

    void forgetIfActive(UUID owner, Inventory inventory) {
        activeInventories.remove(owner, inventory);
    }

    public void closeOpenInventories() {
        try {
            for (Map.Entry<UUID, Inventory> entry : List.copyOf(activeInventories.entrySet())) {
                try {
                    Player player = onlinePlayer.apply(entry.getKey());
                    if (player != null && player.isOnline()
                            && player.getOpenInventory().getTopInventory() == entry.getValue()) {
                        player.closeInventory();
                    }
                } catch (RuntimeException exception) {
                    logger.log(Level.WARNING, "Could not close FriendMenu for " + entry.getKey() + ".", exception);
                }
            }
        } finally {
            activeInventories.clear();
        }
    }

    int activeCount() { return activeInventories.size(); }

    public boolean hasNext(UUID owner, FriendMenuView view, int page) {
        return page >= 0 && Pagination.of(entries(owner, view).size(), page, layout.pageSize()).hasNext();
    }

    public String displayName(UUID id) {
        return identities.findById(Objects.requireNonNull(id, "id"))
                .map(PlayerIdentity::name).orElse(id.toString());
    }

    public void promptAdd(Player player) {
        player.closeInventory();
        messages.send(player, Component.text("Click here to enter /friend add ", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/friend add "))
                .hoverEvent(HoverEvent.showText(Component.text("Click to enter a player name."))));
    }

    private List<Entry> entries(UUID owner, FriendMenuView view) {
        List<UUID> ids = switch (view) {
            case FRIENDS -> friends.getFriends(owner);
            case INCOMING -> friends.getIncomingRequests(owner).stream().map(FriendRequest::sender).toList();
            case OUTGOING -> friends.getOutgoingRequests(owner).stream().map(FriendRequest::recipient).toList();
        };
        List<Entry> result = new ArrayList<>(ids.size());
        for (UUID id : ids) {
            Player online = onlinePlayer.apply(id);
            result.add(new Entry(id, displayName(id), online != null && online.isOnline()));
        }
        result.sort(ENTRY_ORDER);
        return result;
    }

    private UiItemSpec entryItem(FriendMenuView view, Entry entry) {
        List<String> lore = switch (view) {
            case FRIENDS -> List.of(entry.online() ? "Online" : "Offline",
                    "Shift + Right-click to remove");
            case INCOMING -> List.of(entry.online() ? "Online" : "Offline",
                    "Left-click to accept", "Right-click to decline");
            case OUTGOING -> List.of(entry.online() ? "Online" : "Offline", "Click to cancel request.");
        };
        return UiItems.literal(Material.PLAYER_HEAD, entry.name(), NamedTextColor.AQUA, lore);
    }

    private ItemStack tabItem(String name, boolean active, String count) {
        return itemRenderer.render(UiItems.literal(active ? Material.LIME_DYE : Material.GRAY_DYE, name,
                active ? NamedTextColor.GREEN : NamedTextColor.WHITE,
                List.of(count, active ? "Selected" : "Click to open")));
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        return itemRenderer.render(UiItems.literal(material, name, color, lore));
    }

    private static Component title(FriendMenuView view) {
        return UiItems.text(switch (view) {
            case FRIENDS -> "Friends";
            case INCOMING -> "Friends • Incoming";
            case OUTGOING -> "Friends • Outgoing";
        }, NamedTextColor.DARK_GRAY);
    }

    private record Entry(UUID id, String name, boolean online) {}

    @FunctionalInterface
    interface InventoryFactory {
        Inventory create(FriendMenuHolder holder, int size, Component title);
    }

    @FunctionalInterface
    interface ItemRenderer {
        ItemStack render(UiItemSpec spec);
    }
}
