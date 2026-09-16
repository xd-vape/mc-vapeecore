package dev.vapee.core.activity;

import dev.vapee.core.activity.location.ActivityArea;
import dev.vapee.core.activity.location.ActivityPosition;
import dev.vapee.core.activity.location.ActivityVenue;
import dev.vapee.core.activity.player.ActivityParticipant;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ActivityHarness {

    private static int checks;

    private ActivityHarness() {
    }

    public static void main(String[] args) {
        testStateTransitions();
        testLocationModels();
        testTypeAndVenueRegistries();
        testSessionCreationAndReservation();
        testMembershipAndActivation();
        testResetAndTaskOwnership();
        testHookFailureCleanup();
        testListenerCleanupAndShutdown();
        testMainThreadGuard();
        System.out.println("ActivityHarness passed " + checks + " checks.");
    }

    private static void testStateTransitions() {
        check(ActivityState.AVAILABLE.canTransitionTo(ActivityState.ACTIVE), "AVAILABLE -> ACTIVE");
        check(ActivityState.ACTIVE.canTransitionTo(ActivityState.RESETTING), "ACTIVE -> RESETTING");
        check(ActivityState.RESETTING.canTransitionTo(ActivityState.AVAILABLE), "RESETTING -> AVAILABLE");
        check(ActivityState.AVAILABLE.canTransitionTo(ActivityState.CLOSED), "AVAILABLE -> CLOSED");
        check(ActivityState.ACTIVE.canTransitionTo(ActivityState.CLOSED), "ACTIVE -> CLOSED");
        check(ActivityState.RESETTING.canTransitionTo(ActivityState.CLOSED), "RESETTING -> CLOSED");
        check(!ActivityState.AVAILABLE.canTransitionTo(ActivityState.RESETTING), "AVAILABLE !-> RESETTING");
        check(!ActivityState.ACTIVE.canTransitionTo(ActivityState.AVAILABLE), "ACTIVE !-> AVAILABLE");
        check(!ActivityState.RESETTING.canTransitionTo(ActivityState.ACTIVE), "RESETTING !-> ACTIVE");
        check(!ActivityState.CLOSED.canTransitionTo(ActivityState.AVAILABLE), "CLOSED is terminal");
    }

    private static void testLocationModels() {
        expectThrows(() -> new ActivityPosition(" ", 0, 0, 0, 0, 0), "blank position world");
        expectThrows(() -> new ActivityPosition("world", Double.NaN, 0, 0, 0, 0), "NaN position");
        expectThrows(() -> new ActivityPosition("world", 0, 0, 0, Float.POSITIVE_INFINITY, 0), "infinite yaw");

        ActivityPosition position = new ActivityPosition("world", 2, 3, 4, 90, 10);
        World world = world("world");
        Server server = server(Map.of("world", world));
        check(position.toLocation(server).isPresent(), "loaded position resolves");
        check(new ActivityPosition("missing", 0, 0, 0, 0, 0).toLocation(server).isEmpty(),
                "unloaded position does not resolve");

        ActivityArea area = new ActivityArea("world", 10, 10, 10, 0, 0, 0);
        check(area.minX() == 0 && area.maxX() == 10, "area coordinates normalize");
        check(area.contains(new Location(world, 5, 5, 5)), "area contains same-world point");
        check(!area.contains(new Location(world, 11, 5, 5)), "area excludes outside point");
        check(!area.contains(new Location(world("other"), 5, 5, 5)), "area excludes other world");
        expectThrows(() -> new ActivityArea("world", 0, 0, 0, Double.NEGATIVE_INFINITY, 1, 1),
                "area rejects non-finite coordinate");

        new ActivityVenue("table_1", "test", area, position);
        expectThrows(() -> new ActivityVenue("Bad", "test", area, position), "venue rejects invalid id");
        expectThrows(() -> new ActivityVenue(
                "outside",
                "test",
                area,
                new ActivityPosition("world", 20, 3, 4, 0, 0)
        ), "venue rejects outside anchor");
        expectThrows(() -> new ActivityVenue(
                "other_world",
                "test",
                area,
                new ActivityPosition("other", 2, 3, 4, 0, 0)
        ), "venue rejects anchor world mismatch");
    }

    private static void testTypeAndVenueRegistries() {
        Fixture fixture = fixture();
        checkResult(fixture.service.registerActivityType(new FakeType("", 1, 1)), ActivityResult.INVALID_ACTIVITY_TYPE);
        checkResult(fixture.service.registerActivityType(new FakeType("Upper", 1, 1)), ActivityResult.INVALID_ACTIVITY_TYPE);
        checkResult(fixture.service.registerActivityType(new FakeType("bad.key", 1, 1)), ActivityResult.INVALID_ACTIVITY_TYPE);
        checkResult(fixture.service.registerActivityType(new FakeType("zero", 0, 1)), ActivityResult.INVALID_ACTIVITY_TYPE);
        checkResult(fixture.service.registerActivityType(new FakeType("range", 2, 1)), ActivityResult.INVALID_ACTIVITY_TYPE);

        FakeType type = new FakeType("test", 1, 2);
        checkResult(fixture.service.registerActivityType(type), ActivityResult.SUCCESS);
        checkResult(fixture.service.registerActivityType(new FakeType("test", 1, 2)),
                ActivityResult.ACTIVITY_TYPE_ALREADY_REGISTERED);
        check(fixture.service.getActivityType("TEST").orElseThrow() == type, "type lookup is case-insensitive");

        ActivityVenue unknownTypeVenue = venue("unknown", "missing", "world");
        checkResult(fixture.service.registerVenue(unknownTypeVenue), ActivityResult.ACTIVITY_TYPE_NOT_FOUND);
        ActivityVenue venue = venue("one", "test", "world");
        checkResult(fixture.service.registerVenue(venue), ActivityResult.SUCCESS);
        checkResult(fixture.service.registerVenue(venue("one", "test", "world")),
                ActivityResult.VENUE_ALREADY_REGISTERED);
        checkResult(fixture.service.unregisterActivityType("test"), ActivityResult.ACTIVITY_TYPE_IN_USE);
        checkResult(fixture.service.unregisterVenue("test", "one"), ActivityResult.SUCCESS);
        checkResult(fixture.service.unregisterActivityType("test"), ActivityResult.SUCCESS);
    }

    private static void testSessionCreationAndReservation() {
        Fixture fixture = configuredFixture("test", 1, 2, "one");
        ActivitySessionCreationResult creation = fixture.service.createSession("test", "one");
        checkResult(creation.result(), ActivityResult.SUCCESS);
        FakeSession session = (FakeSession) creation.session().orElseThrow();
        check(session.getState() == ActivityState.AVAILABLE, "new session is AVAILABLE");
        check(session.getParticipantCount() == 0, "new session has no participants");
        check(fixture.service.getSession(session.getSessionId()).orElseThrow() == session, "session lookup works");
        check(fixture.service.findAvailableSession("test").orElseThrow() == session,
                "available session lookup is deterministic");
        checkResult(fixture.service.createSession("test", "one").result(), ActivityResult.VENUE_IN_USE);
        checkResult(fixture.service.unregisterVenue("test", "one"), ActivityResult.VENUE_IN_USE);

        checkResult(fixture.service.closeSession(session.getSessionId(), ActivityLeaveReason.SESSION_CLOSED),
                ActivityResult.SUCCESS);
        check(session.getState() == ActivityState.CLOSED, "closed session reaches terminal state");
        check(fixture.service.getSession(session.getSessionId()).isEmpty(), "closed session leaves registry");
        checkResult(fixture.service.createSession("test", "one").result(), ActivityResult.SUCCESS);

        Fixture missingWorld = configuredFixture("test", 1, 1, "one");
        missingWorld.loadedWorlds.clear();
        checkResult(missingWorld.service.createSession("test", "one").result(), ActivityResult.VENUE_UNAVAILABLE);
        checkResult(missingWorld.service.createSession("missing", "one").result(),
                ActivityResult.ACTIVITY_TYPE_NOT_FOUND);
        checkResult(missingWorld.service.createSession("test", "missing").result(), ActivityResult.VENUE_NOT_FOUND);
    }

    private static void testMembershipAndActivation() {
        Fixture fixture = configuredFixture("test", 2, 2, "one", "two");
        FakeSession first = create(fixture, "one");
        FakeSession second = create(fixture, "two");
        UUID firstPlayerId = UUID.randomUUID();
        UUID secondPlayerId = UUID.randomUUID();
        Player firstPlayer = fixture.onlinePlayer(firstPlayerId);
        Player secondPlayer = fixture.onlinePlayer(secondPlayerId);
        fixture.load(firstPlayerId, "First");
        fixture.load(secondPlayerId, "Second");

        checkResult(fixture.service.joinSession(firstPlayer, first.getSessionId()), ActivityResult.SUCCESS);
        checkResult(fixture.service.joinSession(firstPlayer, first.getSessionId()),
                ActivityResult.PLAYER_ALREADY_IN_ACTIVITY);
        checkResult(fixture.service.joinSession(firstPlayer, second.getSessionId()),
                ActivityResult.PLAYER_ALREADY_IN_ACTIVITY);
        checkResult(fixture.service.activateSession(first.getSessionId()), ActivityResult.NOT_ENOUGH_PARTICIPANTS);
        checkResult(fixture.service.joinSession(secondPlayer, first.getSessionId()), ActivityResult.SUCCESS);
        checkResult(fixture.service.activateSession(first.getSessionId()), ActivityResult.SUCCESS);
        check(first.getState() == ActivityState.ACTIVE && first.activatedCalls == 1,
                "activation transitions and calls hook once");
        checkResult(fixture.service.activateSession(first.getSessionId()), ActivityResult.INVALID_STATE);
        check(first.activatedCalls == 1, "second activation does not call hook");

        checkResult(fixture.service.leaveCurrentSession(firstPlayerId, ActivityLeaveReason.VOLUNTARY),
                ActivityResult.SUCCESS);
        check(first.getState() == ActivityState.AVAILABLE, "active session auto-resets below minimum");
        check(first.resetCalls == 1, "auto-reset invokes reset hook");
        check(first.getParticipantCount() == 1 && first.hasParticipant(secondPlayerId),
                "normal auto-reset keeps remaining participants");

        UUID thirdPlayerId = UUID.randomUUID();
        Player thirdPlayer = fixture.onlinePlayer(thirdPlayerId);
        checkResult(fixture.service.joinSession(thirdPlayer, first.getSessionId()), ActivityResult.PLAYER_NOT_LOADED);
        checkResult(fixture.service.joinSession(fixture.player(thirdPlayerId, false), first.getSessionId()),
                ActivityResult.PLAYER_OFFLINE);

        Fixture fullFixture = configuredFixture("test", 1, 1, "one");
        FakeSession full = create(fullFixture, "one");
        UUID loadedOne = UUID.randomUUID();
        UUID loadedTwo = UUID.randomUUID();
        fullFixture.load(loadedOne, "One");
        fullFixture.load(loadedTwo, "Two");
        checkResult(fullFixture.service.joinSession(fullFixture.onlinePlayer(loadedOne), full.getSessionId()),
                ActivityResult.SUCCESS);
        checkResult(fullFixture.service.joinSession(fullFixture.onlinePlayer(loadedTwo), full.getSessionId()),
                ActivityResult.SESSION_FULL);
        checkResult(fullFixture.service.activateSession(full.getSessionId()), ActivityResult.SUCCESS);
        checkResult(fullFixture.service.joinSession(fullFixture.onlinePlayer(loadedTwo), full.getSessionId()),
                ActivityResult.SESSION_NOT_AVAILABLE);
    }

    private static void testResetAndTaskOwnership() {
        Fixture fixture = configuredFixture("test", 1, 2, "one");
        FakeSession session = create(fixture, "one");
        UUID playerId = UUID.randomUUID();
        fixture.load(playerId, "Player");
        checkResult(fixture.service.joinSession(fixture.onlinePlayer(playerId), session.getSessionId()),
                ActivityResult.SUCCESS);
        checkResult(fixture.service.activateSession(session.getSessionId()), ActivityResult.SUCCESS);

        TaskState roundTask = new TaskState(false);
        session.addTask(roundTask.task());
        session.taskDuringReset = new TaskState(false);
        checkResult(fixture.service.resetSession(session.getSessionId()), ActivityResult.SUCCESS);
        check(roundTask.cancelled.get(), "round task cancelled during reset");
        check(session.taskDuringReset.cancelled.get(), "task created during reset also cancelled");
        check(session.getTrackedTaskCount() == 0, "tracked tasks empty after reset");
        check(session.getState() == ActivityState.AVAILABLE, "reset returns session to AVAILABLE");
        check(session.hasParticipant(playerId), "reset keeps participant");

        checkResult(fixture.service.activateSession(session.getSessionId()), ActivityResult.SUCCESS);
        TaskState closeTask = new TaskState(false);
        session.addTask(closeTask.task());
        checkResult(fixture.service.closeSession(session.getSessionId(), ActivityLeaveReason.SESSION_CLOSED),
                ActivityResult.SUCCESS);
        check(closeTask.cancelled.get(), "tracked task cancelled during close");

        Fixture failingTaskFixture = configuredFixture("test", 1, 1, "one");
        FakeSession failingTaskSession = create(failingTaskFixture, "one");
        UUID id = UUID.randomUUID();
        failingTaskFixture.load(id, "Player");
        checkResult(failingTaskFixture.service.joinSession(
                failingTaskFixture.onlinePlayer(id),
                failingTaskSession.getSessionId()
        ), ActivityResult.SUCCESS);
        checkResult(failingTaskFixture.service.activateSession(failingTaskSession.getSessionId()),
                ActivityResult.SUCCESS);
        TaskState failingTask = new TaskState(true);
        failingTaskSession.addTask(failingTask.task());
        checkResult(failingTaskFixture.service.resetSession(failingTaskSession.getSessionId()),
                ActivityResult.HOOK_FAILED);
        check(failingTaskSession.getState() == ActivityState.CLOSED, "task cancellation failure safely closes session");
        check(failingTaskFixture.service.getMembershipCount() == 0, "task failure clears membership");
    }

    private static void testHookFailureCleanup() {
        Fixture joinFixture = configuredFixture("test", 1, 2, "one");
        FakeSession joinSession = create(joinFixture, "one");
        joinSession.failJoin = true;
        UUID joinId = UUID.randomUUID();
        joinFixture.load(joinId, "Join");
        checkResult(joinFixture.service.joinSession(joinFixture.onlinePlayer(joinId), joinSession.getSessionId()),
                ActivityResult.HOOK_FAILED);
        check(joinSession.getParticipantCount() == 0 && !joinFixture.service.isParticipating(joinId),
                "join hook failure rolls back membership");
        check(joinSession.getState() == ActivityState.AVAILABLE, "join hook failure keeps session consistent");

        Fixture activateFixture = configuredFixture("test", 1, 1, "one");
        FakeSession activateSession = create(activateFixture, "one");
        UUID activateId = UUID.randomUUID();
        activateFixture.load(activateId, "Activate");
        checkResult(activateFixture.service.joinSession(
                activateFixture.onlinePlayer(activateId),
                activateSession.getSessionId()
        ), ActivityResult.SUCCESS);
        activateSession.failActivate = true;
        checkResult(activateFixture.service.activateSession(activateSession.getSessionId()), ActivityResult.HOOK_FAILED);
        check(activateSession.getState() == ActivityState.CLOSED, "activation hook failure safely closes");
        check(activateFixture.service.getSessionCount() == 0 && activateFixture.service.getMembershipCount() == 0,
                "activation failure cleans registries");

        Fixture resetFixture = configuredFixture("test", 1, 1, "one");
        FakeSession resetSession = create(resetFixture, "one");
        UUID resetId = UUID.randomUUID();
        resetFixture.load(resetId, "Reset");
        checkResult(resetFixture.service.joinSession(resetFixture.onlinePlayer(resetId), resetSession.getSessionId()),
                ActivityResult.SUCCESS);
        checkResult(resetFixture.service.activateSession(resetSession.getSessionId()), ActivityResult.SUCCESS);
        resetSession.failReset = true;
        checkResult(resetFixture.service.resetSession(resetSession.getSessionId()), ActivityResult.HOOK_FAILED);
        check(resetSession.getState() == ActivityState.CLOSED, "reset hook failure safely closes");
        check(resetFixture.service.getMembershipCount() == 0, "reset failure clears membership");
        checkResult(resetFixture.service.createSession("test", "one").result(), ActivityResult.SUCCESS);

        Fixture leaveFixture = configuredFixture("test", 1, 2, "one");
        FakeSession leaveSession = create(leaveFixture, "one");
        UUID leaveId = UUID.randomUUID();
        leaveFixture.load(leaveId, "Leave");
        checkResult(leaveFixture.service.joinSession(leaveFixture.onlinePlayer(leaveId), leaveSession.getSessionId()),
                ActivityResult.SUCCESS);
        leaveSession.failLeave = true;
        checkResult(leaveFixture.service.leaveCurrentSession(leaveId, ActivityLeaveReason.VOLUNTARY),
                ActivityResult.HOOK_FAILED);
        check(!leaveSession.hasParticipant(leaveId) && !leaveFixture.service.isParticipating(leaveId),
                "leave hook failure cannot roll back cleanup");

        UUID secondId = UUID.randomUUID();
        leaveFixture.load(secondId, "Second");
        checkResult(leaveFixture.service.joinSession(leaveFixture.onlinePlayer(secondId), leaveSession.getSessionId()),
                ActivityResult.SUCCESS);
        checkResult(leaveFixture.service.closeSession(leaveSession.getSessionId(), ActivityLeaveReason.SESSION_CLOSED),
                ActivityResult.SUCCESS);
        check(leaveSession.getState() == ActivityState.CLOSED && leaveFixture.service.getMembershipCount() == 0,
                "close remains best-effort when leave hook fails");
    }

    private static void testListenerCleanupAndShutdown() {
        Fixture fixture = configuredFixture("test", 1, 3, "one", "two");
        ActivityListener listener = new ActivityListener(fixture.service);
        FakeSession first = create(fixture, "one");
        FakeSession second = create(fixture, "two");
        UUID quitId = UUID.randomUUID();
        UUID worldId = UUID.randomUUID();
        fixture.load(quitId, "Quit");
        fixture.load(worldId, "World");
        checkResult(fixture.service.joinSession(fixture.onlinePlayer(quitId), first.getSessionId()),
                ActivityResult.SUCCESS);
        checkResult(fixture.service.joinSession(fixture.onlinePlayer(worldId), second.getSessionId()),
                ActivityResult.SUCCESS);

        listener.handlePlayerChangedWorld(worldId, "world");
        check(fixture.service.isParticipating(worldId), "same-world change keeps membership");
        listener.handlePlayerChangedWorld(worldId, "other");
        check(!fixture.service.isParticipating(worldId), "different-world change removes membership");
        listener.handlePlayerQuit(quitId);
        check(!fixture.service.isParticipating(quitId), "quit removes membership");

        UUID shutdownOne = UUID.randomUUID();
        UUID shutdownTwo = UUID.randomUUID();
        fixture.load(shutdownOne, "ShutdownOne");
        fixture.load(shutdownTwo, "ShutdownTwo");
        checkResult(fixture.service.joinSession(fixture.onlinePlayer(shutdownOne), first.getSessionId()),
                ActivityResult.SUCCESS);
        checkResult(fixture.service.joinSession(fixture.onlinePlayer(shutdownTwo), second.getSessionId()),
                ActivityResult.SUCCESS);
        fixture.service.shutdown();
        check(fixture.service.getSessionCount() == 0, "shutdown clears sessions");
        check(fixture.service.getMembershipCount() == 0, "shutdown clears memberships");
        check(fixture.service.getVenueCount() == 0, "shutdown clears venues");
        check(fixture.service.getActivityTypeCount() == 0, "shutdown clears activity types");
        check(first.getState() == ActivityState.CLOSED && second.getState() == ActivityState.CLOSED,
                "shutdown closes every session");
    }

    private static void testMainThreadGuard() {
        Logger logger = logger();
        PlayerService players = new PlayerService(new MemoryRepository(), logger);
        ActivityService service = new ActivityService(players, logger, () -> false, ignored -> true);
        expectThrows(() -> service.registerActivityType(new FakeType("test", 1, 1)),
                "off-thread mutation rejected");
    }

    private static Fixture configuredFixture(String key, int min, int max, String... venueIds) {
        Fixture fixture = fixture();
        checkResult(fixture.service.registerActivityType(new FakeType(key, min, max)), ActivityResult.SUCCESS);
        for (String venueId : venueIds) {
            checkResult(fixture.service.registerVenue(venue(venueId, key, "world")), ActivityResult.SUCCESS);
        }
        return fixture;
    }

    private static Fixture fixture() {
        Logger logger = logger();
        PlayerService players = new PlayerService(new MemoryRepository(), logger);
        Set<String> loadedWorlds = new HashSet<>(Set.of("world"));
        ActivityService service = new ActivityService(players, logger, () -> true, loadedWorlds::contains);
        return new Fixture(service, players, loadedWorlds, world("world"));
    }

    private static FakeSession create(Fixture fixture, String venueId) {
        return (FakeSession) fixture.service.createSession("test", venueId).session().orElseThrow();
    }

    private static ActivityVenue venue(String id, String activityKey, String worldName) {
        return new ActivityVenue(
                id,
                activityKey,
                new ActivityArea(worldName, 0, 0, 0, 10, 10, 10),
                new ActivityPosition(worldName, 5, 5, 5, 0, 0)
        );
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static World world(String name) {
        return proxy(World.class, (method, arguments) -> {
            if (method.getName().equals("getName")) {
                return name;
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static Server server(Map<String, World> worlds) {
        return proxy(Server.class, (method, arguments) -> {
            if (method.getName().equals("getWorld")
                    && arguments != null
                    && arguments.length == 1
                    && arguments[0] instanceof String worldName) {
                return worlds.get(worldName);
            }
            return defaultValue(method.getReturnType());
        });
    }

    private static void checkResult(ActivityResult actual, ActivityResult expected) {
        check(actual == expected, "expected " + expected + " but got " + actual);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void expectThrows(Runnable action, String message) {
        checks++;
        try {
            action.run();
        } catch (RuntimeException expected) {
            return;
        }
        throw new AssertionError(message);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyHandler handler) {
        return (T) Proxy.newProxyInstance(
                type.getClassLoader(),
                new Class<?>[]{type},
                (proxy, method, arguments) -> {
                    if (method.getDeclaringClass() == Object.class) {
                        return switch (method.getName()) {
                            case "toString" -> type.getSimpleName() + "Proxy";
                            case "hashCode" -> System.identityHashCode(proxy);
                            case "equals" -> proxy == arguments[0];
                            default -> null;
                        };
                    }
                    return handler.invoke(method, arguments);
                }
        );
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) {
            return null;
        }
        if (type == boolean.class) {
            return false;
        }
        if (type == char.class) {
            return '\0';
        }
        if (type == byte.class) {
            return (byte) 0;
        }
        if (type == short.class) {
            return (short) 0;
        }
        if (type == int.class) {
            return 0;
        }
        if (type == long.class) {
            return 0L;
        }
        if (type == float.class) {
            return 0F;
        }
        if (type == double.class) {
            return 0D;
        }
        return null;
    }

    @FunctionalInterface
    private interface ProxyHandler {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }

    private record Fixture(
            ActivityService service,
            PlayerService players,
            Set<String> loadedWorlds,
            World world
    ) {

        private void load(UUID uniqueId, String name) {
            players.loadPlayer(uniqueId, name);
        }

        private Player onlinePlayer(UUID uniqueId) {
            return player(uniqueId, true);
        }

        private Player player(UUID uniqueId, boolean online) {
            Location location = new Location(world, 5, 5, 5);
            return proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> uniqueId;
                case "getName" -> "HarnessPlayer";
                case "isOnline" -> online;
                case "getWorld" -> world;
                case "getLocation" -> location;
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static final class FakeType implements ActivityType {

        private final String key;
        private final int minParticipants;
        private final int maxParticipants;

        private FakeType(String key, int minParticipants, int maxParticipants) {
            this.key = key;
            this.minParticipants = minParticipants;
            this.maxParticipants = maxParticipants;
        }

        @Override
        public String getKey() {
            return key;
        }

        @Override
        public int getMinParticipants() {
            return minParticipants;
        }

        @Override
        public int getMaxParticipants() {
            return maxParticipants;
        }

        @Override
        public ActivitySession createSession(UUID sessionId, ActivityVenue venue) {
            return new FakeSession(sessionId, key, venue);
        }
    }

    private static final class FakeSession extends ActivitySession {

        private int activatedCalls;
        private int resetCalls;
        private boolean failJoin;
        private boolean failLeave;
        private boolean failActivate;
        private boolean failReset;
        private TaskState taskDuringReset;

        private FakeSession(UUID sessionId, String activityKey, ActivityVenue venue) {
            super(sessionId, activityKey, venue);
        }

        private void addTask(BukkitTask task) {
            trackTask(task);
        }

        @Override
        protected void onParticipantJoined(ActivityParticipant participant) {
            if (failJoin) {
                throw new IllegalStateException("join failure");
            }
        }

        @Override
        protected void onParticipantLeft(ActivityParticipant participant, ActivityLeaveReason reason) {
            if (failLeave) {
                throw new IllegalStateException("leave failure");
            }
        }

        @Override
        protected void onActivated() {
            activatedCalls++;
            if (failActivate) {
                throw new IllegalStateException("activate failure");
            }
        }

        @Override
        protected void onReset() {
            resetCalls++;
            if (taskDuringReset != null) {
                trackTask(taskDuringReset.task());
            }
            if (failReset) {
                throw new IllegalStateException("reset failure");
            }
        }
    }

    private static final class TaskState {

        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final boolean failCancellation;

        private TaskState(boolean failCancellation) {
            this.failCancellation = failCancellation;
        }

        private BukkitTask task() {
            return proxy(BukkitTask.class, (method, arguments) -> switch (method.getName()) {
                case "cancel" -> {
                    cancelled.set(true);
                    if (failCancellation) {
                        throw new IllegalStateException("cancel failure");
                    }
                    yield null;
                }
                case "isCancelled" -> cancelled.get();
                case "getTaskId" -> 1;
                case "isSync" -> true;
                case "getOwner" -> proxy(Plugin.class, (pluginMethod, pluginArguments) ->
                        defaultValue(pluginMethod.getReturnType()));
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static final class MemoryRepository implements PlayerRepository {

        private final Map<UUID, CorePlayer> players = new HashMap<>();

        @Override
        public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
            return Optional.ofNullable(players.get(uniqueId));
        }

        @Override
        public void save(CorePlayer player) {
            players.put(player.getUniqueId(), player);
        }

        @Override
        public boolean exists(UUID uniqueId) {
            return players.containsKey(uniqueId);
        }
    }
}
