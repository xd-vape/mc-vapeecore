package dev.vapee.core.friend.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class FriendMenuHolder implements InventoryHolder {

    private final UUID ownerUniqueId;
    private final FriendMenuView view;
    private final int page;
    private final Map<Integer, UUID> targetsBySlot;
    private Inventory inventory;

    public FriendMenuHolder(UUID ownerUniqueId, FriendMenuView view, int page,
                            Map<Integer, UUID> targetsBySlot) {
        this.ownerUniqueId = Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
        this.view = Objects.requireNonNull(view, "view");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        this.page = page;
        Map<Integer, UUID> copy = Map.copyOf(Objects.requireNonNull(targetsBySlot, "targetsBySlot"));
        if (copy.keySet().stream().anyMatch(slot -> slot < 0 || slot >= FriendMenu.CONTENT_SIZE)) {
            throw new IllegalArgumentException("target slot is outside menu content");
        }
        this.targetsBySlot = copy;
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    public FriendMenuView getView() {
        return view;
    }

    public int getPage() {
        return page;
    }

    public Optional<UUID> getTarget(int slot) {
        return Optional.ofNullable(targetsBySlot.get(slot));
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
