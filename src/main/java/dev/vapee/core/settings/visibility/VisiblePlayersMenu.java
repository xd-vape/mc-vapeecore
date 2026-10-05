package dev.vapee.core.settings.visibility;

import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;
import dev.vapee.core.ui.Pagination;
import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.settings.command.SettingsCommand;
import dev.vapee.core.player.settings.PlayerSettingsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class VisiblePlayersMenu {
    public static final int INVENTORY_SIZE = 54;
    public static final int CONTENT_SIZE = 45;
    public static final int PREVIOUS_SLOT = 45;
    public static final int ADD_SLOT = 46;
    public static final int BACK_SLOT = 48;
    public static final int CLOSE_SLOT = 49;
    public static final int REFRESH_SLOT = 52;
    public static final int NEXT_SLOT = 53;

    private static final Component TITLE = UiItems.text("Manage Visible Players", NamedTextColor.DARK_GRAY);
    private static final Comparator<Entry> ENTRY_ORDER = Comparator
            .comparing(Entry::known).reversed()
            .thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER)
            .thenComparing(entry -> entry.id().toString());

    private final PlayerSettingsService settings;
    private final PlayerIdentityService identities;
    private final MessageService messages;
    private final Function<UUID, Player> onlinePlayer;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private final Logger logger;
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public VisiblePlayersMenu(JavaPlugin plugin, PlayerSettingsService settings,
                              PlayerIdentityService identities, MessageService messages) {
        this(settings, identities, messages,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                UiItems::render, plugin.getLogger());
    }

    VisiblePlayersMenu(PlayerSettingsService settings, PlayerIdentityService identities,
                       MessageService messages, Function<UUID, Player> onlinePlayer,
                       InventoryFactory inventoryFactory, ItemRenderer itemRenderer, Logger logger) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int requestedPage) {
        Player viewer = Objects.requireNonNull(player, "player");
        if (!viewer.hasPermission(SettingsCommand.PERMISSION)) {
            messages.send(viewer, "<red>You do not have permission to use settings.</red>");
            return;
        }
        List<Entry> entries = entries(viewer.getUniqueId());
        if (entries == null) {
            messages.send(viewer, "<red>Your player profile is not available.</red>");
            return;
        }
        Pagination pagination = Pagination.of(entries.size(), requestedPage, CONTENT_SIZE);
        int page = pagination.page();
        int first = pagination.fromIndex();
        int end = pagination.toIndex();
        Map<Integer, UUID> targets = new LinkedHashMap<>();
        for (int index = first; index < end; index++) {
            targets.put(index - first, entries.get(index).id());
        }

        VisiblePlayersHolder holder = new VisiblePlayersHolder(viewer.getUniqueId(), page, targets);
        Inventory inventory = inventoryFactory.create(holder, INVENTORY_SIZE, TITLE);
        holder.bindInventory(inventory);
        for (int index = first; index < end; index++) {
            inventory.setItem(index - first, itemRenderer.render(entryItem(entries.get(index))));
        }
        if (entries.isEmpty()) {
            inventory.setItem(22, item(Material.PAPER, "No added visible players", NamedTextColor.GRAY,
                    List.of("Use Add Player to get started.")));
        }
        if (pagination.hasPrevious()) {
            inventory.setItem(PREVIOUS_SLOT, item(Material.ARROW, "Previous Page", NamedTextColor.YELLOW,
                    List.of()));
        }
        inventory.setItem(ADD_SLOT, item(Material.LIME_DYE, "Add Player", NamedTextColor.GREEN,
                List.of("Click to enter a known player name or UUID.")));
        inventory.setItem(BACK_SLOT, item(Material.ARROW, "Back", NamedTextColor.YELLOW,
                List.of("Return to Visibility Settings.")));
        inventory.setItem(CLOSE_SLOT, item(Material.BARRIER, "Close", NamedTextColor.RED, List.of()));
        inventory.setItem(REFRESH_SLOT, item(Material.CLOCK, "Refresh", NamedTextColor.AQUA,
                List.of("Reload names and online status.")));
        if (pagination.hasNext()) {
            inventory.setItem(NEXT_SLOT, item(Material.ARROW, "Next Page", NamedTextColor.YELLOW, List.of()));
        }
        var opened = viewer.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory) {
            activeInventories.put(viewer.getUniqueId(), inventory);
        }
    }

    public boolean isActive(Player viewer, Inventory inventory, VisiblePlayersHolder holder) {
        return viewer != null && inventory != null && holder != null
                && holder.getOwnerUniqueId().equals(viewer.getUniqueId())
                && inventory.getHolder() == holder && holder.isBoundTo(inventory)
                && activeInventories.get(viewer.getUniqueId()) == inventory
                && viewer.getOpenInventory().getTopInventory() == inventory;
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
                    logger.log(Level.WARNING, "Could not close VisiblePlayersMenu for " + entry.getKey() + ".", exception);
                }
            }
        } finally {
            activeInventories.clear();
        }
    }

    int activeCount() { return activeInventories.size(); }

    public boolean hasNext(UUID owner, int page) {
        List<Entry> current = entries(owner);
        return current != null && page >= 0 && Pagination.of(current.size(), page, CONTENT_SIZE).hasNext();
    }

    public String displayName(UUID id) {
        return identities.findById(Objects.requireNonNull(id, "id"))
                .map(PlayerIdentity::name).orElse(id.toString());
    }

    public void promptAdd(Player player) {
        player.closeInventory();
        messages.send(player, Component.text("Click here to add a visible player.", NamedTextColor.GREEN)
                .clickEvent(ClickEvent.suggestCommand("/settings visibility add "))
                .hoverEvent(HoverEvent.showText(Component.text(
                        "Enter a known player name or UUID.", NamedTextColor.GRAY))));
    }

    void forgetIfActive(UUID owner, Inventory inventory) {
        activeInventories.remove(owner, inventory);
    }

    private List<Entry> entries(UUID owner) {
        var added = settings.getLobbyAddedVisiblePlayers(owner);
        if (added.isEmpty()) return null;
        List<Entry> result = new ArrayList<>(added.get().size());
        for (UUID id : added.get()) {
            var identity = identities.findById(id);
            Player online = onlinePlayer.apply(id);
            result.add(new Entry(id, identity.map(PlayerIdentity::name).orElse(id.toString()),
                    identity.isPresent(), online != null && online.isOnline()));
        }
        result.sort(ENTRY_ORDER);
        return List.copyOf(result);
    }

    private UiItemSpec entryItem(Entry entry) {
        return new UiItemSpec(Material.PLAYER_HEAD, UiItems.text(entry.name(), NamedTextColor.AQUA),
                List.of(UiItems.text(entry.online() ? "Online" : "Offline",
                                entry.online() ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                        UiItems.text("UUID: " + entry.id(), NamedTextColor.GRAY),
                        UiItems.text("Right-click to remove.", NamedTextColor.GRAY)));
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        return itemRenderer.render(UiItems.literal(material, name, color, lore));
    }

    private record Entry(UUID id, String name, boolean known, boolean online) {}

    @FunctionalInterface
    interface InventoryFactory {
        Inventory create(VisiblePlayersHolder holder, int size, Component title);
    }

    @FunctionalInterface
    interface ItemRenderer {
        ItemStack render(UiItemSpec spec);
    }
}
