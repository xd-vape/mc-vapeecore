package dev.vapee.core.settings.visibility;

import dev.vapee.core.player.settings.VisibilityMode;
import static dev.vapee.core.i18n.Translations.tr;

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
    private final dev.vapee.core.player.settings.PlayerSettingsChangeListener committedChanges = this::refreshActive;
    private boolean observingChanges;

    public enum Action { MASTER, FRIENDS, STAFF, ADDED_USERS, GAME_PARTICIPANTS, MANAGE_PLAYERS, BACK, CLOSE, REFRESH }
    public static final MenuLayout<Action> DEFAULT_LAYOUT = MenuLayout.builder(45, Action.class)
            .setting(Action.MASTER, 11, 20)
            .setting(Action.FRIENDS, 12, 21)
            .setting(Action.STAFF, 13, 22)
            .setting(Action.ADDED_USERS, 14, 23)
            .setting(Action.GAME_PARTICIPANTS, 15, 24)
            .button(Action.MANAGE_PLAYERS, 40)
            .button(Action.BACK, 36)
            .button(Action.CLOSE, 44)

            .build();

    private static final Component TITLE = UiItems.text(tr("visibility.title"), NamedTextColor.DARK_GRAY);

    private final MenuLayout<Action> layout;
    public MenuLayout<Action> layout() { return layout; }

    private final PlayerSettingsService settings;
    private final MessageService messages;
    private final Function<UUID, Player> onlinePlayer;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private Player renderingFor;
    private final java.util.function.Consumer<Player> localeChanges = viewer -> refreshActive(viewer.getUniqueId());
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
        Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.itemRenderer = spec -> itemRenderer.render(UiItems.localize(spec, this.messages, renderingFor));
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void open(Player player) {
        Player viewer = Objects.requireNonNull(player, "player");
        if (!viewer.hasPermission(SettingsCommand.PERMISSION)) {
            messages.send(viewer, tr("settings.denied").color(NamedTextColor.RED));
            return;
        }
        PlayerSettings current = settings.getSettings(viewer.getUniqueId()).orElse(null);
        if (current == null) {
            messages.send(viewer, tr("common.profile-unavailable").color(NamedTextColor.RED));
            return;
        }
        VisibilitySettingsHolder holder = new VisibilitySettingsHolder(viewer.getUniqueId());
        Inventory inventory = inventoryFactory.create(holder, layout.size(), messages.localize(viewer, TITLE));
        holder.bindInventory(inventory);
        render(viewer, inventory, current);
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

    private void render(Player viewer, Inventory inventory, PlayerSettings current) {
        Player previousViewer = renderingFor; renderingFor = viewer;
        try {
        var visibility = current.getVisibility();
        place(inventory, Action.MASTER, item(Material.ENDER_EYE, tr("settings.visibility.name"), NamedTextColor.AQUA,
                List.of(
                        visibility.getMode() == VisibilityMode.HIDDEN
                                ? tr("visibility.hidden-hint") : visibility.isAllPlayersVisible()
                                ? tr("visibility.visible-hint")
                                : tr("visibility.filtered-hint"),
                        tr("common.current", java.util.Map.of("state", tr("visibility.mode." + visibility.getMode().name()))),
                        tr("common.click-change")
                )));
        place(inventory, Action.MASTER, 1, item(
                switch (visibility.getMode()) {
                    case ALL -> Material.LIME_STAINED_GLASS_PANE;
                    case FILTERED -> Material.YELLOW_STAINED_GLASS_PANE;
                    case HIDDEN -> Material.RED_STAINED_GLASS_PANE;
                },
                tr("visibility.mode." + visibility.getMode().name()),
                switch (visibility.getMode()) {
                    case ALL -> NamedTextColor.GREEN;
                    case FILTERED -> NamedTextColor.YELLOW;
                    case HIDDEN -> NamedTextColor.RED;
                },
                List.of(tr("common.click-change"))));

        place(inventory, Action.FRIENDS, filterItem(Material.PLAYER_HEAD, tr("friends.title"),
                visibility.isShowFriends(), List.of(tr("visibility.friends-hint"))));
        place(inventory, Action.FRIENDS, 1, status(
                visibility.isShowFriends() ? tr("common.enabled") : tr("common.disabled"), visibility.isShowFriends()));

        place(inventory, Action.STAFF, filterItem(Material.GOLDEN_HELMET, tr("visibility.staff-name"),
                visibility.isShowStaff(), List.of(tr("visibility.staff-hint"))));
        place(inventory, Action.STAFF, 1, status(
                visibility.isShowStaff() ? tr("common.enabled") : tr("common.disabled"), visibility.isShowStaff()));

        place(inventory, Action.ADDED_USERS, filterItem(Material.WRITABLE_BOOK, tr("visibility.added-name"),
                visibility.isShowAddedUsers(), List.of(
                        tr("visibility.added-hint"),
                        tr("visibility.added-count", java.util.Map.of("count", Component.text(visibility.getAddedPlayers().size())))
                )));
        place(inventory, Action.ADDED_USERS, 1, status(
                visibility.isShowAddedUsers() ? tr("common.enabled") : tr("common.disabled"), visibility.isShowAddedUsers()));

        place(inventory, Action.GAME_PARTICIPANTS, item(Material.NETHER_STAR, tr("visibility.game-name"),
                NamedTextColor.GRAY, List.of(
                        tr("visibility.game-hint"),
                        tr("common.current", java.util.Map.of("state", tr("common.unavailable"))),
                        tr("visibility.game-unavailable")
                )));
        place(inventory, Action.GAME_PARTICIPANTS, 1, item(Material.GRAY_STAINED_GLASS_PANE,
                tr("common.unavailable"), NamedTextColor.GRAY, List.of(tr("visibility.game-unavailable"))));

        place(inventory, Action.MANAGE_PLAYERS, item(Material.CHEST, tr("visibility.manage-title"),
                NamedTextColor.GREEN, List.of(
                        tr("visibility.manage-hint"),
                        tr("visibility.added-count", java.util.Map.of("count", Component.text(visibility.getAddedPlayers().size()))),
                        tr("common.click-open")
                )));
        place(inventory, Action.BACK, item(Material.ARROW, tr("common.back"), NamedTextColor.YELLOW,
                List.of(tr("visibility.back-root"))));
        place(inventory, Action.CLOSE, item(Material.BARRIER, tr("common.close"), NamedTextColor.RED, List.of()));
        place(inventory, Action.REFRESH, item(Material.CLOCK, tr("common.refresh"), NamedTextColor.AQUA,
                List.of(tr("visibility.refresh-hint"))));
        } finally { renderingFor = previousViewer; }
    }

    private ItemStack filterItem(Material material, Component name, boolean enabled, List<Component> description) {
        java.util.ArrayList<Component> lore = new java.util.ArrayList<>(description);
        lore.add(tr("common.current", java.util.Map.of("state", enabled ? tr("common.enabled") : tr("common.disabled"))));
        lore.add(tr("visibility.filter-use"));
        lore.add(enabled ? tr("common.click-disable") : tr("common.click-enable"));
        return item(material, name, NamedTextColor.AQUA, lore);
    }

    private ItemStack status(Component name, boolean enabled) {
        return item(enabled ? Material.LIME_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE,
                name, enabled ? NamedTextColor.GREEN : NamedTextColor.RED, List.of(tr("common.click-change")));
    }

    private ItemStack item(Material material, Component name, NamedTextColor color, List<Component> lore) {
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

    public void startUpdates() {
        messages.localization().addLocaleListener(localeChanges);
        if (!observingChanges) { settings.addChangeListener(committedChanges); observingChanges = true; }
    }
    public void stopUpdates() {
        RuntimeException failure = null;
        try {
            messages.localization().removeLocaleListener(localeChanges);
        } catch (RuntimeException exception) {
            failure = exception;
        }
        // An add can take effect and throw before observingChanges is set.
        observingChanges = false;
        try {
            settings.removeChangeListener(committedChanges);
        } catch (RuntimeException exception) {
            if (failure == null) throw exception;
            if (failure != exception) failure.addSuppressed(exception);
        }
        if (failure != null) throw failure;
    }

    public void shutdown() {
        try {
            stopUpdates();
        } finally {
            closeOpenInventories();
        }
    }

    public void refreshActive(UUID owner) {
        Inventory inventory = activeInventories.get(owner);
        if (inventory == null) return;
        Player player = onlinePlayer.apply(owner);
        if (player == null || !player.isOnline() || player.getOpenInventory().getTopInventory() != inventory
                || !(inventory.getHolder() instanceof VisibilitySettingsHolder holder) || !isActive(player, inventory, holder)) {
            activeInventories.remove(owner, inventory); return;
        }
        if (!player.hasPermission(SettingsCommand.PERMISSION) || settings.getSettings(owner).isEmpty()) {
            activeInventories.remove(owner, inventory); player.closeInventory(); return;
        }
        try { render(player, inventory, settings.getSettings(owner).orElseThrow()); }
        catch (RuntimeException exception) {
            activeInventories.remove(owner, inventory);
            if (player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
            logger.log(Level.WARNING, "Could not rerender VisibilitySettingsMenu for " + owner, exception);
        }
    }

}
