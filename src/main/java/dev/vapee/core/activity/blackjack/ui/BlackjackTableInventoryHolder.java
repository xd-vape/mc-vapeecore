package dev.vapee.core.activity.blackjack.ui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class BlackjackTableInventoryHolder implements InventoryHolder {

    private final UUID ownerUniqueId;
    private final UUID sessionId;
    private Inventory inventory;

    public BlackjackTableInventoryHolder(UUID ownerUniqueId, UUID sessionId) {
        this.ownerUniqueId = Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    public UUID getSessionId() {
        return sessionId;
    }

    void bindInventory(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Blackjack table inventory is already bound");
        }
        Inventory validatedInventory = Objects.requireNonNull(inventory, "inventory");
        if (validatedInventory.getHolder() != this) {
            throw new IllegalArgumentException("Inventory is not owned by this blackjack table holder");
        }
        this.inventory = validatedInventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Blackjack table inventory is not initialized");
    }
}
