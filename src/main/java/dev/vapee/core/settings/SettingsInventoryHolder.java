package dev.vapee.core.settings;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class SettingsInventoryHolder implements InventoryHolder {

    private final UUID ownerUniqueId;
    private Inventory inventory;

    public SettingsInventoryHolder(UUID ownerUniqueId) {
        this.ownerUniqueId = Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    void bindInventory(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Settings inventory is already bound");
        }

        Inventory validatedInventory = Objects.requireNonNull(inventory, "inventory");
        if (validatedInventory.getHolder() != this) {
            throw new IllegalArgumentException("Inventory is not owned by this settings holder");
        }
        this.inventory = validatedInventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Settings inventory is not initialized");
    }
}
