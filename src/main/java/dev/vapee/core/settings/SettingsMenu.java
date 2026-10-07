package dev.vapee.core.settings;

import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.settings.command.SettingsCommand;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerSettingsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SettingsMenu {

    // Layout (zero-based slots): feature icons 10/12/14/16, matching status 19/21/23/25.
    // Lower features 31/33, status 40/42; footer close 49, refresh 52.
    // SettingsMenuEntries owns each tile and its paired action; listener resolves the same entries.
    public static final int INVENTORY_SIZE = 54;
    public static final int SCOREBOARD_SLOT = 10;
    public static final int SOUNDS_SLOT = 12;
    public static final int PRIVATE_MESSAGES_SLOT = 14;
    public static final int FRIEND_REQUESTS_SLOT = 16;
    public static final int SCOREBOARD_STATUS_SLOT = 19;
    public static final int SOUNDS_STATUS_SLOT = 21;
    public static final int PRIVATE_MESSAGES_STATUS_SLOT = 23;
    public static final int FRIEND_REQUESTS_STATUS_SLOT = 25;
    public static final int VISIBILITY_SLOT = 31;
    public static final int VISIBILITY_STATUS_SLOT = 40;
    public static final int FRIEND_PRESENCE_SLOT = 33;
    public static final int FRIEND_PRESENCE_STATUS_SLOT = 42;
    public static final int CLOSE_SLOT = 49;
    public static final int REFRESH_SLOT = 52;

    private static final Component TITLE = Component.text("Player Settings", NamedTextColor.DARK_GRAY);

    private final List<SettingsMenuEntry> entries;
    private final PlayerSettingsService playerSettingsService;
    private final MessageService messageService;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private final Function<UUID, Player> onlinePlayer;
    private final Logger logger;
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public SettingsMenu(
            JavaPlugin plugin,
            PlayerSettingsService playerSettingsService,
            MessageService messageService
    ) {
        this.entries = validateEntries(SettingsMenuEntries.defaults());
        Objects.requireNonNull(plugin, "plugin");
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.inventoryFactory = (holder, size, title) -> plugin.getServer().createInventory(holder, size, title);
        this.itemRenderer = UiItems::render;
        this.logger = plugin.getLogger();
        this.onlinePlayer = plugin.getServer()::getPlayer;
    }

    SettingsMenu(PlayerSettingsService playerSettingsService, MessageService messageService,
                 Function<UUID, Player> onlinePlayer,
                 InventoryFactory inventoryFactory, ItemRenderer itemRenderer, Logger logger) {
        this(playerSettingsService, messageService, onlinePlayer, inventoryFactory, itemRenderer, logger, SettingsMenuEntries.defaults());
    }

    SettingsMenu(PlayerSettingsService playerSettingsService, MessageService messageService,
                 Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                 ItemRenderer itemRenderer, Logger logger, List<SettingsMenuEntry> entries) {
        this.entries = validateEntries(entries);
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
    }

    public boolean open(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!validatedPlayer.hasPermission(SettingsCommand.PERMISSION)) {
            messageService.send(validatedPlayer, "<red>You do not have permission to use settings.</red>");
            return false;
        }
        UUID uniqueId = validatedPlayer.getUniqueId();
        if (playerSettingsService.getSettings(uniqueId).isEmpty()) {
            validatedPlayer.closeInventory();
            messageService.send(validatedPlayer, "<red>Your player profile is not available.</red>");
            return false;
        }

        SettingsInventoryHolder holder = new SettingsInventoryHolder(uniqueId);
        Inventory inventory = inventoryFactory.create(holder, INVENTORY_SIZE, TITLE);
        holder.bindInventory(inventory);
        if (!refresh(validatedPlayer, inventory)) {
            validatedPlayer.closeInventory();
            messageService.send(validatedPlayer, "<red>Your player profile is not available.</red>");
            return false;
        }
        var opened = validatedPlayer.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory) {
            activeInventories.put(uniqueId, inventory);
            return true;
        }
        return false;
    }

    public boolean isActive(Player viewer, Inventory inventory, SettingsInventoryHolder holder) {
        return viewer != null && inventory != null && holder != null
                && holder.getOwnerUniqueId().equals(viewer.getUniqueId())
                && inventory.getHolder() == holder && holder.isBoundTo(inventory)
                && activeInventories.get(viewer.getUniqueId()) == inventory
                && viewer.getOpenInventory().getTopInventory() == inventory;
    }

    void forgetIfActive(UUID owner, Inventory inventory) {
        activeInventories.remove(owner, inventory);
    }

    void forget(UUID owner) {
        activeInventories.remove(owner);
    }

    public void closeOpenInventories() {
        try {
            for (Map.Entry<UUID, Inventory> entry : List.copyOf(activeInventories.entrySet())) {
                try {
                    Player player = onlinePlayer.apply(entry.getKey());
                    if (player != null && player.isOnline()
                            && player.getOpenInventory().getTopInventory() == entry.getValue()) {
                        player.closeInventory();
                    }
                } catch (RuntimeException exception) {
                    logger.log(Level.WARNING, "Could not close SettingsMenu for " + entry.getKey() + ".", exception);
                }
            }
        } finally {
            activeInventories.clear();
        }
    }

    int activeCount() { return activeInventories.size(); }

    public boolean refresh(Player player, Inventory inventory) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        Inventory validatedInventory = Objects.requireNonNull(inventory, "inventory");
        if (!(validatedInventory.getHolder() instanceof SettingsInventoryHolder holder)
                || !holder.getOwnerUniqueId().equals(validatedPlayer.getUniqueId())
                || !holder.isBoundTo(validatedInventory)) {
            throw new IllegalArgumentException("Inventory is not this player's settings menu");
        }

        Optional<PlayerSettings> optionalSettings = playerSettingsService.getSettings(validatedPlayer.getUniqueId());
        if (optionalSettings.isEmpty()) {
            return false;
        }

        PlayerSettings settings = optionalSettings.get();
        for (var entry : entries) {
            validatedInventory.setItem(entry.slot(), itemRenderer.render(entry.icon().apply(settings)));
            if (entry.statusSlot() >= 0) validatedInventory.setItem(entry.statusSlot(), itemRenderer.render(entry.status().apply(settings)));
        }
        validatedInventory.setItem(CLOSE_SLOT, createCloseItem());
        validatedInventory.setItem(REFRESH_SLOT, itemRenderer.render(new UiItemSpec(
                Material.CLOCK, UiItems.text("Refresh", NamedTextColor.AQUA),
                List.of(UiItems.text("Reload your current settings.", NamedTextColor.GRAY)))));
        return true;
    }

    public Optional<SettingsMenuEntry> entryAt(int slot) {
        return entries.stream().filter(entry -> entry.matches(slot)).findFirst();
    }

    private static List<SettingsMenuEntry> validateEntries(List<SettingsMenuEntry> entries) {
        var slots = new java.util.HashSet<Integer>(List.of(CLOSE_SLOT, REFRESH_SLOT));
        for (var entry : entries) {
            if (!slots.add(entry.slot()) || entry.statusSlot() >= 0 && !slots.add(entry.statusSlot())) {
                throw new IllegalArgumentException("Duplicate/reserved Settings tile slot");
            }
        }
        return List.copyOf(entries);
    }

    private ItemStack createCloseItem() {
        return itemRenderer.render(new UiItemSpec(
                Material.BARRIER,
                UiItems.text("Close", NamedTextColor.RED),
                List.of()
        ));
    }

    @FunctionalInterface
    interface InventoryFactory {
        Inventory create(SettingsInventoryHolder holder, int size, Component title);
    }

    @FunctionalInterface
    interface ItemRenderer {
        ItemStack render(UiItemSpec spec);
    }
}
