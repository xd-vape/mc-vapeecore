package dev.vapee.core.gadgets;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
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
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

final class GadgetsMenu implements Listener, LobbyPlayerStateListener, ActivityMembershipListener {
    private static final MenuLayout<Category> LAYOUT = MenuLayout.builder(27, Category.class)
            .content(List.of(10, 11, 12, 14, 15, 16))
            .build();
    private static final MenuLayout<SizeChanger.Size> SIZES = MenuLayout.builder(27, SizeChanger.Size.class)
            .content(List.of(10, 11, 12, 13, 14))
            .build();
    private static final MenuLayout<ParticleTrails.Trail> TRAILS = MenuLayout.builder(27, ParticleTrails.Trail.class)
            .content(List.of(10, 11, 12, 13, 14, 16))
            .build();
    private static final int EQUIPMENT_STATUS_SLOT = 22;
    private static final int BACK_SLOT = 22;

    private enum Type { ITEM, DIRECT_MENU, DIRECT }

    private enum Page {
        MAIN("gadgets.title"), SIZES("gadgets.categories.size-changer"), TRAILS("gadgets.categories.trails");

        private final String title;
        Page(String title) { this.title = title; }
    }

    private enum Category {
        PLAYER_RIDING("player-riding", Material.SADDLE, Type.ITEM),
        SIZE_CHANGER("size-changer", Material.SLIME_BALL, Type.DIRECT_MENU),
        GLOW("glow", Material.GLOW_INK_SAC, Type.DIRECT),
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
    private final SizeChanger sizeChanger;
    private final GlowGadget glow;
    private final ParticleTrails trails;
    private final Map<UUID, BukkitTask> navigation = new HashMap<>();
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private long generation;

    GadgetsMenu(JavaPlugin plugin, MessageService messages, Predicate<Player> access, PlayerRiding riding,
                GadgetEquipment equipment, SizeChanger sizeChanger, GlowGadget glow, ParticleTrails trails) {
        this.plugin = plugin;
        this.messages = messages;
        this.access = access;
        this.riding = riding;
        this.equipment = equipment;
        this.sizeChanger = sizeChanger;
        this.glow = glow;
        this.trails = trails;
    }

    boolean open(Player player) {
        riding.reconcile(player);
        sizeChanger.reconcile(player);
        glow.reconcile(player);
        trails.reconcile(player);
        if (!canOpen(player) || openInventories.get(player.getUniqueId()) == player.getOpenInventory().getTopInventory()) {
            return false;
        }
        return open(player, Page.MAIN);
    }

    private boolean open(Player player, Page page) {
        long openedGeneration = generation;
        var holder = new Holder(player.getUniqueId(), page);
        int size = switch (page) {
            case MAIN -> LAYOUT.size();
            case SIZES -> SIZES.size();
            case TRAILS -> TRAILS.size();
        };
        var inventory = plugin.getServer().createInventory(holder, size, messages.localize(player, tr(page.title)));
        holder.inventory = inventory;
        switch (page) {
            case MAIN -> renderMain(player, inventory);
            case SIZES -> renderSizes(player, inventory);
            case TRAILS -> renderTrails(player, inventory);
        }
        var opened = player.openInventory(inventory);
        if (opened == null || player.getOpenInventory().getTopInventory() != inventory) return false;
        if (openedGeneration != generation || !canOpen(player, page)) {
            closeOwned(player, inventory);
            return false;
        }
        openInventories.put(holder.owner, inventory);
        return true;
    }

