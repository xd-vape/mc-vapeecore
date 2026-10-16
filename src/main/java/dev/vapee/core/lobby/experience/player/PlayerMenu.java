package dev.vapee.core.lobby.experience.player;

import static dev.vapee.core.i18n.Translations.tr;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.settings.command.SettingsCommand;
import dev.vapee.core.ui.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;
import java.util.function.*;

/** Personal entry points only; each destination keeps its own permission/domain authority. */
public final class PlayerMenu {
    public enum Action { VISIBILITY, FRIENDS, SETTINGS, CLOSE }
    public static final MenuLayout<Action> DEFAULT_LAYOUT = MenuLayout.builder(27, Action.class)
            .button(Action.VISIBILITY, 11).button(Action.FRIENDS, 13).button(Action.SETTINGS, 15)
            .button(Action.CLOSE, 22).build();

    private final MenuLayout<Action> layout;
    private final PlayerSettingsService settings;
    private final MessageService messages;
    private final Predicate<Player> eligible;
    private final Map<Action, Consumer<Player>> destinations;
    private final Function<UUID, Player> online;
    private final InventoryFactory inventories;
    private final Function<UiItemSpec, ItemStack> items;
    private final Map<UUID, Inventory> active = new HashMap<>();
    private final Consumer<Player> locales = this::refresh;

    public PlayerMenu(JavaPlugin plugin, PlayerSettingsService settings, MessageService messages,
                      Predicate<Player> eligible, Map<Action, Consumer<Player>> destinations) {
        this(settings, messages, eligible, destinations, plugin.getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                UiItems::render, DEFAULT_LAYOUT);
    }

    PlayerMenu(PlayerSettingsService settings, MessageService messages, Predicate<Player> eligible,
               Map<Action, Consumer<Player>> destinations, Function<UUID, Player> online,
               InventoryFactory inventories, Function<UiItemSpec, ItemStack> items, MenuLayout<Action> layout) {
        this.settings = Objects.requireNonNull(settings); this.messages = Objects.requireNonNull(messages);
        this.eligible = Objects.requireNonNull(eligible); this.destinations = Map.copyOf(destinations);
        this.online = Objects.requireNonNull(online); this.inventories = Objects.requireNonNull(inventories);
        this.items = Objects.requireNonNull(items); this.layout = Objects.requireNonNull(layout).validateActions(Set.of());
        layout.requireFixed();
    }
    public MenuLayout<Action> layout() { return layout; }
    boolean canAccess(Player player) {
        return player != null && player.isOnline() && eligible.test(player)
                && settings.getSettings(player.getUniqueId()).isPresent();
    }
    public boolean open(Player player) {
        if (!canAccess(player)) return false;
        var holder = new PlayerMenuHolder(player.getUniqueId());
        var inventory = inventories.create(holder, layout.size(), messages.localize(player, tr("player-menu.title")));
        holder.bind(inventory); render(player, inventory, holder);
        var opened = player.openInventory(inventory);
        if (opened == null || opened.getTopInventory() != inventory) return false;
        active.put(player.getUniqueId(), inventory); return true;
    }
    public boolean isActive(Player viewer, Inventory inventory, PlayerMenuHolder holder) {
        return viewer != null && inventory != null && holder != null && holder.owner().equals(viewer.getUniqueId())
                && inventory.getHolder() == holder && holder.boundTo(inventory)
                && active.get(viewer.getUniqueId()) == inventory && viewer.getOpenInventory().getTopInventory() == inventory;
    }
    Optional<Action> actionAt(Player viewer, Inventory inventory, PlayerMenuHolder holder, int slot) {
        if (!isActive(viewer, inventory, holder) || !canAccess(viewer)) return Optional.empty();
        return layout.actionAt(slot).filter(holder::shows).filter(action -> allowed(viewer, action));
    }
    private boolean allowed(Player viewer, Action action) {
        if (action == Action.CLOSE) return true;
        if (!destinations.containsKey(action)) return false;
        return viewer.hasPermission(switch (action) {
            case FRIENDS -> dev.vapee.core.friend.command.FriendCommand.PERMISSION;
            case SETTINGS, VISIBILITY -> SettingsCommand.PERMISSION;
            case CLOSE -> throw new IllegalStateException();
        });
    }
    void use(Player viewer, Action action) {
        if (!canAccess(viewer) || !allowed(viewer, action)) return;
        if (action == Action.CLOSE) viewer.closeInventory();
        else {
            viewer.closeInventory();
            destinations.get(action).accept(viewer);
        }
    }
    private void render(Player viewer, Inventory inventory, PlayerMenuHolder holder) {
        holder.rendered(Set.of());
        inventory.clear();
        var rendered = EnumSet.noneOf(Action.class);
        for (var action : Action.values()) {
            if (!allowed(viewer, action) || layout.actionSlot(action).isEmpty()) continue;
            Material material = switch (action) {
                case VISIBILITY -> Material.SPYGLASS;
                case FRIENDS -> Material.BOOK; case SETTINGS -> Material.COMPARATOR; case CLOSE -> Material.BARRIER;
            };
            var key = "player-menu." + action.name().toLowerCase(Locale.ROOT);
            var spec = new UiItemSpec(material, UiItems.text(tr(key), action == Action.CLOSE ? NamedTextColor.GRAY : NamedTextColor.AQUA),
                    action == Action.CLOSE ? List.of() : List.of(UiItems.text(tr(key + "-hint"), NamedTextColor.GRAY),
                            Component.empty(), UiItems.text(tr("common.click-open"), NamedTextColor.GRAY)));
            inventory.setItem(layout.slot(action), items.apply(UiItems.localize(spec, messages, viewer)));
            rendered.add(action);
        }
        holder.rendered(rendered);
    }
    private void refresh(Player player) {
        Inventory inventory = active.get(player.getUniqueId());
        if (inventory == null) return;
        if (!(inventory.getHolder() instanceof PlayerMenuHolder holder) || !isActive(player, inventory, holder)) {
            active.remove(player.getUniqueId(), inventory); return;
        }
        if (!canAccess(player)) { forget(player.getUniqueId(), inventory); player.closeInventory(); return; }
        render(player, inventory, holder);
    }
    void forget(UUID owner, Inventory inventory) { active.remove(owner, inventory); }
    public void startUpdates() { messages.localization().addLocaleListener(locales); }
    public void shutdown() {
        messages.localization().removeLocaleListener(locales);
        try {
            for (var entry : List.copyOf(active.entrySet())) {
                Player player = online.apply(entry.getKey());
                try {
                    if (player != null && player.isOnline() && player.getOpenInventory().getTopInventory() == entry.getValue()) player.closeInventory();
                } catch (RuntimeException exception) {
                    java.util.logging.Logger.getLogger(PlayerMenu.class.getName()).log(java.util.logging.Level.WARNING,
                            "Could not close Player Menu for " + entry.getKey(), exception);
                }
            }
        } finally { active.clear(); }
    }
    int activeCount() { return active.size(); }
    @FunctionalInterface interface InventoryFactory { Inventory create(PlayerMenuHolder holder, int size, Component title); }
}
