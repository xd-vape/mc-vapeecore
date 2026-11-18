package dev.vapee.core.gadgets;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.player.settings.GadgetPreferences;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.settings.PlayerSettingsService;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.logging.Level;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Restores the latest durable choices after the existing lobby transition and cleanup listeners. */
final class GadgetPreferenceLifecycle implements Listener, LobbyPlayerStateListener, ActivityMembershipListener {
    private final JavaPlugin plugin;
    private final PlayerSettingsService settings;
    private final Predicate<Player> access;
    private final SizeChanger size;
    private final GlowGadget glow;
    private final ParticleTrails trails;
    private final Map<UUID, Restore> pending = new HashMap<>();
    private final Map<UUID, Restore> glowRetries = new HashMap<>();

    GadgetPreferenceLifecycle(JavaPlugin plugin, PlayerSettingsService settings, Predicate<Player> access,
                              SizeChanger size, GlowGadget glow, ParticleTrails trails) {
        this.plugin = plugin;
        this.settings = settings;
        this.access = access;
        this.size = size;
        this.glow = glow;
        this.trails = trails;
    }

    GadgetPreferences preferences(Player player) {
        return settings.getGadgetPreferences(player.getUniqueId()).orElse(GadgetPreferences.DEFAULT);
    }

    boolean save(Player player, UnaryOperator<GadgetPreferences> update, Runnable rollback) {
        try {
            if (settings.updateGadgetPreferences(player.getUniqueId(), update)) return true;
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not save gadget preferences for " + player.getUniqueId(), failure);
        }
        try {
            rollback.run();
        } catch (RuntimeException failure) {
            plugin.getLogger().log(Level.WARNING, "Could not restore previous gadget effect for " + player.getUniqueId(), failure);
        }
        return false;
    }

    void restoreOnline() {
        plugin.getServer().getOnlinePlayers().forEach(this::schedule);
    }

    private void schedule(Player player) {
        // Join can still be outside the lobby until the existing spawn task runs.
        if (player == null || !player.isOnline()) return;
        UUID id = player.getUniqueId();
        var profile = settings.getSettings(id).orElse(null);
        if (profile == null) return;
        var previous = pending.get(id);
        if (previous != null && previous.player == player && previous.profile == profile) return;
        cancel(id);
        var restore = new Restore(player, profile);
        restore.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (pending.get(id) != restore) return;
            try {
                if (valid(restore)) apply(restore);
            } finally {
                pending.remove(id, restore);
            }
        }, 1L);
        pending.put(id, restore);
    }

    private boolean current(Restore restore) {
        return pending.get(restore.player.getUniqueId()) == restore && valid(restore);
    }

    private boolean valid(Restore restore) {
        var player = restore.player;
        return plugin.getServer().getPlayer(player.getUniqueId()) == player && access.test(player) && !player.isDead()
                && settings.getSettings(player.getUniqueId()).orElse(null) == restore.profile;
    }

    private void apply(Restore restore) {
        var player = restore.player;
        var desired = restore.profile.getGadgetPreferences();
        if (desired.size() != GadgetPreferences.Size.NORMAL) {
            size.select(player, SizeChanger.Size.valueOf(desired.size().name()));
        }
        if (!current(restore)) return;
        if (restore.profile.getGadgetPreferences().glow() && glow.setEnabled(player, true) == GlowGadget.Result.EXTERNAL) {
            scheduleGlowRetry(restore);
        }
        if (!current(restore)) return;
        var trail = restore.profile.getGadgetPreferences().trail();
        if (trail != GadgetPreferences.Trail.NONE) trails.select(player, ParticleTrails.Trail.valueOf(trail.name()));
    }

    private void scheduleGlowRetry(Restore session) {
        var player = session.player;
        int remaining = glow.residualTicks(player);
        if (remaining <= 0 || !current(session)) return;
        UUID id = player.getUniqueId();
        cancelGlowRetry(player);
        var retry = new Restore(player, session.profile);
        // A matching short-lived potion could be a saved remnant. It is never adopted or removed.
        retry.task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (glowRetries.remove(id, retry) && valid(retry) && retry.profile.getGadgetPreferences().glow()) {
                glow.setEnabled(player, true); // Still refuses all foreign flags and potions.
            }
        }, remaining + 1L);
        glowRetries.put(id, retry);
    }

    void cancelGlowRetry(Player player) {
        var retry = glowRetries.get(player.getUniqueId());
        if (retry != null && retry.player == player) cancel(glowRetries, player.getUniqueId());
    }

    private void cancel(UUID id) {
        cancel(pending, id);
        cancel(glowRetries, id);
    }

    private static void cancel(Map<UUID, Restore> tasks, UUID id) {
        var task = tasks.remove(id);
        if (task != null) task.task.cancel();
    }

    void cancelAll() {
        pending.values().forEach(entry -> entry.task.cancel());
        pending.clear();
        glowRetries.values().forEach(entry -> entry.task.cancel());
        glowRetries.clear();
    }

    private void cancel(Player player) {
        var restore = pending.get(player.getUniqueId());
        if (restore != null && restore.player == player) cancel(pending, player.getUniqueId());
        cancelGlowRetry(player);
    }

    @Override public void beforeStateChange(Player player) { cancel(player); }
    @Override public void afterStateChange(UUID id) { schedule(plugin.getServer().getPlayer(id)); }
    @Override public void beforeJoin(Player player) { cancel(player); }
    @Override public void membershipChanged(UUID id) {
        var player = plugin.getServer().getPlayer(id);
        if (access.test(player)) schedule(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { schedule(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) { schedule(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) { schedule(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { cancel(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) { cancel(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent event) { cancel(event.getEntity()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { cancelGlowRetry(event.getPlayer()); }

    private static final class Restore {
        final Player player;
        final PlayerSettings profile;
        BukkitTask task;

        Restore(Player player, PlayerSettings profile) {
            this.player = player;
            this.profile = profile;
        }
    }
}
