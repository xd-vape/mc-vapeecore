package dev.vapee.core.lobby.item;

import dev.vapee.core.lobby.LobbyService;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.Objects;
import java.util.Optional;

public final class LobbyItemService {

    public static final int NAVIGATOR_SLOT = 0;
    public static final int VISIBILITY_SLOT = 4;
    public static final int SETTINGS_SLOT = 8;

    private final JavaPlugin plugin;
    private final LobbyService lobbyService;
    private final LobbyItemRegistry registry;
    private final NamespacedKey lobbyItemKey;
    private final Supplier<Map<String, LobbyItemDefinition>> definitions;

    public LobbyItemService(JavaPlugin plugin, LobbyService lobbyService, LobbyItemRegistry registry,
                            Supplier<Map<String, LobbyItemDefinition>> definitions) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.lobbyItemKey = new NamespacedKey(plugin, "lobby_item");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    public void applyLobbyItems(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!lobbyService.isLobbyWorld(validatedPlayer.getWorld())) {
            return;
        }

        removeManagedItems(validatedPlayer);
        Map<String, LobbyItemDefinition> current = definitions.get();
        Set<Integer> reserved = current.values().stream().filter(LobbyItemDefinition::enabled)
                .map(LobbyItemDefinition::slot).collect(Collectors.toSet());
        for (var entry : registry.entries().values()) {
            var definition = current.get(entry.id());
            if (definition != null && definition.enabled()) {
                placeItem(validatedPlayer, definition.slot(), createItem(validatedPlayer, entry, definition), reserved);
            }
        }
    }

    public void refreshVisibilityItem(Player player) { refreshItem(player, "visibility"); }

    public void refreshItem(Player player, String id) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!lobbyService.isLobbyWorld(validatedPlayer.getWorld())) return;
        var entry = registry.resolve(id).orElse(null);
        var definition = definitions.get().get(id);
        if (entry == null || definition == null || !definition.enabled()) return;
        ItemStack currentItem = validatedPlayer.getInventory().getItem(definition.slot());
        if (getItemId(currentItem).filter(id::equals).isPresent() || isEmpty(currentItem)) {
            validatedPlayer.getInventory().setItem(definition.slot(), createItem(validatedPlayer, entry, definition));
        }
    }

    public boolean isEnabled(String id) {
        var definition = definitions.get().get(id);
        return registry.resolve(id).isPresent() && definition != null && definition.enabled();
    }

    public void removeManagedItems(Player player) {
        PlayerInventory inventory = Objects.requireNonNull(player, "player").getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isManagedItem(contents[slot])) {
                inventory.setItem(slot, null);
            }
        }
    }

    public boolean isManagedItem(ItemStack item) {
        if (isEmpty(item)) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .has(lobbyItemKey, PersistentDataType.STRING);
    }

    public Optional<String> getItemId(ItemStack item) {
        if (!isManagedItem(item)) {
            return Optional.empty();
        }
        String persistentId = item.getItemMeta().getPersistentDataContainer()
                .get(lobbyItemKey, PersistentDataType.STRING);
        return persistentId == null
                ? Optional.empty()
                : registry.resolve(persistentId).map(LobbyItemRegistry.Entry::id);
    }

    private void placeItem(Player player, int targetSlot, ItemStack lobbyItem, Set<Integer> reserved) {
        PlayerInventory inventory = player.getInventory();
        ItemStack existingItem = inventory.getItem(targetSlot);
        if (isEmpty(existingItem) || isManagedItem(existingItem)) {
            inventory.setItem(targetSlot, lobbyItem);
            return;
        }

        int freeSlot = findFreeStorageSlot(inventory, reserved);
        if (freeSlot < 0) {
            plugin.getLogger().warning("Could not place lobby item for " + player.getUniqueId()
                    + " in reserved slot " + targetSlot
                    + ": no safe non-reserved storage slot is available; the existing item was preserved."
            );
            return;
        }

        inventory.setItem(freeSlot, existingItem);
        inventory.setItem(targetSlot, lobbyItem);
    }

    private int findFreeStorageSlot(PlayerInventory inventory, Set<Integer> reserved) {
        ItemStack[] storageContents = inventory.getStorageContents();
        for (int slot = 0; slot < storageContents.length; slot++) {
            if (!reserved.contains(slot) && isEmpty(storageContents[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private ItemStack createItem(Player player, LobbyItemRegistry.Entry entry, LobbyItemDefinition definition) {
        var appearance = Objects.requireNonNull(entry.presentation().apply(player, definition));
        ItemStack item = new ItemStack(appearance.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(appearance.name());
        meta.lore(appearance.lore());
        if (appearance.selfHead() && meta instanceof SkullMeta skull) {
            // Uses the already available online-player profile; never completes/fetches profiles or textures.
            skull.setPlayerProfile(player.getPlayerProfile());
        }
        meta.getPersistentDataContainer().set(
                lobbyItemKey,
                PersistentDataType.STRING,
                entry.id()
        );
        item.setItemMeta(meta);
        return item;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
