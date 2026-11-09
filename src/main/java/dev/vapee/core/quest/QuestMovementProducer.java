package dev.vapee.core.quest;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.player.PlayerService;

import io.papermc.paper.event.player.PlayerClientLoadedWorldEvent;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Synchronous, bounded observation only. Assignments, persistence and rewards remain with
 * QuestService.
 */
public final class QuestMovementProducer
        implements Listener, ActivityMembershipListener, LobbyPlayerStateListener {
    public static final QuestProgressKey WALK = QuestProgressKey.of("movement:walk");
    public static final QuestProgressKey SPRINT = QuestProgressKey.of("movement:sprint");
    public static final QuestProgressKey JUMP = QuestProgressKey.of("movement:jump");
    public static final QuestProgressKey ROTATION = QuestProgressKey.of("movement:rotation_full");
    public static final QuestProgressKey SNEAK = QuestProgressKey.of("movement:sneak");
    public static final QuestProgressKey BACKWARD = QuestProgressKey.of("movement:backward");
    public static final QuestProgressKey SNEAK_TOGGLE =
            QuestProgressKey.of("movement:sneak_toggle");
    private static final List<QuestProgressKey> KEYS =
            List.of(WALK, SPRINT, JUMP, ROTATION, SNEAK, BACKWARD, SNEAK_TOGGLE);
    // Distance slots are independent of key bits; discrete events never index this array.
    private static final List<QuestProgressKey> DISTANCE_KEYS =
            List.of(WALK, SPRINT, SNEAK, BACKWARD);
    private static final int SNEAK_COOLDOWN_TICKS = 10;
    private static final double BACKWARD_MIN_COSINE = 0.5, BACKWARD_MAX_TURN = 45.0;
    private static final double EPSILON = 1.0e-9;
    private final PlayerService players;
    private final QuestDefinitionRegistry definitions;
    private final QuestProgressReporter reporter;
    private final Function<UUID, Player> online;
    private final Predicate<UUID> context;
    private final IntSupplier ticks;
    private final BooleanSupplier authorized;
    private final Logger logger;
    private final Function<Runnable, BukkitTask> scheduleConfirmation;
    private final Map<UUID, Sample> samples = new HashMap<>();
    // Session metadata only: readiness must survive discarded measurement/assignment samples.
    private final Map<UUID, ClientReadiness> clientReadiness = new HashMap<>();
    private BukkitTask confirmationTask;
    private long confirmationEpoch;
    private int confirmationScheduledTick;
    private boolean schedulingFailed;
    private int failedScheduleTick;

    QuestMovementProducer(
            PlayerService players,
            QuestDefinitionRegistry definitions,
            QuestProgressReporter reporter,
            Function<UUID, Player> online,
            Predicate<UUID> context,
            IntSupplier ticks,
            BooleanSupplier authorized,
            Function<Runnable, BukkitTask> scheduleConfirmation,
            Logger logger) {
        this.players = Objects.requireNonNull(players);
        this.definitions = Objects.requireNonNull(definitions);
        this.reporter = Objects.requireNonNull(reporter);
        this.online = Objects.requireNonNull(online);
        this.context = Objects.requireNonNull(context);
        this.ticks = Objects.requireNonNull(ticks);
        this.authorized = Objects.requireNonNull(authorized);
        this.scheduleConfirmation = Objects.requireNonNull(scheduleConfirmation);
        this.logger = Objects.requireNonNull(logger);
    }

    private boolean eligible(Player player) {
        return authorized.getAsBoolean()
                && player.isOnline()
                && online.apply(player.getUniqueId()) == player
                && !player.isDead()
                && !player.isInsideVehicle()
                && !player.isFlying()
                && !player.isGliding()
                && !player.isInWater()
                && !player.isSwimming()
                && !player.isClimbing()
                && !player.isRiptiding()
                && player.getGameMode() != GameMode.CREATIVE
                && player.getGameMode() != GameMode.SPECTATOR
                && context.test(player.getUniqueId());
    }

    private Sample sample(Player player) {
        return sample(player, null);
    }

    private Sample sample(Player player, QuestProgressKey required) {
        return sample(player, required, false);
    }

    private Sample sample(Player player, QuestProgressKey required, boolean observeBlocked) {
        UUID id = player.getUniqueId();
        if (!eligible(player)) {
            forget(player);
            return null;
        }
        var loaded = players.getPlayer(id);
        if (loaded.isEmpty()) {
            forget(id);
            return null;
        }
        PlayerQuestState quests = loaded.get().getQuestState();
        var catalog = definitions.snapshotById();
        int wanted = 0;
        for (var progress : quests.progressEntries()) {
            var definition = catalog.get(progress.questId());
            if (definition == null || progress.status() == QuestStatus.COMPLETED) continue;
            int index = KEYS.indexOf(definition.progressKey());
            if (index >= 0) wanted |= 1 << index;
        }
        if (wanted == 0) {
            forget(id);
            return null;
        }
        if (required != null && (wanted & (1 << KEYS.indexOf(required))) == 0) return null;
        int tick = ticks.getAsInt();
        Sample previous = samples.get(id);
        boolean blocked =
                previous != null
                        && previous.entity == player.getEntityId()
                        && previous.blockedAt(tick);
        if (blocked && !observeBlocked) return null;
        if (previous == null
                || previous.entity != player.getEntityId()
                || previous.assignments != quests.assignmentToken()
                || previous.catalog != catalog
                || tick - previous.tick < 0
                || tick - previous.tick > 40) {
            Sample replacement = new Sample(player.getEntityId(), tick);
            if (blocked) {
                replacement.blocked = true;
                replacement.blockedUntil = previous.blockedUntil;
            }
            previous = replacement;
            previous.sneaking = player.isSneaking();
            previous.assignments = quests.assignmentToken();
            previous.catalog = catalog;
            samples.put(id, previous);
        }
        previous.wanted = wanted;
        // Sneak observations may advance while progress stays excluded. Never renew a deadline.
        if (blocked) return previous;
        if (previous.blocked) {
            previous.last = null;
            previous.pendingJump = null;
            previous.airJump = null;
            previous.turn = 0;
        }
        previous.blocked = false;
        for (int index = 0; index < DISTANCE_KEYS.size(); index++)
            if (!previous.wants(DISTANCE_KEYS.get(index))) previous.fractions[index] = 0;
        if (!previous.wants(ROTATION)) previous.turn = 0;
        if (previous.tick != tick) {
            previous.distanceThisTick = 0;
            previous.yawThisTick = 0;
            previous.tick = tick;
        }
        return previous;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event instanceof PlayerTeleportEvent teleport) {
            onTeleport(teleport);
            return;
        }
        if (event.isCancelled()) {
            reject(player, null);
            return;
        }
        Location from = event.getFrom(), to = event.getTo();
        if (!valid(from)
                || !valid(to)
                || !from.getWorld().getUID().equals(to.getWorld().getUID())) {
            reject(player, null);
            return;
        }
        if (samePosition(from, to) && from.getYaw() == to.getYaw())
            return; // Pitch alone is irrelevant.
        try {
            Sample sample = sample(player);
            if (sample == null) return;
            if (sample.wanted == (1 << KEYS.indexOf(SNEAK_TOGGLE))) return;
            Point a = Point.of(from), b = Point.of(to);
            if (b.equals(sample.last)) return; // Same/replayed endpoint cannot earn progress twice.
            if (sample.last != null && !sample.last.equals(a)) {
                reject(player, null);
                return;
            }
            sample.last = b;
            double horizontal = Math.hypot(to.getX() - from.getX(), to.getZ() - from.getZ());
            if (horizontal > 1.0 + EPSILON
                    || Math.abs(to.getY() - from.getY()) > 1.0 + EPSILON
                    || sample.distanceThisTick + horizontal > 1.0 + EPSILON) {
                reject(player, b);
                return;
            }
            sample.distanceThisTick += horizontal;
            Jump pending = sample.pendingJump;
            boolean jump =
                    pending != null
                            && pending.matches(from, to)
                            && sample.tick - sample.jumpTick >= 0
                            && sample.tick - sample.jumpTick <= 2;
            sample.pendingJump = null;
            boolean newAirJump = false;
            if (jump && sample.tick - sample.lastJump >= 5) {
                sample.lastJump = sample.tick;
                if (pending.selfInitiated && (sample.wants(WALK) || sample.wants(SPRINT))) {
                    sample.airJump = new AirJump(sample.tick, from.getY(), to.getY());
                    newAirJump = true;
                }
                if (sample.wants(JUMP)) report(player, sample, JUMP, 1);
            }
            if (!current(player, sample)) return;
            boolean ground = player.isOnGround();
            if (ground && !newAirJump) sample.airJump = null;
            if (sample.airJump != null && !sample.airJump.observe(sample.tick, to.getY()))
                sample.airJump = null;
            if (!ground && sample.airJump == null) {
                sample.turn = 0;
                return;
            }
            var input = player.getCurrentInput();
            if (horizontal > 0
                    && input != null
                    && (input.isForward()
                            || input.isBackward()
                            || input.isLeft()
                            || input.isRight())) {
                distance(player, sample, WALK, horizontal);
                if (!current(player, sample)) return;
                if (player.isSneaking()) {
                    if (ground) distance(player, sample, SNEAK, horizontal);
                } else if (player.isSprinting()) distance(player, sample, SPRINT, horizontal);
                if (!current(player, sample)) return;
                if (ground
                        && sample.wants(BACKWARD)
                        && input.isBackward()
                        && !input.isForward()
                        && !(input.isLeft() && input.isRight())
                        && !input.isSprint()
                        && !player.isSprinting())
                    distance(player, sample, BACKWARD, backward(from, to, horizontal));
            }
            if (!ground) {
                sample.turn = 0;
                return;
            }
            if (!current(player, sample) || !sample.wants(ROTATION)) return;
            double yaw = ((double) to.getYaw() - from.getYaw()) % 360.0;
            if (yaw > 180) yaw -= 360;
            if (yaw < -180) yaw += 360;
            // Exactly half a turn has no observable direction. Larger raw differences
            // describe only their shortest arc, never unobserved additional revolutions.
            if (Math.abs(yaw) >= 180.0 - EPSILON
                    || sample.yawThisTick + Math.abs(yaw) > 360.0 + EPSILON) {
                sample.turn = 0;
                return;
            }
            sample.yawThisTick += Math.abs(yaw);
            sample.turn +=
                    yaw; // Opposite turns subtract, rather than farming oscillation distance.
            if (Math.abs(sample.turn) >= 360.0 - EPSILON) {
                sample.turn -= Math.copySign(360.0, sample.turn);
                report(player, sample, ROTATION, 1);
            }
        } catch (RuntimeException failure) {
            failed(player, failure);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (event.isCancelled()) {
            reject(player, null);
            return;
        }
        try {
            if (!valid(event.getFrom())
                    || !valid(event.getTo())
                    || !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
                reject(player, null);
                return;
            }
            Sample sample = sample(player);
            if (sample != null
                    && (sample.wants(JUMP) || sample.wants(WALK) || sample.wants(SPRINT))) {
                var input = player.getCurrentInput();
                // Paper invokes Jump while still grounded, then updates onGround before Move.
                boolean selfInitiated =
                        player.isOnGround()
                                && input != null
                                && input.isJump()
                                && event.getTo().getY() - event.getFrom().getY() > EPSILON;
                sample.airJump = null;
                sample.pendingJump =
                        new Jump(Point.of(event.getFrom()), Point.of(event.getTo()), selfInitiated);
                sample.jumpTick = sample.tick;
            }
        } catch (RuntimeException failure) {
            failed(player, failure);
        }
    }

    /** Paper fires before updating the sneak flag (and before publishing the new Input). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        PendingSneak intent = null;
        try {
            Sample sample = sample(player, SNEAK_TOGGLE, true);
            boolean next = event.isSneaking();
            if (sample != null) {
                boolean before = player.isSneaking();
                ClientReadiness readiness = clientReadiness.get(player.getUniqueId());
                if (!ready(player, readiness)) {
                    sample.pendingSneak = null;
                    sample.sneaking = before;
                } else if (before != next) {
                    // This is an intent, not proof that Paper will apply the flag.
                    intent = new PendingSneak(player, sample, readiness, next, ticks.getAsInt());
                    sample.pendingSneak = intent;
                } else if (sample.pendingSneak == null || sample.pendingSneak.target != next) {
                    sample.pendingSneak = null;
                    sample.sneaking = before;
                }
            }
            if (!valid(player.getLocation())) {
                reject(player, null);
                return;
            }
            if (intent != null) intent.progressAllowed = next && current(player, sample);
        } catch (RuntimeException failure) {
            failed(player, failure);
        } finally {
            if (intent != null
                    && intent.sample.pendingSneak == intent
                    && samples.get(intent.id) == intent.sample) requestConfirmation(intent);
        }
    }

    private void discardPendingSneaks() {
        for (Sample sample : samples.values()) sample.pendingSneak = null;
    }

    private void confirmationFailure(RuntimeException failure) {
        ++confirmationEpoch;
        discardPendingSneaks();
        schedulingFailed = true;
        failedScheduleTick = ticks.getAsInt();
        logger.log(
                Level.WARNING,
                "Sneak confirmation discarded; no provisional progress was granted.",
                failure);
    }

    private void requestConfirmation(PendingSneak intent) {
        try {
            if (confirmationTask != null) {
                if (!confirmationTask.isCancelled()) {
                    int age = ticks.getAsInt() - confirmationScheduledTick;
                    if (age >= 0 && age <= 1) {
                        intent.batch = confirmationEpoch;
                        return;
                    }
                    confirmationTask.cancel();
                    if (!confirmationTask.isCancelled())
                        throw new IllegalStateException(
                                "Expired sneak task cancellation unconfirmed");
                }
                confirmationTask = null;
                confirmationFailure(
                        new IllegalStateException("Sneak confirmation task was cancelled"));
                return;
            }
            if (schedulingFailed && failedScheduleTick == ticks.getAsInt()) {
                discardPendingSneaks();
                return;
            }
            long epoch = ++confirmationEpoch;
            intent.batch = epoch;
            confirmationScheduledTick = ticks.getAsInt();
            confirmationTask =
                    Objects.requireNonNull(scheduleConfirmation.apply(() -> confirmSneaks(epoch)));
        } catch (RuntimeException failure) {
            confirmationFailure(failure);
        }
    }

    private void confirmSneaks(long epoch) {
        if (epoch != confirmationEpoch || confirmationTask == null) return;
        try {
            if (confirmationTask.isCancelled()) {
                confirmationTask = null;
                confirmationFailure(
                        new IllegalStateException("Cancelled sneak confirmation cannot grant"));
                return;
            }
        } catch (RuntimeException failure) {
            confirmationFailure(failure);
            return;
        }
        confirmationTask = null;
        ++confirmationEpoch; // A duplicate/late callback cannot drain a newer batch.
        List<PendingSneak> batch =
                samples.values().stream()
                        .map(sample -> sample.pendingSneak)
                        .filter(intent -> intent != null && intent.batch == epoch)
                        .toList();
        for (PendingSneak intent : batch) {
            Sample sample = intent.sample;
            if (samples.get(intent.id) != sample || sample.pendingSneak != intent) continue;
            sample.pendingSneak = null;
            Player player = null;
            try {
                player = online.apply(intent.id);
                if (player == null
                        || intent.player.get() != player
                        || player.getEntityId() != sample.entity) {
                    samples.remove(intent.id, sample);
                    continue;
                }
                if (!authorized.getAsBoolean() || sample(player, SNEAK_TOGGLE, true) != sample)
                    continue;
                if (!ready(player, intent.readiness)) continue;
                boolean actual = player.isSneaking();
                sample.sneaking = actual; // Only the authoritative post-event flag is confirmed.
                int age = ticks.getAsInt() - intent.tick;
                if (actual == intent.target
                        && intent.target
                        && intent.progressAllowed
                        && age >= 0
                        && age <= 1
                        && current(player, sample)
                        && intent.tick - sample.lastSneak >= SNEAK_COOLDOWN_TICKS) {
                    sample.lastSneak = intent.tick;
                    report(player, sample, SNEAK_TOGGLE, 1);
                }
            } catch (RuntimeException failure) {
                if (player != null) failed(player, failure);
                else
                    logger.log(
                            Level.WARNING,
                            "Sneak confirmation lookup failed for " + intent.id,
                            failure);
            }
        }
    }

    private static double backward(Location from, Location to, double horizontal) {
        double turn = ((double) to.getYaw() - from.getYaw()) % 360.0;
        if (turn > 180) turn -= 360;
        if (turn < -180) turn += 360;
        if (Math.abs(turn) > BACKWARD_MAX_TURN) return 0;
        double x = to.getX() - from.getX(), z = to.getZ() - from.getZ();
        double start = Math.toRadians(from.getYaw() % 360.0);
        double end = Math.toRadians(to.getYaw() % 360.0);
        // Backward axis is (sin(yaw), -cos(yaw)); retain only the distance supported
        // by BOTH event orientations, within 60 degrees of each backward axis.
        double projection =
                Math.min(
                        x * Math.sin(start) - z * Math.cos(start),
                        x * Math.sin(end) - z * Math.cos(end));
        return projection + EPSILON >= horizontal * BACKWARD_MIN_COSINE
                ? Math.min(horizontal, Math.max(0, projection))
                : 0;
    }

    private void distance(Player player, Sample sample, QuestProgressKey key, double distance) {
        if (!sample.wants(key)) return;
        int index = DISTANCE_KEYS.indexOf(key);
        double total = sample.fractions[index] + distance;
        long whole = (long) Math.floor(total + EPSILON);
        sample.fractions[index] = Math.max(0, total - whole);
        if (whole > 0) report(player, sample, key, whole);
    }

    private void report(Player player, Sample sample, QuestProgressKey key, long amount) {
        if (current(player, sample)) reporter.report(player.getUniqueId(), key, amount);
    }

    private boolean current(Player player, Sample sample) {
        var loaded = players.getPlayer(player.getUniqueId());
        return authorized.getAsBoolean()
                && samples.get(player.getUniqueId()) == sample
                && !sample.blockedAt(ticks.getAsInt())
                && eligible(player)
                && loaded.isPresent()
                && loaded.get().getQuestState().assignmentToken() == sample.assignments
                && definitions.snapshotById() == sample.catalog;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (online.apply(id) != event.getPlayer()) return;
        if (!authorized.getAsBoolean()) {
            forget(event.getPlayer());
            return;
        }
        if (event.isCancelled()) return;
        if (!eligible(event.getPlayer()) || players.getPlayer(id).isEmpty()) {
            forget(event.getPlayer());
            return;
        }
        Sample blocked = new Sample(event.getPlayer().getEntityId(), ticks.getAsInt());
        blocked.blocked = true;
        blocked.blockedUntil = blocked.tick + 20;
        samples.put(id, blocked);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.isCancelled()
                || !valid(event.getFrom())
                || !valid(event.getTo())
                || samePosition(event.getFrom(), event.getTo())) reject(event.getPlayer(), null);
        else forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        forget(event.getPlayer());
        resetReadiness(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer());
        ClientReadiness state = clientReadiness.get(event.getPlayer().getUniqueId());
        if (state != null && state.matches(event.getPlayer()))
            clientReadiness.remove(event.getPlayer().getUniqueId(), state);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!event.isCancelled()) forget(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        forget(event.getPlayer());
        resetReadiness(event.getPlayer(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClientLoaded(PlayerClientLoadedWorldEvent event) {
        resetReadiness(event.getPlayer(), true);
    }

    private void resetReadiness(Player player, boolean observedReady) {
        UUID id = player.getUniqueId();
        if (!authorized.getAsBoolean() || !player.isOnline() || online.apply(id) != player) return;
        clientReadiness.put(id, new ClientReadiness(player, observedReady, ticks.getAsInt()));
        Sample sample = samples.get(id);
        if (sample != null && sample.entity == player.getEntityId()) sample.pendingSneak = null;
        // A ready notification is not motion, a new action or permission to reset a guard/cooldown.
    }

    private boolean ready(Player player, ClientReadiness state) {
        if (state == null
                || clientReadiness.get(player.getUniqueId()) != state
                || !state.matches(player)) return false;
        // Paper publishes its internal loaded flag AFTER the notification returns.
        if (!state.ready && state.observedReady && ticks.getAsInt() - state.observedTick > 0)
            state.ready = true;
        return state.ready;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        if (!event.isCancelled() && event.getNewGameMode() != event.getPlayer().getGameMode())
            forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFlight(PlayerToggleFlightEvent event) {
        if (!event.isCancelled() && event.isFlying() != event.getPlayer().isFlying())
            forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!event.isCancelled()
                && event.getEntity() instanceof Player player
                && event.isGliding() != player.isGliding()) forget(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMount(EntityMountEvent event) {
        if (!event.isCancelled() && event.getEntity() instanceof Player player) forget(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (!event.isCancelled() && event.getEntity() instanceof Player player) forget(player);
    }

    @Override
    public void beforeJoin(Player player) {
        discardObservations(player);
    }

    @Override
    public void membershipChanged(UUID id) {
        contextChanged(id);
    }

    @Override
    public void beforeStateChange(Player player) {
        discardObservations(player);
    }

    @Override
    public void afterStateChange(UUID id) {
        contextChanged(id);
    }

    /** Service callbacks include attempts/finally notifications, not just committed changes. */
    private void contextChanged(UUID id) {
        Player player = online.apply(id);
        if (player == null) forget(id);
        else if (!eligible(player) || players.getPlayer(id).isEmpty()) forget(player);
        else discardObservations(player);
    }

    private void discardObservations(Player player) {
        Sample sample = samples.get(player.getUniqueId());
        if (sample != null
                && sample.entity == player.getEntityId()
                && online.apply(player.getUniqueId()) == player) reject(player, null);
    }

    public void forget(UUID id) {
        samples.remove(id);
    }

    private void forget(Player player) {
        Player current = online.apply(player.getUniqueId());
        if (current != null && current != player) return;
        Sample sample = samples.get(player.getUniqueId());
        if (sample != null && sample.entity == player.getEntityId())
            samples.remove(player.getUniqueId(), sample);
    }

    private void reject(Player player, Point endpoint) {
        UUID id = player.getUniqueId();
        if (!authorized.getAsBoolean() || online.apply(id) != player) {
            forget(player);
            return;
        }
        int tick = ticks.getAsInt();
        Sample sample = samples.get(id);
        if (sample == null || sample.entity != player.getEntityId()) {
            sample = new Sample(player.getEntityId(), tick);
            samples.put(id, sample);
        }
        java.util.Arrays.fill(sample.fractions, 0);
        if (sample.pendingSneak != null) sample.pendingSneak.progressAllowed = false;
        sample.turn = 0;
        sample.pendingJump = null;
        sample.airJump = null;
        sample.last = endpoint;
        // A local rejection clears observations, but neither shortens nor extends a live
        // exclusion. Compare elapsed ticks, not absolute deadlines (which may wrap).
        if (!sample.blockedAt(tick)) sample.blockedUntil = tick + 1;
        sample.blocked = true;
    }

    void clear() {
        ++confirmationEpoch;
        samples.clear();
        clientReadiness.clear();
        if (confirmationTask != null) {
            if (!confirmationTask.isCancelled()) confirmationTask.cancel();
            if (!confirmationTask.isCancelled())
                throw new IllegalStateException("Sneak task cancellation unconfirmed");
            confirmationTask = null;
        }
    }

    int trackedPlayers() {
        return samples.size();
    }

    private void failed(Player player, RuntimeException failure) {
        // A failed observation is not evidence of an actual lifecycle transition.
        try {
            discardObservations(player);
        } catch (RuntimeException cleanupFailure) {
            failure.addSuppressed(cleanupFailure);
        }
        logger.log(
                Level.WARNING,
                "Movement quest sample rejected for "
                        + player.getUniqueId()
                        + "; committed quest progress remains owned by QuestService.",
                failure);
    }

    private static boolean valid(Location at) {
        if (at == null) return false;
        try {
            return at.getWorld() != null
                    && Double.isFinite(at.getX())
                    && Double.isFinite(at.getY())
                    && Double.isFinite(at.getZ())
                    && Float.isFinite(at.getYaw())
                    && Math.abs(at.getX()) <= 30_000_000
                    && Math.abs(at.getZ()) <= 30_000_000;
        } catch (IllegalArgumentException unloadedWorld) {
            return false;
        }
    }

    private static boolean samePosition(Location a, Location b) {
        return a.getWorld().getUID().equals(b.getWorld().getUID())
                && a.getX() == b.getX()
                && a.getY() == b.getY()
                && a.getZ() == b.getZ();
    }

    private record Point(UUID world, double x, double y, double z, float yaw) {
        static Point of(Location at) {
            float yaw = (float) ((((double) at.getYaw() + 180.0) % 360.0 + 360.0) % 360.0 - 180.0);
            return new Point(
                    at.getWorld().getUID(), at.getX(), at.getY(), at.getZ(), yaw == 0 ? 0 : yaw);
        }

        boolean position(Point other) {
            return world.equals(other.world) && x == other.x && y == other.y && z == other.z;
        }
    }

    private record Jump(Point from, Point to, boolean selfInitiated) {
        boolean matches(Location a, Location b) {
            return from.position(Point.of(a)) && to.position(Point.of(b));
        }
    }

    /** Bounded normal-jump observation, not a physics prediction or a general airborne allowance. */
    private static final class AirJump {
        final int tick;
        final double originY;
        double lastY;
        boolean descending;

        AirJump(int tick, double originY, double lastY) {
            this.tick = tick;
            this.originY = originY;
            this.lastY = lastY;
        }

        boolean observe(int now, double y) {
            int age = now - tick;
            double dy = y - lastY;
            if (age < 0
                    || age > 20
                    || y > originY + 1.5
                    || y < originY - 1.0
                    || (descending && dy > EPSILON)) return false;
            if (dy < -EPSILON) descending = true;
            lastY = y;
            return true;
        }
    }

    private static final class Sample {
        final int entity;
        final double[] fractions = new double[DISTANCE_KEYS.size()];
        Object assignments, catalog;
        int wanted, tick, blockedUntil, jumpTick, lastJump, lastSneak;
        boolean blocked, sneaking;
        double distanceThisTick, yawThisTick, turn;
        Point last;
        Jump pendingJump;
        AirJump airJump;
        PendingSneak pendingSneak;

        Sample(int entity, int tick) {
            this.entity = entity;
            this.tick = tick;
            lastJump = tick - 5;
            lastSneak = tick - SNEAK_COOLDOWN_TICKS;
        }

        boolean wants(QuestProgressKey key) {
            return (wanted & (1 << KEYS.indexOf(key))) != 0;
        }

        boolean blockedAt(int tick) {
            return blocked && tick - blockedUntil < 0;
        }
    }

    /** One short-lived intent per sample; the weak identity never retains a disconnected Player. */
    private static final class PendingSneak {
        final UUID id;
        final WeakReference<Player> player;
        final Sample sample;
        final ClientReadiness readiness;
        final boolean target;
        final int tick;
        long batch;
        boolean progressAllowed;

        PendingSneak(
                Player player, Sample sample, ClientReadiness readiness, boolean target, int tick) {
            this.id = player.getUniqueId();
            this.player = new WeakReference<>(player);
            this.sample = sample;
            this.readiness = readiness;
            this.target = target;
            this.tick = tick;
        }
    }

    private static final class ClientReadiness {
        final WeakReference<Player> player;
        final int entity, observedTick;
        final boolean observedReady;
        boolean ready;

        ClientReadiness(Player player, boolean observedReady, int tick) {
            this.player = new WeakReference<>(player);
            entity = player.getEntityId();
            observedTick = tick;
            this.observedReady = observedReady;
        }

        boolean matches(Player actual) {
            return player.get() == actual && entity == actual.getEntityId();
        }
    }
}
