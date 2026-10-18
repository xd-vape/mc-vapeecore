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
    private DailyQuestInventoryHolder rendered;
    private DailyQuestInventoryHolder pending;
    private java.util.Map<Integer, DailyQuestMenu.Action> visibleActions = new java.util.LinkedHashMap<>();
    private DailyQuestInventoryHolder data() { return rendered == null ? this : rendered; }
    public Optional<DailyQuestMenu.Action> actionAt(int slot) { return Optional.ofNullable(data().visibleActions.get(slot)); }
    void beginRender(DailyQuestInventoryHolder next) { pending = next; }
    void recordAction(int slot, DailyQuestMenu.Action action) { (pending == null ? this : pending).visibleActions.put(slot, action); }
    void finishRender() {
        if (pending == null) visibleActions = java.util.Map.copyOf(visibleActions);
        else { pending.visibleActions = java.util.Map.copyOf(pending.visibleActions); rendered = pending; pending = null; }
    }

    private final UUID owner;
    private final int page;
    private final Map<Integer, String> targets;
    public Optional<String> target(int slot) { return Optional.ofNullable(data().targets.get(slot)); }
    private Inventory inventory;
    dev.vapee.core.ui.UiItemSpec resetClock;

    DailyQuestInventoryHolder(UUID owner, int page) {
        this(owner, page, Map.of(), DailyQuestMenu.DEFAULT_LAYOUT.menu());
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
    public int page() { return data().page; }
    public boolean isBoundTo(Inventory inventory) { return this.inventory == inventory && inventory != null; }
    @Override public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Quest inventory is not bound");
    }
}
