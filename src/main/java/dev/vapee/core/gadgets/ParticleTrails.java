package dev.vapee.core.gadgets;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.visibility.VisibilityService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
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

final class ParticleTrails implements Listener, LobbyPlayerStateListener, ActivityMembershipListener {
    static final String PERMISSION = "vapeecore.gadgets.trails";
    private static final int INTERVAL_TICKS = 4;
    private static final int PARTICLES = 2;
    private static final double RANGE = 32.0;
    private static final double RANGE_SQUARED = RANGE * RANGE;
    private static final double MIN_HORIZONTAL_SQUARED = 0.08 * 0.08;
    private static final double MAX_STEP_SQUARED = 1.5 * 1.5;

    enum Trail {
        HEARTS("hearts", Material.POPPY, Particle.HEART),
        FLAME("flame", Material.BLAZE_POWDER, Particle.FLAME),
        CLOUD("cloud", Material.WHITE_WOOL, Particle.CLOUD),
        END_ROD("end-rod", Material.END_ROD, Particle.END_ROD),
        HAPPY_VILLAGER("happy-villager", Material.EMERALD, Particle.HAPPY_VILLAGER),
        NONE("none", Material.BARRIER, null);

        final String key;
        final Material icon;
        private final Particle particle;

        Trail(String key, Material icon, Particle particle) {
            this.key = key;
            this.icon = icon;
            this.particle = particle;
        }
    }

    private final JavaPlugin plugin;
    private final Predicate<Player> access;
    private final VisibilityService visibility;
    private final Map<UUID, Session> sessions = new HashMap<>();

    ParticleTrails(JavaPlugin plugin, Predicate<Player> access, VisibilityService visibility) {
        this.plugin = plugin;
        this.access = access;
        this.visibility = visibility;
    }

    boolean permitted(Player player) {
        return player.hasPermission(GadgetsModule.USE_PERMISSION) && player.hasPermission(PERMISSION);
    }

    private boolean eligible(Player player) {
        return access.test(player) && permitted(player) && !player.isDead();
    }

    Trail selected(Player player) {
        var session = session(player);
        return session == null ? Trail.NONE : session.trail;
    }

    boolean select(Player player, Trail trail) {
        if (!plugin.getServer().isPrimaryThread()) return false;
        if (!eligible(player)) {
            clear(player);
            return false;
        }
        if (trail == Trail.NONE) {
            clear(player);
        } else {
            var session = session(player);
            if (session == null) {
                sessions.put(player.getUniqueId(), new Session(player, trail, plugin.getServer().getCurrentTick()));
            } else {
                session.trail = trail;
            }
        }
        return true;
    }

    void reconcile(Player player) {
        if (session(player) != null && !eligible(player)) clear(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (!plugin.getServer().isPrimaryThread() || event instanceof PlayerTeleportEvent) return;
        Player source = event.getPlayer();
        var session = session(source);
        if (session == null) return;
        if (!eligible(source)) {
            clear(source);
            return;
        }
        int tick = plugin.getServer().getCurrentTick();
        if (Integer.toUnsignedLong(tick - session.lastEmission) < INTERVAL_TICKS) return;

        var from = event.getFrom();
        var to = event.getTo();
        var world = source.getWorld();
        if (to == null || world != from.getWorld() || world != to.getWorld() || !validStep(from, to)) return;
        if (source.getVehicle() != null || !source.getPassengers().isEmpty() || source.isFlying() || source.isGliding()) return;

        var position = from.clone().add(0, 0.15, 0);
        var recipients = new ArrayList<Player>();
        recipients.add(source);
        for (Player viewer : world.getNearbyPlayers(position, RANGE)) {
            if (!source.getUniqueId().equals(viewer.getUniqueId()) && canReceive(viewer, source, position)) {
                recipients.add(viewer);
            }
        }
        session.lastEmission = tick;
        world.spawnParticle(session.trail.particle, recipients, source, position.getX(), position.getY(), position.getZ(),
                PARTICLES, 0.10, 0.06, 0.10, 0.01, null, false);
    }

    private boolean canReceive(Player viewer, Player source, Location position) {
        try {
            if (viewer.getWorld() != position.getWorld() || !access.test(viewer)) return false;
            var viewerPosition = viewer.getLocation();
            return viewerPosition.getWorld() == position.getWorld()
                    && viewerPosition.distanceSquared(position) <= RANGE_SQUARED && visibility.canSee(viewer, source);
        } catch (RuntimeException unavailable) {
            // A failed lookup cannot authorize disclosure to this viewer.
            return false;
        }
    }

    private static boolean validStep(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double horizontal = dx * dx + dz * dz;
        return horizontal >= MIN_HORIZONTAL_SQUARED && horizontal + dy * dy <= MAX_STEP_SQUARED;
    }

    private Session session(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.player == player ? session : null;
    }

    private void clear(Player player) {
        var session = session(player);
        if (session != null) sessions.remove(player.getUniqueId(), session);
    }

    void reset() { sessions.clear(); }

    @Override public void beforeStateChange(Player player) { clear(player); }
    @Override public void afterStateChange(UUID id) { }
    @Override public void beforeJoin(Player player) { clear(player); }
    @Override public void membershipChanged(UUID id) { }

    @EventHandler public void onJoin(PlayerJoinEvent event) { sessions.remove(event.getPlayer().getUniqueId()); }
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

    private static final class Session {
        private final Player player;
        private Trail trail;
        private int lastEmission;

        private Session(Player player, Trail trail, int tick) {
            this.player = player;
            this.trail = trail;
            this.lastEmission = tick;
        }
    }
}
