package dev.vapee.core.lobby.experience.navigator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;

public final class NavigatorMenu {

    public static final int INVENTORY_SIZE = 27;
    public static final int LOBBY_SLOT = 11;
    public static final int ACTIVITIES_SLOT = 13;
    public static final int MINIGAMES_SLOT = 15;
    public static final int CLOSE_SLOT = 22;

    private static final Component TITLE = Component.text("Navigator", NamedTextColor.DARK_GRAY);

    private final JavaPlugin plugin;

    public NavigatorMenu(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void open(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        NavigatorInventoryHolder holder = new NavigatorInventoryHolder(validatedPlayer.getUniqueId());
        Inventory inventory = plugin.getServer().createInventory(holder, INVENTORY_SIZE, TITLE);
        holder.bindInventory(inventory);

        inventory.setItem(LOBBY_SLOT, createItem(
                Material.GRASS_BLOCK,
                "Lobby",
                NamedTextColor.GREEN,
                List.of("Teleport to the lobby spawn.")
        ));
        inventory.setItem(ACTIVITIES_SLOT, createItem(
                Material.EMERALD,
                "Activities",
                NamedTextColor.AQUA,
                List.of("Lobby activities are coming soon.")
        ));
        inventory.setItem(MINIGAMES_SLOT, createItem(
                Material.DIAMOND_SWORD,
                "Minigames",
                NamedTextColor.LIGHT_PURPLE,
                List.of("Minigames are coming soon.")
        ));
        inventory.setItem(CLOSE_SLOT, createItem(
                Material.BARRIER,
                "Close",
                NamedTextColor.RED,
                List.of()
        ));
        validatedPlayer.openInventory(inventory);
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
