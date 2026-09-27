package dev.vapee.core.utility;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

public final class InvseeService implements Listener {

    public static final int INVENTORY_SIZE = 54;
    public static final int HOTBAR_START = 0;
    public static final int MAIN_START = 9;
    public static final int HELMET_SLOT = 45;
    public static final int CHESTPLATE_SLOT = 46;
    public static final int LEGGINGS_SLOT = 47;
    public static final int BOOTS_SLOT = 48;
    public static final int OFFHAND_SLOT = 50;

    private final BooleanSupplier primaryThreadCheck;
    private final Function<UUID, Player> playerLookup;
    private final InventoryFactory inventoryFactory;
    private final Map<UUID, ActiveView> activeViews = new HashMap<>();

    public InvseeService(JavaPlugin plugin) {
        this(
                Objects.requireNonNull(plugin, "plugin").getServer()::isPrimaryThread,
                plugin.getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title)
        );
    }

    InvseeService(
            BooleanSupplier primaryThreadCheck,
            Function<UUID, Player> playerLookup,
            InventoryFactory inventoryFactory
    ) {
        this.primaryThreadCheck = Objects.requireNonNull(primaryThreadCheck, "primaryThreadCheck");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
    }

    public @Nullable Inventory openSnapshot(Player viewer, Player target) {
        requirePrimaryThread();
        Player validatedViewer = Objects.requireNonNull(viewer, "viewer");
        Player validatedTarget = Objects.requireNonNull(target, "target");

        UUID viewerId = validatedViewer.getUniqueId();
        UUID targetId = validatedTarget.getUniqueId();
        InvseeInventoryHolder holder = new InvseeInventoryHolder(viewerId, targetId);
        Component title = Component.text("Inventory: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(validatedTarget.getName(), NamedTextColor.WHITE))
                .append(Component.text(" (read-only)", NamedTextColor.GRAY));
        Inventory inventory = inventoryFactory.create(holder, INVENTORY_SIZE, title);
        holder.bindInventory(inventory);
        copySnapshot(validatedTarget.getInventory(), inventory);

        ActiveView newView = new ActiveView(targetId, inventory);
        activeViews.put(viewerId, newView);
        try {
            InventoryView openedView = validatedViewer.openInventory(inventory);
            if (openedView == null || openedView.getTopInventory() != inventory) {
                activeViews.remove(viewerId, newView);
                return null;
            }
        } catch (RuntimeException exception) {
            activeViews.remove(viewerId, newView);
            throw exception;
        }
        return inventory;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (!(topInventory.getHolder() instanceof InvseeInventoryHolder holder)) {
            return;
        }
        event.setCancelled(true);
        if (!holder.isBoundTo(topInventory)
                || !holder.getViewerUniqueId().equals(event.getWhoClicked().getUniqueId())
                || !isActiveView(holder.getViewerUniqueId(), topInventory)) {
            return;
        }
        // Every click is intentionally cancelled: normal, shift, number-key, double-click,
        // collect-to-cursor, hotbar/offhand swap, drop and creative clicks all reach this base event.
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory topInventory = event.getView().getTopInventory();
        if (topInventory.getHolder() instanceof InvseeInventoryHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrop(PlayerDropItemEvent event) {
        if (hasActiveOpenView(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (hasActiveOpenView(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID viewerId = event.getPlayer().getUniqueId();
        activeViews.computeIfPresent(viewerId, (ignored, activeView) ->
                activeView.inventory() == event.getInventory() ? null : activeView);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        requirePrimaryThread();
        UUID playerId = event.getPlayer().getUniqueId();
        activeViews.remove(playerId);
        closeViewsForTarget(playerId);
    }

    public void disable() {
        requirePrimaryThread();
        List<Map.Entry<UUID, ActiveView>> views = new ArrayList<>(activeViews.entrySet());
        activeViews.clear();
        for (Map.Entry<UUID, ActiveView> entry : views) {
            Player viewer = playerLookup.apply(entry.getKey());
            if (viewer != null && viewer.isOnline() && isOpenInventory(viewer, entry.getValue().inventory())) {
                viewer.closeInventory();
            }
        }
    }

    static void copySnapshot(PlayerInventory source, Inventory target) {
        Objects.requireNonNull(source, "source");
        Inventory validatedTarget = Objects.requireNonNull(target, "target");
        if (validatedTarget.getSize() != INVENTORY_SIZE) {
            throw new IllegalArgumentException("Invsee snapshot inventory must have 54 slots");
        }
        for (int slot = 0; slot <= 35; slot++) {
            validatedTarget.setItem(slot, cloneItem(source.getItem(slot)));
        }
        validatedTarget.setItem(HELMET_SLOT, cloneItem(source.getHelmet()));
        validatedTarget.setItem(CHESTPLATE_SLOT, cloneItem(source.getChestplate()));
        validatedTarget.setItem(LEGGINGS_SLOT, cloneItem(source.getLeggings()));
        validatedTarget.setItem(BOOTS_SLOT, cloneItem(source.getBoots()));
        validatedTarget.setItem(OFFHAND_SLOT, cloneItem(source.getItemInOffHand()));
    }

    boolean isActiveView(UUID viewerId, Inventory inventory) {
        ActiveView activeView = activeViews.get(viewerId);
        return activeView != null && activeView.inventory() == inventory;
    }

    int activeViewCount() {
        return activeViews.size();
    }

    private void closeViewsForTarget(UUID targetId) {
        List<Map.Entry<UUID, ActiveView>> targetViews = activeViews.entrySet().stream()
                .filter(entry -> entry.getValue().targetId().equals(targetId))
                .toList();
        for (Map.Entry<UUID, ActiveView> entry : targetViews) {
            if (!activeViews.remove(entry.getKey(), entry.getValue())) {
                continue;
            }
            Player viewer = playerLookup.apply(entry.getKey());
            if (viewer != null && viewer.isOnline() && isOpenInventory(viewer, entry.getValue().inventory())) {
                viewer.closeInventory();
            }
        }
    }

    private boolean hasActiveOpenView(Player viewer) {
        ActiveView activeView = activeViews.get(viewer.getUniqueId());
        return activeView != null && isOpenInventory(viewer, activeView.inventory());
    }

    private static boolean isOpenInventory(Player viewer, Inventory inventory) {
        return viewer.getOpenInventory().getTopInventory() == inventory;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private void requirePrimaryThread() {
        if (!primaryThreadCheck.getAsBoolean()) {
            throw new IllegalStateException("Invsee lifecycle must run on the primary server thread");
        }
    }

    private record ActiveView(UUID targetId, Inventory inventory) {

        private ActiveView {
            Objects.requireNonNull(targetId, "targetId");
            Objects.requireNonNull(inventory, "inventory");
        }
    }

    @FunctionalInterface
    interface InventoryFactory {

        Inventory create(InvseeInventoryHolder holder, int size, Component title);
    }
}
