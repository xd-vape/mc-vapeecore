package dev.vapee.core.activity.blackjack.table;

import dev.vapee.core.activity.ActivityLeaveReason;
import dev.vapee.core.activity.ActivityResult;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.BlackjackActivityType;
import dev.vapee.core.activity.blackjack.BlackjackService;
import dev.vapee.core.activity.blackjack.BlackjackSession;
import dev.vapee.core.activity.blackjack.card.BlackjackShoe;
import dev.vapee.core.activity.location.ActivityPosition;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class BlackjackTableHarness {

    private static int checks;

    private BlackjackTableHarness() {
    }

    public static void main(String[] args) throws Exception {
        testDraftPersistenceRoundTrip();
        testDefinitionValidation();
        testRuntimeEnableDisableAndPersistence();
        testWorldAndInUseGuards();
        testSeatOrderAndLeaveCleanup();
        System.out.println("BlackjackTableHarness passed " + checks + " checks.");
    }

    private static void testDraftPersistenceRoundTrip() throws Exception {
        Path file = workspaceTempDirectory("vapeecore-blackjack-table-").resolve("blackjack.yml");
        BlackjackTableConfig config = new BlackjackTableConfig(file, logger());
        config.initialize();
        check(config.getDrafts().isEmpty(), "default blackjack config contains no tables");
        check(Files.readString(file).contains("tables: {}"), "default blackjack config is an empty table map");
        check(config.createDraft("casino-1"), "draft can be created");
        check(!config.createDraft("casino-1"), "duplicate draft is rejected");
        check(!config.createDraft("Invalid ID"), "invalid draft id is rejected");

        BlackjackTableDraft partial = config.getDraft("casino-1").orElseThrow();
        partial.setPos1(position("world", 0, 0, 0, 0, 0));
        config.saveDraft(partial);
        BlackjackTableConfig partialReload = new BlackjackTableConfig(file, logger());
        partialReload.initialize();
        check(partialReload.getDraft("casino-1").orElseThrow().getPos1().isPresent(),
                "partial draft survives reload");
        check(partialReload.getDraft("casino-1").orElseThrow().getPos2().isEmpty(),
                "missing draft fields remain optional");

        BlackjackTableDraft complete = validDraft("casino-1", 3);
        complete.setEnabled(true);
        partialReload.saveDraft(complete);
        BlackjackTableConfig reloaded = new BlackjackTableConfig(file, logger());
        reloaded.initialize();
        BlackjackTableDraft loaded = reloaded.getDraft("casino-1").orElseThrow();
        check(loaded.isEnabled(), "enabled flag survives roundtrip");
        check(loaded.getPos1().orElseThrow().equals(complete.getPos1().orElseThrow()), "pos1 survives roundtrip");
        check(loaded.getPos2().orElseThrow().equals(complete.getPos2().orElseThrow()), "pos2 survives roundtrip");
        check(loaded.getDealer().orElseThrow().equals(complete.getDealer().orElseThrow()),
                "dealer coordinates and rotation survive roundtrip");
        check(loaded.getInteraction().orElseThrow().equals(complete.getInteraction().orElseThrow()),
                "interaction block survives roundtrip");
        check(loaded.getSeats().equals(complete.getSeats()), "all seats and rotations survive roundtrip");
        check(reloaded.deleteDraft("casino-1"), "disabled/declarative draft can be deleted from config");
        check(!reloaded.deleteDraft("casino-1"), "missing draft delete is controlled");
    }

    private static void testDefinitionValidation() {
        expectThrows(() -> new BlackjackTableDraft("UPPER CASE"), "invalid id throws");
        BlackjackTableDraft empty = new BlackjackTableDraft("empty");
        assertError(empty, "missing area pos1");
        assertError(empty, "missing area pos2");
        assertError(empty, "missing dealer");
        assertError(empty, "missing interaction");
        assertError(empty, "missing seat");

        Map<Integer, ActivityPosition> sixSeats = new LinkedHashMap<>();
        for (int number = 1; number <= 6; number++) {
            sixSeats.put(number, position("world", number, 1, 1, 0, 0));
        }
        BlackjackTableDraft tooMany = new BlackjackTableDraft("many", false,
                position("world", 0, 0, 0, 0, 0), position("world", 10, 10, 10, 0, 0),
                position("world", 5, 5, 5, 0, 0), new BlackjackBlockPosition("world", 5, 5, 5), sixSeats);
        assertError(tooMany, "more than 5 seats");
        assertError(tooMany, "outside 1-5");

        BlackjackTableDraft worldMismatch = validDraft("world-mismatch", 1);
        worldMismatch.setDealer(position("other", 5, 5, 5, 0, 0));
        assertError(worldMismatch, "dealer uses a different world");
        BlackjackTableDraft dealerOutside = validDraft("dealer-outside", 1);
        dealerOutside.setDealer(position("world", 50, 5, 5, 0, 0));
        assertError(dealerOutside, "dealer is outside the area");
        BlackjackTableDraft interactionOutside = validDraft("interaction-outside", 1);
        interactionOutside.setInteraction(new BlackjackBlockPosition("world", 50, 5, 5));
        assertError(interactionOutside, "interaction is outside the area");
        BlackjackTableDraft floorInteraction = new BlackjackTableDraft(
                "floor-interaction",
                false,
                position("world", 0.25, 88, 0.25, 0, 0),
                position("world", 10.75, 88, 10.75, 0, 0),
                position("world", 5, 88, 5, 0, 0),
                new BlackjackBlockPosition("world", 5, 87, 5),
                Map.of(1, position("world", 4, 88, 4, 0, 0))
        );
        check(BlackjackTableDefinition.validate(floorInteraction).isEmpty(),
                "interaction floor block touching the area plane is accepted");
        floorInteraction.setInteraction(new BlackjackBlockPosition("world", 5, 86, 5));
        assertError(floorInteraction, "interaction is outside the area");
        BlackjackTableDraft seatOutside = validDraft("seat-outside", 1);
        seatOutside.setSeat(1, position("world", 50, 5, 5, 0, 0));
        assertError(seatOutside, "seat 1 is outside the area");
        BlackjackTableDefinition definition = BlackjackTableDefinition.fromDraft(validDraft("valid", 3));
        check(definition.capacity() == 3, "runtime capacity equals configured seat count");
        check(definition.seats().stream().map(BlackjackSeat::number).toList().equals(List.of(1, 2, 3)),
                "runtime seats are ordered by number");
        expectThrows(() -> definition.seats().add(definition.seats().getFirst()), "runtime seats are immutable");
    }

    private static void testRuntimeEnableDisableAndPersistence() throws Exception {
        RuntimeFixture fixture = new RuntimeFixture();
        fixture.config.createDraft("table-one");
        fixture.config.saveDraft(validDraft("table-one", 3));
        BlackjackTableOperationResult enabled = fixture.tables.enableTable("table-one");
        check(enabled.isSuccess(), "valid table enables");
        check(fixture.tables.getDefinition("table-one").orElseThrow().capacity() == 3,
                "runtime definition is registered");
        BlackjackSession session = fixture.tables.getSession("table-one").orElseThrow();
        check(session.getState() == ActivityState.AVAILABLE, "persistent table session starts available");
        check(fixture.activity.getVenue(BlackjackActivityType.KEY, "table-one").isPresent(),
                "activity venue is registered");
        check(fixture.tables.getTableAt(new BlackjackBlockPosition("world", 5, 5, 5))
                .orElseThrow().equals("table-one"), "interaction lookup is registered");
        check(fixture.config.getDraft("table-one").orElseThrow().isEnabled(), "enabled state is persisted");

        BlackjackTableOperationResult disabled = fixture.tables.disableTable("table-one");
        check(disabled.isSuccess(), "empty available table disables");
        check(fixture.tables.getSession("table-one").isEmpty(), "session is closed on disable");
        check(fixture.activity.getVenue(BlackjackActivityType.KEY, "table-one").isEmpty(),
                "venue is removed on disable");
        check(fixture.tables.getTableAt(new BlackjackBlockPosition("world", 5, 5, 5)).isEmpty(),
                "interaction is removed on disable");
        check(!fixture.config.getDraft("table-one").orElseThrow().isEnabled(), "disabled state is persisted");
        check(fixture.cleanedTables.contains("table-one"), "table seat cleanup runs on disable");
        check(fixture.config.deleteDraft("table-one"), "disabled table can be deleted");
    }

    private static void testWorldAndInUseGuards() throws Exception {
        RuntimeFixture missingWorld = new RuntimeFixture(Set.of());
        missingWorld.config.createDraft("unloaded");
        missingWorld.config.saveDraft(validDraft("unloaded", 1));
        check(missingWorld.tables.enableTable("unloaded").status()
                        == BlackjackTableOperationResult.Status.WORLD_NOT_LOADED,
                "unloaded table world is rejected");
        check(!missingWorld.config.getDraft("unloaded").orElseThrow().isEnabled(),
                "failed enable does not persist enabled state");

        RuntimeFixture fixture = new RuntimeFixture();
        fixture.config.createDraft("occupied");
        fixture.config.saveDraft(validDraft("occupied", 1));
        check(fixture.tables.enableTable("occupied").isSuccess(), "occupied fixture enables");
        Player player = fixture.player("Occupied");
        BlackjackTableDefinition definition = fixture.tables.getDefinition("occupied").orElseThrow();
        check(fixture.seats.reserveLowestFreeSeat(definition, player.getUniqueId()).isPresent(), "seat is reserved");
        BlackjackSession session = fixture.tables.getSession("occupied").orElseThrow();
        check(fixture.activity.joinSession(player, session.getSessionId()) == ActivityResult.SUCCESS,
                "player joins enabled physical table");
        check(fixture.tables.disableTable("occupied").status() == BlackjackTableOperationResult.Status.TABLE_IN_USE,
                "occupied table cannot disable");
        check(fixture.tables.isRuntimeActive("occupied"), "failed disable leaves runtime untouched");
        check(fixture.config.getDraft("occupied").orElseThrow().isEnabled(),
                "failed disable leaves persistent state untouched");
        fixture.activity.leaveCurrentSession(player.getUniqueId(), ActivityLeaveReason.DISCONNECT);
        check(fixture.seats.getSeatNumber(player.getUniqueId()).isEmpty(), "disconnect hook frees seat");
    }

    private static void testSeatOrderAndLeaveCleanup() throws Exception {
        RuntimeFixture fixture = new RuntimeFixture();
        fixture.config.createDraft("seats");
        fixture.config.saveDraft(validDraft("seats", 3));
        fixture.tables.enableTable("seats");
        BlackjackTableDefinition definition = fixture.tables.getDefinition("seats").orElseThrow();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        UUID fourth = UUID.randomUUID();
        check(fixture.seats.reserveLowestFreeSeat(definition, first).orElseThrow().seatNumber() == 1, "A gets seat 1");
        check(fixture.seats.reserveLowestFreeSeat(definition, second).orElseThrow().seatNumber() == 2, "B gets seat 2");
        fixture.seats.releaseSeat(first);
        check(fixture.seats.reserveLowestFreeSeat(definition, third).orElseThrow().seatNumber() == 1,
                "C reuses lowest free seat 1");
        check(fixture.seats.reserveLowestFreeSeat(definition, fourth).orElseThrow().seatNumber() == 3,
                "D gets final seat 3");
        check(fixture.seats.reserveLowestFreeSeat(definition, UUID.randomUUID()).isEmpty(),
                "no reservation beyond configured capacity");

        Player player = fixture.player("Dismount");
        fixture.seats.releaseSeat(third);
        check(fixture.seats.reserveLowestFreeSeat(definition, player.getUniqueId()).orElseThrow().seatNumber() == 1,
                "dismount player reserves seat 1");
        BlackjackSession session = fixture.tables.getSession("seats").orElseThrow();
        check(fixture.activity.joinSession(player, session.getSessionId()) == ActivityResult.SUCCESS,
                "dismount player joins session");
        fixture.seats.handleManagedDismount(player);
        check(fixture.activity.getSessionForPlayer(player.getUniqueId()).isEmpty(), "manual dismount leaves activity");
        check(fixture.seats.getSeatNumber(player.getUniqueId()).isEmpty(), "manual dismount frees seat");
        check(fixture.seats.cleanupStaleSeats() == 0, "headless stale-seat scan does not touch foreign entities");
    }

    private static BlackjackTableDraft validDraft(String id, int seats) {
        BlackjackTableDraft draft = new BlackjackTableDraft(id);
        draft.setPos1(position("world", 0, 0, 0, 0, 0));
        draft.setPos2(position("world", 10, 10, 10, 0, 0));
        draft.setDealer(position("world", 5.5, 5, 2.5, 180, 10));
        draft.setInteraction(new BlackjackBlockPosition("world", 5, 5, 5));
        for (int number = 1; number <= seats; number++) {
            draft.setSeat(number, position("world", number, 5, 7, number * 10, number));
        }
        return draft;
    }

    private static ActivityPosition position(String world, double x, double y, double z, float yaw, float pitch) {
        return new ActivityPosition(world, x, y, z, yaw, pitch);
    }

    private static void assertError(BlackjackTableDraft draft, String fragment) {
        check(BlackjackTableDefinition.validate(draft).stream().anyMatch(error -> error.contains(fragment)),
                "validation reports " + fragment);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
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

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static Path workspaceTempDirectory(String prefix) throws Exception {
        Path root = Path.of("target", "harness-temp").toAbsolutePath().normalize();
        Files.createDirectories(root);
        return Files.createTempDirectory(root, prefix);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (instance, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Proxy";
                    case "hashCode" -> System.identityHashCode(instance);
                    case "equals" -> instance == args[0];
                    default -> null;
                };
            }
            return handler.invoke(method, args);
        });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive()) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        if (type == int.class) return 0;
        if (type == long.class) return 0L;
        if (type == float.class) return 0F;
        if (type == double.class) return 0D;
        return null;
    }

    private static ActivityService activityService(PlayerService players, Logger logger, Set<String> worlds) {
        try {
            Constructor<ActivityService> constructor = ActivityService.class.getDeclaredConstructor(
                    PlayerService.class, Logger.class, BooleanSupplier.class, Predicate.class);
            constructor.setAccessible(true);
            return constructor.newInstance(players, logger, (BooleanSupplier) () -> true,
                    (Predicate<String>) worlds::contains);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static BlackjackService blackjackService(ActivityService activity,
                                                      BlackjackSeatService seats,
                                                      Logger logger) {
        try {
            Class<?> tableAccess = Class.forName(BlackjackService.class.getName() + "$TableAccess");
            Class<?> seatAccess = Class.forName(BlackjackService.class.getName() + "$SeatAccess");
            Class<?> taskScheduler = Class.forName(BlackjackService.class.getName() + "$TaskScheduler");
            Object tableProxy = Proxy.newProxyInstance(tableAccess.getClassLoader(), new Class<?>[]{tableAccess},
                    (proxy, method, args) -> Optional.empty());
            Object seatProxy = Proxy.newProxyInstance(seatAccess.getClassLoader(), new Class<?>[]{seatAccess},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "reserveLowestFreeSeat" -> seats.reserveLowestFreeSeat(
                                (BlackjackTableDefinition) args[0], (UUID) args[1]).isPresent();
                        case "mountReservedPlayer" -> null;
                        case "releaseSeat" -> {
                            seats.releaseSeat((UUID) args[0]);
                            yield null;
                        }
                        case "getSeatNumber" -> seats.getSeatNumber((UUID) args[0]);
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
            Object schedulerProxy = Proxy.newProxyInstance(taskScheduler.getClassLoader(),
                    new Class<?>[]{taskScheduler}, (proxy, method, args) -> fakeTask());
            Constructor<BlackjackService> constructor = BlackjackService.class.getDeclaredConstructor(
                    ActivityService.class, tableAccess, seatAccess, Function.class, BiConsumer.class,
                    taskScheduler, Supplier.class, Logger.class);
            constructor.setAccessible(true);
            return constructor.newInstance(activity, tableProxy, seatProxy,
                    (Function<UUID, Player>) ignored -> null,
                    (BiConsumer<Player, String>) (player, message) -> { },
                    schedulerProxy,
                    (Supplier<BlackjackShoe>) () -> new BlackjackShoe(6, new java.util.Random(1L)),
                    logger);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create blackjack table harness service", exception);
        }
    }

    private static BukkitTask fakeTask() {
        return proxy(BukkitTask.class, (method, args) -> switch (method.getName()) {
            case "getTaskId" -> 1;
            case "getOwner" -> null;
            case "isSync" -> true;
            case "isCancelled" -> false;
            default -> null;
        });
    }

    @FunctionalInterface
    private interface Handler {
        Object invoke(java.lang.reflect.Method method, Object[] args) throws Throwable;
    }

    private static final class RuntimeFixture {
        private final Logger logger = logger();
        private final PlayerService players = new PlayerService(new MemoryRepository(), logger);
        private final World world = proxy(World.class, (method, args) -> method.getName().equals("getName")
                ? "world" : defaultValue(method.getReturnType()));
        private final ActivityService activity;
        private final BlackjackTableConfig config;
        private final BlackjackSeatService seats;
        private final BlackjackTableService tables;
        private final List<String> cleanedTables = new java.util.ArrayList<>();

        private RuntimeFixture() throws Exception {
            this(Set.of("world"));
        }

        private RuntimeFixture(Set<String> loadedWorlds) throws Exception {
            activity = activityService(players, logger, loadedWorlds);
            seats = new BlackjackSeatService(activity, logger, Runnable::run);
            BlackjackService blackjack = blackjackService(activity, seats, logger);
            check(activity.registerActivityType(new BlackjackActivityType(blackjack)) == ActivityResult.SUCCESS,
                    "blackjack activity type registers");
            Path file = Files.createTempDirectory("vapeecore-blackjack-runtime-").resolve("blackjack.yml");
            config = new BlackjackTableConfig(file, logger);
            config.initialize();
            tables = new BlackjackTableService(activity, config, tableId -> {
                cleanedTables.add(tableId);
                seats.cleanupTable(tableId);
            }, loadedWorlds::contains, logger);
        }

        private Player player(String name) {
            UUID id = UUID.randomUUID();
            players.loadPlayer(id, name);
            return proxy(Player.class, (method, args) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> true;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 5, 5, 5);
                default -> defaultValue(method.getReturnType());
            });
        }
    }

    private static final class MemoryRepository implements PlayerRepository {
        private final Map<UUID, CorePlayer> values = new HashMap<>();
        public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(values.get(id)); }
        public void save(CorePlayer player) { values.put(player.getUniqueId(), player); }
        public boolean exists(UUID id) { return values.containsKey(id); }
    }
}
