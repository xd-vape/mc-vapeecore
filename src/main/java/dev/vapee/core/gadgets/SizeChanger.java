package dev.vapee.core.gadgets;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.Location;
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
import org.bukkit.util.BoundingBox;

final class SizeChanger implements Listener, LobbyPlayerStateListener, ActivityMembershipListener {
    static final String PERMISSION = "vapeecore.gadgets.size-changer";
    static final String VIP_PERMISSION = "vapeecore.gadgets.size.vip";
    static final String TEAM_PERMISSION = "vapeecore.gadgets.team.use";
    static final String EXTREME_PERMISSION = "vapeecore.gadgets.team.extreme-size";

    // Minecraft 1.21.11's SCALE bounds and standing Player dimensions.
    private static final double MIN_SCALE = 0.0625;
    private static final double MAX_SCALE = 16.0;
    private static final float STANDING_WIDTH = 0.6F;
    private static final float STANDING_HEIGHT = 1.8F;

    private enum Tier { STANDARD, VIP, TEAM }

    enum Size {
        TINY("tiny", 0.5), SMALL("small", 0.75), NORMAL("normal", 1.0),
        TALL("tall", 1.25), GIANT("giant", 1.5),
        HUGE("huge", 2.0, Tier.VIP), MASSIVE("massive", 3.0, Tier.VIP),
        COLOSSAL("colossal", 5.0, Tier.TEAM);

        final String key;
        final double factor;
        private final Tier tier;

        Size(String key, double factor) {
            this(key, factor, Tier.STANDARD);
        }

        Size(String key, double factor, Tier tier) {
            this.key = key;
            this.factor = factor;
            this.tier = tier;
        }
    }

    enum Result { APPLIED, DENIED, UNAVAILABLE, INCOMPATIBLE, NO_SPACE, SCALE_LIMIT }

    private final JavaPlugin plugin;
    private final Predicate<Player> access;
    private final Predicate<UUID> managedFlight;
    private final NamespacedKey key;
    private final Set<UUID> active = new HashSet<>();

    SizeChanger(JavaPlugin plugin, Predicate<Player> access, Predicate<UUID> managedFlight) {
        this.plugin = plugin;
        this.access = access;
        this.managedFlight = managedFlight;
        this.key = new NamespacedKey(plugin, "gadgets_size_changer");
    }

    boolean permitted(Player player) {
        return player.hasPermission(GadgetsModule.USE_PERMISSION) && player.hasPermission(PERMISSION);
    }

    boolean permitted(Player player, Size size) {
        return permitted(player) && switch (size.tier) {
            case STANDARD -> true;
            case VIP -> player.hasPermission(VIP_PERMISSION);
            case TEAM -> player.hasPermission(TEAM_PERMISSION) && player.hasPermission(EXTREME_PERMISSION);
        };
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
        return select(player, size, false);
    }

    Result restoreAtTeleportDestination(Player player, Size size) {
        return select(player, size, true);
    }

    private Result select(Player player, Size size, boolean teleportRestore) {
        if (!plugin.getServer().isPrimaryThread()) return Result.UNAVAILABLE;
        if (!access.test(player) || !permitted(player)) {
            clear(player);
            return Result.DENIED;
        }
        if (!permitted(player, size)) return Result.DENIED;
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
            if (teleportRestore || size.tier != Tier.STANDARD) {
                if (managedFlight.test(player.getUniqueId())) return Result.INCOMPATIBLE;
                var clearance = checkClearance(player, scale.getValue(), previous, size.factor);
                if (clearance != Result.APPLIED) return clearance;
            }
            if (previous != null) scale.removeModifier(key);
            scale.addTransientModifier(replacement);
        }
        active.add(player.getUniqueId());
        return Result.APPLIED;
    }

    private Result checkClearance(Player player, double current, AttributeModifier previous, double factor) {
        // SCALE already includes foreign modifiers. Our own operation is a distinct final multiplier.
        // A value at a clamp boundary cannot be inverted reliably.
        if (!withinScaleBounds(current) || (previous != null
                && previous.getOperation() != AttributeModifier.Operation.MULTIPLY_SCALAR_1)) return Result.SCALE_LIMIT;
        double previousFactor = previous == null ? 1.0 : 1.0 + previous.getAmount();
        double projected = current / previousFactor * factor;
        if (!withinScaleBounds(projected)) return Result.SCALE_LIMIT;

        // The current entity box can still reflect the old pose or the previous tick's SCALE.
        // Check the standing envelope anchored at the feet, even when the player is crouching.
        var position = player.getLocation();
        var world = player.getWorld();
        if (!Double.isFinite(position.getX()) || !Double.isFinite(position.getY()) || !Double.isFinite(position.getZ())) {
            return Result.NO_SPACE;
        }
        float envelopeScale = Math.nextUp((float) projected);
        double halfWidth = STANDING_WIDTH * envelopeScale / 2.0;
        double height = STANDING_HEIGHT * envelopeScale;
        var box = new BoundingBox(position.getX() - halfWidth, position.getY(), position.getZ() - halfWidth,
                position.getX() + halfWidth, position.getY() + height, position.getZ() + halfWidth);
        if (box.getMinY() < world.getMinHeight() || box.getMaxY() > world.getMaxHeight()) return Result.NO_SPACE;

        // Paper's collision probe does not load chunks. Include a block around the footprint for voxel shapes
        // extending outside their block, such as piston extensions and fences.
        int minChunkX = Location.locToBlock(box.getMinX() - 1.0) >> 4;
        int maxChunkX = Location.locToBlock(box.getMaxX() + 1.0) >> 4;
        int minChunkZ = Location.locToBlock(box.getMinZ() - 1.0) >> 4;
        int maxChunkZ = Location.locToBlock(box.getMaxZ() + 1.0) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!world.isChunkLoaded(chunkX, chunkZ)) return Result.NO_SPACE;
            }
        }
        return player.wouldCollideUsing(box) ? Result.NO_SPACE : Result.APPLIED;
    }

    private static boolean withinScaleBounds(double scale) {
        return scale > MIN_SCALE && scale < MAX_SCALE;
    }

    private boolean incompatible(Player player) {
        return player.isDead() || player.getVehicle() != null || !player.getPassengers().isEmpty()
                || player.isFlying() || player.isGliding();
    }

    void reconcile(Player player) {
        if (!active.contains(player.getUniqueId())) return;
        var size = selected(player);
        if (!access.test(player) || !permitted(player, size) || incompatible(player)
                || (size.tier != Tier.STANDARD && managedFlight.test(player.getUniqueId()))) {
            clear(player);
        }
    }

    void clear(Player player) {
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
