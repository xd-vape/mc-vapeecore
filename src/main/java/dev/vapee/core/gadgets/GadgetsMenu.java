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
    private static final int EQUIPMENT_STATUS_SLOT = 22;

    private enum Type { ITEM, DIRECT_MENU }

    private enum Category {
        PLAYER_RIDING("player-riding", Material.SADDLE, Type.ITEM),
        SIZE_CHANGER("size-changer", Material.SLIME_BALL, Type.DIRECT_MENU),
        GLOW("glow", Material.GLOW_INK_SAC, Type.DIRECT_MENU),
        TRAILS("trails", Material.BLAZE_POWDER, Type.DIRECT_MENU),
        MOVEMENT_EXTRAS("movement-extras", Material.FEATHER, Type.DIRECT_MENU),
        FUN_GADGETS("fun-gadgets", Material.FIREWORK_ROCKET, Type.DIRECT_MENU);

        private final String key;
        private final Material icon;
        private final Type type;

        Category(String key, Material icon, Type type) {
            this.key = key;
            this.icon = icon;
            this.type = type;
        }
    }

    private final JavaPlugin plugin;
    private final MessageService messages;
    private final Predicate<Player> access;
    private final PlayerRiding riding;
    private final GadgetEquipment equipment;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private long generation;

    GadgetsMenu(JavaPlugin plugin, MessageService messages, Predicate<Player> access, PlayerRiding riding,
                GadgetEquipment equipment) {
        this.plugin = plugin;
        this.messages = messages;
        this.access = access;
        this.riding = riding;
        this.equipment = equipment;
    }

    boolean open(Player player) {
        riding.reconcile(player);
        if (!canOpen(player)) return false;
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
                    MessageStyle.TEXT, List.of(tr("gadgets.description." + category.key),
                            tr("gadgets.unavailable").color(MessageStyle.MUTED)));
            inventory.setItem(LAYOUT.contentSlot(index), UiItems.render(UiItems.localize(item, messages, player)));
        }
        renderCount(player, inventory);
        var opened = player.openInventory(inventory);
        if (opened == null || opened.getTopInventory() != inventory) return false;
        if (openedGeneration != generation || !canOpen(player)) {
            closeOwned(player, inventory);
            return false;
        }
        openInventories.put(owner, inventory);
        return true;
    }

    private boolean canOpen(Player player) {
        return access.test(player) && player.hasPermission(GadgetsModule.USE_PERMISSION);
    }

    private void renderRiding(Player player, Inventory inventory) {
        boolean active = riding.active(player);
        var lore = !riding.permitted(player) ? List.of(tr("gadgets.riding.description"), MessageStyle.error(tr("gadgets.locked")))
                : active ? List.of(MessageStyle.success(tr("gadgets.riding.active")), tr("gadgets.riding.disable"))
                : List.of(tr("gadgets.riding.description"), tr("gadgets.riding.enable"));
        var item = UiItems.literal(Material.SADDLE, tr("gadgets.categories.player-riding"), MessageStyle.TEXT, lore);
        inventory.setItem(LAYOUT.contentSlot(0), UiItems.render(UiItems.localize(item, messages, player)));
    }

    private void renderCount(Player player, Inventory inventory) {
        var count = tr("gadgets.equipped-count", Map.of(
                "count", Component.text(equipment.count(player)).color(MessageStyle.accent()),
                "limit", Component.text(equipment.limit()).color(MessageStyle.accent())));
        var item = UiItems.literal(Material.PAPER, count, MessageStyle.TEXT, List.of());
        inventory.setItem(EQUIPMENT_STATUS_SLOT, UiItems.render(UiItems.localize(item, messages, player)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent event) {
        var top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder holder)) return;
        boolean wasCancelled = event.isCancelled();
        event.setCancelled(true);
        if (wasCancelled || !(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != top
                || event.getClick() != ClickType.LEFT
                || holder.inventory != top || !holder.owner.equals(player.getUniqueId())
                || openInventories.get(holder.owner) != top || player.getOpenInventory().getTopInventory() != top) return;
        riding.reconcile(player);
        if (!canOpen(player)) return;
        int index = LAYOUT.contentSlots().indexOf(event.getRawSlot());
        if (index < 0 || Category.values()[index].type != Type.ITEM) return;
        riding.toggle(player);
        renderRiding(player, top);
        renderCount(player, top);
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
