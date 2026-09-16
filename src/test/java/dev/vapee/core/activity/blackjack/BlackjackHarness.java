package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.ActivityLeaveReason;
import dev.vapee.core.activity.ActivityResult;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.card.BlackjackCard;
import dev.vapee.core.activity.blackjack.card.BlackjackHand;
import dev.vapee.core.activity.blackjack.card.BlackjackRank;
import dev.vapee.core.activity.blackjack.card.BlackjackShoe;
import dev.vapee.core.activity.blackjack.card.BlackjackSuit;
import dev.vapee.core.activity.blackjack.ui.BlackjackModeInventoryHolder;
import dev.vapee.core.activity.blackjack.ui.BlackjackTableInventoryHolder;
import dev.vapee.core.activity.navigation.ActivityCatalog;
import dev.vapee.core.activity.navigation.ActivityEntryPoint;
import dev.vapee.core.lobby.experience.navigator.activity.ActivitiesInventoryHolder;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.random.RandomGenerator;

public final class BlackjackHarness {

    private static int checks;

    private BlackjackHarness() {
    }

    public static void main(String[] args) {
        testHandValuesAndNaturals();
        testOutcomesAndDealerRule();
        testSixDeckShoe();
        testActivityCatalogAndHolderIdentity();
        testSoloRoundAndReset();
        testSoloPrivacyAndPublicNoWait();
        testPublicMultiplayerDealOrderAndActions();
        testHitTwentyOneAndBustAdvance();
        testPublicCapacityAndActiveTableSelection();
        testTimeoutStaleSafetyAndDisconnect();
        testAllLeaveAndEmptyReuse();
        testForeignWorldCleanup();
        testMissingSpawnThenLateAvailability();
        System.out.println("BlackjackHarness passed " + checks + " checks.");
    }

    private static void testHandValuesAndNaturals() {
        check(hand(BlackjackRank.TWO, BlackjackRank.THREE).getValue() == 5, "2 + 3 = 5");
        check(hand(BlackjackRank.TEN, BlackjackRank.KING).getValue() == 20, "10 + K = 20");
        check(hand(BlackjackRank.ACE, BlackjackRank.NINE).getValue() == 20, "A + 9 = 20");
        check(hand(BlackjackRank.ACE, BlackjackRank.NINE, BlackjackRank.FIVE).getValue() == 15,
                "A + 9 + 5 = 15");
        check(hand(BlackjackRank.ACE, BlackjackRank.ACE, BlackjackRank.NINE).getValue() == 21,
                "A + A + 9 = 21");
        check(hand(BlackjackRank.ACE, BlackjackRank.KING).isBlackjack(), "A + K is natural blackjack");
        BlackjackHand nonNatural = hand(BlackjackRank.ACE, BlackjackRank.FIVE, BlackjackRank.FIVE);
        check(nonNatural.getValue() == 21 && !nonNatural.isBlackjack(), "three-card 21 is not natural");
    }

    private static void testOutcomesAndDealerRule() {
        checkOutcome(hand(BlackjackRank.TEN, BlackjackRank.KING), hand(BlackjackRank.TEN, BlackjackRank.EIGHT),
                BlackjackOutcome.WIN);
        checkOutcome(hand(BlackjackRank.TEN, BlackjackRank.EIGHT), hand(BlackjackRank.TEN, BlackjackRank.KING),
                BlackjackOutcome.LOSS);
        checkOutcome(hand(BlackjackRank.TEN, BlackjackRank.KING), hand(BlackjackRank.QUEEN, BlackjackRank.KING),
                BlackjackOutcome.PUSH);
        checkOutcome(hand(BlackjackRank.KING, BlackjackRank.QUEEN, BlackjackRank.TWO),
                hand(BlackjackRank.TEN, BlackjackRank.EIGHT), BlackjackOutcome.BUST);
        checkOutcome(hand(BlackjackRank.TEN, BlackjackRank.EIGHT),
                hand(BlackjackRank.KING, BlackjackRank.QUEEN, BlackjackRank.TWO), BlackjackOutcome.WIN);
        checkOutcome(hand(BlackjackRank.ACE, BlackjackRank.KING),
                hand(BlackjackRank.SEVEN, BlackjackRank.SEVEN, BlackjackRank.SEVEN),
                BlackjackOutcome.BLACKJACK);
        checkOutcome(hand(BlackjackRank.ACE, BlackjackRank.KING),
                hand(BlackjackRank.ACE, BlackjackRank.QUEEN), BlackjackOutcome.PUSH);

        check(BlackjackService.shouldDealerHit(hand(BlackjackRank.TEN, BlackjackRank.SIX)), "dealer hits 16");
        check(!BlackjackService.shouldDealerHit(hand(BlackjackRank.TEN, BlackjackRank.SEVEN)), "dealer stands 17");
        BlackjackHand softSeventeen = hand(BlackjackRank.ACE, BlackjackRank.SIX);
        check(softSeventeen.isSoft() && !BlackjackService.shouldDealerHit(softSeventeen),
                "dealer stands soft 17");
        check(!BlackjackService.shouldDealerHit(hand(BlackjackRank.TEN, BlackjackRank.EIGHT)),
                "dealer stands 18+");
    }

