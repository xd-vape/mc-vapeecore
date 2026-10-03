package dev.vapee.core.quest.daily.menu;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.UUID;

public final class DailyQuestInventoryHolder implements InventoryHolder {
    private final UUID owner;
    private final int page;
    private Inventory inventory;

    DailyQuestInventoryHolder(UUID owner, int page) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.page = page;
    }

    void bind(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("Quest inventory already bound");
        this.inventory = Objects.requireNonNull(inventory, "inventory");
    }

    public UUID owner() { return owner; }
    public int page() { return page; }
    public boolean isBoundTo(Inventory inventory) { return this.inventory == inventory && inventory != null; }
    @Override public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Quest inventory is not bound");
    }
}
