package dev.vapee.core.gadgets;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.message.MessageService;
import dev.vapee.core.message.MessageStyle;
import dev.vapee.core.ui.MenuLayout;
import dev.vapee.core.ui.UiItems;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;

final class GadgetsMenu implements Listener {
    private static final MenuLayout<Category> LAYOUT = MenuLayout.builder(27, Category.class)
            .content(List.of(10, 11, 12, 14, 15, 16))
            .build();

    private enum Category {
        PLAYER_RIDING("player-riding", Material.SADDLE),
        SIZE_CHANGER("size-changer", Material.SLIME_BALL),
        GLOW("glow", Material.GLOW_INK_SAC),
        TRAILS("trails", Material.BLAZE_POWDER),
        MOVEMENT_EXTRAS("movement-extras", Material.FEATHER),
        FUN_GADGETS("fun-gadgets", Material.FIREWORK_ROCKET);

        private final String key;
        private final Material icon;

        Category(String key, Material icon) {
            this.key = key;
            this.icon = icon;
        }
    }

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final Predicate<Player> access;
    private final PlayerRiding riding;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private long generation;

    GadgetsMenu(JavaPlugin plugin, MessageService messages, Predicate<Player> access, PlayerRiding riding) {
        this.plugin = plugin;
        this.messages = messages;
        this.access = access;
        this.riding = riding;
    }

    boolean open(Player player) {
        if (!access.test(player)) return false;
        UUID owner = player.getUniqueId();
        if (openInventories.get(owner) == player.getOpenInventory().getTopInventory()) return false;

        long openedGeneration = generation;
        var holder = new Holder(owner);
        var inventory = plugin.getServer().createInventory(holder, LAYOUT.size(), messages.localize(player, tr("gadgets.title")));
        holder.inventory = inventory;
        var categories = Category.values();
        for (int index = 0; index < categories.length; index++) {
            var category = categories[index];
            if (category == Category.PLAYER_RIDING) {
                renderRiding(player, inventory);
                continue;
            }
            var item = UiItems.literal(category.icon, tr("gadgets.categories." + category.key),
                    MessageStyle.TEXT, List.of(tr("gadgets.unavailable").color(MessageStyle.MUTED)));
            inventory.setItem(LAYOUT.contentSlot(index), UiItems.render(UiItems.localize(item, messages, player)));
        }
        var opened = player.openInventory(inventory);
        if (opened == null || opened.getTopInventory() != inventory) return false;
        if (openedGeneration != generation || !access.test(player)) {
            closeOwned(player, inventory);
            return false;
        }
        openInventories.put(owner, inventory);
        return true;
    }

    private void renderRiding(Player player, Inventory inventory) {
        boolean active = riding.active(player);
        var lore = List.of(MessageStyle.status(tr(active ? "gadgets.riding.active" : "gadgets.riding.inactive"), active),
                Component.empty(), tr("gadgets.riding.pickup-help"), tr("gadgets.riding.ride-help"),
                tr("gadgets.riding.dismount-help"), tr("gadgets.riding.launch-help"), tr("gadgets.riding.open-menu"),
                Component.empty(), tr(active ? "gadgets.riding.disable" : "gadgets.riding.enable"));
        var item = UiItems.literal(Material.SADDLE, tr("gadgets.categories.player-riding"), MessageStyle.TEXT, lore);
        inventory.setItem(LAYOUT.contentSlot(0), UiItems.render(UiItems.localize(item, messages, player)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        var top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;
        boolean wasCancelled = event.isCancelled();
        event.setCancelled(true);
        if (wasCancelled || !(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top
                || event.getClick() != ClickType.LEFT || event.getRawSlot() != LAYOUT.contentSlot(0)
                || holder.inventory != top || !holder.owner.equals(player.getUniqueId())
                || openInventories.get(holder.owner) != top || player.getOpenInventory().getTopInventory() != top
                || !access.test(player)) return;
        if (riding.toggle(player)) renderRiding(player, top);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (top.getHolder() instanceof Holder holder && holder.inventory == top
                && holder.owner.equals(event.getPlayer().getUniqueId())) {
            openInventories.remove(holder.owner, top);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        closeFor(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        closeFor(event.getPlayer());
    }

    private void closeFor(Player player) {
        closeOwned(player, openInventories.remove(player.getUniqueId()));
    }

    void closeAll() {
        generation++;
        var inventories = Map.copyOf(openInventories);
        openInventories.clear();
        inventories.forEach((owner, inventory) -> {
            try {
                Player player = plugin.getServer().getPlayer(owner);
                if (player != null) closeOwned(player, inventory);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not close Gadgets menu for " + owner, failure);
            }
        });
    }

    private void closeOwned(Player player, Inventory inventory) {
        if (inventory != null && player.getOpenInventory().getTopInventory() == inventory) player.closeInventory();
    }

    private static final class Holder implements InventoryHolder {
        private final UUID owner;
        private Inventory inventory;

        private Holder(UUID owner) {
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