    private static void testSixDeckShoe() {
        BlackjackShoe shoe = new BlackjackShoe(6, RandomGenerator.of("L64X128MixRandom"));
        check(shoe.remaining() == 312, "six-deck shoe has 312 cards");
        Map<BlackjackSuit, Map<BlackjackRank, Integer>> counts = new EnumMap<>(BlackjackSuit.class);
        for (BlackjackCard card : shoe.getRemainingCards()) {
            counts.computeIfAbsent(card.suit(), ignored -> new EnumMap<>(BlackjackRank.class))
                    .merge(card.rank(), 1, Integer::sum);
        }
        for (BlackjackSuit suit : BlackjackSuit.values()) {
            for (BlackjackRank rank : BlackjackRank.values()) {
                check(counts.get(suit).get(rank) == 6, "six copies of " + rank + " " + suit);
            }
        }
        shoe.draw();
        check(shoe.remaining() == 311, "draw reduces shoe count");

        BlackjackShoe deterministicOne = new BlackjackShoe(6, new java.util.Random(123456L));
        BlackjackShoe deterministicTwo = new BlackjackShoe(6, new java.util.Random(123456L));
        check(deterministicOne.getRemainingCards().equals(deterministicTwo.getRemainingCards()),
                "seeded random source produces deterministic shoe");
    }

    private static void testActivityCatalogAndHolderIdentity() {
        Fixture fixture = new Fixture();
        ActivityCatalog catalog = new ActivityCatalog(fixture.activityService);
        ActivityEntryPoint entryPoint = new ActivityEntryPoint() {
            @Override
            public String getActivityKey() {
                return BlackjackActivityType.KEY;
            }

            @Override
            public Component getDisplayName() {
                return Component.text("Blackjack");
            }

            @Override
            public Material getIcon() {
                return Material.PAPER;
            }

            @Override
            public List<Component> getDescription() {
                return List.of();
            }

            @Override
            public void open(Player player) {
            }
        };
        catalog.register(entryPoint);
        check(catalog.get(BlackjackActivityType.KEY).orElseThrow() == entryPoint, "catalog retrieves entry");
        check(catalog.getEntries().size() == 1, "catalog exposes registered entries");
        expectThrows(() -> catalog.register(entryPoint), "catalog rejects duplicate activity key");
        expectThrows(() -> catalog.getEntries().add(entryPoint), "catalog read API is immutable");
        check(catalog.unregister(BlackjackActivityType.KEY).isPresent(), "catalog unregisters entry");

        check(InventoryHolder.class.isAssignableFrom(BlackjackModeInventoryHolder.class),
                "mode menu has custom holder");
        check(InventoryHolder.class.isAssignableFrom(BlackjackTableInventoryHolder.class),
                "table menu has custom holder");
        check(InventoryHolder.class.isAssignableFrom(ActivitiesInventoryHolder.class),
                "activities menu has custom holder");
    }

