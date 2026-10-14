package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.ui.MenuLayout;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NavigatorInventoryHolder implements InventoryHolder {
    private NavigatorInventoryHolder rendered;
    private NavigatorInventoryHolder pending;
    private java.util.Map<Integer, NavigatorMenu.Action> visibleActions = new java.util.LinkedHashMap<>();
    private NavigatorInventoryHolder data() { return rendered == null ? this : rendered; }
    public Optional<NavigatorMenu.Action> actionAt(int slot) { return Optional.ofNullable(data().visibleActions.get(slot)); }
    void beginRender(NavigatorInventoryHolder next) { pending = next; }
    void recordAction(int slot, NavigatorMenu.Action action) { (pending == null ? this : pending).visibleActions.put(slot, action); }
    void finishRender() {
        if (pending == null) visibleActions = java.util.Map.copyOf(visibleActions);
        else { pending.visibleActions = java.util.Map.copyOf(pending.visibleActions); rendered = pending; pending = null; }
    }


    private final UUID ownerUniqueId;
    private final int page;
    private final Map<Integer, String> warpIdsBySlot;
    private Inventory inventory;

    public NavigatorInventoryHolder(UUID ownerUniqueId, int page, Map<Integer, String> warpIdsBySlot) {
        this(ownerUniqueId, page, warpIdsBySlot, NavigatorMenu.DEFAULT_LAYOUT);
    }

    public NavigatorInventoryHolder(UUID ownerUniqueId, int page, Map<Integer, String> warpIdsBySlot, MenuLayout<NavigatorMenu.Action> layout) {
        this.ownerUniqueId = Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        this.page = page;
        layout.validateTargets(warpIdsBySlot.keySet());
        this.warpIdsBySlot = Map.copyOf(new LinkedHashMap<>(
                Objects.requireNonNull(warpIdsBySlot, "warpIdsBySlot")
        ));
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    public int getPage() {
        return data().page;
    }

    public Optional<String> getWarpId(int slot) {
        return Optional.ofNullable(data().warpIdsBySlot.get(slot));
    }

    void bindInventory(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Navigator inventory is already bound");
        }

        Inventory validatedInventory = Objects.requireNonNull(inventory, "inventory");
        if (validatedInventory.getHolder() != this) {
            throw new IllegalArgumentException("Inventory is not owned by this navigator holder");
        }
        this.inventory = validatedInventory;
    }

    public boolean isBoundTo(Inventory inventory) {
        return this.inventory != null && this.inventory == inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Navigator inventory is not initialized");
    }
}
