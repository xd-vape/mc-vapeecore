package dev.vapee.core.lobby.experience.navigator;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class NavigatorInventoryHolder implements InventoryHolder {

    private final UUID ownerUniqueId;
    private final int page;
    private final Map<Integer, String> warpIdsBySlot;
    private Inventory inventory;

    public NavigatorInventoryHolder(UUID ownerUniqueId, int page, Map<Integer, String> warpIdsBySlot) {
        this.ownerUniqueId = Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        this.page = page;
        this.warpIdsBySlot = Map.copyOf(new LinkedHashMap<>(
                Objects.requireNonNull(warpIdsBySlot, "warpIdsBySlot")
        ));
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    public int getPage() {
        return page;
    }

    public Optional<String> getWarpId(int slot) {
        return Optional.ofNullable(warpIdsBySlot.get(slot));
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

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Navigator inventory is not initialized");
    }
}
