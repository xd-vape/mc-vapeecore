package dev.vapee.core.gadgets;

import static dev.vapee.core.i18n.Translations.tr;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.lobby.item.LobbyItemDefinition;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.message.MessageStyle;
import dev.vapee.core.utility.UtilityFlightListener;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInputEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

final class PlayerRiding implements Listener, LobbyPlayerStateListener, ActivityMembershipListener, UtilityFlightListener {
    static final String ITEM = "gadgets";
    private static final double FORWARD = 0.75;
    private static final double UPWARD = 0.32;
    private static final LobbyItemDefinition.Appearance SADDLE = new LobbyItemDefinition.Appearance(
            Material.SADDLE, tr("gadgets.categories.player-riding"), List.of(tr("gadgets.riding.open-menu")), false);

    private record Ride(Player carrier, Player rider) {
        boolean attached() {
            return carrier.equals(rider.getVehicle()) && carrier.getPassengers().contains(rider);
        }
    }

    private final JavaPlugin plugin;
    private final LobbyItemService items;
    private final MessageService messages;
    private final Predicate<Player> access;
    private final Predicate<UUID> incompatibleFlight;
    private final BiPredicate<Player, Player> compatiblePair;
    private final NamespacedKey saddleKey;
    private final Map<UUID, String> active = new HashMap<>();
    private final Map<UUID, Ride> rides = new HashMap<>();
    private final Map<UUID, Integer> lastInteraction = new HashMap<>();
    private Ride mounting;
    private Ride detaching;
    private boolean transitioning;
    private boolean stopping;

    PlayerRiding(JavaPlugin plugin, LobbyItemService items, MessageService messages, Predicate<Player> access,
                 Predicate<UUID> incompatibleFlight, BiPredicate<Player, Player> compatiblePair) {
        this.plugin = plugin;
        this.items = items;
        this.messages = messages;
        this.access = access;
        this.incompatibleFlight = incompatibleFlight;
        this.compatiblePair = compatiblePair;
        saddleKey = new NamespacedKey(plugin, "riding_session");
    }

    boolean active(Player player) {
        return active.containsKey(player.getUniqueId());
    }

    LobbyItemDefinition.Appearance appearance(Player player, LobbyItemDefinition definition) {
        return active(player) ? SADDLE : definition.appearance();
    }

    void decorate(Player player, ItemMeta meta) {
        var token = active.get(player.getUniqueId());
        if (token != null) meta.getPersistentDataContainer().set(saddleKey, PersistentDataType.STRING, token);
    }

    boolean toggle(Player player) {
        if (stopping || transitioning || !access.test(player)) return false;
        if (active(player)) {
            deactivate(player);
            return true;
        }
        if (!items.isInConfiguredSlot(player, ITEM)) {
            tell(player, "slot-unavailable", null);
            return false;
        }
        active.put(player.getUniqueId(), UUID.randomUUID().toString());
        try {
            items.refreshItem(player, ITEM);
            return true;
        } catch (RuntimeException failure) {
            active.remove(player.getUniqueId());
            throw failure;
        }
    }

    private void deactivate(Player player) {
        UUID id = player.getUniqueId();
        boolean wasActive = active.remove(id) != null;
        lastInteraction.remove(id);
        release(player);
        if (wasActive) items.refreshItem(player, ITEM);
    }

    private void cleanupPlayer(Player player) {
        cleanup(() -> deactivate(player));
    }

    boolean holdsSaddle(Player player) {
        return !stopping && !transitioning && access.test(player) && items.isHeldInConfiguredSlot(player, ITEM)
                && matchesSaddle(player, player.getInventory().getItemInMainHand());
    }

    private boolean matchesSaddle(Player player, ItemStack item) {
        String token = active.get(player.getUniqueId());
        return token != null && item != null && item.getType() == Material.SADDLE && item.getAmount() == 1
                && items.getItemId(item).filter(ITEM::equals).isPresent()
                && token.equals(item.getItemMeta().getPersistentDataContainer().get(saddleKey, PersistentDataType.STRING));
    }

    private boolean isSessionItem(ItemStack item) {
        return item != null && item.getType() == Material.SADDLE && item.getItemMeta() != null
                && items.getItemId(item).filter(ITEM::equals).isPresent()
                && item.getItemMeta().getPersistentDataContainer().has(saddleKey, PersistentDataType.STRING);
    }

