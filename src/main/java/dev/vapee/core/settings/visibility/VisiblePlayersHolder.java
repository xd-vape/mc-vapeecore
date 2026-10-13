package dev.vapee.core.settings.visibility;

import dev.vapee.core.ui.MenuLayout;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class VisiblePlayersHolder implements InventoryHolder {
    private VisiblePlayersHolder rendered;
    private VisiblePlayersHolder pending;
    private java.util.Map<Integer, VisiblePlayersMenu.Action> visibleActions = new java.util.LinkedHashMap<>();
    private VisiblePlayersHolder data() { return rendered == null ? this : rendered; }
    public Optional<VisiblePlayersMenu.Action> actionAt(int slot) { return Optional.ofNullable(data().visibleActions.get(slot)); }
    void beginRender(VisiblePlayersHolder next) { pending = next; }
    void recordAction(int slot, VisiblePlayersMenu.Action action) { (pending == null ? this : pending).visibleActions.put(slot, action); }
    void finishRender() {
        if (pending == null) visibleActions = java.util.Map.copyOf(visibleActions);
        else { pending.visibleActions = java.util.Map.copyOf(pending.visibleActions); rendered = pending; pending = null; }
    }

    private final UUID ownerUniqueId;
    private final int page;
    private final Map<Integer, UUID> targetsBySlot;
    private Inventory inventory;

    public VisiblePlayersHolder(UUID ownerUniqueId, int page, Map<Integer, UUID> targetsBySlot) {
        this(ownerUniqueId, page, targetsBySlot, VisiblePlayersMenu.DEFAULT_LAYOUT);
    }

    public VisiblePlayersHolder(UUID ownerUniqueId, int page, Map<Integer, UUID> targetsBySlot, MenuLayout<VisiblePlayersMenu.Action> layout) {
        this.ownerUniqueId = Objects.requireNonNull(ownerUniqueId, "ownerUniqueId");
        if (page < 0) throw new IllegalArgumentException("page must not be negative");
        this.page = page;
        Map<Integer, UUID> copy = Map.copyOf(Objects.requireNonNull(targetsBySlot, "targetsBySlot"));
        if (copy.keySet().stream().anyMatch(slot -> !layout.isContent(slot))) {
            throw new IllegalArgumentException("target slot is outside menu content");
        }
        this.targetsBySlot = copy;
    }

    public UUID getOwnerUniqueId() {
        return ownerUniqueId;
    }

    public int getPage() {
        return data().page;
    }

    public Optional<UUID> getTarget(int slot) {
        return Optional.ofNullable(data().targetsBySlot.get(slot));
    }

    void bindInventory(Inventory inventory) {
        if (this.inventory != null) throw new IllegalStateException("Visible players menu is already bound");
        Inventory candidate = Objects.requireNonNull(inventory, "inventory");
        if (candidate.getHolder() != this) {
            throw new IllegalArgumentException("Inventory does not belong to this visible players holder");
        }
        this.inventory = candidate;
    }

    boolean isBoundTo(Inventory candidate) {
        return inventory != null && inventory == candidate;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Visible players menu is not initialized");
    }
}
