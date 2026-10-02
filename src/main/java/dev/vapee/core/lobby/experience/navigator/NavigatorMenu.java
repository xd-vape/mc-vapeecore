package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.lobby.warp.WarpPoint;
import dev.vapee.core.lobby.warp.WarpService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class NavigatorMenu {

    public static final int INVENTORY_SIZE = 54;
    public static final int CONTENT_SIZE = 45;
    public static final int PREVIOUS_SLOT = 45;
    public static final int PAGE_INFO_SLOT = 49;
    public static final int CLOSE_SLOT = 50;
    public static final int NEXT_SLOT = 53;

    private static final Component TITLE = Component.text("Warp Navigator", NamedTextColor.DARK_GRAY);

    private final WarpService warpService;
    private final NavigatorAccessPolicy accessPolicy;
    private final InventoryFactory inventoryFactory;
    private final ItemRenderer itemRenderer;
    private final Function<UUID, Player> onlinePlayer;
    private final Logger logger;
    private final Map<UUID, Inventory> activeInventories = new HashMap<>();

    public NavigatorMenu(JavaPlugin plugin, WarpService warpService, NavigatorAccessPolicy accessPolicy) {
        this(warpService, accessPolicy, Objects.requireNonNull(plugin, "plugin").getServer()::getPlayer,
                (holder, size, title) -> plugin.getServer().createInventory(holder, size, title),
                NavigatorMenu::renderItem, plugin.getLogger());
    }

    NavigatorMenu(WarpService warpService, NavigatorAccessPolicy accessPolicy,
                  Function<UUID, Player> onlinePlayer, InventoryFactory inventoryFactory,
                  ItemRenderer itemRenderer, Logger logger) {
        this.warpService = Objects.requireNonNull(warpService, "warpService");
        this.accessPolicy = Objects.requireNonNull(accessPolicy, "accessPolicy");
        this.onlinePlayer = Objects.requireNonNull(onlinePlayer, "onlinePlayer");
        this.inventoryFactory = Objects.requireNonNull(inventoryFactory, "inventoryFactory");
        this.itemRenderer = Objects.requireNonNull(itemRenderer, "itemRenderer");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public boolean open(Player player) {
        return open(player, 0);
    }

    public boolean open(Player player, int requestedPage) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!canAccess(validatedPlayer)) {
            close(validatedPlayer);
            return false;
        }
        List<WarpPoint> warps = warpService.getNavigatorWarps();
        int pageCount = Math.max(1, (warps.size() + CONTENT_SIZE - 1) / CONTENT_SIZE);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int fromIndex = page * CONTENT_SIZE;
        int toIndex = Math.min(fromIndex + CONTENT_SIZE, warps.size());
        Map<Integer, String> warpIdsBySlot = new LinkedHashMap<>();
        for (int index = fromIndex; index < toIndex; index++) {
            warpIdsBySlot.put(index - fromIndex, warps.get(index).id());
        }

        NavigatorInventoryHolder holder = new NavigatorInventoryHolder(
                validatedPlayer.getUniqueId(),
                page,
                warpIdsBySlot
        );
        Inventory inventory = inventoryFactory.create(holder, INVENTORY_SIZE, TITLE);
        holder.bindInventory(inventory);

        for (int index = fromIndex; index < toIndex; index++) {
            inventory.setItem(index - fromIndex, createWarpItem(warps.get(index)));
        }
        if (warps.isEmpty()) {
            inventory.setItem(22, createItem(
                    Material.GRAY_DYE,
                    "No Destinations Available",
                    NamedTextColor.GRAY,
                    List.of()
            ));
        }
        if (page > 0) {
            inventory.setItem(PREVIOUS_SLOT, createItem(
                    Material.ARROW,
                    "Previous Page",
                    NamedTextColor.YELLOW,
                    List.of()
            ));
        }
        inventory.setItem(PAGE_INFO_SLOT, createItem(
                Material.PAPER,
                "Page " + (page + 1) + "/" + pageCount,
                NamedTextColor.AQUA,
                List.of(warps.size() + (warps.size() == 1 ? " destination." : " destinations."))
        ));
        inventory.setItem(CLOSE_SLOT, createItem(
                Material.BARRIER,
                "Close",
                NamedTextColor.RED,
                List.of()
        ));
        if (page + 1 < pageCount) {
            inventory.setItem(NEXT_SLOT, createItem(
                    Material.ARROW,
                    "Next Page",
                    NamedTextColor.YELLOW,
                    List.of()
            ));
        }
        var opened = validatedPlayer.openInventory(inventory);
        if (opened != null && opened.getTopInventory() == inventory
                && validatedPlayer.getOpenInventory().getTopInventory() == inventory) {
            if (!canAccess(validatedPlayer)) {
                close(validatedPlayer);
                return false;
            }
            activeInventories.put(validatedPlayer.getUniqueId(), inventory);
            return true;
        }
        return false;
    }

    private ItemStack createWarpItem(WarpPoint warp) {
        return createItem(
                warp.icon(),
                warp.displayName(),
                NamedTextColor.AQUA,
                List.of("Click to teleport.")
        );
    }

    public void closeOpenInventories() {
        try {
            for (Map.Entry<UUID, Inventory> entry : List.copyOf(activeInventories.entrySet())) {
                try {
                    Player player = onlinePlayer.apply(entry.getKey());
                    if (player != null && player.isOnline()
                            && player.getOpenInventory().getTopInventory() == entry.getValue()) {
                        close(player);
                    }
                } catch (RuntimeException exception) {
                    logger.log(Level.WARNING, "Could not close navigator for " + entry.getKey() + ".", exception);
                }
            }
        } finally {
            activeInventories.clear();
        }
    }

    public boolean isActive(Player player, Inventory inventory, NavigatorInventoryHolder holder) {
        return player != null && inventory != null && holder != null
                && holder.getOwnerUniqueId().equals(player.getUniqueId())
                && inventory.getHolder() == holder && holder.isBoundTo(inventory)
                && activeInventories.get(player.getUniqueId()) == inventory
                && player.getOpenInventory().getTopInventory() == inventory;
    }

    public boolean canAccess(Player player) {
        return accessPolicy.canAccess(player);
    }

    boolean isLobbyWorld(World world) {
        return accessPolicy.isLobbyWorld(world);
    }

    void forgetIfActive(UUID owner, Inventory inventory) {
        activeInventories.remove(owner, inventory);
    }

    void forget(UUID owner) {
        activeInventories.remove(owner);
    }

    void close(Player player) {
        forget(player.getUniqueId());
        Inventory inventory = player.getOpenInventory().getTopInventory();
        if (inventory.getHolder() instanceof NavigatorInventoryHolder holder
                && holder.getOwnerUniqueId().equals(player.getUniqueId()) && holder.isBoundTo(inventory)) {
            player.closeInventory();
        }
    }

    private ItemStack createItem(
            Material material,
            String name,
            NamedTextColor nameColor,
            List<String> loreLines
    ) {
        return itemRenderer.render(new ItemSpec(material, uiText(name, nameColor), loreLines.stream()
                .map(line -> uiText(line, NamedTextColor.GRAY)).toList()));
    }

    private static ItemStack renderItem(ItemSpec spec) {
        ItemStack item = new ItemStack(spec.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(spec.name());
        meta.lore(spec.lore());
        item.setItemMeta(meta);
        return item;
    }

    private Component uiText(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    record ItemSpec(Material material, Component name, List<Component> lore) {
        ItemSpec {
            Objects.requireNonNull(material, "material");
            Objects.requireNonNull(name, "name");
            lore = List.copyOf(Objects.requireNonNull(lore, "lore"));
        }
    }

    @FunctionalInterface
    interface InventoryFactory {
        Inventory create(NavigatorInventoryHolder holder, int size, Component title);
    }

    @FunctionalInterface
    interface ItemRenderer {
        ItemStack render(ItemSpec spec);
    }
}
