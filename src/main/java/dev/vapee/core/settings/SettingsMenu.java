package dev.vapee.core.settings;

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
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SettingsMenu {
    private final dev.vapee.core.player.settings.PlayerSettingsChangeListener committedChanges = this::refreshActive;
    private boolean observingChanges;


    public enum Action { SCOREBOARD, SOUNDS, PRIVATE_MESSAGES, FRIEND_REQUESTS, VISIBILITY, FRIEND_PRESENCE, CLOSE, REFRESH }
    public static final MenuLayout<Action> DEFAULT_LAYOUT = MenuLayout.builder(45, Action.class)
            .setting(Action.SCOREBOARD, 11, 20)
            .setting(Action.SOUNDS, 12, 21)
            .setting(Action.PRIVATE_MESSAGES, 13, 22)
            .setting(Action.FRIEND_REQUESTS, 14, 23)
            .setting(Action.FRIEND_PRESENCE, 15, 24)
            .button(Action.CLOSE, 40)
            .build();

    private static final Component TITLE = tr("settings.title").color(NamedTextColor.DARK_GRAY);

    private final MenuLayout<Action> layout;
    public MenuLayout<Action> layout() { return layout; }

    private final List<SettingsMenuEntry> entries;
    private final PlayerSettingsService playerSettingsService;
    private final MessageService messageService;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private final java.util.function.Consumer<Player> localeChanges = viewer -> refreshActive(viewer.getUniqueId());
    private Player renderingFor; // Scoped to one synchronous render, never an authority or locale cache.
    private final Function<UUID, Player> onlinePlayer;
    private final Logger logger;
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public SettingsMenu(
            JavaPlugin plugin,
            PlayerSettingsService playerSettingsService,
            MessageService messageService
    ) {
        this(playerSettingsService, messageService,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                UiItems::render, plugin.getLogger(), DEFAULT_LAYOUT);
    }

    SettingsMenu(PlayerSettingsService playerSettingsService, MessageService messageService,
                 Function<UUID, Player> onlinePlayer,
                 InventoryFactory inventoryFactory, ItemRenderer itemRenderer, Logger logger) {
        this(playerSettingsService, messageService, onlinePlayer, inventoryFactory, itemRenderer, logger, SettingsMenuEntries.defaults());
    }

    SettingsMenu(PlayerSettingsService playerSettingsService, MessageService messageService,
                 Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                 ItemRenderer itemRenderer, Logger logger, List<SettingsMenuEntry> entries) {
        this(playerSettingsService, messageService, onlinePlayer, inventoryFactory, itemRenderer, logger, entries, DEFAULT_LAYOUT);
    }

    SettingsMenu(PlayerSettingsService playerSettingsService, MessageService messageService,
                 Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                 ItemRenderer itemRenderer, Logger logger, List<SettingsMenuEntry> entries, MenuLayout<Action> menuLayout) {
        this.layout = Objects.requireNonNull(menuLayout, "layout").validateActions(java.util.Set.of(Action.FRIEND_PRESENCE, Action.FRIEND_REQUESTS, Action.PRIVATE_MESSAGES, Action.SCOREBOARD, Action.SOUNDS, Action.VISIBILITY));
        this.layout.requireFixed();
        this.entries = validateEntries(entries);
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.itemRenderer = spec -> itemRenderer.render(UiItems.localize(spec, this.messageService, renderingFor));
        this.logger = Objects.requireNonNull(logger, "logger");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
    }

    SettingsMenu(PlayerSettingsService settings, MessageService messages, Function<UUID, Player> online,
                 InventoryFactory inventories, ItemRenderer items, Logger logger, MenuLayout<Action> layout) {
        this(settings, messages, online, inventories, items, logger, SettingsMenuEntries.defaults(layout), layout);
    }

    public boolean open(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!validatedPlayer.hasPermission(SettingsCommand.PERMISSION)) {
            messageService.send(validatedPlayer, tr("settings.denied").color(NamedTextColor.RED));
            return false;
        }
        UUID uniqueId = validatedPlayer.getUniqueId();
        if (playerSettingsService.getSettings(uniqueId).isEmpty()) {
            validatedPlayer.closeInventory();
            messageService.send(validatedPlayer, tr("common.profile-unavailable").color(NamedTextColor.RED));
            return false;
        }

        SettingsInventoryHolder holder = new SettingsInventoryHolder(uniqueId);
        Inventory inventory = inventoryFactory.create(holder, layout.size(), messageService.localize(validatedPlayer, TITLE));
        holder.bindInventory(inventory);
        if (!refresh(validatedPlayer, inventory)) {
            validatedPlayer.closeInventory();
            messageService.send(validatedPlayer, tr("common.profile-unavailable").color(NamedTextColor.RED));
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

        Player previousViewer = renderingFor;
        renderingFor = validatedPlayer;
        try {
        PlayerSettings settings = optionalSettings.get();
        for (var entry : entries) {
            validatedInventory.setItem(entry.slot(), itemRenderer.render(entry.icon().apply(settings)));
            if (entry.statusSlot() >= 0) validatedInventory.setItem(entry.statusSlot(), itemRenderer.render(entry.status().apply(settings)));
        }
        place(validatedInventory, Action.CLOSE, createCloseItem());
        place(validatedInventory, Action.REFRESH, itemRenderer.render(new UiItemSpec(
                Material.CLOCK, UiItems.text(tr("common.refresh"), NamedTextColor.AQUA),
                List.of(UiItems.text(tr("settings.refresh-hint"), NamedTextColor.GRAY)))));
        return true;
        } finally { renderingFor = previousViewer; }
    }

    public Optional<SettingsMenuEntry> entryAt(int slot) {
        return entries.stream().filter(entry -> entry.matches(slot)).findFirst();
    }

    private List<SettingsMenuEntry> validateEntries(List<SettingsMenuEntry> entries) {
        var slots = new java.util.HashSet<Integer>();
        layout.actionSlot(Action.CLOSE).ifPresent(slots::add);
        layout.actionSlot(Action.REFRESH).ifPresent(slots::add);
        for (var entry : entries) {
            MenuLayout.validateSlot(layout.size(), entry.slot());
            if (entry.statusSlot() >= 0) MenuLayout.validateSlot(layout.size(), entry.statusSlot());
            if (!slots.add(entry.slot()) || entry.statusSlot() >= 0 && !slots.add(entry.statusSlot())) {
                throw new IllegalArgumentException("Duplicate/reserved Settings tile slot");
            }
        }
        return List.copyOf(entries);
    }

    private ItemStack createCloseItem() {
        return itemRenderer.render(new UiItemSpec(
                Material.BARRIER,
                UiItems.text(tr("common.close"), NamedTextColor.RED),
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
    private void place(Inventory inventory, Action action, ItemStack item) {
        place(inventory, action, 0, item);
    }
    private void place(Inventory inventory, Action action, int position, ItemStack item) {
        layout.actionSlot(action, position).ifPresent(slot -> inventory.setItem(slot, item));
    }

    public void startUpdates() {
        messageService.localization().addLocaleListener(localeChanges);
        if (!observingChanges) { playerSettingsService.addChangeListener(committedChanges); observingChanges = true; }
    }
    public void stopUpdates() {
        messageService.localization().removeLocaleListener(localeChanges);
        if (observingChanges) { playerSettingsService.removeChangeListener(committedChanges); observingChanges = false; }
    }
    public void shutdown() { stopUpdates(); closeOpenInventories(); }

    public void refreshActive(UUID owner) {
        Inventory inventory = activeInventories.get(owner);
        if (inventory == null) return;
        Player player = onlinePlayer.apply(owner);
        if (player == null || !player.isOnline() || player.getOpenInventory().getTopInventory() != inventory
                || !(inventory.getHolder() instanceof SettingsInventoryHolder holder) || !isActive(player, inventory, holder)) {
            activeInventories.remove(owner, inventory); return;
        }
        if (!player.hasPermission(SettingsCommand.PERMISSION) || playerSettingsService.getSettings(owner).isEmpty()) {
            activeInventories.remove(owner, inventory); player.closeInventory(); return;
        }
        try { if (!refresh(player, inventory)) player.closeInventory(); }
        catch (RuntimeException exception) {
            activeInventories.remove(owner, inventory);
            if (player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
            logger.log(Level.WARNING, "Could not rerender SettingsMenu for " + owner, exception);
        }
    }

}
