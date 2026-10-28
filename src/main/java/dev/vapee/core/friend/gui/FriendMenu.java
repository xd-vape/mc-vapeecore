package dev.vapee.core.friend.gui;

import static dev.vapee.core.i18n.Translations.tr;

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
    private UUID presenceTarget;
    private boolean presenceOnline;

    private final dev.vapee.core.friend.FriendChangeListener committedChanges = affected -> affected.forEach(this::refreshActive);
    private boolean observingChanges;

    private Inventory rerenderInventory;


    public enum Action { PREVIOUS, ADD, FRIENDS, INCOMING, CLOSE, OUTGOING, PAGE_INFO, REFRESH, NEXT }
    public static final MenuLayout<Action> DEFAULT_LAYOUT = MenuLayout.builder(54, Action.class)
            .content(List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44))
            .emptyState(22)
            .button(Action.PREVIOUS, 45)
            .button(Action.ADD, 46)
            .button(Action.FRIENDS, 47)
            .button(Action.INCOMING, 48)
            .button(Action.CLOSE, 49)
            .button(Action.OUTGOING, 50)

            .button(Action.PAGE_INFO, 52)
            .button(Action.NEXT, 53)
            .build();

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
    private Player renderingFor;
    private final java.util.function.Consumer<Player> localeChanges = viewer -> refreshActive(viewer.getUniqueId());
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
        this.layout = Objects.requireNonNull(menuLayout, "layout").validateActions(java.util.Set.of());
        this.layout.requirePaged();
        this.friends = Objects.requireNonNull(friends, "friends");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.itemRenderer = spec -> itemRenderer.render(UiItems.localize(spec, this.messages, renderingFor));
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean open(Player player) {
        return open(player, FriendMenuView.FRIENDS, 0);
    }

    public boolean open(Player player, FriendMenuView view, int requestedPage) {
        Player previousViewer = renderingFor; renderingFor = player;
        try {
        Player viewer = Objects.requireNonNull(player, "player");
        FriendMenuView selectedView = Objects.requireNonNull(view, "view");
        if (!viewer.hasPermission(FriendCommand.PERMISSION)) {
            dev.vapee.core.message.SocialChannel.FRIENDS.error(messages, viewer, tr("friends.denied"));
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
        Inventory inventory;
        if (rerenderInventory != null && rerenderInventory.getHolder() instanceof FriendMenuHolder existing) {
            inventory = rerenderInventory;
            existing.beginRender(holder); holder = existing;
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, null);
        } else {
            inventory = inventoryFactory.create(holder, layout.size(), messages.localize(viewer, title(selectedView)));
            holder.bindInventory(inventory);
        }
        for (int index = first; index < end; index++) {
            inventory.setItem(layout.contentSlot(index - first), itemRenderer.render(entryItem(selectedView, entries.get(index))));
        }
        if (entries.isEmpty()) {
            Component empty = switch (selectedView) {
                case FRIENDS -> tr("friends.empty");
                case INCOMING -> tr("friends.incoming-empty");
                case OUTGOING -> tr("friends.outgoing-empty");
            };
            placeDisplay(inventory, layout.emptySlot(), item(Material.PAPER, empty, NamedTextColor.GRAY,
                    selectedView == FriendMenuView.FRIENDS
                            ? List.of(tr("friends.empty-hint")) : List.of()));
        }
        if (pagination.hasMultiplePages()) place(inventory, Action.PAGE_INFO, item(Material.PAPER, tr("common.page", java.util.Map.of("current", Component.text(pagination.page()+1), "total", Component.text(pagination.pageCount()))), NamedTextColor.AQUA, List.of()));
        if (pagination.hasPrevious()) {
            place(inventory, Action.PREVIOUS, item(Material.ARROW, tr("common.previous-page"), NamedTextColor.YELLOW,
                    List.of()));
        }
        place(inventory, Action.ADD, item(Material.LIME_DYE, tr("friends.add-name"), NamedTextColor.GREEN,
                List.of(tr("friends.add-hint"))));
        place(inventory, Action.FRIENDS, tabItem(tr("friends.title"), FriendMenuView.FRIENDS == selectedView,
                friends.countFriends(owner) + " / " + friends.getLimits().maxFriends()));
        place(inventory, Action.INCOMING, tabItem(tr("friends.incoming-tab"), FriendMenuView.INCOMING == selectedView,
                Integer.toString(friends.getIncomingRequests(owner).size())));
        place(inventory, Action.CLOSE, item(Material.BARRIER, tr("common.close"), NamedTextColor.RED, List.of()));
        place(inventory, Action.OUTGOING, tabItem(tr("friends.outgoing-tab"), FriendMenuView.OUTGOING == selectedView,
                Integer.toString(friends.getOutgoingRequests(owner).size())));
        place(inventory, Action.REFRESH, item(Material.CLOCK, tr("common.refresh"), NamedTextColor.AQUA,
                List.of(tr("friends.refresh-hint"))));
        if (pagination.hasNext()) {
            place(inventory, Action.NEXT, item(Material.ARROW, tr("common.next-page"), NamedTextColor.YELLOW,
                    List.of()));
        }
        holder.finishRender();
        var opened = inventory == rerenderInventory ? viewer.getOpenInventory() : viewer.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory) {
            activeInventories.put(owner, inventory);
            return true;
        }
        return false;

        } finally { renderingFor = previousViewer; }
    }

    public boolean isActive(Player viewer, Inventory inventory, FriendMenuHolder holder) {
        return viewer != null && inventory != null && holder != null
                && holder.getOwnerUniqueId().equals(viewer.getUniqueId())
                && inventory.getHolder() == holder && holder.isBoundTo(inventory)
                && activeInventories.get(viewer.getUniqueId()) == inventory
                && viewer.getOpenInventory().getTopInventory() == inventory;
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
        messages.send(player, tr("friends.add-prompt").color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/friend add "))
                .hoverEvent(HoverEvent.showText(tr("friends.add-prompt-hover"))));
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
            result.add(new Entry(id, displayName(id), (id.equals(presenceTarget) ? presenceOnline : online != null && online.isOnline())));
        }
        result.sort(ENTRY_ORDER);
        return result;
    }

    private UiItemSpec entryItem(FriendMenuView view, Entry entry) {
        List<Component> lore = switch (view) {
            case FRIENDS -> List.of(entry.online() ? tr("common.online") : tr("common.offline"),
                    tr("friends.remove-hint"));
            case INCOMING -> List.of(entry.online() ? tr("common.online") : tr("common.offline"),
                    tr("friends.accept-hint"), tr("friends.deny-hint"));
            case OUTGOING -> List.of(entry.online() ? tr("common.online") : tr("common.offline"), tr("friends.cancel-hint"));
        };
        return UiItems.literal(Material.PLAYER_HEAD, Component.text(entry.name()), NamedTextColor.AQUA, lore);
    }

    private ItemStack tabItem(Component name, boolean active, String count) {
        return itemRenderer.render(UiItems.literal(active ? Material.LIME_DYE : Material.GRAY_DYE, name,
                active ? NamedTextColor.GREEN : NamedTextColor.WHITE,
                List.of(Component.text(count), active ? tr("common.selected") : tr("common.click-open-short"))));
    }

    private ItemStack item(Material material, Component name, NamedTextColor color, List<Component> lore) {
        return itemRenderer.render(UiItems.literal(material, name, color, lore));
    }

    private static Component title(FriendMenuView view) {
        return UiItems.text(switch (view) {
            case FRIENDS -> tr("friends.title");
            case INCOMING -> tr("friends.incoming-title");
            case OUTGOING -> tr("friends.outgoing-title");
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
    private void place(Inventory inventory, Action action, ItemStack item) {
        place(inventory, action, 0, item);
    }
    private void place(Inventory inventory, Action action, int position, ItemStack item) {
        layout.actionSlot(action, position).ifPresent(slot -> { inventory.setItem(slot, item); ((FriendMenuHolder) inventory.getHolder()).recordAction(slot, action); });
    }

    private void placeDisplay(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0) inventory.setItem(slot, item);
    }

    public void refreshActive(UUID owner) {
        Inventory inventory = activeInventories.get(owner);
        if (inventory == null || rerenderInventory != null) return;
        Player player = onlinePlayer.apply(owner);
        if (player == null || !player.isOnline() || player.getOpenInventory().getTopInventory() != inventory
                || !(inventory.getHolder() instanceof FriendMenuHolder holder) || !isActive(player, inventory, holder)) {
            activeInventories.remove(owner, inventory); return;
        }
        if (!player.hasPermission(dev.vapee.core.friend.command.FriendCommand.PERMISSION)) { activeInventories.remove(owner, inventory); player.closeInventory(); return; }
        try { rerenderInventory = inventory; open(player, holder.getView(), holder.getPage()); }
        catch (RuntimeException exception) {
            activeInventories.remove(owner, inventory);
            if (player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
            logger.log(Level.WARNING, "Could not update FriendMenu for " + owner, exception);
        } finally { rerenderInventory = null; }
    }

    public void startUpdates() {
        if (observingChanges) {
            return;
        }
        try {
            messages.localization().addLocaleListener(localeChanges);
            friends.addChangeListener(committedChanges);
            observingChanges = true;
        } catch (RuntimeException exception) {
            try {
                shutdown();
            } catch (RuntimeException cleanupFailure) {
                if (cleanupFailure != exception) {
                    exception.addSuppressed(cleanupFailure);
                }
            }
            throw exception;
        }
    }

    public void stopUpdates() {
        RuntimeException failure = null;
        try {
            messages.localization().removeLocaleListener(localeChanges);
        } catch (RuntimeException exception) {
            failure = new IllegalStateException("Could not remove FriendMenu locale observer", exception);
        }
        // An add can take effect and throw before observingChanges is set.
        observingChanges = false;
        try {
            friends.removeChangeListener(committedChanges);
        } catch (RuntimeException exception) {
            RuntimeException changeFailure = new IllegalStateException(
                    "Could not remove FriendMenu change observer", exception);
            if (failure == null) {
                failure = changeFailure;
            } else {
                failure.addSuppressed(changeFailure);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    public void shutdown() {
        try {
            stopUpdates();
        } finally {
            try {
                closeOpenInventories();
            } finally {
                rerenderInventory = null;
                renderingFor = null;
                presenceTarget = null;
            }
        }
    }

    public void targetPresenceChanged(UUID target, boolean online) {
        presenceTarget = target; presenceOnline = online;
        try { for (UUID owner : java.util.List.copyOf(activeInventories.keySet())) {
            if (!owner.equals(target) && (friends.getRelation(owner, target) != dev.vapee.core.friend.FriendRelation.NONE)) refreshActive(owner);
        } } finally { presenceTarget = null; }
    }

}
