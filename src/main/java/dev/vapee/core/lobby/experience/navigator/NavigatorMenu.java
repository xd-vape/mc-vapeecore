package dev.vapee.core.lobby.experience.navigator;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.ui.MenuLayout;
import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;
import dev.vapee.core.ui.Pagination;
import dev.vapee.core.lobby.warp.WarpPoint;
import dev.vapee.core.lobby.warp.WarpService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NavigatorMenu {
    private final Map<UUID, NavigatorConfig.State> previews = new HashMap<>();
    private java.util.function.Predicate<Player> previewAccess = player -> false;
    public void previewAccess(java.util.function.Predicate<Player> access) { previewAccess = Objects.requireNonNull(access); }
    private NavigatorConfig.State current(Player player) {
        var preview = previews.get(player.getUniqueId());
        return preview != null && previewAccess.test(player) ? preview : configuration.get();
    }
    public boolean preview(Player player, NavigatorConfig.State state) {
        if (!previewAccess.test(player)) return false;
        previews.put(player.getUniqueId(), state);
        return openConfigured(player, state.root(), List.of(), 0, state);
    }
    public void endPreview(Player player) { previews.remove(player.getUniqueId()); close(player); }
    private final Supplier<NavigatorConfig.State> configuration;
    private final Supplier<? extends java.util.Collection<? extends Player>> onlinePlayers;
    private final dev.vapee.core.lobby.warp.WarpChangeListener committedChanges = () -> java.util.List.copyOf(this.activeInventories.keySet()).forEach(this::refreshActive);
    private boolean observingChanges;

    private Inventory rerenderInventory;


    public enum Action { PREVIOUS, PAGE_INFO, CLOSE, NEXT }
    public static final MenuLayout<Action> DEFAULT_LAYOUT = MenuLayout.builder(27, Action.class)
            .content(List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    19, 20, 21, 22, 23, 24, 25))
            .emptyState(13)
            .button(Action.PREVIOUS, 18)
            .button(Action.NEXT, 26)
            .build();

    private static final Component TITLE = tr("navigator.title").color(NamedTextColor.DARK_GRAY);

    private final MenuLayout<Action> layout;
    public MenuLayout<Action> layout() { return layout; }

    private final WarpService warpService;
    private final dev.vapee.core.message.MessageService messageService;
    private final NavigatorAccessPolicy accessPolicy;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private Player renderingFor;
    private final java.util.function.Consumer<Player> localeChanges = viewer -> refreshActive(viewer.getUniqueId());
    private final Function<UUID, Player> onlinePlayer;
    private final Logger logger;
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();
    private final java.util.Set<UUID> backHints = new java.util.HashSet<>();

    public NavigatorMenu(JavaPlugin plugin, WarpService warpService, NavigatorAccessPolicy accessPolicy) {
        this(plugin, warpService, accessPolicy, new dev.vapee.core.message.MessageService(() -> "",
                dev.vapee.core.i18n.LocalizationService.bundled(plugin.getLogger())));
    }
    public NavigatorMenu(JavaPlugin plugin, WarpService warpService, NavigatorAccessPolicy accessPolicy,
                         dev.vapee.core.message.MessageService messages) {
        this(plugin, warpService, accessPolicy, messages, NavigatorConfig::legacy);
    }

    public NavigatorMenu(JavaPlugin plugin, WarpService warpService, NavigatorAccessPolicy accessPolicy,
                         dev.vapee.core.message.MessageService messages, Supplier<NavigatorConfig.State> configuration) {
        this(warpService, accessPolicy, plugin.getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                NavigatorMenu::renderIcon, plugin.getLogger(), DEFAULT_LAYOUT, messages, configuration,
                plugin.getServer()::getOnlinePlayers);
    }

    NavigatorMenu(WarpService warpService, NavigatorAccessPolicy accessPolicy,
                  Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                  ItemRenderer itemRenderer, Logger logger) {
        this(warpService, accessPolicy, onlinePlayer, inventoryFactory, itemRenderer, logger, DEFAULT_LAYOUT);
    }

    NavigatorMenu(WarpService warpService, NavigatorAccessPolicy accessPolicy,
                  Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                  ItemRenderer itemRenderer, Logger logger, MenuLayout<Action> menuLayout) {
        this(warpService, accessPolicy, onlinePlayer, inventoryFactory, itemRenderer, logger, menuLayout,
                new dev.vapee.core.message.MessageService(() -> "", dev.vapee.core.i18n.LocalizationService.bundled(logger)));
    }
    NavigatorMenu(WarpService warpService, NavigatorAccessPolicy accessPolicy,
                  Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                  ItemRenderer itemRenderer, Logger logger, MenuLayout<Action> menuLayout,
                  dev.vapee.core.message.MessageService messages) {
        this(warpService, accessPolicy, onlinePlayer, inventoryFactory, itemRenderer, logger, menuLayout,
                messages, NavigatorConfig::legacy, List::of);
    }

    NavigatorMenu(WarpService warpService, NavigatorAccessPolicy accessPolicy,
                  Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                  ItemRenderer itemRenderer, Logger logger, MenuLayout<Action> menuLayout,
                  dev.vapee.core.message.MessageService messages, Supplier<NavigatorConfig.State> configuration,
                  Supplier<? extends java.util.Collection<? extends Player>> onlinePlayers) {
        this.configuration = Objects.requireNonNull(configuration);
        this.onlinePlayers = Objects.requireNonNull(onlinePlayers);
        this.messageService = Objects.requireNonNull(messages);
        this.layout = Objects.requireNonNull(menuLayout, "layout").validateActions(java.util.Set.of());
        this.layout.requirePaged();
        this.warpService = Objects.requireNonNull(warpService, "warpService");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.itemRenderer = spec -> itemRenderer.render(UiItems.localize(spec, this.messageService, renderingFor));
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean open(Player player) {
        return open(player, 0);
    }

    public boolean open(Player player, int requestedPage) {
        previews.remove(player.getUniqueId());
        NavigatorConfig.State current = configuration.get();
        if (current.enabled()) return openConfigured(player, current.root(), List.of(), requestedPage, current);
        Player previousViewer = renderingFor; renderingFor = player;
        try {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!canAccess(validatedPlayer)) {
            close(validatedPlayer);
            return false;
        }
        List<WarpPoint> warps = warpService.getNavigatorWarps();
        Pagination pagination = Pagination.of(warps.size(), requestedPage, layout.pageSize());
        int page = pagination.page();
        int pageCount = pagination.pageCount();
        int fromIndex = pagination.fromIndex();
        int toIndex = pagination.toIndex();
        Map<Integer, String> warpIdsBySlot = new LinkedHashMap<>();
        for (int index = fromIndex; index < toIndex; index++) {
            warpIdsBySlot.put(layout.contentSlot(index - fromIndex), warps.get(index).id());
        }

        NavigatorInventoryHolder holder = new NavigatorInventoryHolder(
                validatedPlayer.getUniqueId(),
                page,
                warpIdsBySlot, layout
        );
        Inventory inventory;
        if (rerenderInventory != null && rerenderInventory.getHolder() instanceof NavigatorInventoryHolder existing) {
            inventory = rerenderInventory;
            existing.beginRender(holder); holder = existing;
            for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, null);
        } else {
            inventory = inventoryFactory.create(holder, layout.size(), messageService.localize(validatedPlayer, TITLE));
            holder.bindInventory(inventory);
        }

        for (int index = fromIndex; index < toIndex; index++) {
            inventory.setItem(layout.contentSlot(index - fromIndex), createWarpItem(warps.get(index)));
        }
        if (warps.isEmpty()) {
            placeDisplay(inventory, layout.emptySlot(), createItem(
                    Material.GRAY_DYE,
                    tr("navigator.empty"),
                    NamedTextColor.GRAY,
                    List.of()
            ));
        }
        if (pagination.hasPrevious()) {
            place(inventory, Action.PREVIOUS, createItem(
                    Material.ARROW,
                    tr("common.previous-page"),
                    NamedTextColor.YELLOW,
                    List.of()
            ));
        }
        if (pagination.hasMultiplePages()) place(inventory, Action.PAGE_INFO, createItem(
                Material.PAPER,
                tr("common.page", java.util.Map.of("current", Component.text(page+1), "total", Component.text(pageCount))),
                NamedTextColor.AQUA,
                List.of(tr(warps.size() == 1 ? "navigator.count.one" : "navigator.count.many", java.util.Map.of("count", Component.text(warps.size()))))
        ));
        place(inventory, Action.CLOSE, createItem(
                Material.BARRIER,
                tr("common.close"),
                NamedTextColor.RED,
                List.of()
        ));
        if (pagination.hasNext()) {
            place(inventory, Action.NEXT, createItem(
                    Material.ARROW,
                    tr("common.next-page"),
                    NamedTextColor.YELLOW,
                    List.of()
            ));
        }
        holder.finishRender();
        var opened = inventory == rerenderInventory ? validatedPlayer.getOpenInventory() : validatedPlayer.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory
                && validatedPlayer.getOpenInventory().getTopInventory() == inventory) {
            if (!canAccess(validatedPlayer)) {
                close(validatedPlayer);
                return false;
            }
            activeInventories.put(validatedPlayer.getUniqueId(), inventory);
            return true;
        }
        return false;

        } finally { renderingFor = previousViewer; }
    }

    private ItemStack createWarpItem(WarpPoint warp) {
        return createItem(
                warp.icon(),
                Component.text(warp.displayName()),
                NamedTextColor.AQUA,
                List.of()
        );
    }

    public void closeOpenInventories() {
        try {
            for (Map.Entry<UUID, Inventory> entry : List.copyOf(activeInventories.entrySet())) {
                try {
                    Player player = onlinePlayer.apply(entry.getKey());
                    if (player != null && player.isOnline()
                            && player.getOpenInventory().getTopInventory() == entry.getValue()) {
                        close(player);
                    }
                } catch (RuntimeException exception) {
                    logger.log(Level.WARNING, "Could not close navigator for " + entry.getKey() + ".", exception);
                }
            }
        } finally {
            activeInventories.clear();
            previews.clear();
            backHints.clear();
        }
    }

    public boolean isActive(Player player, Inventory inventory, NavigatorInventoryHolder holder) {
        return player != null && inventory != null && holder != null
                && holder.getOwnerUniqueId().equals(player.getUniqueId())
                && inventory.getHolder() == holder && holder.isBoundTo(inventory)
                && activeInventories.get(player.getUniqueId()) == inventory
                && player.getOpenInventory().getTopInventory() == inventory;
    }

    public boolean canAccess(Player player) {
        return accessPolicy.canAccess(player);
    }

    boolean isLobbyWorld(World world) {
        return accessPolicy.isLobbyWorld(world);
    }

    void forgetIfActive(UUID owner, Inventory inventory) {
        activeInventories.remove(owner, inventory);
    }

    void forget(UUID owner) {
        activeInventories.remove(owner);
        previews.remove(owner);
        backHints.remove(owner);
    }

    void close(Player player) {
        forget(player.getUniqueId());
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory.getHolder() instanceof NavigatorInventoryHolder holder
                && holder.getOwnerUniqueId().equals(player.getUniqueId()) && holder.isBoundTo(inventory)) {
            player.closeInventory();
        }
    }

    private ItemStack createItem(
            Material material,
            Component name,
            NamedTextColor nameColor,
            List<Component> loreLines
    ) {
        return itemRenderer.render(UiItems.literal(material, name, nameColor, loreLines));
    }

    @FunctionalInterface
    interface InventoryFactory {
        Inventory create(NavigatorInventoryHolder holder, int size, Component title);
    }

    @FunctionalInterface
    interface ItemRenderer {
        ItemStack render(UiItemSpec spec);
    }
    private void place(Inventory inventory, Action action, ItemStack item) {
        place(inventory, action, 0, item);
    }
    private void place(Inventory inventory, Action action, int position, ItemStack item) {
        layout.actionSlot(action, position).ifPresent(slot -> { inventory.setItem(slot, item); ((NavigatorInventoryHolder) inventory.getHolder()).recordAction(slot, action); });
    }

    private void placeDisplay(Inventory inventory, int slot, ItemStack item) {
        if (slot >= 0) inventory.setItem(slot, item);
    }

    public void refreshActive(UUID owner) {
        Inventory inventory = activeInventories.get(owner);
        if (inventory == null || rerenderInventory != null) return;
        Player player = onlinePlayer.apply(owner);
        if (player == null || !player.isOnline() || player.getOpenInventory().getTopInventory() != inventory
                || !(inventory.getHolder() instanceof NavigatorInventoryHolder holder) || !isActive(player, inventory, holder)) {
            activeInventories.remove(owner, inventory); return;
        }
        if (!canAccess(player)) { activeInventories.remove(owner, inventory); player.closeInventory(); return; }
        try {
            NavigatorConfig.State current = current(player);
            if (holder.configuration() != null && holder.configuration() != current
                    || holder.configuration() == null && current.enabled()) {
                open(player); return;
            }
            rerenderInventory = inventory;
            openPage(player, holder, holder.getPage());
        }
        catch (RuntimeException exception) {
            activeInventories.remove(owner, inventory);
            if (player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
            logger.log(Level.WARNING, "Could not update NavigatorMenu for " + owner, exception);
        } finally { rerenderInventory = null; }
    }

    public void startUpdates() {
        messageService.localization().addLocaleListener(localeChanges);
        if (!observingChanges) { warpService.addChangeListener(committedChanges); observingChanges = true; }
    }
    public void stopUpdates() {
        RuntimeException failure = null;
        try {
            messageService.localization().removeLocaleListener(localeChanges);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        // An add can take effect and throw before observingChanges is set.
        observingChanges = false;
        try {
            warpService.removeChangeListener(committedChanges);
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

    boolean validateView(Player player, NavigatorInventoryHolder holder) {
        NavigatorConfig.State current = current(player);
        if (holder.configuration() == null && !current.enabled()) return true;
        if (holder.configuration() == current && current.enabled() && routeAllowed(player, holder.menuId(), holder.parents(), current))
            return true;
        messageService.send(player, tr("navigator.unavailable"));
        open(player);
        return false;
    }

    void openPage(Player player, NavigatorInventoryHolder holder, int page) {
        if (holder.configuration() == null) open(player, page);
        else openConfigured(player, holder.menuId(), holder.parents(), page, current(player));
    }

    void back(Player player, NavigatorInventoryHolder holder) {
        var parents = holder.parents();
        if (parents.isEmpty()) return;
        var parent = parents.getLast();
        openConfigured(player, parent.menuId(), parents.subList(0, parents.size() - 1), parent.page(), current(player));
    }

    void activateEntry(Player player, NavigatorInventoryHolder holder, String id) {
        if (!validateView(player, holder)) return;
        var state = current(player);
        var entry = state.menus().get(holder.menuId()).entries().get(id);
        if (entry == null || !entry.allowed(player)) {
            messageService.send(player, tr("navigator.unavailable"));
            openPage(player, holder, holder.getPage());
            return;
        }
        switch (entry.type()) {
            case MENU -> {
                var parents = new java.util.ArrayList<>(holder.parents());
                parents.add(new NavigatorInventoryHolder.Breadcrumb(holder.menuId(), holder.getPage(), entry.id()));
                openConfigured(player, entry.target(), parents, 0, state);
            }
            case UNAVAILABLE -> { }
            case WARP -> {
                if (previews.containsKey(player.getUniqueId())) return;
                var result = warpService.teleportFromNavigator(player, entry.target());
                switch (result) {
                    case SUCCESS -> close(player);
                    case NOT_FOUND, NOT_NAVIGABLE -> {
                        messageService.send(player, tr("navigator.destination-missing"));
                        openPage(player, holder, holder.getPage());
                    }
                    case WORLD_NOT_LOADED -> messageService.send(player, tr("warp.world-missing"));
                    case TELEPORT_FAILED -> messageService.send(player, tr("warp.teleport-failed"));
                    default -> messageService.send(player, tr("navigator.unavailable"));
                }
            }
        }
    }

    private boolean routeAllowed(Player player, String menuId, List<NavigatorInventoryHolder.Breadcrumb> parents,
                                 NavigatorConfig.State state) {
        String current = state.root();
        for (var parent : parents) {
            if (!current.equals(parent.menuId()) || !state.menus().containsKey(current)) return false;
            var link = state.menus().get(current).entries().get(parent.entryId());
            if (link == null || link.type() != NavigatorConfig.Type.MENU || !link.allowed(player)) return false;
            current = link.target();
        }
        return current.equals(menuId) && state.menus().containsKey(menuId);
    }

    private boolean openConfigured(Player player, String menuId, List<NavigatorInventoryHolder.Breadcrumb> parents,
                                   int requestedPage, NavigatorConfig.State state) {
        Objects.requireNonNull(player, "player");
        if (!canAccess(player) || !state.enabled() || !routeAllowed(player, menuId, parents, state)) {
            close(player); return false;
        }
        Player previousViewer = renderingFor; renderingFor = player;
        try {
            var menu = state.menus().get(menuId);
            var entries = menu.entries().values().stream().filter(entry -> entry.allowed(player)).toList();
            var pages = entries.isEmpty() ? List.of(0) : entries.stream().map(NavigatorConfig.Entry::page).distinct().sorted().toList();
            Pagination pagination = Pagination.of(pages.size(), requestedPage, 1);
            int configuredPage = pages.get(pagination.page());
            var visible = entries.stream().filter(entry -> entry.page() == configuredPage).toList();
            Map<Integer, String> ids = new LinkedHashMap<>();
            visible.forEach(entry -> ids.put(entry.slot(), entry.id()));
            NavigatorInventoryHolder holder = new NavigatorInventoryHolder(player.getUniqueId(), pagination.page(),
                    menuId, ids, state, parents);
            Inventory inventory;
            if (rerenderInventory != null && rerenderInventory.getSize() == menu.inventorySize()
                    && rerenderInventory.getHolder() instanceof NavigatorInventoryHolder existing) {
                inventory = rerenderInventory; existing.beginRender(holder); holder = existing;
                for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, null);
            } else {
                inventory = inventoryFactory.create(holder, menu.inventorySize(),
                        Component.text(menu.title().resolve(messageService.localization().locale(player)), NamedTextColor.DARK_GRAY));
                holder.bindInventory(inventory);
            }
            for (var entry : visible) inventory.setItem(entry.slot(), createConfiguredItem(player, entry));
            if (visible.isEmpty()) placeDisplay(inventory, menu.inventorySize() / 2,
                    createItem(Material.GRAY_DYE, tr("navigator.empty"), NamedTextColor.GRAY, List.of()));
            if (pagination.hasPrevious()) placeConfigured(inventory, Action.PREVIOUS,
                    createItem(Material.ARROW, tr("common.previous-page"), NamedTextColor.YELLOW, List.of()));
            if (!parents.isEmpty() && menu.backSlot() >= 0) inventory.setItem(menu.backSlot(),
                    createItem(Material.ARROW, tr("common.back"), NamedTextColor.YELLOW, List.of()));
            if (pagination.hasNext()) placeConfigured(inventory, Action.NEXT,
                    createItem(Material.ARROW, tr("common.next-page"), NamedTextColor.YELLOW, List.of()));
            holder.finishRender();
            var opened = inventory == rerenderInventory ? player.getOpenInventory() : player.openInventory(inventory);
            if (opened == null || opened.getTopInventory() != inventory || player.getOpenInventory().getTopInventory() != inventory)
                return false;
            if (!canAccess(player) || current(player) != state || !routeAllowed(player, menuId, parents, state)) {
                close(player); return false;
            }
            activeInventories.put(player.getUniqueId(), inventory);
            if (!parents.isEmpty() && backHints.add(player.getUniqueId()))
                player.sendActionBar(messageService.localize(player, tr("navigator.back-hint")).color(NamedTextColor.GRAY));
            return true;
        } finally { renderingFor = previousViewer; }
    }

    private void placeConfigured(Inventory inventory, Action action, ItemStack item) {
        int slot = action == Action.PREVIOUS ? inventory.getSize() - 9 : inventory.getSize() - 1;
        inventory.setItem(slot, item);
        ((NavigatorInventoryHolder) inventory.getHolder()).recordAction(slot, action);
    }

    private ItemStack createConfiguredItem(Player player, NavigatorConfig.Entry entry) {
        var locale = messageService.localization().locale(player);
        boolean available = entry.type() != NavigatorConfig.Type.UNAVAILABLE
                && (entry.type() != NavigatorConfig.Type.WARP || warpService.getWarp(entry.target())
                    .filter(warp -> warp.navigation().visible()).isPresent());
        var lore = new java.util.ArrayList<Component>();
        entry.lore().forEach(line -> lore.add(Component.text(line.resolve(locale))));
        long count = -1;
        if (available && entry.region() != null) {
            count = entry.region().count(player, onlinePlayers.get());
            if (!entry.stackAmount()) lore.add(tr(count == 1 ? "navigator.players.one" : "navigator.players.many",
                    Map.of("count", Component.text(count, NamedTextColor.GREEN))));
        }
        if (!available) lore.add(tr("navigator.unavailable-hint"));
        else if (entry.type() == NavigatorConfig.Type.MENU) lore.add(tr("navigator.menu-hint"));
        ItemStack item = createItem(entry.icon(), Component.text(entry.name().resolve(locale)),
                available ? NamedTextColor.AQUA : NamedTextColor.GRAY, lore);
        if (count >= 0 && entry.stackAmount()) {
            int amount = NavigatorRegion.stackAmount(count, item.getMaxStackSize());
            if (count == 0 || count > amount) {
                lore.add(tr(count == 0 ? "navigator.players.zero" : "navigator.players.overflow",
                        count == 0 ? Map.of() : Map.of("count", Component.text(count, NamedTextColor.GREEN))));
                item = createItem(entry.icon(), Component.text(entry.name().resolve(locale)), NamedTextColor.AQUA, lore);
            }
            item.setAmount(amount);
        }
        return item;
    }
    /** Navigator-only tooltip policy; item names/lore and other menus are untouched. */
    static ItemStack renderIcon(UiItemSpec spec) {
        ItemStack item = UiItems.render(spec);
        var meta = item.getItemMeta();
        if (meta != null) {
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                    org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS, org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE,
                    org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP, org.bukkit.inventory.ItemFlag.HIDE_DYE,
                    org.bukkit.inventory.ItemFlag.HIDE_ARMOR_TRIM);
            item.setItemMeta(meta);
        }
        return item;
    }

}
