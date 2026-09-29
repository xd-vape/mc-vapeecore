package dev.vapee.core.settings.visibility;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class VisibilitySettingsHolder implements InventoryHolder {
    private final UUID ownerUniqueId;
    private Inventory inventory;

    public VisibilitySettingsHolder(UUID ownerUniqueId) {
        this.ownerUniqueId = Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    void bindInventory(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("Visibility settings menu is already bound");
        Inventory candidate = Objects.requireNonNull(inventory, "inventory");
        if (candidate.getHolder() != this) {
            throw new IllegalArgumentException("Inventory does not belong to this visibility settings holder");
        }
        this.inventory = candidate;
    }

    boolean isBoundTo(Inventory candidate) {
        return inventory != null && inventory == candidate;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Visibility settings menu is not initialized");
    }
}
