package dev.vapee.core.lobby.experience;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
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

    public LobbyItemService(
            JavaPlugin plugin,
            LobbyService lobbyService,
            PlayerSettingsService playerSettingsService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
        this.lobbyItemKey = new NamespacedKey(plugin, "lobby_item");
    }

    public void applyLobbyItems(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!lobbyService.isLobbyWorld(validatedPlayer.getWorld())) {
            return;
        }

        removeManagedItems(validatedPlayer);
        placeItem(validatedPlayer, NAVIGATOR_SLOT, createNavigatorItem());
        placeItem(validatedPlayer, VISIBILITY_SLOT, createVisibilityItem(validatedPlayer));
        placeItem(validatedPlayer, SETTINGS_SLOT, createSettingsItem());
    }

    public void refreshVisibilityItem(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!lobbyService.isLobbyWorld(validatedPlayer.getWorld())) {
            return;
        }

        ItemStack currentItem = validatedPlayer.getInventory().getItem(VISIBILITY_SLOT);
        if (isManagedItem(currentItem) || isEmpty(currentItem)) {
            validatedPlayer.getInventory().setItem(VISIBILITY_SLOT, createVisibilityItem(validatedPlayer));
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

    private void placeItem(Player player, int targetSlot, ItemStack lobbyItem) {
        PlayerInventory inventory = player.getInventory();
        ItemStack existingItem = inventory.getItem(targetSlot);
        if (isEmpty(existingItem) || isManagedItem(existingItem)) {
            inventory.setItem(targetSlot, lobbyItem);
            return;
        }

        int freeSlot = findFreeStorageSlot(inventory);
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

    private int findFreeStorageSlot(PlayerInventory inventory) {
        ItemStack[] storageContents = inventory.getStorageContents();
        for (int slot = 0; slot < storageContents.length; slot++) {
            if (!isReservedSlot(slot) && isEmpty(storageContents[slot])) {
                return slot;
            }
        }
        return -1;
    }

    private boolean isReservedSlot(int slot) {
        return slot == NAVIGATOR_SLOT || slot == VISIBILITY_SLOT || slot == SETTINGS_SLOT;
    }

    private ItemStack createNavigatorItem() {
        return createItem(
                Material.COMPASS,
                "Navigator",
                NamedTextColor.AQUA,
                List.of("Right-click to open the navigator."),
                LobbyItemType.NAVIGATOR
        );
    }

    private ItemStack createVisibilityItem(Player player) {
        boolean visible = playerSettingsService.areLobbyPlayersVisible(player.getUniqueId()).orElse(true);
        return createItem(
                visible ? Material.LIME_DYE : Material.GRAY_DYE,
                visible ? "Players: Visible" : "Players: Hidden",
                visible ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                List.of(visible
                        ? "Right-click to hide lobby players."
                        : "Right-click to show lobby players."),
                LobbyItemType.VISIBILITY
        );
    }

    private ItemStack createSettingsItem() {
        return createItem(
                Material.COMPARATOR,
                "Settings",
                NamedTextColor.YELLOW,
                List.of("Right-click to open your settings."),
                LobbyItemType.SETTINGS
        );
    }

    private ItemStack createItem(
            Material material,
            String name,
            NamedTextColor nameColor,
            List<String> loreLines,
            LobbyItemType type
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(uiText(name, nameColor));
        meta.lore(loreLines.stream()
                .map(line -> uiText(line, NamedTextColor.GRAY))
                .toList()
        );
        meta.getPersistentDataContainer().set(
                lobbyItemKey,
                PersistentDataType.STRING,
                type.getPersistentId()
        );
        item.setItemMeta(meta);
        return item;
    }

    private Component uiText(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir();
    }
}
