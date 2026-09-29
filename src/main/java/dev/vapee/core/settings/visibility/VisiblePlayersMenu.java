package dev.vapee.core.settings.visibility;

import dev.vapee.core.identity.PlayerIdentity;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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

public final class VisiblePlayersMenu {
    public static final int INVENTORY_SIZE = 54;
    public static final int CONTENT_SIZE = 45;
    public static final int PREVIOUS_SLOT = 45;
    public static final int ADD_SLOT = 46;
    public static final int BACK_SLOT = 48;
    public static final int CLOSE_SLOT = 49;
    public static final int REFRESH_SLOT = 52;
    public static final int NEXT_SLOT = 53;

    private static final Component TITLE = uiText("Manage Visible Players", NamedTextColor.DARK_GRAY);
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
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public VisiblePlayersMenu(JavaPlugin plugin, PlayerSettingsService settings,
                              PlayerIdentityService identities, MessageService messages) {
        this(settings, identities, messages,
                Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                VisiblePlayersMenu::renderItem);
    }

    VisiblePlayersMenu(PlayerSettingsService settings, PlayerIdentityService identities,
                       MessageService messages, Function<UUID, Player> onlinePlayer,
                       InventoryFactory inventoryFactory, ItemRenderer itemRenderer) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.identities = Objects.requireNonNull(identities, "identities");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int requestedPage) {
        Player viewer = Objects.requireNonNull(player, "player");
        List<Entry> entries = entries(viewer.getUniqueId());
        if (entries == null) {
            messages.send(viewer, "<red>Your player profile is not available.</red>");
            return;
        }
        int pageCount = pageCount(entries.size());
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int first = page * CONTENT_SIZE;
        int end = Math.min(first + CONTENT_SIZE, entries.size());
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
        if (page > 0) {
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
        if (page + 1 < pageCount) {
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

    public boolean hasNext(UUID owner, int page) {
        List<Entry> current = entries(owner);
        return current != null && page >= 0 && page + 1 < pageCount(current.size());
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

    public void closeOpenInventories() {
        for (Map.Entry<UUID, Inventory> entry : List.copyOf(activeInventories.entrySet())) {
            Player player = onlinePlayer.apply(entry.getKey());
            if (player != null && player.isOnline()
                    && player.getOpenInventory().getTopInventory() == entry.getValue()) {
                player.closeInventory();
            }
        }
        activeInventories.clear();
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

    private ItemSpec entryItem(Entry entry) {
        return new ItemSpec(Material.PLAYER_HEAD, uiText(entry.name(), NamedTextColor.AQUA),
                List.of(uiText(entry.online() ? "Online" : "Offline",
                                entry.online() ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                        uiText("UUID: " + entry.id(), NamedTextColor.GRAY),
                        uiText("Right-click to remove.", NamedTextColor.GRAY)));
    }

    private ItemStack item(Material material, String name, NamedTextColor color, List<String> lore) {
        return itemRenderer.render(spec(material, name, color, lore));
    }

    private static ItemSpec spec(Material material, String name, NamedTextColor color, List<String> lore) {
        return new ItemSpec(material, uiText(name, color),
                lore.stream().map(line -> uiText(line, NamedTextColor.GRAY)).toList());
    }

    private static ItemStack renderItem(ItemSpec spec) {
        ItemStack item = new ItemStack(spec.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(spec.name());
        meta.lore(spec.lore());
        item.setItemMeta(meta);
        return item;
    }

    private static int pageCount(int size) {
        return size == 0 ? 1 : 1 + (size - 1) / CONTENT_SIZE;
    }

    private static Component uiText(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }

    record ItemSpec(Material material, Component name, List<Component> lore) {
        ItemSpec {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        }
    }

    private record Entry(UUID id, String name, boolean known, boolean online) {}

    @FunctionalInterface
    interface InventoryFactory {
        Inventory create(VisiblePlayersHolder holder, int size, Component title);
    }

    @FunctionalInterface
    interface ItemRenderer {
        ItemStack render(ItemSpec spec);
    }
}
