package dev.vapee.core.settings.visibility;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.ui.MenuLayout;
import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;
import dev.vapee.core.ui.CachedPlayerHeads;
import dev.vapee.core.ui.Pagination;
import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.settings.command.SettingsCommand;
import dev.vapee.core.player.settings.PlayerSettingsService;
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
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VisiblePlayersMenu {
    private UUID presenceTarget;
    private boolean presenceOnline;

    private final dev.vapee.core.player.settings.PlayerSettingsChangeListener committedChanges = this::refreshActive;
    private boolean observingChanges;

    private Inventory rerenderInventory;

    public enum Action { PREVIOUS, ADD, BACK, CLOSE, PAGE_INFO, REFRESH, NEXT }
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
            .button(Action.BACK, 48)
            .button(Action.CLOSE, 49)

            .button(Action.PAGE_INFO, 52)
            .button(Action.NEXT, 53)
            .build();

    private static final Component TITLE = UiItems.text(tr("visibility.manage-title"), NamedTextColor.DARK_GRAY);
    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparing(Entry::known).reversed()
            .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.id().toString());

    private final MenuLayout<Action> layout;
    public MenuLayout<Action> layout() { return layout; }

    private final PlayerSettingsService settings;
    private final PlayerIdentityService identities;
    private final MessageService messages;
    private final Function<UUID, Player> onlinePlayer;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private final BiFunction<UUID, ItemStack, ItemStack> playerHeads;
    private Player renderingFor;
    private final java.util.function.Consumer<Player> localeChanges = viewer -> refreshActive(viewer.getUniqueId());
    private final Logger logger;
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public VisiblePlayersMenu(JavaPlugin plugin, PlayerSettingsService settings,
                              PlayerIdentityService identities, MessageService messages) {
        this(settings, identities, messages,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                UiItems::render, plugin.getLogger(), DEFAULT_LAYOUT,
                new CachedPlayerHeads(plugin.getServer())::apply);
    }

    VisiblePlayersMenu(PlayerSettingsService settings, PlayerIdentityService identities,
                       MessageService messages, Function<UUID, Player> onlinePlayer,
                       InventoryFactory inventoryFactory, ItemRenderer itemRenderer, Logger logger) {
        this(settings, identities, messages, onlinePlayer, inventoryFactory, itemRenderer, logger, DEFAULT_LAYOUT);
    }

    VisiblePlayersMenu(PlayerSettingsService settings, PlayerIdentityService identities,
                       MessageService messages, Function<UUID, Player> onlinePlayer,
                       InventoryFactory inventoryFactory, ItemRenderer itemRenderer, Logger logger, MenuLayout<Action> menuLayout) {
        this(settings, identities, messages, onlinePlayer, inventoryFactory, itemRenderer, logger,
                menuLayout, (id, item) -> item);
    }

    VisiblePlayersMenu(PlayerSettingsService settings, PlayerIdentityService identities,
                       MessageService messages, Function<UUID, Player> onlinePlayer,
                       InventoryFactory inventoryFactory, ItemRenderer itemRenderer, Logger logger,
                       MenuLayout<Action> menuLayout, BiFunction<UUID, ItemStack, ItemStack> playerHeads) {
        this.playerHeads = Objects.requireNonNull(playerHeads, "playerHeads");
        this.layout = Objects.requireNonNull(menuLayout, "layout").validateActions(java.util.Set.of());
        this.layout.requirePaged();
        this.settings = Objects.requireNonNull(settings, "settings");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.itemRenderer = spec -> itemRenderer.render(UiItems.localize(spec, this.messages, renderingFor));
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int requestedPage) {
        Player previousViewer = renderingFor; renderingFor = player;
        try {
        Player viewer = Objects.requireNonNull(player, "player");
        if (!viewer.hasPermission(SettingsCommand.PERMISSION)) {
            messages.send(viewer, tr("settings.denied").color(NamedTextColor.RED));
            return;
        }
        List<Entry> entries = entries(viewer.getUniqueId());
        if (entries == null) {
            messages.send(viewer, tr("common.profile-unavailable").color(NamedTextColor.RED));
            return;
        }
        Pagination pagination = Pagination.of(entries.size(), requestedPage, layout.pageSize());
        int page = pagination.page();
        int first = pagination.fromIndex();
        int end = pagination.toIndex();
        Map<Integer, UUID> targets = new LinkedHashMap<>();
        for (int index = first; index < end; index++) {
            targets.put(layout.contentSlot(index - first), entries.get(index).id());
        }

        VisiblePlayersHolder holder = new VisiblePlayersHolder(viewer.getUniqueId(), page, targets, layout);
        Inventory inventory;
        if (rerenderInventory != null && rerenderInventory.getHolder() instanceof VisiblePlayersHolder existing) {
            inventory = rerenderInventory;
            existing.beginRender(holder); holder = existing;
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, null);
        } else {
            inventory = inventoryFactory.create(holder, layout.size(), messages.localize(viewer, TITLE));
            holder.bindInventory(inventory);
        }
        for (int index = first; index < end; index++) {
            Entry entry = entries.get(index);
            inventory.setItem(layout.contentSlot(index - first),
                    playerHeads.apply(entry.id(), itemRenderer.render(entryItem(entry))));
        }
        if (entries.isEmpty()) {
            placeDisplay(inventory, layout.emptySlot(), item(Material.PAPER, tr("visibility.empty"), NamedTextColor.GRAY,
                    List.of(tr("visibility.empty-hint"))));
        }
        if (pagination.hasMultiplePages()) place(inventory, Action.PAGE_INFO, item(Material.PAPER, tr("common.page", java.util.Map.of("current", Component.text(pagination.page()+1), "total", Component.text(pagination.pageCount()))), NamedTextColor.AQUA, List.of()));
        if (pagination.hasPrevious()) {
            place(inventory, Action.PREVIOUS, item(Material.ARROW, tr("common.previous-page"), NamedTextColor.YELLOW,
                    List.of()));
        }
        place(inventory, Action.ADD, item(Material.LIME_DYE, tr("visibility.add-name"), NamedTextColor.GREEN,
                List.of(tr("visibility.add-hint"))));
        place(inventory, Action.BACK, item(Material.ARROW, tr("common.back"), NamedTextColor.YELLOW,
                List.of(tr("visibility.back-parent"))));
        place(inventory, Action.CLOSE, item(Material.BARRIER, tr("common.close"), NamedTextColor.RED, List.of()));
        place(inventory, Action.REFRESH, item(Material.CLOCK, tr("common.refresh"), NamedTextColor.AQUA,
                List.of(tr("visibility.list-refresh-hint"))));
        if (pagination.hasNext()) {
            place(inventory, Action.NEXT, item(Material.ARROW, tr("common.next-page"), NamedTextColor.YELLOW, List.of()));
        }
        holder.finishRender();
        var opened = inventory == rerenderInventory ? viewer.getOpenInventory() : viewer.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory) {
            activeInventories.put(viewer.getUniqueId(), inventory);
        }

        } finally { renderingFor = previousViewer; }
    }

    public boolean isActive(Player viewer, Inventory inventory, VisiblePlayersHolder holder) {
        return viewer != null && inventory != null && holder != null
                && holder.getOwnerUniqueId().equals(viewer.getUniqueId())
                && inventory.getHolder() == holder && holder.isBoundTo(inventory)
                && activeInventories.get(viewer.getUniqueId()) == inventory
                && viewer.getOpenInventory().getTopInventory() == inventory;
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
                    logger.log(Level.WARNING, "Could not close VisiblePlayersMenu for " + entry.getKey() + ".", exception);
                }
            }
        } finally {
            activeInventories.clear();
        }
    }

    int activeCount() { return activeInventories.size(); }

    public boolean hasNext(UUID owner, int page) {
        List<Entry> current = entries(owner);
        return current != null && page >= 0 && Pagination.of(current.size(), page, layout.pageSize()).hasNext();
    }

    public String displayName(UUID id) {
        return identities.findById(Objects.requireNonNull(id, "id"))
                .map(PlayerIdentity::name).orElse(id.toString());
    }

    public void promptAdd(Player player) {
        player.closeInventory();
        messages.send(player, tr("visibility.add-prompt").color(NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/settings visibility add "))
                .hoverEvent(HoverEvent.showText(tr("visibility.add-prompt-hover").color(NamedTextColor.GRAY))));
    }

    void forgetIfActive(UUID owner, Inventory inventory) {
        activeInventories.remove(owner, inventory);
    }

    private List<Entry> entries(UUID owner) {
        var added = settings.getLobbyAddedVisiblePlayers(owner);
        if (added.isEmpty()) return null;
        List<Entry> result = new ArrayList<>(added.get().size());
        for (UUID id : added.get()) {
            var identity = identities.findById(id);
            Player online = onlinePlayer.apply(id);
            result.add(new Entry(id, identity.map(PlayerIdentity::name).orElse(id.toString()),
                    identity.isPresent(), (id.equals(presenceTarget) ? presenceOnline : online != null && online.isOnline())));
        }
        result.sort(ENTRY_ORDER);
        return List.copyOf(result);
    }

    private UiItemSpec entryItem(Entry entry) {
        return new UiItemSpec(Material.PLAYER_HEAD, UiItems.text(entry.name(), NamedTextColor.AQUA),
                List.of(UiItems.text(entry.online() ? tr("common.online") : tr("common.offline"),
                                entry.online() ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                        UiItems.text(tr("common.uuid", java.util.Map.of("uuid", Component.text(entry.id().toString()))), NamedTextColor.GRAY),
                        UiItems.text(tr("common.remove-hint"), NamedTextColor.GRAY)));
    }

    private ItemStack item(Material material, Component name, NamedTextColor color, List<Component> lore) {
        return itemRenderer.render(UiItems.literal(material, name, color, lore));
    }

    private record Entry(UUID id, String name, boolean known, boolean online) {}

    @FunctionalInterface
    interface InventoryFactory {
        Inventory create(VisiblePlayersHolder holder, int size, Component title);
    }

    @FunctionalInterface
    interface ItemRenderer {
        ItemStack render(UiItemSpec spec);
    }
    private void place(Inventory inventory, Action action, ItemStack item) {
        place(inventory, action, 0, item);
    }
    private void place(Inventory inventory, Action action, int position, ItemStack item) {
        layout.actionSlot(action, position).ifPresent(slot -> { inventory.setItem(slot, item); ((VisiblePlayersHolder) inventory.getHolder()).recordAction(slot, action); });
    }

    private void placeDisplay(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0) inventory.setItem(slot, item);
    }

    public void refreshActive(UUID owner) {
        Inventory inventory = activeInventories.get(owner);
        if (inventory == null || rerenderInventory != null) return;
        Player player = onlinePlayer.apply(owner);
        if (player == null || !player.isOnline() || player.getOpenInventory().getTopInventory() != inventory
                || !(inventory.getHolder() instanceof VisiblePlayersHolder holder) || !isActive(player, inventory, holder)) {
            activeInventories.remove(owner, inventory); return;
        }
        if (!player.hasPermission(dev.vapee.core.settings.command.SettingsCommand.PERMISSION) || settings.getSettings(owner).isEmpty()) { activeInventories.remove(owner, inventory); player.closeInventory(); return; }
        try { rerenderInventory = inventory; open(player, holder.getPage()); }
        catch (RuntimeException exception) {
            activeInventories.remove(owner, inventory);
            if (player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
            logger.log(Level.WARNING, "Could not update VisiblePlayersMenu for " + owner, exception);
        } finally { rerenderInventory = null; }
    }

    public void startUpdates() {
        messages.localization().addLocaleListener(localeChanges);
        if (!observingChanges) { settings.addChangeListener(committedChanges); observingChanges = true; }
    }
    public void stopUpdates() {
        RuntimeException failure = null;
        try {
            messages.localization().removeLocaleListener(localeChanges);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        // An add can take effect and throw before observingChanges is set.
        observingChanges = false;
        try {
            settings.removeChangeListener(committedChanges);
        } catch (RuntimeException exception) {
            if (failure == null) throw exception;
            if (failure != exception) failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    public void shutdown() {
        try {
            stopUpdates();
        } finally {
            closeOpenInventories();
        }
    }

    public void targetPresenceChanged(UUID target, boolean online) {
        presenceTarget = target; presenceOnline = online;
        try { for (UUID owner : java.util.List.copyOf(activeInventories.keySet())) {
            if (!owner.equals(target) && (settings.getLobbyAddedVisiblePlayers(owner).map(ids -> ids.contains(target)).orElse(false))) refreshActive(owner);
        } } finally { presenceTarget = null; }
    }

}