    private void renderMain(Player player, Inventory inventory) {
        var categories = Category.values();
        for (int index = 0; index < categories.length; index++) {
            var category = categories[index];
            if (category == Category.PLAYER_RIDING) {
                renderRiding(player, inventory);
                continue;
            }
            if (category == Category.GLOW) {
                renderGlow(player, inventory);
                continue;
            }
            Component status = switch (category) {
                case SIZE_CHANGER -> entryStatus(sizeChanger.permitted(player), "gadgets.size.open");
                case TRAILS -> entryStatus(trails.permitted(player), "gadgets.trails.open");
                default -> tr("gadgets.unavailable").color(MessageStyle.MUTED);
            };
            var item = UiItems.literal(category.icon, tr("gadgets.categories." + category.key),
                    MessageStyle.TEXT, List.of(tr("gadgets.description." + category.key), status));
            inventory.setItem(LAYOUT.contentSlot(index), UiItems.render(UiItems.localize(item, messages, player)));
        }
        renderCount(player, inventory);
    }

    private Component entryStatus(boolean permitted, String key) {
        return permitted ? tr(key) : MessageStyle.error(tr("gadgets.locked"));
    }

    private void renderSizes(Player player, Inventory inventory) {
        var selected = sizeChanger.selected(player);
        for (var size : SizeChanger.Size.values()) {
            boolean active = size == selected;
            var multiplier = Component.text(Double.toString(size.factor), MessageStyle.accent());
            var lore = List.of(tr("gadgets.size.multiplier", Map.of("factor", multiplier)),
                    active ? MessageStyle.success(tr("gadgets.size.selected")) : tr("gadgets.size.apply"));
            var item = UiItems.literal(active ? Material.LIME_DYE : Material.GRAY_DYE,
                    tr("gadgets.size." + size.key), MessageStyle.TEXT, lore);
            inventory.setItem(SIZES.contentSlot(size.ordinal()), UiItems.render(UiItems.localize(item, messages, player)));
        }
        renderBack(player, inventory, "gadgets.size.back");
    }

    private void renderTrails(Player player, Inventory inventory) {
        var selected = trails.selected(player);
        for (var trail : ParticleTrails.Trail.values()) {
            var status = trail == selected ? MessageStyle.success(tr("gadgets.trails.active")) : tr("gadgets.trails.apply");
            var item = UiItems.literal(trail.icon, tr("gadgets.trails." + trail.key), MessageStyle.TEXT, List.of(status));
            inventory.setItem(TRAILS.contentSlot(trail.ordinal()), UiItems.render(UiItems.localize(item, messages, player)));
        }
        renderBack(player, inventory, "gadgets.trails.back");
    }

    private void renderBack(Player player, Inventory inventory, String label) {
        var back = UiItems.literal(Material.ARROW, tr(label), MessageStyle.TEXT, List.of());
        inventory.setItem(BACK_SLOT, UiItems.render(UiItems.localize(back, messages, player)));
    }

