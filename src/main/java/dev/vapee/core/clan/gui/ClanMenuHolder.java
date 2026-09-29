package dev.vapee.core.clan.gui;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable slot targets plus one exact inventory binding. */
public final class ClanMenuHolder implements InventoryHolder {
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
        this.owner = Objects.requireNonNull(owner, "owner");
        this.view = Objects.requireNonNull(view, "view");
        if (page < 0) throw new IllegalArgumentException("Negative page");
        this.page = page;
        this.clanId = clanId;
        this.targets = Map.copyOf(Objects.requireNonNull(targets, "targets"));
        if (this.targets.keySet().stream().anyMatch(slot -> slot < 0 || slot >= ClanMenu.CONTENT_SIZE))
            throw new IllegalArgumentException("Target outside content slots");
    }

    public UUID owner() { return owner; }
    public ClanMenuView view() { return view; }
    public int page() { return page; }
    public Optional<UUID> clanId() { return Optional.ofNullable(clanId); }
    public Optional<UUID> target(int slot) { return Optional.ofNullable(targets.get(slot)); }

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
