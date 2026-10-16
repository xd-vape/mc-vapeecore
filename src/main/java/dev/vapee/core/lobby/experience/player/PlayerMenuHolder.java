package dev.vapee.core.lobby.experience.player;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.*;

public final class PlayerMenuHolder implements InventoryHolder {
    private final UUID owner;
    private Inventory inventory;
    private Set<PlayerMenu.Action> rendered = Set.of();

    public PlayerMenuHolder(UUID owner) { this.owner = Objects.requireNonNull(owner); }
    public UUID owner() { return owner; }
    void bind(Inventory value) {
        if (inventory != null || value.getHolder() != this) throw new IllegalStateException("Invalid Player Menu binding");
        inventory = value;
    }
    boolean boundTo(Inventory value) { return inventory != null && inventory == value; }
    void rendered(Set<PlayerMenu.Action> value) { rendered = Set.copyOf(value); }
    boolean shows(PlayerMenu.Action action) { return rendered.contains(action); }
    @Override public Inventory getInventory() { return Objects.requireNonNull(inventory, "Unbound Player Menu"); }
}
