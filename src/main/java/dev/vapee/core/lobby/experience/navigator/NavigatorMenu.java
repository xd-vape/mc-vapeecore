package dev.vapee.core.lobby.experience.navigator;

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
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NavigatorMenu {
    private final dev.vapee.core.lobby.warp.WarpChangeListener committedChanges = () -> java.util.List.copyOf(this.activeInventories.keySet()).forEach(this::refreshActive);
    private boolean observingChanges;

    private Inventory rerenderInventory;


    public enum Action { PREVIOUS, PAGE_INFO, CLOSE, NEXT }
    public static final MenuLayout<Action> DEFAULT_LAYOUT = MenuLayout.builder(54, Action.class)
            .content(List.of(
                    0, 1, 2, 3, 4, 5, 6, 7, 8,
                    9, 10, 11, 12, 13, 14, 15, 16, 17,
                    18, 19, 20, 21, 22, 23, 24, 25, 26,
                    27, 28, 29, 30, 31, 32, 33, 34, 35,
                    36, 37, 38, 39, 40, 41, 42, 43, 44))
            .emptyState(22)
            .button(Action.PREVIOUS, 45)
            .button(Action.PAGE_INFO, 49)
            .button(Action.CLOSE, 50)
            .button(Action.NEXT, 53)
            .build();

    private static final Component TITLE = Component.text("Warp Navigator", NamedTextColor.DARK_GRAY);

    private final MenuLayout<Action> layout;
    public MenuLayout<Action> layout() { return layout; }

    private final WarpService warpService;
    private final NavigatorAccessPolicy accessPolicy;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private final Function<UUID, Player> onlinePlayer;
    private final Logger logger;
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public NavigatorMenu(JavaPlugin plugin, WarpService warpService, NavigatorAccessPolicy accessPolicy) {
        this(warpService, accessPolicy, Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                UiItems::render, plugin.getLogger());
    }

    NavigatorMenu(WarpService warpService, NavigatorAccessPolicy accessPolicy,
                  Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                  ItemRenderer itemRenderer, Logger logger) {
        this(warpService, accessPolicy, onlinePlayer, inventoryFactory, itemRenderer, logger, DEFAULT_LAYOUT);
    }

    NavigatorMenu(WarpService warpService, NavigatorAccessPolicy accessPolicy,
                  Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                  ItemRenderer itemRenderer, Logger logger, MenuLayout<Action> menuLayout) {
        this.layout = Objects.requireNonNull(menuLayout, "layout").validateActions(java.util.Set.of());
        this.layout.requirePaged();
        this.warpService = Objects.requireNonNull(warpService, "warpService");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean open(Player player) {
        return open(player, 0);
    }

    public boolean open(Player player, int requestedPage) {
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
            inventory = inventoryFactory.create(holder, layout.size(), TITLE);
            holder.bindInventory(inventory);
        }

        for (int index = fromIndex; index < toIndex; index++) {
            inventory.setItem(layout.contentSlot(index - fromIndex), createWarpItem(warps.get(index)));
        }
        if (warps.isEmpty()) {
            placeDisplay(inventory, layout.emptySlot(), createItem(
                    Material.GRAY_DYE,
                    "No Destinations Available",
                    NamedTextColor.GRAY,
                    List.of()
            ));
        }
        if (pagination.hasPrevious()) {
            place(inventory, Action.PREVIOUS, createItem(
                    Material.ARROW,
                    "Previous Page",
                    NamedTextColor.YELLOW,
                    List.of()
            ));
        }
        if (pagination.hasMultiplePages()) place(inventory, Action.PAGE_INFO, createItem(
                Material.PAPER,
                "Page " + (page + 1) + "/" + pageCount,
                NamedTextColor.AQUA,
                List.of(warps.size() + (warps.size() == 1 ? " destination." : " destinations."))
        ));
        place(inventory, Action.CLOSE, createItem(
                Material.BARRIER,
                "Close",
                NamedTextColor.RED,
                List.of()
        ));
        if (pagination.hasNext()) {
            place(inventory, Action.NEXT, createItem(
                    Material.ARROW,
                    "Next Page",
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
    }

    private ItemStack createWarpItem(WarpPoint warp) {
        return createItem(
                warp.icon(),
                warp.displayName(),
                NamedTextColor.AQUA,
                List.of("Click to teleport.")
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
            String name,
            NamedTextColor nameColor,
            List<String> loreLines
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
        try { rerenderInventory = inventory; open(player, holder.getPage()); }
        catch (RuntimeException exception) {
            activeInventories.remove(owner, inventory);
            if (player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
            logger.log(Level.WARNING, "Could not update NavigatorMenu for " + owner, exception);
        } finally { rerenderInventory = null; }
    }

    public void startUpdates() {
        if (!observingChanges) { warpService.addChangeListener(committedChanges); observingChanges = true; }
    }
    public void stopUpdates() {
        if (observingChanges) { warpService.removeChangeListener(committedChanges); observingChanges = false; }
    }
    public void shutdown() { stopUpdates(); closeOpenInventories(); }

}
