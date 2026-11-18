package dev.vapee.core.gadgets;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

final class GlowGadget implements Listener, LobbyPlayerStateListener, ActivityMembershipListener {
    static final String PERMISSION = "vapeecore.gadgets.glow";
    private static final int DURATION = 100;
    private static final int RENEW_AFTER = 60;

    private final JavaPlugin plugin;
    private final Predicate<Player> access;
    private final Map<UUID, Session> sessions = new HashMap<>();

    enum Result { ENABLED, DISABLED, DENIED, EXTERNAL, UNAVAILABLE }

    GlowGadget(JavaPlugin plugin, Predicate<Player> access) {
        this.plugin = plugin;
        this.access = access;
    }

    boolean permitted(Player player) {
        return player.hasPermission(GadgetsModule.USE_PERMISSION) && player.hasPermission(PERMISSION);
    }

    boolean active(Player player) {
        return session(player) != null;
    }

    Result setEnabled(Player player, boolean enabled) {
        if (!plugin.getServer().isPrimaryThread()) return Result.UNAVAILABLE;
        reconcile(player);
        if (!eligible(player)) return Result.DENIED;
        if (!enabled) {
            if (!active(player)) return Result.DISABLED;
            clear(player);
            return effect(player) == null ? Result.DISABLED : Result.UNAVAILABLE;
        }
        if (active(player)) return Result.ENABLED;
        // Paper's isGlowing also observes potion glow, so consult it only before taking ownership.
        if (player.isGlowing() || effect(player) != null || sessions.containsKey(player.getUniqueId())) {
            return Result.EXTERNAL;
        }
        var session = new Session(player);
        sessions.put(player.getUniqueId(), session);
        return renew(session) ? Result.ENABLED : Result.UNAVAILABLE;
    }

    int residualTicks(Player player) {
        var current = effect(player);
        return matches(current) ? current.getDuration() : 0;
    }

    private boolean eligible(Player player) {
        return access.test(player) && permitted(player) && !player.isDead();
    }

    private boolean renew(Session session) {
        var player = session.player;
        var requested = new PotionEffect(PotionEffectType.GLOWING, DURATION, 0, false, false, false);
        session.requested = requested;
        session.sawWrite = false;
        try {
            player.addPotionEffect(requested);
        } finally {
            session.requested = null;
            if (!session.sawWrite || !requested.equals(effect(player))) forget(session);
        }

        if (session(player) != session) return false;
        if (session.clearAfterWrite || !eligible(player)) {
            clear(player);
            return false;
        }
        // Idle players need uninterrupted Glow. The finite effect also bounds any saved copy after a crash.
        session.renewal = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (session(player) != session) return;
            reconcile(player);
            if (session(player) == session) renew(session);
        }, RENEW_AFTER);
        return true;
    }

    void reconcile(Player player) {
        var session = session(player);
        if (session == null || session.requested != null || session.removing != null) return;
        if (!matches(effect(player))) forget(session);
        else if (!eligible(player)) clear(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEffectChange(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getModifiedType() != PotionEffectType.GLOWING) return;
        var session = session(player);
        if (session == null) return;
        if (!session.sawWrite && session.requested != null && session.requested.equals(event.getNewEffect())
                && event.getCause() == EntityPotionEffectEvent.Cause.PLUGIN) {
            session.sawWrite = true;
        } else if (session.removing == null) {
            // A same-type write can introduce a hidden potion effect, even when the visible state looks unchanged.
            forget(session);
        } else if (event.getNewEffect() != null) {
            session.replacedDuringRemoval = true;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void protectReplacementDuringRemoval(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getModifiedType() != PotionEffectType.GLOWING
                || event.getCause() != EntityPotionEffectEvent.Cause.PLUGIN || event.getNewEffect() != null) return;
        var session = session(player);
        if (session == null || session.removing == null || !session.removing.equals(event.getOldEffect())) return;
        // Paper removes by type after callbacks. Back out if a listener installed another effect during our removal.
        var current = effect(player);
        if (!matches(current) || !session.removing.equals(current) || session.replacedDuringRemoval) {
            event.setCancelled(true);
        }
    }

    private void clear(Player player) {
        var session = session(player);
        if (session == null || session.removing != null) return;
        if (session.requested != null) {
            // Potion callbacks can re-enter lobby transitions before Paper finishes the write.
            session.clearAfterWrite = true;
            return;
        }
        var current = effect(player);
        if (!matches(current)) {
            forget(session);
            return;
        }
        session.removing = current;
        try {
            player.removePotionEffect(PotionEffectType.GLOWING);
        } finally {
            forget(session);
        }
    }

    private void forget(Session session) {
        sessions.remove(session.player.getUniqueId(), session);
        if (session.renewal != null) session.renewal.cancel();
    }

    private Session session(Player player) {
        var session = sessions.get(player.getUniqueId());
        return session != null && session.player == player ? session : null;
    }

    private static PotionEffect effect(Player player) {
        return player.getPotionEffect(PotionEffectType.GLOWING);
    }

    private static boolean matches(PotionEffect effect) {
        return effect != null && effect.getAmplifier() == 0 && effect.getDuration() > 0 && effect.getDuration() <= DURATION
                && !effect.isAmbient() && !effect.hasParticles() && !effect.hasIcon() && effect.getHiddenPotionEffect() == null;
    }

    void reset() {
        for (var session : List.copyOf(sessions.values())) {
            try {
                clear(session.player);
            } catch (RuntimeException failure) {
                plugin.getLogger().log(Level.WARNING, "Could not clear Glow for " + session.player.getUniqueId(), failure);
            }
        }
    }

    @Override public void beforeStateChange(Player player) { clear(player); }
    @Override public void afterStateChange(UUID id) { }
    @Override public void beforeJoin(Player player) { clear(player); }
    @Override public void membershipChanged(UUID id) { }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        var previous = sessions.get(event.getPlayer().getUniqueId());
        if (previous != null && previous.player != event.getPlayer()) forget(previous);
    }
    @EventHandler public void onMove(PlayerMoveEvent event) { reconcile(event.getPlayer()); }
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
        private BukkitTask renewal;
        private PotionEffect requested;
        private boolean sawWrite;
        private boolean clearAfterWrite;
        private PotionEffect removing;
        private boolean replacedDuringRemoval;

        private Session(Player player) {
            this.player = player;
        }
    }
}
