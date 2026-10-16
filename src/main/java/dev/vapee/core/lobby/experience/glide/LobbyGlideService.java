package dev.vapee.core.lobby.experience.glide;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.lobby.experience.navigator.NavigatorAccessPolicy;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.utility.UtilityFlightListener;
import dev.vapee.core.utility.UtilityService;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.*;
import java.util.logging.*;

/** Lobby movement with disposable wings in an empty chest slot; real armor is never replaced. */
public final class LobbyGlideService implements UtilityFlightListener, LobbyPlayerStateListener, ActivityMembershipListener {
    private static final double LAUNCH_UP = 0.85;
    private static final double LAUNCH_FORWARD = 0.85;

    private enum Phase { WAITING, ARMED, LAUNCHING, GLIDING }
    private static final class State {
        final long generation;
        Phase phase = Phase.WAITING;
        boolean glideOwned;
        boolean allowGranted;
        Task task;
        State(long generation) { this.generation = generation; }
        boolean expectedAllowFlight() { return allowGranted; }
    }
    private static final class Pending {
        final long generation;
        Task task;
        final boolean recoverEquipment;
        Pending(long generation, boolean recoverEquipment) { this.generation = generation; this.recoverEquipment = recoverEquipment; }
    }
    @FunctionalInterface interface Task { void cancel(); }
    private final Function<UUID, Player> online;
    private final Predicate<Player> access;
    private final Predicate<UUID> managedFlight;
    private final Function<Runnable, Task> nextTick;
    private final Logger logger;
    private final LobbyGlideEquipment equipment;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Pending> pending = new HashMap<>();
    private long generation;
    private boolean enabled = true;

    public LobbyGlideService(JavaPlugin plugin, NavigatorAccessPolicy access, UtilityService utility) {
        this(plugin.getServer()::getPlayer, access::canAccess, utility::hasManagedFlight,
                action -> { var task = plugin.getServer().getScheduler().runTask(plugin, action); return task::cancel; },
                plugin.getLogger(), new LobbyGlideEquipment(plugin));
    }
    LobbyGlideService(Function<UUID, Player> online, Predicate<Player> access, Predicate<UUID> managedFlight,
                      Function<Runnable, Task> nextTick, Logger logger, LobbyGlideEquipment equipment) {
        this.online = Objects.requireNonNull(online); this.access = Objects.requireNonNull(access);
        this.managedFlight = Objects.requireNonNull(managedFlight); this.nextTick = Objects.requireNonNull(nextTick);
        this.logger = Objects.requireNonNull(logger);
        this.equipment = Objects.requireNonNull(equipment);
    }
    LobbyGlideEquipment equipment() { return equipment; }
    public void onPlayerAvailable(Player player) {
        equipment.cleanupOrphans(player);
        deferEquipmentCleanup(player.getUniqueId());
    }
    public boolean isOwned(UUID id) { return states.containsKey(id); }
    boolean isActive(UUID id) { var state = states.get(id); return state != null && state.phase == Phase.GLIDING; }
    long generation(UUID id) { return states.get(id).generation; }
    int ownedCount() { return states.size(); }
    int pendingCount() { return pending.size(); }

