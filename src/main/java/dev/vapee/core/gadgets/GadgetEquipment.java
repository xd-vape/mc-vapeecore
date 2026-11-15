package dev.vapee.core.gadgets;

import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.IntSupplier;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Temporary ITEM gadgets only. Direct actions and submenus do not acquire a slot. */
final class GadgetEquipment {
    private static final String OWNER = "gadget-equipment";
    private static final List<Integer> HOTBAR_ORDER = List.of(2, 3, 5, 6, 7, 0, 1, 4, 8);

    enum Result { EQUIPPED, AT_LIMIT, NO_SLOT }

    private record Equipped(int slot, String token, UiItemSpec item) { }

    private final LobbyItemService items;
    private final MessageService messages;
    private final IntSupplier limit;
    private final NamespacedKey idKey;
    private final NamespacedKey sessionKey;
    private final NamespacedKey legacyRidingKey;
    private final Map<UUID, Map<String, Equipped>> equipped = new HashMap<>();

    GadgetEquipment(JavaPlugin plugin, LobbyItemService items, MessageService messages, IntSupplier limit) {
        this.items = items;
        this.messages = messages;
        this.limit = limit;
        idKey = new NamespacedKey(plugin, "gadget_id");
        sessionKey = new NamespacedKey(plugin, "gadget_session");
        legacyRidingKey = new NamespacedKey(plugin, "riding_session");
    }

    int limit() { return limit.getAsInt(); }
    int count(Player player) { return entries(player).size(); }
    boolean equipped(Player player, String id) { return entries(player).containsKey(id); }

    Result equip(Player player, String id, UiItemSpec item) {
        if (equipped(player, id)) return Result.EQUIPPED;
        if (count(player) >= limit()) return Result.AT_LIMIT;
        var inventory = player.getInventory();
        var reserved = items.reservedHotbarSlots();
        for (int slot : HOTBAR_ORDER) {
            if (reserved.contains(slot) || !empty(inventory.getItem(slot))
                    || entries(player).values().stream().anyMatch(entry -> entry.slot() == slot)) continue;
            var entry = new Equipped(slot, UUID.randomUUID().toString(), item);
            inventory.setItem(slot, render(player, id, entry));
            equipped.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>()).put(id, entry);
            return Result.EQUIPPED;
        }
        return Result.NO_SLOT;
    }

    boolean holds(Player player, String id) {
        var entry = entries(player).get(id);
        var inventory = player.getInventory();
        return entry != null && entry.slot() == inventory.getHeldItemSlot()
                && matches(inventory.getItemInMainHand(), id, entry);
    }

    boolean present(Player player, String id) {
        var entry = entries(player).get(id);
        return entry != null && matches(player.getInventory().getItem(entry.slot()), id, entry);
    }

    /** Called after the shared lobby refresh has removed old managed items and filled configured slots. */
    boolean restoreAfterRefresh(Player player, String id) {
        var entry = entries(player).get(id);
        if (entry == null || items.reservedHotbarSlots().contains(entry.slot())) return false;
        var inventory = player.getInventory();
        if (!empty(inventory.getItem(entry.slot()))) return present(player, id);
        inventory.setItem(entry.slot(), render(player, id, entry));
        return true;
    }

    void remove(Player player, String id) {
        UUID owner = player.getUniqueId();
        var entries = equipped.get(owner);
        if (entries == null) return;
        var entry = entries.remove(id);
        if (entries.isEmpty()) equipped.remove(owner);
        if (entry != null) removeMatching(player.getInventory(), item -> owns(item, id, entry));
    }

    List<UUID> owners() { return List.copyOf(equipped.keySet()); }

    void forget(UUID owner) { equipped.remove(owner); }

    void clear(Player player) {
        for (String id : List.copyOf(entries(player).keySet())) remove(player, id);
    }

    boolean isTemporary(ItemStack item) {
        return items.hasManagedMarker(item, OWNER) || (items.hasManagedMarker(item, PlayerRiding.ITEM)
                && item.getItemMeta().getPersistentDataContainer().has(legacyRidingKey, PersistentDataType.STRING));
    }

    void clearOrphans(Player player) {
        removeMatching(player.getInventory(), this::isTemporary);
    }

    private static void removeMatching(PlayerInventory inventory, java.util.function.Predicate<ItemStack> match) {
        var contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (match.test(contents[slot])) inventory.setItem(slot, null);
        }
    }

    private Map<String, Equipped> entries(Player player) {
        return equipped.getOrDefault(player.getUniqueId(), Map.of());
    }

    private boolean matches(ItemStack item, String id, Equipped entry) {
        return owns(item, id, entry) && item.getType() == entry.item().material() && item.getAmount() == 1;
    }

    private boolean owns(ItemStack item, String id, Equipped entry) {
        if (!items.hasManagedMarker(item, OWNER)) return false;
        var data = item.getItemMeta().getPersistentDataContainer();
        return id.equals(data.get(idKey, PersistentDataType.STRING))
                && entry.token().equals(data.get(sessionKey, PersistentDataType.STRING));
    }

    private ItemStack render(Player player, String id, Equipped entry) {
        ItemStack item = UiItems.render(UiItems.localize(entry.item(), messages, player));
        var meta = item.getItemMeta();
        items.markTemporaryItem(meta, OWNER);
        var data = meta.getPersistentDataContainer();
        data.set(idKey, PersistentDataType.STRING, id);
        data.set(sessionKey, PersistentDataType.STRING, entry.token());
        item.setItemMeta(meta);
        return item;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
