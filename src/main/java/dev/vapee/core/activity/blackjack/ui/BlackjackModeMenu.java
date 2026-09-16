package dev.vapee.core.activity.blackjack.ui;

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

public final class BlackjackModeMenu {

    public static final int INVENTORY_SIZE = 27;
    public static final int SOLO_SLOT = 11;
    public static final int PUBLIC_SLOT = 15;
    public static final int CLOSE_SLOT = 22;

    private static final Component TITLE = Component.text("Blackjack", NamedTextColor.DARK_GRAY);

    private final JavaPlugin plugin;

    public BlackjackModeMenu(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
    }

    public void open(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        BlackjackModeInventoryHolder holder = new BlackjackModeInventoryHolder(validatedPlayer.getUniqueId());
        Inventory inventory = plugin.getServer().createInventory(holder, INVENTORY_SIZE, TITLE);
        holder.bindInventory(inventory);
        inventory.setItem(SOLO_SLOT, createItem(
                Material.PLAYER_HEAD,
                "Solo Play",
                NamedTextColor.GREEN,
                List.of("Your own private table.", "No waiting. Deal whenever you are ready.")
        ));
        inventory.setItem(PUBLIC_SLOT, createItem(
                Material.OAK_SIGN,
                "Public Table",
                NamedTextColor.AQUA,
                List.of("Join an available public table.", "One player is enough to start.")
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
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof BlackjackModeInventoryHolder) {
                player.closeInventory();
            }
        }
    }

    private ItemStack createItem(
            Material material,
            String name,
            NamedTextColor color,
            List<String> lore
    ) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(text(name, color));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(line -> text(line, NamedTextColor.GRAY)).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text(value, color).decoration(TextDecoration.ITALIC, false);
    }
}
