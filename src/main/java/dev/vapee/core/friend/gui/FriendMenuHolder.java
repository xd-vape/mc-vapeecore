package dev.vapee.core.friend.gui;

import dev.vapee.core.ui.MenuLayout;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class FriendMenuHolder implements InventoryHolder {
    private FriendMenuHolder rendered;
    private FriendMenuHolder pending;
    private java.util.Map<Integer, FriendMenu.Action> visibleActions = new java.util.LinkedHashMap<>();
    private FriendMenuHolder data() { return rendered == null ? this : rendered; }
    public Optional<FriendMenu.Action> actionAt(int slot) { return Optional.ofNullable(data().visibleActions.get(slot)); }
    void beginRender(FriendMenuHolder next) { pending = next; }
    void recordAction(int slot, FriendMenu.Action action) { (pending == null ? this : pending).visibleActions.put(slot, action); }
    void finishRender() {
        if (pending == null) visibleActions = java.util.Map.copyOf(visibleActions);
        else { pending.visibleActions = java.util.Map.copyOf(pending.visibleActions); rendered = pending; pending = null; }
    }


    private final UUID ownerUniqueId;
    private final FriendMenuView view;
    private final int page;
    private final Map<Integer, UUID> targetsBySlot;
    private Inventory inventory;

    public FriendMenuHolder(UUID ownerUniqueId, FriendMenuView view, int page,
                            Map<Integer, UUID> targetsBySlot) {
        this(ownerUniqueId, view, page, targetsBySlot, FriendMenu.DEFAULT_LAYOUT);
    }

    public FriendMenuHolder(UUID ownerUniqueId, FriendMenuView view, int page,
                            Map<Integer, UUID> targetsBySlot, MenuLayout<FriendMenu.Action> layout) {
        this.ownerUniqueId = Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
        this.view = Objects.requireNonNull(view, "view");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        this.page = page;
        Map<Integer, UUID> copy = Map.copyOf(Objects.requireNonNull(targetsBySlot, "targetsBySlot"));
        if (copy.keySet().stream().anyMatch(slot -> !layout.isContent(slot))) {
            throw new IllegalArgumentException("target slot is outside menu content");
        }
        this.targetsBySlot = copy;
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    public FriendMenuView getView() {
        return data().view;
    }

    public int getPage() {
        return data().page;
    }

    public Optional<UUID> getTarget(int slot) {
        return Optional.ofNullable(data().targetsBySlot.get(slot));
    }

    void bindInventory(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Friend menu is already bound");
        }
        Inventory candidate = Objects.requireNonNull(inventory, "inventory");
        if (candidate.getHolder() != this) {
            throw new IllegalArgumentException("Inventory does not belong to this friend menu holder");
        }
        this.inventory = candidate;
    }

    boolean isBoundTo(Inventory candidate) {
        return inventory != null && inventory == candidate;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Friend menu is not initialized");
    }
}
