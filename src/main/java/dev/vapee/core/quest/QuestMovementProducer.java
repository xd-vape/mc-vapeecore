package dev.vapee.core.quest;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.player.PlayerService;

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

/** Synchronous, bounded observation only. Assignments, persistence and rewards remain with QuestService. */
public final class QuestMovementProducer
        implements Listener, ActivityMembershipListener, LobbyPlayerStateListener {
    public static final QuestProgressKey WALK = QuestProgressKey.of("movement:walk");
    public static final QuestProgressKey SPRINT = QuestProgressKey.of("movement:sprint");
    public static final QuestProgressKey JUMP = QuestProgressKey.of("movement:jump");
    public static final QuestProgressKey ROTATION = QuestProgressKey.of("movement:rotation_full");
    public static final QuestProgressKey SNEAK = QuestProgressKey.of("movement:sneak");
    private static final List<QuestProgressKey> KEYS = List.of(WALK, SPRINT, JUMP, ROTATION, SNEAK);
    private static final double EPSILON = 1.0e-9;
    private final PlayerService players;
    private final QuestDefinitionRegistry definitions;
    private final QuestProgressReporter reporter;
    private final Function<UUID, Player> online;
    private final Predicate<UUID> context;
    private final IntSupplier ticks;
    private final BooleanSupplier authorized;
    private final Logger logger;
    private final Map<UUID, Sample> samples = new HashMap<>();

    QuestMovementProducer(
            PlayerService players,
            QuestDefinitionRegistry definitions,
            QuestProgressReporter reporter,
            Function<UUID, Player> online,
            Predicate<UUID> context,
            IntSupplier ticks,
            BooleanSupplier authorized,
            Logger logger) {
        this.players = Objects.requireNonNull(players);
        this.definitions = Objects.requireNonNull(definitions);
        this.reporter = Objects.requireNonNull(reporter);
        this.online = Objects.requireNonNull(online);
        this.context = Objects.requireNonNull(context);
        this.ticks = Objects.requireNonNull(ticks);
        this.authorized = Objects.requireNonNull(authorized);
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
        int tick = ticks.getAsInt();
        Sample previous = samples.get(id);
        if (previous != null
                && previous.entity == player.getEntityId()
                && previous.blocked
                && tick - previous.blockedUntil < 0) return null;
        if (previous == null
                || previous.entity != player.getEntityId()
                || previous.assignments != quests.assignmentToken()
                || previous.catalog != catalog
                || tick - previous.tick < 0
                || tick - previous.tick > 40) {
            previous = new Sample(player.getEntityId(), tick);
            previous.assignments = quests.assignmentToken();
            previous.catalog = catalog;
            samples.put(id, previous);
        }
        if (previous.blocked) {
            previous.last = null;
            previous.pendingJump = null;
            previous.turn = 0;
        }
        previous.blocked = false;
        previous.wanted = wanted;
        for (int index = 0; index < KEYS.size(); index++)
            if ((wanted & (1 << index)) == 0) previous.fractions[index] = 0;
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
        UUID id = player.getUniqueId();
        if (event instanceof PlayerTeleportEvent) {
            forget(player);
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
            boolean jump =
                    sample.pendingJump != null
                            && sample.pendingJump.matches(from, to)
                            && sample.tick - sample.jumpTick >= 0
                            && sample.tick - sample.jumpTick <= 2;
            sample.pendingJump = null;
            if (jump && sample.wants(JUMP) && sample.tick - sample.lastJump >= 5) {
                sample.lastJump = sample.tick;
                report(player, sample, JUMP, 1);
            }
            if (!current(player, sample)) return;
            if (!player.isOnGround()) {
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
                if (player.isSneaking()) distance(player, sample, SNEAK, horizontal);
                else if (player.isSprinting()) distance(player, sample, SPRINT, horizontal);
            }
            if (!current(player, sample) || !sample.wants(ROTATION)) return;
            double yaw = ((double) to.getYaw() - from.getYaw()) % 360.0;
            if (yaw > 180) yaw -= 360;
            if (yaw < -180) yaw += 360;
            if (Math.abs(yaw) > 90.0 || sample.yawThisTick + Math.abs(yaw) > 90.0 + EPSILON) {
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
            failed(id, failure);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJump(PlayerJumpEvent event) {
        Player player = event.getPlayer();
        if (event.isCancelled()) {
            forget(player);
            return;
        }
        try {
            if (!valid(event.getFrom())
                    || !valid(event.getTo())
                    || !event.getFrom().getWorld().equals(event.getTo().getWorld())) {
                forget(player);
                return;
            }
            Sample sample = sample(player);
            if (sample != null && sample.wants(JUMP)) {
                sample.pendingJump = new Jump(Point.of(event.getFrom()), Point.of(event.getTo()));
                sample.jumpTick = sample.tick;
            }
        } catch (RuntimeException failure) {
            failed(player.getUniqueId(), failure);
        }
    }

    private void distance(Player player, Sample sample, QuestProgressKey key, double distance) {
        if (!sample.wants(key)) return;
        int index = KEYS.indexOf(key);
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
                && eligible(player)
                && loaded.isPresent()
                && loaded.get().getQuestState().assignmentToken() == sample.assignments
                && definitions.snapshotById() == sample.catalog;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onVelocity(PlayerVelocityEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (online.apply(id) != event.getPlayer()) return;
        forget(id);
        if (!authorized.getAsBoolean() || event.isCancelled()) return;
        Sample blocked = new Sample(event.getPlayer().getEntityId(), ticks.getAsInt());
        blocked.blocked = true;
        blocked.blockedUntil = blocked.tick + 20;
        samples.put(id, blocked);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onTeleport(PlayerTeleportEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorld(PlayerChangedWorldEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        forget(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onFlight(PlayerToggleFlightEvent event) {
        forget(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player) forget(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onMount(EntityMountEvent event) {
        if (event.getEntity() instanceof Player player) forget(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player) forget(player);
    }

    @Override
    public void beforeJoin(Player player) {
        forget(player);
    }

    @Override
    public void membershipChanged(UUID id) {
        forget(id);
    }

    @Override
    public void beforeStateChange(Player player) {
        forget(player);
    }

    @Override
    public void afterStateChange(UUID id) {
        forget(id);
    }

    public void forget(UUID id) {
        samples.remove(id);
    }

    private void forget(Player player) {
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
        sample.turn = 0;
        sample.pendingJump = null;
        sample.last = endpoint;
        sample.blocked = true;
        sample.blockedUntil =
                tick + 1; // Rejection must not replenish this tick's anti-burst budgets.
    }

    void clear() {
        samples.clear();
    }

    int trackedPlayers() {
        return samples.size();
    }

    private void failed(UUID id, RuntimeException failure) {
        forget(id);
        logger.log(
                Level.WARNING,
                "Movement quest sample rejected for "
                        + id
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

    private record Jump(Point from, Point to) {
        boolean matches(Location a, Location b) {
            return from.position(Point.of(a)) && to.position(Point.of(b));
        }
    }

    private static final class Sample {
        final int entity;
        final double[] fractions = new double[5];
        Object assignments, catalog;
        int wanted, tick, blockedUntil, jumpTick, lastJump;
        boolean blocked;
        double distanceThisTick, yawThisTick, turn;
        Point last;
        Jump pendingJump;

        Sample(int entity, int tick) {
            this.entity = entity;
            this.tick = tick;
            lastJump = tick - 5;
        }

        boolean wants(QuestProgressKey key) {
            return (wanted & (1 << KEYS.indexOf(key))) != 0;
        }
    }
}
