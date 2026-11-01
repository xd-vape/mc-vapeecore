package dev.vapee.core.staff;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/** UUID and exact inventory binding; item names, profiles and PDC never authorize actions. */
public final class StaffPanelHolder implements InventoryHolder {
    public enum View { STAFF, PLAYERS }

    record Content(View view, int page, Player selected, Map<Integer, Player> targets,
                   Map<Integer, StaffPanel.Action> actions) {
        Content {
            Objects.requireNonNull(view);
            if (page < 0) throw new IllegalArgumentException("negative page");
            targets = Map.copyOf(targets);
            actions = Map.copyOf(actions);
        }
    }

    private final UUID viewerId;
    private Inventory inventory;
    private Content content;

    public StaffPanelHolder(UUID viewerId) { this.viewerId = Objects.requireNonNull(viewerId); }
    public UUID viewerId() { return viewerId; }
    Content content() { return content; }
    void content(Content next) { content = Objects.requireNonNull(next); }

    void bind(Inventory candidate) {
        Objects.requireNonNull(candidate);
        if (inventory != null || candidate.getHolder() != this) throw new IllegalArgumentException("Invalid staff panel binding");
        inventory = candidate;
    }
    boolean boundTo(Inventory candidate) { return inventory != null && inventory == candidate; }
    @Override public @NotNull Inventory getInventory() { return Objects.requireNonNull(inventory, "Unbound staff panel"); }
}
