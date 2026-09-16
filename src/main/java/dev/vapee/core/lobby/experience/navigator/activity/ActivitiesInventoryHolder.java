package dev.vapee.core.lobby.experience.navigator.activity;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ActivitiesInventoryHolder implements InventoryHolder {

    private final UUID ownerUniqueId;
    private final Map<Integer, String> activityKeysBySlot;
    private Inventory inventory;

    public ActivitiesInventoryHolder(UUID ownerUniqueId, Map<Integer, String> activityKeysBySlot) {
        this.ownerUniqueId = Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
        this.activityKeysBySlot = Map.copyOf(new LinkedHashMap<>(
                Objects.requireNonNull(activityKeysBySlot, "activityKeysBySlot")
        ));
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    public Optional<String> getActivityKey(int slot) {
        return Optional.ofNullable(activityKeysBySlot.get(slot));
    }

    void bindInventory(Inventory inventory) {
        if (this.inventory != null) {
            throw new IllegalStateException("Activities inventory is already bound");
        }
        Inventory validatedInventory = Objects.requireNonNull(inventory, "inventory");
        if (validatedInventory.getHolder() != this) {
            throw new IllegalArgumentException("Inventory is not owned by this activities holder");
        }
        this.inventory = validatedInventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Activities inventory is not initialized");
    }
}