    private boolean canOpen(Player player, Page page) {
        return canOpen(player) && switch (page) {
            case MAIN -> true;
            case SIZES -> sizeChanger.permitted(player);
            case TRAILS -> trails.permitted(player);
        };
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

    private void renderGlow(Player player, Inventory inventory) {
        var description = tr("gadgets.description.glow");
        var lore = !glow.permitted(player) ? List.of(description, MessageStyle.error(tr("gadgets.locked")))
                : glow.active(player) ? List.of(MessageStyle.success(tr("gadgets.glow.active")), tr("gadgets.glow.disable"))
                : List.of(description, tr("gadgets.glow.enable"));
        var item = UiItems.literal(Category.GLOW.icon, tr("gadgets.categories.glow"), MessageStyle.TEXT, lore);
        inventory.setItem(LAYOUT.contentSlot(Category.GLOW.ordinal()), UiItems.render(UiItems.localize(item, messages, player)));
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
        sizeChanger.reconcile(player);
        glow.reconcile(player);
        trails.reconcile(player);
        if (holder.page == Page.MAIN && event.getRawSlot() == LAYOUT.contentSlot(Category.GLOW.ordinal()) && access.test(player)) {
            glow.toggle(player);
            renderGlow(player, top);
            return;
        }
        if (!canOpen(player)) return;
        if (holder.page != Page.MAIN && event.getRawSlot() == BACK_SLOT) {
            navigate(player, holder, Page.MAIN);
            return;
        }
        if (holder.page == Page.TRAILS) {
            selectTrail(player, top, event.getRawSlot());
            return;
        }
        if (holder.page == Page.SIZES) {
            int index = SIZES.contentSlots().indexOf(event.getRawSlot());
            if (index < 0) return;
            var result = sizeChanger.select(player, SizeChanger.Size.values()[index]);
            switch (result) {
                case DENIED -> messages.send(player, tr("gadgets.size.denied"));
                case UNAVAILABLE -> messages.send(player, tr("gadgets.size.missing-scale"));
                case INCOMPATIBLE -> messages.send(player, tr("gadgets.size.incompatible"));
                case APPLIED -> { }
            }
            renderSizes(player, top);
            return;
        }
        int index = LAYOUT.contentSlots().indexOf(event.getRawSlot());
        if (index < 0) return;
        var category = Category.values()[index];
        if (category == Category.SIZE_CHANGER) {
            if (sizeChanger.permitted(player)) navigate(player, holder, Page.SIZES);
            else {
                messages.send(player, tr("gadgets.size.denied"));
                renderMain(player, top);
            }
        } else if (category == Category.TRAILS) {
            if (trails.permitted(player)) navigate(player, holder, Page.TRAILS);
            else {
                messages.send(player, tr("gadgets.trails.denied"));
                renderMain(player, top);
            }
        } else if (category.type == Type.ITEM) {
            riding.toggle(player);
            renderRiding(player, top);
            renderCount(player, top);
        }
    }

    private void selectTrail(Player player, Inventory inventory, int rawSlot) {
        int index = TRAILS.contentSlots().indexOf(rawSlot);
        if (index < 0) return;
        var trail = ParticleTrails.Trail.values()[index];
        var previous = trails.selected(player);
        if (!trails.select(player, trail)) {
            messages.send(player, tr("gadgets.trails.denied"));
        } else if (trail != previous) {
            if (trail == ParticleTrails.Trail.NONE) messages.send(player, tr("gadgets.trails.disabled"));
            else messages.send(player, tr("gadgets.trails.selected",
                    Map.of("trail", tr("gadgets.trails." + trail.key).color(MessageStyle.accent()))));
        }
        renderTrails(player, inventory);
    }

    private void navigate(Player player, Holder from, Page page) {
        // Bukkit forbids opening another inventory in the click callback.
        navigation.computeIfAbsent(from.owner, owner -> plugin.getServer().getScheduler().runTask(plugin, () -> {
            navigation.remove(owner);
            if (openInventories.get(owner) != from.inventory || player.getOpenInventory().getTopInventory() != from.inventory
                    || !canOpen(player, page)) return;
            open(player, page);
        }));
    }

    private void cancelNavigation(UUID owner) {
        var task = navigation.remove(owner);
        if (task != null) task.cancel();
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
            if (openInventories.remove(holder.owner, top)) cancelNavigation(holder.owner);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) { closeFor(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { closeSubmenu(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) { closeSubmenu(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) { closeFor(event.getEntity()); }

    @Override public void beforeStateChange(Player player) { closeFor(player); }
    @Override public void afterStateChange(UUID id) { }
    @Override public void beforeJoin(Player player) { closeFor(player); }
    @Override public void membershipChanged(UUID id) { }

    private void closeSubmenu(Player player) {
        var inventory = openInventories.get(player.getUniqueId());
        if (inventory != null && inventory.getHolder() instanceof Holder holder
                && (holder.page != Page.MAIN || navigation.containsKey(holder.owner))) closeFor(player);
    }

    private void closeFor(Player player) {
        UUID owner = player.getUniqueId();
        cancelNavigation(owner);
        closeOwned(player, openInventories.remove(owner));
    }

    void closeAll() {
        generation++;
        navigation.values().forEach(BukkitTask::cancel);
        navigation.clear();
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
        private final Page page;
        private Inventory inventory;

        private Holder(UUID owner, Page page) {
            this.owner = owner;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
