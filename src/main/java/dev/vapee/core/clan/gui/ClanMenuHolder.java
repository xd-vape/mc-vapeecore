package dev.vapee.core.clan.gui;

import dev.vapee.core.ui.MenuLayout;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable slot targets plus one exact inventory binding. */
public final class ClanMenuHolder implements InventoryHolder {
    private ClanMenuHolder rendered;
    private ClanMenuHolder pending;
    private java.util.Map<Integer, ClanMenu.Action> visibleActions = new java.util.LinkedHashMap<>();
    private ClanMenuHolder data() { return rendered == null ? this : rendered; }
    public Optional<ClanMenu.Action> actionAt(int slot) { return Optional.ofNullable(data().visibleActions.get(slot)); }
    void beginRender(ClanMenuHolder next) { pending = next; }
    void recordAction(int slot, ClanMenu.Action action) { (pending == null ? this : pending).visibleActions.put(slot, action); }
    void finishRender() {
        if (pending == null) visibleActions = java.util.Map.copyOf(visibleActions);
        else { pending.visibleActions = java.util.Map.copyOf(pending.visibleActions); rendered = pending; pending = null; }
    }

    private final UUID owner;
    private final ClanMenuView view;
    private final int page;
    private final UUID clanId;
    private final Map<Integer, UUID> targets;
    private Inventory inventory;

    public ClanMenuHolder(UUID owner, ClanMenuView view, int page, Map<Integer, UUID> targets) {
        this(owner, view, page, null, targets);
    }

    public ClanMenuHolder(UUID owner, ClanMenuView view, int page, UUID clanId, Map<Integer, UUID> targets) {
        this(owner, view, page, clanId, targets, ClanMenu.DEFAULT_LAYOUT.menu());
    }

    public ClanMenuHolder(UUID owner, ClanMenuView view, int page, UUID clanId, Map<Integer, UUID> targets, MenuLayout<ClanMenu.Action> layout) {
        this.owner = Objects.requireNonNull(owner, "owner");
        this.view = Objects.requireNonNull(view, "view");
        if (page < 0) throw new IllegalArgumentException("Negative page");
        this.page = page;
        this.clanId = clanId;
        this.targets = Map.copyOf(Objects.requireNonNull(targets, "targets"));
        if (this.targets.keySet().stream().anyMatch(slot -> !layout.isContent(slot)))
            throw new IllegalArgumentException("Target outside content slots");
    }

    public UUID owner() { return owner; }
    public ClanMenuView view() { return data().view; }
    public int page() { return data().page; }
    public Optional<UUID> clanId() { return Optional.ofNullable(data().clanId); }
    public Optional<UUID> target(int slot) { return Optional.ofNullable(data().targets.get(slot)); }

    void bind(Inventory candidate) {
        if (inventory != null) throw new IllegalStateException("Already bound");
        Inventory checked = Objects.requireNonNull(candidate, "inventory");
        if (checked.getHolder() != this) throw new IllegalArgumentException("Wrong holder");
        inventory = checked;
    }

    boolean boundTo(Inventory candidate) { return inventory != null && inventory == candidate; }

    @Override public @NotNull Inventory getInventory() {
        return Objects.requireNonNull(inventory, "Clan menu is not initialized");
    }
}