    private static void testSoloRoundAndReset() {
        Fixture fixture = new Fixture();
        Player solo = fixture.player("Solo");
        BlackjackSession session = fixture.service.launchSolo(solo).orElseThrow();
        check(session.getMode() == BlackjackMode.SOLO, "solo session mode");
        check(session.getParticipantCount() == 1, "solo session has one participant");
        check(session.getState() == ActivityState.AVAILABLE, "solo starts available");

        fixture.queueShoe(cards(
                BlackjackRank.TEN, BlackjackRank.SIX,
                BlackjackRank.SEVEN, BlackjackRank.TEN,
                BlackjackRank.TWO
        ));
        checkResult(fixture.service.startRound(solo), ActivityResult.SUCCESS);
        check(session.getState() == ActivityState.ACTIVE, "solo deal activates with one player");
        check(session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS, "solo enters player turns");
        check(fixture.scheduler.tasks.getFirst().delayTicks == BlackjackService.TURN_TIMEOUT_TICKS,
                "player turn timeout is 20 seconds");
        checkResult(fixture.service.stand(solo), ActivityResult.SUCCESS);
        check(session.getRoundPhase() == BlackjackRoundPhase.SETTLED, "solo settles after stand and dealer turn");
        check(session.getDealerHand().getValue() == 18, "dealer hits 16 to 18");
        check(fixture.scheduler.tasks.stream()
                        .anyMatch(task -> !task.cancelled
                                && task.delayTicks == BlackjackService.RESULT_VIEW_TICKS),
                "settlement remains visible for three seconds");
        fixture.scheduler.runNextActive();
        check(session.getState() == ActivityState.AVAILABLE, "settlement resets to available");
        check(session.getRoundPhase() == BlackjackRoundPhase.IDLE, "reset clears round phase");
        check(session.getParticipantCount() == 1, "reset keeps solo participant");
        check(session.getMode() == BlackjackMode.SOLO, "reset keeps solo claim");
    }

    private static void testSoloPrivacyAndPublicNoWait() {
        Fixture fixture = new Fixture();
        Player solo = fixture.player("Solo");
        Player publicPlayer = fixture.player("Public");
        BlackjackSession soloSession = fixture.service.launchSolo(solo).orElseThrow();
        BlackjackSession publicSession = fixture.service.launchPublic(publicPlayer).orElseThrow();
        check(!soloSession.getSessionId().equals(publicSession.getSessionId()),
                "public matchmaking never selects solo session");
        check(publicSession.getMode() == BlackjackMode.PUBLIC, "public session mode");

        fixture.queueShoe(cards(
                BlackjackRank.TEN, BlackjackRank.SIX,
                BlackjackRank.SEVEN, BlackjackRank.TEN,
                BlackjackRank.TWO
        ));
        checkResult(fixture.service.startRound(publicPlayer), ActivityResult.SUCCESS);
        check(publicSession.getState() == ActivityState.ACTIVE, "public table starts with one player");
    }

    private static void testPublicMultiplayerDealOrderAndActions() {
        Fixture fixture = new Fixture();
        Player first = fixture.player("First");
        Player second = fixture.player("Second");
        BlackjackSession session = fixture.service.launchPublic(first).orElseThrow();
        BlackjackSession joined = fixture.service.launchPublic(second).orElseThrow();
        check(session.getSessionId().equals(joined.getSessionId()), "available public players share a table");

        fixture.queueShoe(cards(
                BlackjackRank.TWO, BlackjackRank.THREE, BlackjackRank.SIX,
                BlackjackRank.FOUR, BlackjackRank.FIVE, BlackjackRank.TEN,
                BlackjackRank.TWO, BlackjackRank.TWO
        ));
        checkResult(fixture.service.startRound(first), ActivityResult.SUCCESS);
        UUID firstId = first.getUniqueId();
        UUID secondId = second.getUniqueId();
        check(session.getPlayerRound(firstId).orElseThrow().getHand().getCards().get(0).rank() == BlackjackRank.TWO,
                "first joined player receives first card");
        check(session.getPlayerRound(secondId).orElseThrow().getHand().getCards().get(0).rank() == BlackjackRank.THREE,
                "second joined player receives second card");
        check(session.getDealerHand().getCards().get(0).rank() == BlackjackRank.SIX,
                "dealer receives card after first pass");
        check(session.getCurrentTurnPlayer().orElseThrow().equals(firstId), "turn order starts with first joiner");
        checkResult(fixture.service.stand(first), ActivityResult.SUCCESS);
        check(session.getCurrentTurnPlayer().orElseThrow().equals(secondId), "turn advances to second joiner");
        checkResult(fixture.service.hit(second), ActivityResult.SUCCESS);
        check(session.getPlayerRound(secondId).orElseThrow().getHand().size() == 3, "hit adds one card");
        check(session.getCurrentTurnPlayer().orElseThrow().equals(secondId), "hit below 21 keeps turn");
        checkResult(fixture.service.stand(second), ActivityResult.SUCCESS);
        check(session.getRoundPhase() == BlackjackRoundPhase.SETTLED, "stand advances immediately to dealer");
    }

