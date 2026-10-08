package dev.vapee.core.settings.visibility;

import dev.vapee.core.ui.MenuLayout;
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
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VisibilitySettingsMenu {
    public enum Action { MASTER, FRIENDS, STAFF, ADDED_USERS, GAME_PARTICIPANTS, MANAGE_PLAYERS, BACK, CLOSE, REFRESH }
    public static final MenuLayout<Action> DEFAULT_LAYOUT = MenuLayout.builder(54, Action.class)
            .setting(Action.MASTER, 9, 18)
            .setting(Action.FRIENDS, 11, 20)
            .setting(Action.STAFF, 13, 22)
            .setting(Action.ADDED_USERS, 15, 24)
            .setting(Action.GAME_PARTICIPANTS, 17, 26)
            .button(Action.MANAGE_PLAYERS, 31)
            .button(Action.BACK, 45)
            .button(Action.CLOSE, 49)
            .button(Action.REFRESH, 52)
            .build();

    private static final Component TITLE = UiItems.text("Visibility Settings", NamedTextColor.DARK_GRAY);

    private final MenuLayout<Action> layout;
    public MenuLayout<Action> layout() { return layout; }

    private final PlayerSettingsService settings;
    private final MessageService messages;
    private final Function<UUID, Player> onlinePlayer;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private final Logger logger;
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public VisibilitySettingsMenu(JavaPlugin plugin, PlayerSettingsService settings, MessageService messages) {
        this(settings, messages,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                UiItems::render, plugin.getLogger());
    }

    VisibilitySettingsMenu(PlayerSettingsService settings, MessageService messages,
                           Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                           ItemRenderer itemRenderer, Logger logger) {
        this(settings, messages, onlinePlayer, inventoryFactory, itemRenderer, logger, DEFAULT_LAYOUT);
    }

    VisibilitySettingsMenu(PlayerSettingsService settings, MessageService messages,
                           Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                           ItemRenderer itemRenderer, Logger logger, MenuLayout<Action> menuLayout) {
        this.layout = Objects.requireNonNull(menuLayout, "layout").validateActions(java.util.Set.of(Action.ADDED_USERS, Action.FRIENDS, Action.GAME_PARTICIPANTS, Action.MASTER, Action.STAFF));
        this.layout.requireFixed();
        this.settings = Objects.requireNonNull(settings, "settings");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void open(Player player) {
        Player viewer = Objects.requireNonNull(player, "player");
        if (!viewer.hasPermission(SettingsCommand.PERMISSION)) {
            messages.send(viewer, "<red>You do not have permission to use settings.</red>");
            return;
        }
        PlayerSettings current = settings.getSettings(viewer.getUniqueId()).orElse(null);
        if (current == null) {
            messages.send(viewer, "<red>Your player profile is not available.</red>");
            return;
        }
        VisibilitySettingsHolder holder = new VisibilitySettingsHolder(viewer.getUniqueId());
        Inventory inventory = inventoryFactory.create(holder, layout.size(), TITLE);
        holder.bindInventory(inventory);
        render(inventory, current);
        var opened = viewer.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory) {
            activeInventories.put(viewer.getUniqueId(), inventory);
        }
    }

    public boolean isActive(Player viewer, Inventory inventory, VisibilitySettingsHolder holder) {
        return viewer != null && inventory != null && holder != null
                && holder.getOwnerUniqueId().equals(viewer.getUniqueId())
                && inventory.getHolder() == holder && holder.isBoundTo(inventory)
                && activeInventories.get(viewer.getUniqueId()) == inventory
                && viewer.getOpenInventory().getTopInventory() == inventory;
    }

    void forgetIfActive(UUID owner, Inventory inventory) {
        activeInventories.remove(owner, inventory);
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
                    logger.log(Level.WARNING, "Could not close VisibilitySettingsMenu for " + entry.getKey() + ".", exception);
                }
            }
        } finally {
            activeInventories.clear();
        }
    }

    int activeCount() { return activeInventories.size(); }

    private void render(Inventory inventory, PlayerSettings current) {
        var visibility = current.getVisibility();
        place(inventory, Action.MASTER, item(Material.ENDER_EYE, "All Players", NamedTextColor.AQUA,
                List.of(
                        visibility.isAllPlayersVisible()
                                ? "All lobby players are visible unless privacy rules block them."
                                : "Only enabled visibility groups are shown.",
                        "Current: " + (visibility.isAllPlayersVisible() ? "ON" : "FILTERED"),
                        "Click to change."
                )));
        place(inventory, Action.MASTER, 1, item(
                visibility.isAllPlayersVisible() ? Material.LIME_STAINED_GLASS_PANE
                        : Material.YELLOW_STAINED_GLASS_PANE,
                visibility.isAllPlayersVisible() ? "All Players" : "Filtered",
                visibility.isAllPlayersVisible() ? NamedTextColor.GREEN : NamedTextColor.YELLOW,
                List.of("Click to change.")));

        place(inventory, Action.FRIENDS, filterItem(Material.PLAYER_HEAD, "Friends",
                visibility.isShowFriends(), List.of("Show confirmed friends while visibility is filtered.")));
        place(inventory, Action.FRIENDS, 1, status(
                visibility.isShowFriends() ? "Enabled" : "Disabled", visibility.isShowFriends()));

        place(inventory, Action.STAFF, filterItem(Material.GOLDEN_HELMET, "Staff Members",
                visibility.isShowStaff(), List.of("Show server staff while visibility is filtered.")));
        place(inventory, Action.STAFF, 1, status(
                visibility.isShowStaff() ? "Enabled" : "Disabled", visibility.isShowStaff()));

        place(inventory, Action.ADDED_USERS, filterItem(Material.WRITABLE_BOOK, "Added Users",
                visibility.isShowAddedUsers(), List.of(
                        "Show players from your personal visible players list.",
                        "Configured users: " + visibility.getAddedPlayers().size()
                )));
        place(inventory, Action.ADDED_USERS, 1, status(
                visibility.isShowAddedUsers() ? "Enabled" : "Disabled", visibility.isShowAddedUsers()));

        place(inventory, Action.GAME_PARTICIPANTS, item(Material.NETHER_STAR, "Game Participants",
                NamedTextColor.GRAY, List.of(
                        "Unavailable until game/activity integration is completed.",
                        "Current: UNAVAILABLE"
                )));
        place(inventory, Action.GAME_PARTICIPANTS, 1, item(Material.GRAY_STAINED_GLASS_PANE,
                "Unavailable", NamedTextColor.GRAY, List.of("Game integration is not available yet.")));

        place(inventory, Action.MANAGE_PLAYERS, item(Material.CHEST, "Manage Visible Players",
                NamedTextColor.GREEN, List.of(
                        "Add or remove players from your visible players list.",
                        "Configured users: " + visibility.getAddedPlayers().size(),
                        "Click to open."
                )));
        place(inventory, Action.BACK, item(Material.ARROW, "Back", NamedTextColor.YELLOW,
                List.of("Return to Player Settings.")));
        place(inventory, Action.CLOSE, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of()));
        place(inventory, Action.REFRESH, item(Material.CLOCK, "Refresh", NamedTextColor.AQUA,
                List.of("Reload your current visibility settings.")));
    }

    private ItemStack filterItem(Material material, String name, boolean enabled, List<String> description) {
        java.util.ArrayList<String> lore = new java.util.ArrayList<>(description);
        lore.add("Current: " + (enabled ? "ON" : "OFF"));
        lore.add("Used while All Players is filtered.");
        lore.add("Click to " + (enabled ? "disable." : "enable."));
        return item(material, name, NamedTextColor.AQUA, lore);
    }

    private ItemStack status(String name, boolean enabled) {
        return item(enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                name, enabled ? NamedTextColor.GREEN : NamedTextColor.RED, List.of("Click to change."));
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        return itemRenderer.render(UiItems.literal(material, name, color, lore));
    }

    @FunctionalInterface
    interface InventoryFactory {
        Inventory create(VisibilitySettingsHolder holder, int size, Component title);
    }

    @FunctionalInterface
    interface ItemRenderer {
        ItemStack render(UiItemSpec spec);
    }
    private void place(Inventory inventory, Action action, ItemStack item) {
        place(inventory, action, 0, item);
    }
    private void place(Inventory inventory, Action action, int position, ItemStack item) {
        layout.actionSlot(action, position).ifPresent(slot -> inventory.setItem(slot, item));
    }

}
