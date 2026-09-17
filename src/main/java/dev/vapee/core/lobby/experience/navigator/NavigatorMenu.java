package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.lobby.warp.WarpPoint;
import dev.vapee.core.lobby.warp.WarpService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class NavigatorMenu {

    public static final int INVENTORY_SIZE = 54;
    public static final int CONTENT_SIZE = 45;
    public static final int PREVIOUS_SLOT = 45;
    public static final int PAGE_INFO_SLOT = 49;
    public static final int CLOSE_SLOT = 50;
    public static final int NEXT_SLOT = 53;

    private static final Component TITLE = Component.text("Warp Navigator", NamedTextColor.DARK_GRAY);

    private final JavaPlugin plugin;
    private final WarpService warpService;

    public NavigatorMenu(JavaPlugin plugin, WarpService warpService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.warpService = Objects.requireNonNull(warpService, "warpService");
    }

    public void open(Player player) {
        open(player, 0);
    }

    public void open(Player player, int requestedPage) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        List<WarpPoint> warps = warpService.getWarps();
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
        Inventory inventory = plugin.getServer().createInventory(holder, INVENTORY_SIZE, TITLE);
        holder.bindInventory(inventory);

        for (Map.Entry<Integer, String> entry : warpIdsBySlot.entrySet()) {
            warpService.getWarp(entry.getValue()).ifPresent(warp ->
                    inventory.setItem(entry.getKey(), createWarpItem(warp))
            );
        }
        if (warps.isEmpty()) {
            inventory.setItem(22, createItem(
                    Material.GRAY_DYE,
                    "No Warps Configured",
                    NamedTextColor.GRAY,
                    List.of("An administrator can configure destinations with /warp.")
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
                List.of(warps.size() + " configured warp(s).")
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
        validatedPlayer.openInventory(inventory);
    }

    private ItemStack createWarpItem(WarpPoint warp) {
        return createItem(
                warp.icon(),
                warp.displayName(),
                NamedTextColor.AQUA,
                List.of("Click to teleport.", "ID: " + warp.id())
        );
    }

    public void closeOpenInventories() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof NavigatorInventoryHolder) {
                player.closeInventory();
            }
        }
    }

    private ItemStack createItem(
            Material material,
            String name,
            NamedTextColor nameColor,
            List<String> loreLines
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(uiText(name, nameColor));
        if (!loreLines.isEmpty()) {
            meta.lore(loreLines.stream()
                    .map(line -> uiText(line, NamedTextColor.GRAY))
                    .toList()
            );
        }
        item.setItemMeta(meta);
        return item;
    }

    private Component uiText(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