    private static void testHitTwentyOneAndBustAdvance() {
        Fixture fixture = new Fixture();
        Player first = fixture.player("TwentyOne");
        Player second = fixture.player("Bust");
        BlackjackSession session = fixture.service.launchPublic(first).orElseThrow();
        fixture.service.launchPublic(second).orElseThrow();
        fixture.queueShoe(cards(
                BlackjackRank.TEN, BlackjackRank.FIVE, BlackjackRank.TEN,
                BlackjackRank.NINE, BlackjackRank.FIVE, BlackjackRank.SEVEN,
                BlackjackRank.TWO, BlackjackRank.KING, BlackjackRank.FIVE
        ));
        checkResult(fixture.service.startRound(first), ActivityResult.SUCCESS);
        checkResult(fixture.service.hit(first), ActivityResult.SUCCESS);
        check(session.getPlayerRound(first.getUniqueId()).orElseThrow().getHand().getValue() == 21,
                "hit can reach 21");
        check(session.getCurrentTurnPlayer().orElseThrow().equals(second.getUniqueId()),
                "21 automatically advances turn");
        checkResult(fixture.service.hit(second), ActivityResult.SUCCESS);
        check(session.getCurrentTurnPlayer().orElseThrow().equals(second.getUniqueId()),
                "hit below 21 keeps second player's turn");
        checkResult(fixture.service.hit(second), ActivityResult.SUCCESS);
        check(session.getPlayerRound(second.getUniqueId()).orElseThrow().getHand().isBust(),
                "hit can bust player");
        check(session.getRoundPhase() == BlackjackRoundPhase.SETTLED,
                "bust automatically advances through dealer settlement");
    }

    private static void testPublicCapacityAndActiveTableSelection() {
        Fixture capacityFixture = new Fixture();
        List<BlackjackSession> sessions = new ArrayList<>();
        for (int index = 1; index <= 6; index++) {
            sessions.add(capacityFixture.service.launchPublic(capacityFixture.player("P" + index)).orElseThrow());
        }
        UUID firstTable = sessions.getFirst().getSessionId();
        check(sessions.subList(0, 5).stream().allMatch(session -> session.getSessionId().equals(firstTable)),
                "first five public players fill one table");
        check(!sessions.get(5).getSessionId().equals(firstTable), "sixth public player receives another table");

        Fixture activeFixture = new Fixture();
        Player first = activeFixture.player("Active");
        BlackjackSession active = activeFixture.service.launchPublic(first).orElseThrow();
        activeFixture.queueShoe(cards(
                BlackjackRank.FIVE, BlackjackRank.SIX,
                BlackjackRank.SIX, BlackjackRank.TEN,
                BlackjackRank.TWO
        ));
        checkResult(activeFixture.service.startRound(first), ActivityResult.SUCCESS);
        Player later = activeFixture.player("Later");
        BlackjackSession other = activeFixture.service.launchPublic(later).orElseThrow();
        check(!other.getSessionId().equals(active.getSessionId()), "active public table rejects later joiner");
        check(other.getState() == ActivityState.AVAILABLE, "later joiner gets immediately available table");
    }

    private static void testTimeoutStaleSafetyAndDisconnect() {
        Fixture staleFixture = new Fixture();
        Player player = staleFixture.player("Stale");
        BlackjackSession session = staleFixture.service.launchSolo(player).orElseThrow();
        staleFixture.queueShoe(cards(
                BlackjackRank.FIVE, BlackjackRank.TEN,
                BlackjackRank.SIX, BlackjackRank.SEVEN,
                BlackjackRank.TWO
        ));
        checkResult(staleFixture.service.startRound(player), ActivityResult.SUCCESS);
        FakeTask originalTimeout = staleFixture.scheduler.tasks.getFirst();
        checkResult(staleFixture.service.hit(player), ActivityResult.SUCCESS);
        originalTimeout.runRegardlessOfCancellation();
        check(session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS
                        && session.getCurrentTurnPlayer().orElseThrow().equals(player.getUniqueId()),
                "stale canceled timeout cannot end current turn");
        staleFixture.scheduler.runNextActive();
        check(session.getRoundPhase() == BlackjackRoundPhase.SETTLED, "current timeout automatically stands");

        Fixture disconnectFixture = new Fixture();
        Player first = disconnectFixture.player("First");
        Player second = disconnectFixture.player("Second");
        BlackjackSession publicSession = disconnectFixture.service.launchPublic(first).orElseThrow();
        disconnectFixture.service.launchPublic(second).orElseThrow();
        disconnectFixture.queueShoe(cards(
                BlackjackRank.TEN, BlackjackRank.NINE, BlackjackRank.SIX,
                BlackjackRank.SEVEN, BlackjackRank.EIGHT, BlackjackRank.TEN,
                BlackjackRank.TWO
        ));
        checkResult(disconnectFixture.service.startRound(first), ActivityResult.SUCCESS);
        disconnectFixture.scheduler.runNextActive();
        check(publicSession.getCurrentTurnPlayer().orElseThrow().equals(second.getUniqueId()),
                "timeout advances to next player");
        checkResult(disconnectFixture.activityService.leaveCurrentSession(
                second.getUniqueId(),
                ActivityLeaveReason.DISCONNECT
        ), ActivityResult.SUCCESS);
        check(publicSession.getRoundPhase() == BlackjackRoundPhase.SETTLED,
                "current player disconnect advances immediately without timeout delay");
    }

