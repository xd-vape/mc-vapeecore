package dev.vapee.core.activity.blackjack.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class BlackjackModeInventoryHolder implements InventoryHolder {

    private final UUID ownerUniqueId;
    private Inventory inventory;

    public BlackjackModeInventoryHolder(UUID ownerUniqueId) {
        this.ownerUniqueId = Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    void bindInventory(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Blackjack mode inventory is already bound");
        }
        Inventory validatedInventory = Objects.requireNonNull(inventory, "inventory");
        if (validatedInventory.getHolder() != this) {
            throw new IllegalArgumentException("Inventory is not owned by this blackjack mode holder");
        }
        this.inventory = validatedInventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Blackjack mode inventory is not initialized");
    }
}
