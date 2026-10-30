package dev.vapee.core.lobby.experience.navigator;

import java.util.*;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable Navigator-only block selector. Empty hotbar slots only; no item escrow or world writes. */
public final class NavigatorRegionTool implements Listener {
    record Target(UUID owner, UUID session, String menu, String entry) { }
    private record Lease(Target target, UUID nonce, int slot, int previousHeld, ItemStack issued) { }
    private final NamespacedKey key;
    private final NavigatorEditor editor;
    private final Supplier<Set<Integer>> reserved;
    private final Supplier<ItemStack> items;
    private final Logger logger;
    @FunctionalInterface interface Task { void cancel(); }
    private static final class Pending { Task task; }
    private final java.util.function.Function<Runnable, Task> nextTick;
    private final Map<UUID, Pending> pending = new HashMap<>();
    private final Set<UUID> interactionFence = new HashSet<>();
    private final Map<UUID, Lease> leases = new HashMap<>();
    private boolean enabled = true;

    public NavigatorRegionTool(JavaPlugin plugin, NavigatorEditor editor, Supplier<Set<Integer>> reserved) {
        this(new NamespacedKey(plugin, "navigator_region_tool"), editor, reserved, NavigatorRegionTool::newTool, plugin.getLogger(),
                action -> { var task = plugin.getServer().getScheduler().runTask(plugin, action); return task::cancel; });
    }
    NavigatorRegionTool(NamespacedKey key, NavigatorEditor editor, Supplier<Set<Integer>> reserved,
                        Supplier<ItemStack> items, Logger logger, java.util.function.Function<Runnable, Task> nextTick) {
        this.key = key; this.editor = editor; this.reserved = reserved; this.items = items; this.logger = logger; this.nextTick = nextTick;
    }
    /** CAN_BREAK supplies Adventure client hit routing; every server-side world action is denied below. */
    private static ItemStack newTool() {
        var item = new ItemStack(Material.BLAZE_ROD);
        item.setData(io.papermc.paper.datacomponent.DataComponentTypes.CAN_BREAK,
                io.papermc.paper.datacomponent.item.ItemAdventurePredicate.itemAdventurePredicate(
                        List.of(io.papermc.paper.block.BlockPredicate.predicate().build())));
        return item;
    }
    private String identity(Target target, UUID nonce) {
        return target.owner() + ":" + target.session() + ":" + nonce + ":" + target.menu() + ":" + target.entry();
    }
    boolean marked(ItemStack item) {
        return item != null && !item.isEmpty() && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(key);
    }
    private boolean matches(ItemStack item, Lease lease) {
        return marked(item) && item.equals(lease.issued())
                && identity(lease.target(), lease.nonce()).equals(item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING));
    }
    void start(Player player) {
        if (!enabled) return;
        Target target = editor.toolTarget(player);
        editor.closeForTool(player); // Close first: vanilla may return a real cursor item to the inventory.
        cancel(player, false);
        var inventory = player.getInventory();
        if (inventory == null) return;
        int held = inventory.getHeldItemSlot(), slot = -1;
        Set<Integer> protectedSlots = Set.copyOf(reserved.get());
        for (int i = 0; i < 9; i++) {
            int candidate = (held + i) % 9;
            var current = inventory.getItem(candidate);
            if (!protectedSlots.contains(candidate) && (current == null || current.isEmpty())) { slot = candidate; break; }
        }
        if (slot < 0) {
            editor.toolFeedback(player, "Free a non-reserved hotbar slot first.", "Mache zuerst einen nicht reservierten Hotbar-Platz frei."); return;
        }
        try {
            ItemStack item = Objects.requireNonNull(items.get());
            if (item.getType() != Material.BLAZE_ROD || item.getAmount() != 1) throw new IllegalStateException("Invalid Navigator tool factory");
            var meta = Objects.requireNonNull(item.getItemMeta());
            UUID nonce = UUID.randomUUID();
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, identity(target, nonce));
            meta.displayName(Component.text("Navigator • Region", NamedTextColor.AQUA));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_DESTROYS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            if (!item.setItemMeta(meta)) throw new IllegalStateException("Region tool metadata rejected");
            var lease = new Lease(target, nonce, slot, held, item.clone());
            leases.put(player.getUniqueId(), lease); // Setter failures remain eligible for exact cleanup.
            inventory.setItem(slot, item);
            if (!matches(inventory.getItem(slot), lease) || !editor.toolValid(player, target)) { cancel(player, false); return; }
            inventory.setHeldItemSlot(slot);
            editor.toolFeedback(player, "Left-click: Pos1; right-click: Pos2. /navigator region cancel",
                    "Linksklick: Pos1; Rechtsklick: Pos2. /navigator region cancel");
        } catch (RuntimeException failure) {
            cancel(player, false); logger.log(Level.WARNING, "Could not give Navigator region tool to " + player.getUniqueId(), failure);
            editor.toolFailure(player, failure);
        }
    }
    void reconcile(Player player) {
        Lease lease = leases.get(player.getUniqueId());
        if (lease == null) return;
        try {
            var inventory = player.getInventory();
            if (!enabled || !editor.toolValid(player, lease.target()) || inventory == null
                    || reserved.get().contains(lease.slot()) || inventory.getHeldItemSlot() != lease.slot()
                    || !matches(inventory.getItem(lease.slot()), lease)) {
                cancel(player, true);
                if (!editor.toolAuthorized(player)) editor.toolPermissionLost(player);
            }
        } catch (RuntimeException failure) { cancel(player, false); logger.log(Level.WARNING, "Could not reconcile Navigator region tool", failure); }
    }
    void cancel(Player player, boolean notify) { cancel(player, notify, true); }
    private void cancel(Player player, boolean notify, boolean restoreHeld) {
        Lease lease = leases.remove(player.getUniqueId());
        try {
            var inventory = player.getInventory();
            boolean ownedHeld = lease != null && inventory != null && inventory.getHeldItemSlot() == lease.slot()
                    && matches(inventory.getItem(lease.slot()), lease);
            purge(player);
            if (restoreHeld && ownedHeld) inventory.setHeldItemSlot(lease.previousHeld());
        } catch (RuntimeException failure) { logger.log(Level.WARNING, "Could not release Navigator region tool for " + player.getUniqueId(), failure); }
        if (notify && lease != null) editor.toolFeedback(player, "Selection canceled; pending Pos1 is retained.", "Markierung abgebrochen; eine offene Pos1 bleibt erhalten.");
    }
    /** Restart/re-enable recovery: only our disposable PDC markers are removed; no genuine item was escrowed. */
    public void available(Player player) { if (!leases.containsKey(player.getUniqueId())) purge(player); }
    private void purge(Player player) {
        var inventory = player.getInventory();
        if (inventory != null) {
            var contents = inventory.getContents();
            for (int i = 0; i < contents.length; i++) if (marked(contents[i])) inventory.setItem(i, null);
        }
        if (marked(player.getItemOnCursor())) player.setItemOnCursor(null);
    }
    /** Cancellation may return a cursor/drop stack after the event. Recheck once after vanilla finishes. */
    private void defer(Player player) {
        UUID id = player.getUniqueId();
        if (!enabled || pending.containsKey(id)) return;
        var request = new Pending(); pending.put(id, request);
        try {
            request.task = nextTick.apply(() -> {
                if (!enabled || pending.get(id) != request) return;
                pending.remove(id); interactionFence.remove(id);
                var current = editor.toolPlayer(id); if (current == null) return;
                reconcile(current); Lease lease = leases.get(id);
                if (lease == null) { purge(current); return; }
                var inventory = current.getInventory(); var contents = inventory.getContents();
                for (int slot = 0; slot < contents.length; slot++)
                    if (marked(contents[slot]) && (slot != lease.slot() || !matches(contents[slot], lease))) inventory.setItem(slot, null);
                if (marked(current.getItemOnCursor())) current.setItemOnCursor(null);
            });
        } catch (RuntimeException failure) { pending.remove(id); interactionFence.remove(id); logger.log(Level.WARNING, "Could not schedule Navigator tool post-event cleanup", failure); }
    }
    private boolean carrying(Player player) {
        var inventory = player.getInventory();
        return inventory != null && (marked(inventory.getItemInMainHand()) || marked(inventory.getItemInOffHand()));
    }
    private boolean guarding(Player player) { return leases.containsKey(player.getUniqueId()) || carrying(player); }
    private static void deny(PlayerInteractEvent event) {
        event.setCancelled(true); event.setUseInteractedBlock(Event.Result.DENY); event.setUseItemInHand(Event.Result.DENY);
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!marked(event.getItem()) && !carrying(player) && !leases.containsKey(player.getUniqueId()) && !interactionFence.contains(player.getUniqueId())) return;
        deny(event); // Also observe already-denied Adventure/lobby events, without enabling any world action.
        interactionFence.add(player.getUniqueId()); defer(player);
        reconcile(player);
        Lease lease = leases.get(player.getUniqueId());
        if (lease == null) { purge(player); return; }
        if (event.getHand() != EquipmentSlot.HAND || !matches(event.getItem(), lease)) return;
        boolean first = event.getAction() == Action.LEFT_CLICK_BLOCK;
        if (!first && event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        try {
            editor.toolPosition(player, lease.target(), event.getClickedBlock().getLocation(), first);
            if (!first) cancel(player, false);
        } catch (RuntimeException failure) { editor.toolFailure(player, failure); } // Correctable errors retain tool and Pos1.
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void damage(BlockDamageEvent event) { if (guarding(event.getPlayer())) { event.setCancelled(true); event.setInstaBreak(false); reconcile(event.getPlayer()); } }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void breakBlock(BlockBreakEvent event) { if (guarding(event.getPlayer())) { event.setCancelled(true); reconcile(event.getPlayer()); } }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void place(BlockPlaceEvent event) { if (marked(event.getItemInHand()) || guarding(event.getPlayer())) { event.setCancelled(true); reconcile(event.getPlayer()); } }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityUse(PlayerInteractEntityEvent event) { if (guarding(event.getPlayer())) { event.setCancelled(true); reconcile(event.getPlayer()); } }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void entityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && guarding(player)) { event.setCancelled(true); reconcile(player); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void hanging(org.bukkit.event.hanging.HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player player && guarding(player)) { event.setCancelled(true); reconcile(player); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void armorStand(PlayerArmorStandManipulateEvent event) {
        if (marked(event.getPlayerItem()) || marked(event.getArmorStandItem()) || guarding(event.getPlayer())) { event.setCancelled(true); reconcile(event.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int hotbar = event.getHotbarButton();
        boolean hotbarTool = hotbar >= 0 && hotbar < 9 && player.getInventory() != null && marked(player.getInventory().getItem(hotbar));
        if (leases.containsKey(player.getUniqueId()) || marked(event.getCurrentItem()) || marked(event.getCursor()) || hotbarTool
                || event.getClick() == ClickType.SWAP_OFFHAND && carrying(player)) {
            event.setCancelled(true); reconcile(player);
            if (!leases.containsKey(player.getUniqueId())) purge(player);
            defer(player);
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && (leases.containsKey(player.getUniqueId()) || marked(event.getOldCursor())
                || event.getNewItems().values().stream().anyMatch(this::marked))) { event.setCancelled(true); reconcile(player); if (!leases.containsKey(player.getUniqueId())) purge(player); defer(player); }
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void drop(PlayerDropItemEvent event) {
        if (!marked(event.getItemDrop().getItemStack())) return;
        event.setCancelled(true);
        var lease = leases.get(event.getPlayer().getUniqueId());
        if (lease == null || !editor.toolValid(event.getPlayer(), lease.target()) || !matches(event.getItemDrop().getItemStack(), lease))
            cancel(event.getPlayer(), true);
        if (!editor.toolAuthorized(event.getPlayer())) editor.toolPermissionLost(event.getPlayer());
        defer(event.getPlayer()); // Vanilla returns the stack; keep a valid lease, purge stale/forged returns.
    }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void swap(PlayerSwapHandItemsEvent event) {
        if (marked(event.getMainHandItem()) || marked(event.getOffHandItem())) { event.setCancelled(true); reconcile(event.getPlayer()); defer(event.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void held(PlayerItemHeldEvent event) {
        var lease = leases.get(event.getPlayer().getUniqueId());
        if (lease != null && event.getNewSlot() != lease.slot()) cancel(event.getPlayer(), true, false);
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void move(PlayerMoveEvent event) { if (leases.containsKey(event.getPlayer().getUniqueId())) reconcile(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR)
    public void world(PlayerChangedWorldEvent event) { if (leases.containsKey(event.getPlayer().getUniqueId())) reconcile(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void join(PlayerJoinEvent event) { available(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void quit(PlayerQuitEvent event) { cancel(event.getPlayer(), false); }
    @EventHandler(priority = EventPriority.LOWEST)
    public void death(PlayerDeathEvent event) { cancel(event.getEntity(), false); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void deathDrops(PlayerDeathEvent event) { event.getDrops().removeIf(this::marked); event.getItemsToKeep().removeIf(this::marked); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void pickup(EntityPickupItemEvent event) { if (marked(event.getItem().getItemStack())) { event.setCancelled(true); event.getItem().remove(); } }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void moveItem(InventoryMoveItemEvent event) { if (marked(event.getItem())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void hopper(InventoryPickupItemEvent event) { if (marked(event.getItem().getItemStack())) { event.setCancelled(true); event.getItem().remove(); } }
    @EventHandler(priority = EventPriority.HIGHEST)
    public void spawn(ItemSpawnEvent event) { if (marked(event.getEntity().getItemStack())) event.setCancelled(true); }
    public void shutdown() {
        enabled = false;
        var owners = new HashSet<>(leases.keySet()); owners.addAll(pending.keySet());
        try {
            for (var request : pending.values()) if (request.task != null) {
                try { request.task.cancel(); } catch (RuntimeException failure) { logger.log(Level.WARNING, "Could not cancel Navigator post-event cleanup", failure); }
            }
            for (UUID id : owners) {
                try { var player = editor.toolPlayer(id); if (player != null) cancel(player, false); }
                catch (RuntimeException failure) { logger.log(Level.WARNING, "Could not clean Navigator tool for " + id, failure); }
            }
        } finally { leases.clear(); pending.clear(); interactionFence.clear(); }
    }
}