    private static boolean nativeFlight(Player player) {
        return player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR;
    }
    private boolean eligible(Player player) {
        return enabled && player != null && access.test(player) && !player.isDead()
                && !nativeFlight(player) && !managedFlight.test(player.getUniqueId());
    }
    private static boolean clearAir(Player player) {
        return !player.isInsideVehicle() && !player.isInWater() && !player.isSwimming() && !player.isClimbing();
    }
    private boolean flightMatches(Player player, State state) {
        return !player.isFlying() && player.getAllowFlight() == state.expectedAllowFlight()
                && (state.glideOwned || !player.isGliding());
    }
    public void reconcile(Player player) {
        if (player == null) return;
        UUID id = player.getUniqueId();
        try {
            if (!eligible(player)) { release(player); return; }
            State state = states.get(id);
            if (state == null) {
                // Pre-existing flags belong to native flight or another owner. Never adopt them.
                if (player.getAllowFlight() || player.isFlying() || player.isGliding()) return;
                state = new State(++generation);
                states.put(id, state);
            }
            if (!flightMatches(player, state)) { release(player); return; }
            if (state.phase == Phase.GLIDING || state.phase == Phase.LAUNCHING) return;
            if (state.phase == Phase.WAITING && player.isOnGround() && clearAir(player)) {
                state.phase = Phase.ARMED;
                state.allowGranted = true;
                player.setAllowFlight(true);
            }
        } catch (RuntimeException failure) { failed(player, "reconcile", failure); }
    }
    public void deferReconcile(UUID id) { queueReconcile(id, false); }
    void deferEquipmentCleanup(UUID id) { queueReconcile(id, true); }
    private void queueReconcile(UUID id, boolean recoverEquipment) {
        if (!enabled) return;
        Pending previous = pending.get(id);
        boolean recover = recoverEquipment || (previous != null && previous.recoverEquipment);
        cancelPending(id);
        Pending request = new Pending(++generation, recover);
        pending.put(id, request);
        try {
            request.task = nextTick.apply(() -> {
                if (!enabled || pending.get(id) != request) return;
                pending.remove(id);
                Player player = online.apply(id);
                if (player != null && request.recoverEquipment) equipment.cleanupOrphans(player);
                reconcile(player);
            });
        } catch (RuntimeException failure) {
            pending.remove(id, request);
            logger.log(Level.WARNING, "Could not schedule lobby glide reconciliation for " + id, failure);
        }
    }
    /** True means this UUID's flight-toggle must be cancelled, even if its launch was already consumed. */
    boolean onFlightToggle(Player player, boolean startingFlight) {
        State state = states.get(player.getUniqueId());
        if (state == null) return false;
        try {
            if (!eligible(player) || !flightMatches(player, state)) { release(player); return false; }
            if (!startingFlight || state.phase != Phase.ARMED) return true;
            state.phase = Phase.LAUNCHING;
            player.setAllowFlight(false);
            state.allowGranted = false;
            if (!clearAir(player)) { state.phase = Phase.WAITING; return true; }
            Vector direction = player.getLocation().getDirection().setY(0);
            if (direction.lengthSquared() < 0.000001) {
                double yaw = Math.toRadians(player.getYaw());
                direction = new Vector(-Math.sin(yaw), 0, Math.cos(yaw));
            }
            player.setVelocity(direction.normalize().multiply(LAUNCH_FORWARD).setY(LAUNCH_UP));
            UUID id = player.getUniqueId();
            state.task = nextTick.apply(() -> startGlide(id, state));
            return true;
        } catch (RuntimeException failure) { failed(player, "launch", failure); return true; }
    }
    private void startGlide(UUID id, State expected) {
        if (!enabled || states.get(id) != expected || expected.phase != Phase.LAUNCHING) return;
        expected.task = null;
        Player player = online.apply(id);
        if (player == null) { states.remove(id, expected); return; }
        try {
            if (!eligible(player) || !flightMatches(player, expected)) { release(player, expected.generation); return; }
            if (player.isOnGround() || !clearAir(player)) { expected.phase = Phase.WAITING; reconcile(player); return; }
            expected.phase = Phase.GLIDING;
            expected.glideOwned = true;
            player.setGliding(true);
            if (player.isGliding()) equipment.equip(player, expected.generation);
            else { expected.glideOwned = false; expected.phase = Phase.WAITING; }
        } catch (RuntimeException failure) { failed(player, "start glide", failure); }
    }
    /** Keep only the feature's active airborne glide; all termination paths remain open. */
    boolean keepGliding(Player player) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.phase != Phase.GLIDING || !state.glideOwned) return false;
        if (!eligible(player) || !flightMatches(player, state)) { release(player); return false; }
        if (player.isOnGround() || !clearAir(player)) {
            state.glideOwned = false; state.phase = Phase.WAITING;
            equipment.release(player, state.generation);
            deferReconcile(player.getUniqueId());
            return false;
        }
        return true;
    }
    void moved(Player player, boolean verticalMovement) {
        State state = states.get(player.getUniqueId());
        if (state == null || state.phase == Phase.ARMED) return;
        try {
            if (!eligible(player) || !flightMatches(player, state)) { release(player); return; }
            if (state.phase == Phase.GLIDING && (player.isOnGround() || !clearAir(player) || !player.isGliding())) {
                state.phase = Phase.WAITING;
                equipment.release(player, state.generation);
                boolean stop = state.glideOwned && player.isGliding(); state.glideOwned = false;
                if (stop) player.setGliding(false);
            }
            if (state.phase == Phase.WAITING) {
                if (player.isOnGround()) reconcile(player);
                // Ground status may be applied after the final falling move. This is one scoped event check.
                else if (verticalMovement) deferReconcile(player.getUniqueId());
            }
        } catch (RuntimeException failure) { failed(player, "landing", failure); }
    }
    public void release(Player player) {
        UUID id = player.getUniqueId();
        cancelPending(id);
        State state = states.get(id);
        if (state != null) release(player, state.generation);
    }
    void release(Player player, long token) {
        UUID id = player.getUniqueId();
        State state = states.get(id);
        if (state == null || state.generation != token) return;
        states.remove(id);
        cancelPending(id); cancel(state.task, id);
        equipment.release(player, state.generation);
        // Remove authority before touching the flag: our glide-stop listener must permit this cleanup.
        if (state.glideOwned && player.isGliding()) {
            try { player.setGliding(false); }
            catch (RuntimeException failure) { logger.log(Level.WARNING, "Could not stop owned lobby glide for " + id, failure); }
        }
        if (state.expectedAllowFlight() && !nativeFlight(player) && !managedFlight.test(id)
                && !player.isFlying() && !player.isGliding() && player.getAllowFlight()) {
            try { player.setAllowFlight(false); }
            catch (RuntimeException failure) { logger.log(Level.WARNING, "Could not release owned lobby flight for " + id, failure); }
        }
    }
    private void cancelPending(UUID id) { var request = pending.remove(id); if (request != null) cancel(request.task, id); }
    private void cancel(Task task, UUID id) {
        if (task == null) return;
        try { task.cancel(); }
        catch (RuntimeException failure) { logger.log(Level.WARNING, "Could not cancel lobby glide task for " + id, failure); }
    }
    private void failed(Player player, String action, RuntimeException failure) {
        logger.log(Level.WARNING, "Could not " + action + " for lobby glide player " + player.getUniqueId(), failure);
        release(player);
    }
    public void shutdown() {
        enabled = false;
        for (UUID id : List.copyOf(pending.keySet())) cancelPending(id);
        try {
            for (UUID id : List.copyOf(states.keySet())) {
                try { Player player = online.apply(id); if (player != null) release(player); }
                catch (RuntimeException failure) { logger.log(Level.WARNING, "Could not clean up lobby glide for " + id, failure); }
            }
        } finally { equipment.shutdown(online); states.clear(); pending.clear(); }
    }
    @Override public void beforeFlightChange(Player player) { release(player); }
    @Override public void afterFlightChange(UUID id) { deferEquipmentCleanup(id); }
    @Override public void beforeStateChange(Player player) { release(player); }
    @Override public void afterStateChange(UUID id) { deferEquipmentCleanup(id); }
    @Override public void beforeJoin(Player player) { release(player); }
    @Override public void membershipChanged(UUID id) { deferEquipmentCleanup(id); }
}
