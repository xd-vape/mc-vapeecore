package dev.vapee.core.lobby.experience.navigator.activity;

import dev.vapee.core.activity.navigation.ActivityCatalog;
import dev.vapee.core.activity.navigation.ActivityEntryPoint;
import dev.vapee.core.lobby.experience.navigator.NavigatorMenu;
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

public final class ActivitiesMenu {

    public static final int INVENTORY_SIZE = 27;
    public static final int BACK_SLOT = 18;
    public static final int CLOSE_SLOT = 22;

    private static final Component TITLE = Component.text("Activities", NamedTextColor.DARK_GRAY);
    private static final List<Integer> ENTRY_SLOTS = List.of(
            13, 11, 15, 10, 12, 14, 16, 19, 20, 21, 23, 24, 25
    );

    private final JavaPlugin plugin;
    private final ActivityCatalog activityCatalog;
    private final NavigatorMenu navigatorMenu;

    public ActivitiesMenu(JavaPlugin plugin, ActivityCatalog activityCatalog, NavigatorMenu navigatorMenu) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.activityCatalog = Objects.requireNonNull(activityCatalog, "activityCatalog");
        this.navigatorMenu = Objects.requireNonNull(navigatorMenu, "navigatorMenu");
    }

    public void open(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        List<ActivityEntryPoint> entries = activityCatalog.getEntries();
        Map<Integer, String> keysBySlot = new LinkedHashMap<>();
        int entryCount = Math.min(entries.size(), ENTRY_SLOTS.size());
        for (int index = 0; index < entryCount; index++) {
            keysBySlot.put(ENTRY_SLOTS.get(index), entries.get(index).getActivityKey());
        }

        ActivitiesInventoryHolder holder = new ActivitiesInventoryHolder(
                validatedPlayer.getUniqueId(),
                keysBySlot
        );
        Inventory inventory = plugin.getServer().createInventory(holder, INVENTORY_SIZE, TITLE);
        holder.bindInventory(inventory);

        for (Map.Entry<Integer, String> slotEntry : keysBySlot.entrySet()) {
            activityCatalog.get(slotEntry.getValue()).ifPresent(entryPoint ->
                    inventory.setItem(slotEntry.getKey(), createEntryItem(entryPoint))
            );
        }
        inventory.setItem(BACK_SLOT, createSimpleItem(Material.ARROW, "Back", NamedTextColor.YELLOW));
        inventory.setItem(CLOSE_SLOT, createSimpleItem(Material.BARRIER, "Close", NamedTextColor.RED));
        validatedPlayer.openInventory(inventory);
    }

    public void openNavigator(Player player) {
        navigatorMenu.open(Objects.requireNonNull(player, "player"));
    }

    public void closeOpenInventories() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof ActivitiesInventoryHolder) {
                player.closeInventory();
            }
        }
    }

    private ItemStack createEntryItem(ActivityEntryPoint entryPoint) {
        ItemStack item = new ItemStack(Objects.requireNonNull(entryPoint.getIcon(), "entryPoint icon"));
        ItemMeta meta = item.getItemMeta();
        meta.displayName(noItalic(Objects.requireNonNull(entryPoint.getDisplayName(), "entryPoint displayName")));
        List<Component> description = List.copyOf(Objects.requireNonNull(
                entryPoint.getDescription(),
                "entryPoint description"
        ));
        if (!description.isEmpty()) {
            meta.lore(description.stream().map(this::noItalic).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack createSimpleItem(Material material, String name, NamedTextColor color) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        item.setItemMeta(meta);
        return item;
    }

    private Component noItalic(Component component) {
        return component.decoration(TextDecoration.ITALIC, false);
    }
}