    private static void testAllLeaveAndEmptyReuse() {
        Fixture activeFixture = new Fixture();
        Player player = activeFixture.player("Leaving");
        BlackjackSession activeSession = activeFixture.service.launchPublic(player).orElseThrow();
        activeFixture.queueShoe(cards(
                BlackjackRank.TEN, BlackjackRank.SIX,
                BlackjackRank.SEVEN, BlackjackRank.TEN,
                BlackjackRank.TWO
        ));
        checkResult(activeFixture.service.startRound(player), ActivityResult.SUCCESS);
        checkResult(activeFixture.activityService.leaveCurrentSession(
                player.getUniqueId(),
                ActivityLeaveReason.DISCONNECT
        ), ActivityResult.SUCCESS);
        check(activeSession.getState() == ActivityState.AVAILABLE, "all leave resets active session");
        check(activeSession.getRoundPhase() == BlackjackRoundPhase.IDLE, "all leave clears active round");
        check(activeSession.getMode() == BlackjackMode.UNCLAIMED, "empty reset releases table");

        Fixture reuseFixture = new Fixture();
        Player solo = reuseFixture.player("Solo");
        BlackjackSession empty = reuseFixture.service.launchSolo(solo).orElseThrow();
        UUID reusableId = empty.getSessionId();
        checkResult(reuseFixture.service.leave(solo), ActivityResult.SUCCESS);
        check(empty.getMode() == BlackjackMode.UNCLAIMED, "available empty solo table is unclaimed");
        BlackjackSession reused = reuseFixture.service.launchPublic(reuseFixture.player("Public")).orElseThrow();
        check(reused.getSessionId().equals(reusableId), "empty table is reused for another mode");
    }

    private static void testForeignWorldCleanup() {
        Fixture fixture = new Fixture();
        Player first = fixture.player("First");
        BlackjackSession oldSession = fixture.service.launchSolo(first).orElseThrow();
        String oldVenueId = oldSession.getVenue().id();
        checkResult(fixture.service.leave(first), ActivityResult.SUCCESS);

        World secondWorld = world("world_two");
        fixture.loadedWorlds.add("world_two");
        fixture.spawn.set(new Location(secondWorld, 20, 70, 20));
        BlackjackSession newSession = fixture.service.launchPublic(fixture.player("Second")).orElseThrow();
        check(fixture.activityService.getSession(oldSession.getSessionId()).isEmpty(),
                "foreign-world idle session is closed on next launch");
        check(fixture.activityService.getVenue(BlackjackActivityType.KEY, oldVenueId).isEmpty(),
                "foreign-world idle venue is unregistered");
        check(newSession.getVenue().area().worldName().equals("world_two"),
                "new table uses current lobby world");
    }

    private static void testMissingSpawnThenLateAvailability() {
        Fixture fixture = new Fixture();
        Player player = fixture.player("LateSpawn");
        fixture.spawn.set(null);
        check(fixture.service.launchSolo(player).isEmpty(), "missing lobby spawn blocks launch cleanly");
        check(fixture.activityService.getSessionCount() == 0, "missing spawn creates no table");
        check(fixture.messages.stream().anyMatch(message -> message.contains("currently unavailable")),
                "missing spawn sends controlled unavailable message");
        fixture.spawn.set(new Location(fixture.world, 5, 65, 5));
        check(fixture.service.launchSolo(player).isPresent(), "later spawn enables next launch without restart");
    }

    private static BlackjackHand hand(BlackjackRank... ranks) {
        BlackjackHand hand = new BlackjackHand();
        for (BlackjackRank rank : ranks) {
            hand.add(card(rank));
        }
        return hand;
    }

