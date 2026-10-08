package dev.vapee.core.quest.daily.menu;

import dev.vapee.core.ui.MenuLayout;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.UUID;

public final class DailyQuestInventoryHolder implements InventoryHolder {
    private final UUID owner;
    private final int page;
    private final Map<Integer, String> targets;
    public Optional<String> target(int slot) { return Optional.ofNullable(targets.get(slot)); }
    private Inventory inventory;

    DailyQuestInventoryHolder(UUID owner, int page) {
        this(owner, page, Map.of(), DailyQuestMenu.DEFAULT_LAYOUT);
    }

    DailyQuestInventoryHolder(UUID owner, int page, Map<Integer, String> targets,
                               MenuLayout<DailyQuestMenu.Action> layout) {
        layout.validateTargets(targets.keySet());
        this.targets = Map.copyOf(targets);
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
