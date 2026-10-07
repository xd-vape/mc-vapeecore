package dev.vapee.core.lobby.item;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.config.LobbyItemsConfig;
import dev.vapee.core.player.settings.PlayerSettingsService;
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
    private final PlayerSettingsService playerSettingsService;
    private final NamespacedKey lobbyItemKey;
    private final Supplier<Map<LobbyItemType, LobbyItemDefinition>> definitions;

    public LobbyItemService(
            JavaPlugin plugin,
            LobbyService lobbyService,
            PlayerSettingsService playerSettingsService
    ) {
        this(plugin, lobbyService, playerSettingsService, LobbyItemsConfig::defaults);
    }

    public LobbyItemService(JavaPlugin plugin, LobbyService lobbyService,
                            PlayerSettingsService playerSettingsService,
                            Supplier<Map<LobbyItemType, LobbyItemDefinition>> definitions) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
        this.lobbyItemKey = new NamespacedKey(plugin, "lobby_item");
        this.definitions = Objects.requireNonNull(definitions, "definitions");
    }

    public void applyLobbyItems(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!lobbyService.isLobbyWorld(validatedPlayer.getWorld())) {
            return;
        }

        removeManagedItems(validatedPlayer);
        Map<LobbyItemType, LobbyItemDefinition> current = definitions.get();
        Set<Integer> reserved = current.values().stream().filter(LobbyItemDefinition::enabled)
                .map(LobbyItemDefinition::slot).collect(Collectors.toSet());
        for (LobbyItemType type : LobbyItemType.values()) {
            var definition = current.get(type);
            if (definition.enabled()) {
                placeItem(validatedPlayer, definition.slot(), createItem(validatedPlayer, type, definition), reserved);
            }
        }
    }

    public void refreshVisibilityItem(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!lobbyService.isLobbyWorld(validatedPlayer.getWorld())) {
            return;
        }

        var definition = definitions.get().get(LobbyItemType.VISIBILITY);
        if (!definition.enabled()) return;
        ItemStack currentItem = validatedPlayer.getInventory().getItem(definition.slot());
        if (getItemType(currentItem).orElse(null) == LobbyItemType.VISIBILITY || isEmpty(currentItem)) {
            validatedPlayer.getInventory().setItem(definition.slot(), createItem(validatedPlayer, LobbyItemType.VISIBILITY, definition));
        }
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

    public Optional<LobbyItemType> getItemType(ItemStack item) {
        if (!isManagedItem(item)) {
            return Optional.empty();
        }
        String persistentId = item.getItemMeta().getPersistentDataContainer()
                .get(lobbyItemKey, PersistentDataType.STRING);
        return persistentId == null
                ? Optional.empty()
                : LobbyItemType.fromPersistentId(persistentId);
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

    private ItemStack createItem(Player player, LobbyItemType type, LobbyItemDefinition definition) {
        boolean filtered = type == LobbyItemType.VISIBILITY
                && !playerSettingsService.areLobbyPlayersVisible(player.getUniqueId()).orElse(true);
        var appearance = filtered ? definition.filteredAppearance() : definition.appearance();
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
                type.getPersistentId()
        );
        item.setItemMeta(meta);
        return item;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
