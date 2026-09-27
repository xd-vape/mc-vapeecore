package dev.vapee.core.utility;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class InvseeInventoryHolder implements InventoryHolder {

    private final UUID viewerUniqueId;
    private final UUID targetUniqueId;
    private Inventory inventory;

    public InvseeInventoryHolder(UUID viewerUniqueId, UUID targetUniqueId) {
        this.viewerUniqueId = Objects.requireNonNull(viewerUniqueId, "viewerUniqueId");
        this.targetUniqueId = Objects.requireNonNull(targetUniqueId, "targetUniqueId");
    }

    public UUID getViewerUniqueId() {
        return viewerUniqueId;
    }

    public UUID getTargetUniqueId() {
        return targetUniqueId;
    }

    void bindInventory(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Invsee inventory is already bound");
        }
        Inventory validatedInventory = Objects.requireNonNull(inventory, "inventory");
        if (validatedInventory.getHolder() != this) {
            throw new IllegalArgumentException("Inventory is not owned by this invsee holder");
        }
        this.inventory = validatedInventory;
    }

    boolean isBoundTo(Inventory inventory) {
        return this.inventory == inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Invsee inventory is not initialized");
    }
}