    private static List<BlackjackCard> cards(BlackjackRank... ranks) {
        return java.util.Arrays.stream(ranks).map(BlackjackHarness::card).toList();
    }

    private static BlackjackCard card(BlackjackRank rank) {
        return new BlackjackCard(rank, BlackjackSuit.SPADES);
    }

    private static void checkOutcome(
            BlackjackHand player,
            BlackjackHand dealer,
            BlackjackOutcome expected
    ) {
        check(BlackjackOutcome.determine(player, dealer) == expected, "expected outcome " + expected);
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

    private static World world(String name) {
        return proxy(World.class, (method, arguments) -> method.getName().equals("getName")
                ? name
                : defaultValue(method.getReturnType()));
    }

    @FunctionalInterface
    private interface ProxyHandler {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }

    private static final class Fixture {

        private final Logger logger = logger();
        private final PlayerService playerService = new PlayerService(new MemoryRepository(), logger);
        private final Set<String> loadedWorlds = new HashSet<>(Set.of("world"));
        private final World world = world("world");
        private final AtomicReference<Location> spawn = new AtomicReference<>(new Location(world, 0, 64, 0));
        private final Map<UUID, Player> onlinePlayers = new HashMap<>();
        private final Deque<BlackjackShoe> shoes = new ArrayDeque<>();
        private final FakeScheduler scheduler = new FakeScheduler();
        private final List<String> messages = new ArrayList<>();
        private final ActivityService activityService = createActivityService(
                playerService,
                logger,
                loadedWorlds::contains
        );
        private final BlackjackService service = new BlackjackService(
                activityService,
                () -> Optional.ofNullable(spawn.get()),
                onlinePlayers::get,
                (player, message) -> messages.add(player.getUniqueId() + ":" + message),
                scheduler::schedule,
                () -> shoes.isEmpty()
                        ? new BlackjackShoe(6, new java.util.Random(123L))
                        : shoes.removeFirst(),
                logger
        );

        private Fixture() {
            checkResult(
                    activityService.registerActivityType(new BlackjackActivityType(service)),
                    ActivityResult.SUCCESS
            );
        }

        private Player player(String name) {
            UUID uniqueId = UUID.randomUUID();
            playerService.loadPlayer(uniqueId, name);
            Player player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> uniqueId;
                case "getName" -> name;
                case "isOnline" -> true;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 0, 64, 0);
                default -> defaultValue(method.getReturnType());
            });
            onlinePlayers.put(uniqueId, player);
            return player;
        }

        private void queueShoe(List<BlackjackCard> drawOrder) {
            shoes.addLast(new BlackjackShoe(drawOrder));
        }
    }

    private static ActivityService createActivityService(
            PlayerService playerService,
            Logger logger,
            Predicate<String> loadedWorldCheck
    ) {
        try {
            Constructor<ActivityService> constructor = ActivityService.class.getDeclaredConstructor(
                    PlayerService.class,
                    Logger.class,
                    BooleanSupplier.class,
                    Predicate.class
            );
            constructor.setAccessible(true);
            return constructor.newInstance(playerService, logger, (BooleanSupplier) () -> true, loadedWorldCheck);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create activity harness service", exception);
        }
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static final class FakeScheduler {

        private final List<FakeTask> tasks = new ArrayList<>();

        private BukkitTask schedule(Runnable action, long delayTicks) {
            FakeTask task = new FakeTask(tasks.size() + 1, action, delayTicks);
            tasks.add(task);
            return task;
        }

        private void runNextActive() {
            FakeTask task = tasks.stream()
                    .filter(candidate -> !candidate.isCancelled() && !candidate.executed)
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("No active scheduled task"));
            task.runRegardlessOfCancellation();
        }
    }

    private static final class FakeTask implements BukkitTask {

        private final int id;
        private final Runnable action;
        @SuppressWarnings("unused")
        private final long delayTicks;
        private boolean cancelled;
        private boolean executed;

        private FakeTask(int id, Runnable action, long delayTicks) {
            this.id = id;
            this.action = action;
            this.delayTicks = delayTicks;
        }

        private void runRegardlessOfCancellation() {
            if (executed) {
                throw new AssertionError("Task already executed");
            }
            executed = true;
            action.run();
        }

        @Override
        public int getTaskId() {
            return id;
        }

        @Override
        public Plugin getOwner() {
            return null;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
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
