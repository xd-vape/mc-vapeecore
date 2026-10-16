package dev.vapee.core.lobby.experience.glide;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Disposable glide wings only: an occupied chest is never replaced or escrowed. */
final class LobbyGlideEquipment {
    private record Tag(UUID owner, UUID token) { }
    private record Lease(long generation, UUID token) { }
    private final NamespacedKey key;
    private final Supplier<ItemStack> newItem;
    private final Logger logger;
    private final Map<UUID, Lease> leases = new HashMap<>();

    LobbyGlideEquipment(JavaPlugin plugin) {
        this(new NamespacedKey(plugin, "glide_elytra"), () -> new ItemStack(Material.ELYTRA), plugin.getLogger());
    }
    LobbyGlideEquipment(NamespacedKey key, Supplier<ItemStack> newItem, Logger logger) {
        this.key = Objects.requireNonNull(key);
        this.newItem = Objects.requireNonNull(newItem);
        this.logger = Objects.requireNonNull(logger);
    }
    int leaseCount() { return leases.size(); }

    void equip(Player player, long generation) {
        UUID id = player.getUniqueId();
        if (leases.containsKey(id)) return;
        try {
            var inventory = player.getInventory();
            ItemStack chest = inventory.getChestplate();
            if (chest != null && !chest.isEmpty()) return;
            var lease = new Lease(generation, UUID.randomUUID());
            ItemStack item = Objects.requireNonNull(newItem.get(), "glide Elytra");
            if (item.getType() != Material.ELYTRA || item.isEmpty()) throw new IllegalStateException("Invalid glide Elytra factory");
            var meta = Objects.requireNonNull(item.getItemMeta(), "glide Elytra metadata");
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id + ":" + lease.token());
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            if (!item.setItemMeta(meta)) throw new IllegalStateException("Glide Elytra metadata rejected");
            // Record before the setter: a partial Bukkit failure must still be eligible for exact cleanup.
            leases.put(id, lease);
            inventory.setChestplate(item);
            if (!matches(inventory.getChestplate(), id, lease)) release(player, generation);
        } catch (RuntimeException failure) {
            warn(id, "equip", failure);
            release(player, generation);
        }
    }
    void release(Player player, long generation) {
        UUID id = player.getUniqueId();
        Lease lease = leases.get(id);
        if (lease == null || lease.generation() != generation) return;
        leases.remove(id, lease);
        removeMatching(player, tag -> tag.owner().equals(id) && tag.token().equals(lease.token()));
    }
    /** Join/enable recovery for disposable markers; real armor and any current lease stay untouched. */
    void cleanupOrphans(Player player) {
        UUID id = player.getUniqueId();
        Lease current = leases.get(id);
        removeMatching(player, tag -> current == null || !tag.owner().equals(id) || !tag.token().equals(current.token()));
    }
    boolean isManaged(ItemStack item) { return tag(item) != null; }
    private boolean matches(ItemStack item, UUID id, Lease lease) {
        Tag tag = tag(item);
        return tag != null && tag.owner().equals(id) && tag.token().equals(lease.token());
    }
    private Tag tag(ItemStack item) {
        if (item == null || item.getType() != Material.ELYTRA || !item.hasItemMeta()) return null;
        var data = item.getItemMeta().getPersistentDataContainer();
        if (!data.has(key, PersistentDataType.STRING)) return null;
        String value = data.get(key, PersistentDataType.STRING);
        if (value == null || value.length() != 73 || value.charAt(36) != ':') return null;
        try {
            UUID owner = UUID.fromString(value.substring(0, 36)), token = UUID.fromString(value.substring(37));
            if (!value.equals(owner + ":" + token)) return null;
            return new Tag(owner, token);
        } catch (IllegalArgumentException malformed) { return null; }
    }
    private void removeMatching(Player player, Predicate<Tag> remove) {
        UUID id = player.getUniqueId();
        try {
            var inventory = player.getInventory();
            ItemStack[] contents = inventory.getContents();
            // getContents/setItem use the same PlayerInventory mapping, including armor/offhand.
            for (int slot = 0; slot < contents.length; slot++) {
                try {
                    Tag tag = tag(contents[slot]);
                    if (tag != null && remove.test(tag)) inventory.setItem(slot, null);
                } catch (RuntimeException failure) { warn(id, "remove slot " + slot, failure); }
            }
        } catch (RuntimeException failure) { warn(id, "read inventory", failure); }
        try {
            Tag cursor = tag(player.getItemOnCursor());
            if (cursor != null && remove.test(cursor)) player.setItemOnCursor(null);
        } catch (RuntimeException failure) { warn(id, "remove cursor", failure); }
    }
    void shutdown(Function<UUID, Player> online) {
        try {
            for (UUID id : List.copyOf(leases.keySet())) {
                try {
                    Player player = online.apply(id);
                    if (player != null) release(player, leases.get(id).generation());
                } catch (RuntimeException failure) { warn(id, "shutdown", failure); }
            }
        } finally { leases.clear(); }
    }
    private void warn(UUID id, String action, RuntimeException failure) {
        logger.log(Level.WARNING, "Could not " + action + " temporary lobby glide Elytra for " + id, failure);
    }
}
