package dev.vapee.core.gadgets;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

final class SizeChanger implements Listener, LobbyPlayerStateListener, ActivityMembershipListener {
    static final String PERMISSION = "vapeecore.gadgets.size-changer";

    enum Size {
        TINY("tiny", 0.5), SMALL("small", 0.75), NORMAL("normal", 1.0),
        TALL("tall", 1.25), GIANT("giant", 1.5);

        final String key;
        final double factor;

        Size(String key, double factor) {
            this.key = key;
            this.factor = factor;
        }
    }

    enum Result { APPLIED, DENIED, UNAVAILABLE, INCOMPATIBLE }

    private final JavaPlugin plugin;
    private final Predicate<Player> access;
    private final NamespacedKey key;
    private final Set<UUID> active = new HashSet<>();

    SizeChanger(JavaPlugin plugin, Predicate<Player> access) {
        this.plugin = plugin;
        this.access = access;
        this.key = new NamespacedKey(plugin, "gadgets_size_changer");
    }

    boolean permitted(Player player) {
        return player.hasPermission(GadgetsModule.USE_PERMISSION) && player.hasPermission(PERMISSION);
    }

    Size selected(Player player) {
        var scale = player.getAttribute(Attribute.SCALE);
        var modifier = scale != null ? scale.getModifier(key) : null;
        if (modifier != null && modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) {
            for (Size size : Size.values()) {
                if (modifier.getAmount() == size.factor - 1.0) return size;
            }
        }
        return Size.NORMAL;
    }

    Result select(Player player, Size size) {
        if (!plugin.getServer().isPrimaryThread()) return Result.UNAVAILABLE;
        if (!access.test(player) || !permitted(player)) {
            clear(player);
            return Result.DENIED;
        }
        if (size == Size.NORMAL) {
            clear(player);
            return Result.APPLIED;
        }
        if (incompatible(player)) {
            clear(player);
            return Result.INCOMPATIBLE;
        }
        var scale = player.getAttribute(Attribute.SCALE);
        if (scale == null) return Result.UNAVAILABLE;

        var replacement = new AttributeModifier(key, size.factor - 1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        var previous = scale.getModifier(key);
        if (!replacement.equals(previous)) {
            if (previous != null) scale.removeModifier(key);
            scale.addTransientModifier(replacement);
        }
        active.add(player.getUniqueId());
        return Result.APPLIED;
    }

    private boolean incompatible(Player player) {
        return player.isDead() || player.getVehicle() != null || !player.getPassengers().isEmpty()
                || player.isFlying() || player.isGliding();
    }

    void reconcile(Player player) {
        if (active.contains(player.getUniqueId()) && (!access.test(player) || !permitted(player) || incompatible(player))) {
            clear(player);
        }
    }

    private void clear(Player player) {
        if (!plugin.getServer().isPrimaryThread()) return;
        var scale = player.getAttribute(Attribute.SCALE);
        if (scale != null && scale.getModifier(key) != null) scale.removeModifier(key);
        active.remove(player.getUniqueId());
    }

    void reset() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            try {
                clear(player);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not clear Size Changer for " + player.getUniqueId(), failure);
            }
        }
        active.clear();
    }

    @Override public void beforeStateChange(Player player) { clear(player); }
    @Override public void afterStateChange(UUID id) { }
    @Override public void beforeJoin(Player player) { clear(player); }
    @Override public void membershipChanged(UUID id) { }

    @EventHandler public void onMove(PlayerMoveEvent event) { reconcile(event.getPlayer()); }
    @EventHandler public void onJoin(PlayerJoinEvent event) { clear(event.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent event) { clear(event.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent event) { clear(event.getPlayer()); }
    @EventHandler public void onRespawn(PlayerRespawnEvent event) { clear(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) { clear(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) { clear(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { clear(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) { clear(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player rider) clear(rider);
        if (event.getMount() instanceof Player mount) clear(mount);
    }
}