    boolean useHotbar(Player player, org.bukkit.event.block.Action click, Predicate<Player> openMenu) {
        if (!active(player)) return click.isRightClick() && !isSessionItem(player.getInventory().getItemInMainHand())
                && openMenu.test(player);
        if (!holdsSaddle(player)) return false;
        if (click == org.bukkit.event.block.Action.LEFT_CLICK_AIR) launch(player);
        return click == org.bukkit.event.block.Action.RIGHT_CLICK_AIR && player.isSneaking() && openMenu.test(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityClick(PlayerInteractEntityEvent event) {
        if (event.isCancelled() || event.getHand() != EquipmentSlot.HAND || !holdsSaddle(event.getPlayer())) return;
        event.setCancelled(true);
        if (event.getRightClicked() instanceof Player target) mount(event.getPlayer(), target, true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityClickAt(PlayerInteractAtEntityEvent event) {
        onEntityClick(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAttack(PrePlayerAttackEntityEvent event) {
        if (event.isCancelled() || !holdsSaddle(event.getPlayer())) return;
        event.setCancelled(true);
        if (event.getAttacked() instanceof Player target) mount(event.getPlayer(), target, false);
    }

    private boolean ready(Player player) {
        return !stopping && !transitioning && access.test(player) && !player.isDead() && !player.isFlying()
                && !player.isGliding() && !player.isSleeping() && !player.isSwimming()
                && !player.isRiptiding() && !player.isClimbing() && !player.isInWater()
                && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                && !incompatibleFlight.test(player.getUniqueId());
    }

    private boolean free(Player player) {
        return !rides.containsKey(player.getUniqueId()) && player.getVehicle() == null && player.getPassengers().isEmpty();
    }

    private boolean allowInteraction(Player player) {
        int tick = plugin.getServer().getCurrentTick();
        var previous = lastInteraction.get(player.getUniqueId());
        if (previous != null && tick - previous >= 0 && tick - previous < 3) return false;
        lastInteraction.put(player.getUniqueId(), tick);
        return true;
    }

    private void mount(Player actor, Player target, boolean pickUp) {
        if (mounting != null || actor.getUniqueId().equals(target.getUniqueId()) || !ready(actor) || !ready(target)
                || !actor.getWorld().equals(target.getWorld()) || !free(actor) || !free(target)
                || !compatiblePair.test(actor, target) || !allowInteraction(actor)) return;
        if (!target.isSneaking()) {
            tell(actor, pickUp ? "sneak-pickup" : "sneak-ride", target);
            return;
        }

        Ride ride = pickUp ? new Ride(actor, target) : new Ride(target, actor);
        rides.put(ride.carrier().getUniqueId(), ride);
        rides.put(ride.rider().getUniqueId(), ride);
        mounting = ride;
        try {
            if (!ride.carrier().addPassenger(ride.rider()) || !ride.attached()) return;
            if (rides.get(actor.getUniqueId()) != ride || !ready(actor) || !ready(target)
                    || !compatiblePair.test(actor, target)) {
                detach(ride);
                return;
            }
            // Vanilla uses this flag every ride tick. A fresh sneak press still dismounts.
            if (ride.rider().isSneaking()) ride.rider().setSneaking(false);
            tell(actor, pickUp ? "carrying" : "riding", target);
        } finally {
            mounting = null;
            if (!ride.attached()) forget(ride);
        }
    }

    private void launch(Player carrier) {
        Ride ride = rides.get(carrier.getUniqueId());
        if (ride == null || ride.carrier() != carrier || !ride.attached()) return;
        if (!ready(carrier) || !ready(ride.rider()) || !compatiblePair.test(carrier, ride.rider())) {
            detach(ride);
            return;
        }
        double yaw = Math.toRadians(carrier.getYaw());
        if (!Double.isFinite(yaw)) return;
        if (detach(ride) && ready(carrier) && ready(ride.rider()) && ride.rider().getVehicle() == null
                && carrier.getWorld().equals(ride.rider().getWorld()) && compatiblePair.test(carrier, ride.rider())) {
            ride.rider().setFallDistance(0);
            ride.rider().setVelocity(new Vector(-Math.sin(yaw) * FORWARD, UPWARD, Math.cos(yaw) * FORWARD));
            tell(carrier, "launched", ride.rider());
        }
    }

    private void tell(Player player, String key, Player target) {
        var args = target == null ? Map.<String, Component>of()
                : Map.<String, Component>of("player", Component.text(target.getName(), MessageStyle.accent()));
        messages.send(player, MessageStyle.feature(tr("gadgets.categories.player-riding"),
                MessageStyle.GADGETS_LABEL, tr("gadgets.riding." + key, args)));
    }

    @EventHandler
    public void onInput(PlayerInputEvent event) {
        Player player = event.getPlayer();
        Ride ride = rides.get(player.getUniqueId());
        if (ride != null && ride.rider() == player && event.getInput().isSneak() && !player.getCurrentInput().isSneak())
            detach(ride);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDismount(EntityDismountEvent event) {
        // Paper's removal call can be vetoed. Only override a veto on our own explicit cleanup.
        if (detaching != null && detaching.rider() == event.getEntity() && detaching.carrier() == event.getDismounted())
            event.setCancelled(false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void afterDismount(EntityDismountEvent event) {
        var ride = rides.get(event.getEntity().getUniqueId());
        if (!event.isCancelled() && ride != null && ride != detaching
                && ride.rider() == event.getEntity() && ride.carrier() == event.getDismounted()) forget(ride);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (mounting != null && mounting.rider() == event.getEntity() && mounting.carrier() == event.getMount()
                && rides.get(mounting.rider().getUniqueId()) == mounting) return;
        if (rides.containsKey(event.getEntity().getUniqueId()) || rides.containsKey(event.getMount().getUniqueId()))
            event.setCancelled(true);
    }

    private boolean detach(Ride ride) {
        if (ride == detaching) return false;
        if (ride.attached()) {
            Ride previous = detaching;
            detaching = ride;
            try {
                // CraftEntity#removePassenger delegates to stopRiding on the argument.
                // Only invoke it after checking both sides of the exact owned edge.
                ride.carrier().removePassenger(ride.rider());
            } finally {
                detaching = previous;
            }
        }
        if (ride.attached()) return false;
        forget(ride);
        return true;
    }

    private void forget(Ride ride) {
        rides.remove(ride.carrier().getUniqueId(), ride);
        rides.remove(ride.rider().getUniqueId(), ride);
    }

    private void release(Player player) {
        Ride ride = rides.get(player.getUniqueId());
        if (ride != null && ride != detaching && (ride.carrier() == player || ride.rider() == player)
                && !detach(ride)) {
            plugin.getLogger().warning("Paper declined to release owned Player Riding mount for " + player.getUniqueId());
        }
    }

    void reconcileVisibility(UUID id) {
        Ride ride = rides.get(id);
        if (ride != null && !compatiblePair.test(ride.carrier(), ride.rider())) detach(ride);
    }

    void reset() {
        if (transitioning) return;
        transitioning = true;
        try {
            clearSessions();
        } finally {
            transitioning = false;
        }
    }

    private void clearSessions() {
        var sessions = List.copyOf(active.keySet());
        active.clear();
        lastInteraction.clear();
        for (Ride ride : rides.values().stream().distinct().toList())
            cleanup(() -> {
                if (!detach(ride)) throw new IllegalStateException("Paper declined to dismount " + ride.rider().getUniqueId());
            });
        for (UUID id : sessions) cleanup(() -> {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) items.refreshItem(player, ITEM);
        });
    }

    private void cleanup(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not clean up Player Riding state", failure);
        }
    }

    void shutdown() {
        stopping = true;
        reset();
    }

    @Override public void beforeStateChange(Player player) { cleanupPlayer(player); }
    @Override public void afterStateChange(UUID id) { }
    @Override public void beforeJoin(Player player) { cleanupPlayer(player); }
    @Override public void membershipChanged(UUID id) { }
    @Override public void beforeFlightChange(Player player) { cleanup(() -> release(player)); }
    @Override public void afterFlightChange(UUID id) { }

    @EventHandler public void onQuit(PlayerQuitEvent event) { cleanupPlayer(event.getPlayer()); }
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        var inventory = event.getPlayer().getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            if (isSessionItem(contents[slot])) inventory.setItem(slot, null);
        }
    }

    @EventHandler
    public void onPickup(EntityPickupItemEvent event) {
        var item = event.getItem();
        if (isSessionItem(item.getItemStack())) {
            event.setCancelled(true);
            item.remove();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) { if (!event.isCancelled()) cleanupPlayer(event.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { cleanupPlayer(event.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) { cleanupPlayer(event.getPlayer()); }
    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        cleanupPlayer(event.getEntity());
        event.getDrops().removeIf(this::isSessionItem);
        event.getItemsToKeep().removeIf(this::isSessionItem);
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { if (!event.isCancelled()) release(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) { if (!event.isCancelled()) release(event.getPlayer()); }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) { release(event.getPlayer()); }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (event.isFlying()) release(event.getPlayer());
    }
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.isGliding() && event.getEntity() instanceof Player player) release(player);
    }
}
