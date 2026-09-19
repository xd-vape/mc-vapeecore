package dev.vapee.core.activity.blackjack;

import dev.vapee.core.activity.ActivityLeaveReason;
import dev.vapee.core.activity.ActivityResult;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.activity.ActivitySessionCreationResult;
import dev.vapee.core.activity.ActivityState;
import dev.vapee.core.activity.blackjack.card.BlackjackCard;
import dev.vapee.core.activity.blackjack.card.BlackjackHand;
import dev.vapee.core.activity.blackjack.card.BlackjackRank;
import dev.vapee.core.activity.blackjack.card.BlackjackShoe;
import dev.vapee.core.activity.blackjack.card.BlackjackSuit;
import dev.vapee.core.activity.blackjack.table.BlackjackBlockPosition;
import dev.vapee.core.activity.blackjack.table.BlackjackSeat;
import dev.vapee.core.activity.blackjack.table.BlackjackTableDefinition;
import dev.vapee.core.activity.blackjack.presentation.BlackjackAction;
import dev.vapee.core.activity.location.ActivityArea;
import dev.vapee.core.activity.location.ActivityPosition;
import dev.vapee.core.activity.location.ActivityVenue;
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
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

/* Previous Phase-15 harness retained below as historical source, but excluded from compilation.

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
*/

public final class BlackjackHarness {

    private static int checks;

    private BlackjackHarness() {
    }

    public static void main(String[] args) {
        testHandValuesAndNaturals();
        testOutcomesAndDealerRule();
        testSixDeckShoe();
        testBuildModeJoinGuard();
        testPhysicalTableJoinCapacityAndReopen();
        testJoinRollback();
        testSinglePlayerRoundResetAndRematch();
        testMultiplayerDealOrderAndActions();
        testDoubleDown();
        testHitTimeoutAndStaleTaskSafety();
        testMidRoundJoinRejected();
        testLeaveDisconnectAndWorldChangeCleanup();
        check(java.util.Arrays.equals(BlackjackAction.values(), new BlackjackAction[]{
                BlackjackAction.DEAL, BlackjackAction.HIT, BlackjackAction.STAND,
                BlackjackAction.DOUBLE, BlackjackAction.LEAVE
        }), "blackjack hotbar exposes exactly the five world actions");
        System.out.println("BlackjackHarness passed " + checks + " checks.");
    }

    private static void testBuildModeJoinGuard() {
        Fixture fixture = new Fixture(3);
        Player player = fixture.player("Builder");
        fixture.buildPlayers.add(player.getUniqueId());
        check(fixture.service.joinTable(player, Fixture.TABLE_ID).isEmpty(),
                "BUILD player cannot join a blackjack table");
        check(fixture.activityService.getSessionForPlayer(player.getUniqueId()).isEmpty(),
                "rejected BUILD player does not enter the activity registry");
        check(!fixture.seats.assignments.containsKey(player.getUniqueId()),
                "rejected BUILD player does not reserve a physical seat");
        check(fixture.messages.stream().anyMatch(message -> message.contains("build mode")),
                "BUILD conflict returns a controlled blackjack message");
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
                hand(BlackjackRank.SEVEN, BlackjackRank.SEVEN, BlackjackRank.SEVEN), BlackjackOutcome.BLACKJACK);
        checkOutcome(hand(BlackjackRank.ACE, BlackjackRank.KING),
                hand(BlackjackRank.ACE, BlackjackRank.QUEEN), BlackjackOutcome.PUSH);
        check(BlackjackService.shouldDealerHit(hand(BlackjackRank.TEN, BlackjackRank.SIX)), "dealer hits 16");
        check(!BlackjackService.shouldDealerHit(hand(BlackjackRank.TEN, BlackjackRank.SEVEN)), "dealer stands 17");
        BlackjackHand softSeventeen = hand(BlackjackRank.ACE, BlackjackRank.SIX);
        check(softSeventeen.isSoft() && !BlackjackService.shouldDealerHit(softSeventeen),
                "dealer stands soft 17");
    }

    private static void testSixDeckShoe() {
        BlackjackShoe shoe = new BlackjackShoe(6, new java.util.Random(987654L));
        check(shoe.remaining() == 312, "six-deck shoe has 312 cards");
        Map<BlackjackSuit, Map<BlackjackRank, Integer>> counts = new EnumMap<>(BlackjackSuit.class);
        for (BlackjackCard card : shoe.getRemainingCards()) {
            counts.computeIfAbsent(card.suit(), ignored -> new EnumMap<>(BlackjackRank.class))
                    .merge(card.rank(), 1, Integer::sum);
        }
        for (BlackjackSuit suit : BlackjackSuit.values()) {
            for (BlackjackRank rank : BlackjackRank.values()) {
                check(counts.get(suit).get(rank) == 6, "six copies of every rank and suit");
            }
        }
        shoe.draw();
        check(shoe.remaining() == 311, "draw reduces shoe count");
        BlackjackShoe first = new BlackjackShoe(6, new java.util.Random(123456L));
        BlackjackShoe second = new BlackjackShoe(6, new java.util.Random(123456L));
        check(first.getRemainingCards().equals(second.getRemainingCards()), "seeded shoes are deterministic");
    }

    private static void testPhysicalTableJoinCapacityAndReopen() {
        Fixture fixture = new Fixture(3);
        Player first = fixture.player("First");
        Player second = fixture.player("Second");
        Player third = fixture.player("Third");
        Player fourth = fixture.player("Fourth");
        check(fixture.service.joinTable(first, Fixture.TABLE_ID).orElseThrow() == fixture.session,
                "first player joins configured physical table");
        check(fixture.service.getSeatNumber(first.getUniqueId()).orElseThrow() == 1, "first player gets seat 1");
        check(fixture.service.joinTable(second, Fixture.TABLE_ID).isPresent(), "second player joins");
        check(fixture.service.getSeatNumber(second.getUniqueId()).orElseThrow() == 2, "second player gets seat 2");
        check(fixture.service.joinTable(third, Fixture.TABLE_ID).isPresent(), "third player joins");
        check(fixture.service.getSeatNumber(third.getUniqueId()).orElseThrow() == 3, "third player gets seat 3");
        check(fixture.service.joinTable(fourth, Fixture.TABLE_ID).isEmpty(), "fourth player is rejected at capacity 3");
        check(fixture.seats.getSeatNumber(fourth.getUniqueId()).isEmpty(), "rejected player has no seat");
        check(fixture.service.joinTable(first, Fixture.TABLE_ID).orElseThrow() == fixture.session,
                "same-table click reopens without joining again");
        check(fixture.session.getParticipantCount() == 3, "reopen does not duplicate membership");
        checkResult(fixture.service.leave(first), ActivityResult.SUCCESS);
        Player replacement = fixture.player("Replacement");
        check(fixture.service.joinTable(replacement, Fixture.TABLE_ID).isPresent(), "freed seat can be reused");
        check(fixture.service.getSeatNumber(replacement.getUniqueId()).orElseThrow() == 1,
                "lowest free configured seat is reused");
    }

    private static void testJoinRollback() {
        Fixture fixture = new Fixture(2);
        Player player = fixture.player("Rollback");
        fixture.seats.failMount = true;
        check(fixture.service.joinTable(player, Fixture.TABLE_ID).isEmpty(), "mount failure rejects join");
        check(fixture.activityService.getSessionForPlayer(player.getUniqueId()).isEmpty(),
                "mount failure rolls back membership");
        check(fixture.seats.getSeatNumber(player.getUniqueId()).isEmpty(), "mount failure frees reservation");
        check(!fixture.seats.mounted.contains(player.getUniqueId()), "mount failure leaves no seat entity state");
        check(fixture.session.getParticipantCount() == 0, "mount failure leaves table empty");
    }

    private static void testSinglePlayerRoundResetAndRematch() {
        Fixture fixture = new Fixture(3);
        Player solo = fixture.player("Solo");
        fixture.service.joinTable(solo, Fixture.TABLE_ID).orElseThrow();
        fixture.queueShoe(cards(BlackjackRank.TEN, BlackjackRank.SIX,
                BlackjackRank.SEVEN, BlackjackRank.TEN, BlackjackRank.TWO));
        checkResult(fixture.service.startRound(solo), ActivityResult.SUCCESS);
        check(fixture.session.getState() == ActivityState.ACTIVE, "one player starts immediately");
        check(fixture.scheduler.tasks.getFirst().delayTicks == BlackjackService.TURN_TIMEOUT_TICKS,
                "turn timeout is 20 seconds");
        checkResult(fixture.service.stand(solo), ActivityResult.SUCCESS);
        check(fixture.session.getRoundPhase() == BlackjackRoundPhase.SETTLED, "stand settles single round");
        check(fixture.session.getDealerHand().getValue() == 18, "dealer hits 16 to 18");
        check(fixture.scheduler.tasks.stream().anyMatch(task -> !task.cancelled
                        && task.delayTicks == BlackjackService.RESULT_VIEW_TICKS),
                "settlement is displayed for three seconds");
        fixture.scheduler.runNextActive();
        check(fixture.session.getState() == ActivityState.AVAILABLE, "round resets to available");
        check(fixture.session.getRoundPhase() == BlackjackRoundPhase.IDLE, "round state clears");
        check(fixture.session.getParticipantCount() == 1, "reset keeps participant");
        check(fixture.service.getSeatNumber(solo.getUniqueId()).orElseThrow() == 1, "reset keeps seat");
        check(fixture.seats.mounted.contains(solo.getUniqueId()), "reset keeps seat entity state");
        fixture.queueShoe(cards(BlackjackRank.TEN, BlackjackRank.NINE,
                BlackjackRank.EIGHT, BlackjackRank.EIGHT));
        checkResult(fixture.service.startRound(solo), ActivityResult.SUCCESS);
        check(fixture.session.getState() == ActivityState.ACTIVE, "seated player starts rematch");
    }

    private static void testMultiplayerDealOrderAndActions() {
        Fixture fixture = new Fixture(3);
        Player first = fixture.player("First");
        Player second = fixture.player("Second");
        Player third = fixture.player("Third");
        fixture.service.joinTable(first, Fixture.TABLE_ID).orElseThrow();
        fixture.service.joinTable(second, Fixture.TABLE_ID).orElseThrow();
        fixture.service.joinTable(third, Fixture.TABLE_ID).orElseThrow();
        fixture.queueShoe(cards(BlackjackRank.TWO, BlackjackRank.THREE, BlackjackRank.FOUR, BlackjackRank.SIX,
                BlackjackRank.FIVE, BlackjackRank.SIX, BlackjackRank.SEVEN, BlackjackRank.TEN,
                BlackjackRank.TWO));
        checkResult(fixture.service.startRound(first), ActivityResult.SUCCESS);
        check(fixture.session.getPlayerRounds().size() == 3, "all seated players enter round");
        check(fixture.session.getPlayerRound(first.getUniqueId()).orElseThrow().getHand().getCards().getFirst().rank()
                == BlackjackRank.TWO, "first joiner receives first card");
        check(fixture.session.getPlayerRound(second.getUniqueId()).orElseThrow().getHand().getCards().getFirst().rank()
                == BlackjackRank.THREE, "second joiner receives second card");
        check(fixture.session.getPlayerRound(third.getUniqueId()).orElseThrow().getHand().getCards().getFirst().rank()
                == BlackjackRank.FOUR, "third joiner receives third card");
        check(fixture.session.getDealerHand().getCards().getFirst().rank() == BlackjackRank.SIX,
                "dealer receives card after first pass");
        check(fixture.session.getCurrentTurnPlayer().orElseThrow().equals(first.getUniqueId()),
                "turn starts with first joiner");
        checkResult(fixture.service.stand(first), ActivityResult.SUCCESS);
        check(fixture.session.getCurrentTurnPlayer().orElseThrow().equals(second.getUniqueId()),
                "turn advances to second joiner");
        checkResult(fixture.service.stand(second), ActivityResult.SUCCESS);
        check(fixture.session.getCurrentTurnPlayer().orElseThrow().equals(third.getUniqueId()),
                "turn advances to third joiner");
        checkResult(fixture.service.stand(third), ActivityResult.SUCCESS);
        check(fixture.session.getRoundPhase() == BlackjackRoundPhase.SETTLED, "last stand settles round");
    }

    private static void testHitTimeoutAndStaleTaskSafety() {
        Fixture fixture = new Fixture(1);
        Player player = fixture.player("Timeout");
        fixture.service.joinTable(player, Fixture.TABLE_ID).orElseThrow();
        fixture.queueShoe(cards(BlackjackRank.FIVE, BlackjackRank.TEN,
                BlackjackRank.SIX, BlackjackRank.SEVEN, BlackjackRank.TWO));
        checkResult(fixture.service.startRound(player), ActivityResult.SUCCESS);
        FakeTask firstTimeout = fixture.scheduler.tasks.getFirst();
        checkResult(fixture.service.hit(player), ActivityResult.SUCCESS);
        check(fixture.session.getPlayerRound(player.getUniqueId()).orElseThrow().getHand().size() == 3,
                "hit adds card");
        firstTimeout.runRegardlessOfCancellation();
        check(fixture.session.getRoundPhase() == BlackjackRoundPhase.PLAYER_TURNS,
                "stale timeout cannot end newer turn");
        fixture.scheduler.runNextActive();
        check(fixture.session.getRoundPhase() == BlackjackRoundPhase.SETTLED,
                "current timeout automatically stands");
    }

    private static void testDoubleDown() {
        Fixture fixture = new Fixture(1);
        Player player = fixture.player("Double");
        fixture.service.joinTable(player, Fixture.TABLE_ID).orElseThrow();
        fixture.queueShoe(cards(BlackjackRank.FIVE, BlackjackRank.TEN,
                BlackjackRank.SIX, BlackjackRank.SEVEN, BlackjackRank.TWO));
        checkResult(fixture.service.startRound(player), ActivityResult.SUCCESS);
        FakeTask originalTimeout = fixture.scheduler.tasks.getFirst();
        checkResult(fixture.service.doubleDown(player), ActivityResult.SUCCESS);
        BlackjackPlayerRound round = fixture.session.getPlayerRound(player.getUniqueId()).orElseThrow();
        check(round.isDoubledDown(), "double down is recorded on the player round");
        check(round.getHand().size() == 3, "double down draws exactly one card");
        check(round.isFinished(), "double down finishes the player hand");
        check(originalTimeout.cancelled, "double down cancels the active turn timeout");
        check(fixture.session.getRoundPhase() == BlackjackRoundPhase.SETTLED,
                "double down advances through dealer settlement");
    }

    private static void testMidRoundJoinRejected() {
        Fixture fixture = new Fixture(3);
        Player first = fixture.player("First");
        Player late = fixture.player("Late");
        fixture.service.joinTable(first, Fixture.TABLE_ID).orElseThrow();
        fixture.queueShoe(cards(BlackjackRank.FIVE, BlackjackRank.SIX,
                BlackjackRank.SIX, BlackjackRank.TEN, BlackjackRank.TWO));
        checkResult(fixture.service.startRound(first), ActivityResult.SUCCESS);
        check(fixture.service.joinTable(late, Fixture.TABLE_ID).isEmpty(), "active round rejects late join");
        check(fixture.activityService.getSessionForPlayer(late.getUniqueId()).isEmpty(),
                "late player has no membership");
        check(fixture.seats.getSeatNumber(late.getUniqueId()).isEmpty(), "late player has no seat");
        check(!fixture.seats.mounted.contains(late.getUniqueId()), "late player has no entity state");
        check(fixture.messages.stream().anyMatch(message -> message.contains("already in progress")),
                "active rejection is explained");
    }

    private static void testLeaveDisconnectAndWorldChangeCleanup() {
        for (ActivityLeaveReason reason : List.of(ActivityLeaveReason.VOLUNTARY,
                ActivityLeaveReason.DISCONNECT, ActivityLeaveReason.WORLD_CHANGE, ActivityLeaveReason.DEATH)) {
            Fixture fixture = new Fixture(2);
            Player player = fixture.player(reason.name());
            fixture.service.joinTable(player, Fixture.TABLE_ID).orElseThrow();
            checkResult(fixture.activityService.leaveCurrentSession(player.getUniqueId(), reason), ActivityResult.SUCCESS);
            check(fixture.activityService.getSessionForPlayer(player.getUniqueId()).isEmpty(), reason + " clears membership");
            check(fixture.seats.getSeatNumber(player.getUniqueId()).isEmpty(), reason + " releases seat");
            check(!fixture.seats.mounted.contains(player.getUniqueId()), reason + " removes entity state");
            check(fixture.session.getState() == ActivityState.AVAILABLE, reason + " leaves table available");
            check(fixture.session.getParticipantCount() == 0, reason + " leaves persistent table empty");
        }
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

    private static void checkOutcome(BlackjackHand player, BlackjackHand dealer, BlackjackOutcome expected) {
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

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, ProxyHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, arguments) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "toString" -> type.getSimpleName() + "Proxy";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == arguments[0];
                    default -> null;
                };
            }
            return handler.invoke(method, arguments);
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

    private static ActivityService createActivityService(PlayerService players, Logger logger,
                                                         Predicate<String> loadedWorldCheck) {
        try {
            Constructor<ActivityService> constructor = ActivityService.class.getDeclaredConstructor(
                    PlayerService.class, Logger.class, BooleanSupplier.class, Predicate.class);
            constructor.setAccessible(true);
            return constructor.newInstance(players, logger, (BooleanSupplier) () -> true, loadedWorldCheck);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not create activity harness service", exception);
        }
    }

    private static Logger logger() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        return logger;
    }

    private static World world(String name) {
        return proxy(World.class, (method, arguments) -> method.getName().equals("getName")
                ? name : defaultValue(method.getReturnType()));
    }

    @FunctionalInterface
    private interface ProxyHandler {
        Object invoke(java.lang.reflect.Method method, Object[] arguments) throws Throwable;
    }

    private static final class Fixture {
        private static final String TABLE_ID = "physical-table";
        private final Logger logger = logger();
        private final PlayerService players = new PlayerService(new MemoryRepository(), logger);
        private final World world = world("world");
        private final Map<UUID, Player> onlinePlayers = new HashMap<>();
        private final Deque<BlackjackShoe> shoes = new ArrayDeque<>();
        private final FakeScheduler scheduler = new FakeScheduler();
        private final List<String> messages = new ArrayList<>();
        private final Set<UUID> buildPlayers = new HashSet<>();
        private final Map<String, BlackjackTableDefinition> definitions = new HashMap<>();
        private final Map<String, BlackjackSession> sessions = new HashMap<>();
        private final FakeSeats seats = new FakeSeats();
        private final ActivityService activityService;
        private final BlackjackService service;
        private final BlackjackSession session;

        private Fixture(int capacity) {
            activityService = createActivityService(players, logger, Set.of("world")::contains);
            BlackjackTableDefinition definition = definition(capacity);
            definitions.put(TABLE_ID, definition);
            service = new BlackjackService(activityService, new BlackjackService.TableAccess() {
                public Optional<BlackjackTableDefinition> getDefinition(String id) {
                    return Optional.ofNullable(definitions.get(id));
                }
                public Optional<BlackjackSession> getSession(String id) {
                    return Optional.ofNullable(sessions.get(id));
                }
            }, seats, onlinePlayers::get,
                    (player, message) -> messages.add(player.getUniqueId() + ":" + message),
                    scheduler::schedule,
                    () -> shoes.isEmpty() ? new BlackjackShoe(6, new java.util.Random(123L)) : shoes.removeFirst(),
                    buildPlayers::contains,
                    logger);
            checkResult(activityService.registerActivityType(new BlackjackActivityType(service)), ActivityResult.SUCCESS);
            ActivityVenue venue = new ActivityVenue(TABLE_ID, BlackjackActivityType.KEY,
                    definition.area(), definition.dealer());
            checkResult(activityService.registerVenue(venue), ActivityResult.SUCCESS);
            ActivitySessionCreationResult creation = activityService.createSession(BlackjackActivityType.KEY, TABLE_ID);
            checkResult(creation.result(), ActivityResult.SUCCESS);
            session = (BlackjackSession) creation.session().orElseThrow();
            sessions.put(TABLE_ID, session);
        }

        private Player player(String name) {
            UUID id = UUID.randomUUID();
            players.loadPlayer(id, name);
            Player player = proxy(Player.class, (method, arguments) -> switch (method.getName()) {
                case "getUniqueId" -> id;
                case "getName" -> name;
                case "isOnline" -> true;
                case "getWorld" -> world;
                case "getLocation" -> new Location(world, 5, 5, 5);
                default -> defaultValue(method.getReturnType());
            });
            onlinePlayers.put(id, player);
            return player;
        }

        private void queueShoe(List<BlackjackCard> drawOrder) {
            shoes.addLast(new BlackjackShoe(drawOrder));
        }

        private static BlackjackTableDefinition definition(int capacity) {
            List<BlackjackSeat> seats = new ArrayList<>();
            for (int number = 1; number <= capacity; number++) {
                seats.add(new BlackjackSeat(number, new ActivityPosition("world", number, 5, 5, 0, 0)));
            }
            return new BlackjackTableDefinition(TABLE_ID,
                    new ActivityArea("world", 0, 0, 0, 10, 10, 10),
                    new ActivityPosition("world", 5, 5, 2, 180, 0),
                    new BlackjackBlockPosition("world", 5, 4, 5), seats);
        }
    }

    private static final class FakeSeats implements BlackjackService.SeatAccess {
        private final Map<String, Map<Integer, UUID>> occupants = new HashMap<>();
        private final Map<UUID, Integer> assignments = new HashMap<>();
        private final Map<UUID, String> tables = new HashMap<>();
        private final Set<UUID> mounted = new HashSet<>();
        private boolean failMount;

        public boolean reserveLowestFreeSeat(BlackjackTableDefinition definition, UUID playerId) {
            if (assignments.containsKey(playerId)) return tables.get(playerId).equals(definition.id());
            Map<Integer, UUID> table = occupants.computeIfAbsent(definition.id(), ignored -> new HashMap<>());
            for (BlackjackSeat seat : definition.seats()) {
                if (!table.containsKey(seat.number())) {
                    table.put(seat.number(), playerId);
                    assignments.put(playerId, seat.number());
                    tables.put(playerId, definition.id());
                    return true;
                }
            }
            return false;
        }

        public void mountReservedPlayer(Player player) {
            if (failMount) throw new IllegalStateException("simulated mount failure");
            mounted.add(player.getUniqueId());
        }

        public void releaseSeat(UUID playerId) {
            Integer seat = assignments.remove(playerId);
            String tableId = tables.remove(playerId);
            mounted.remove(playerId);
            if (seat != null && tableId != null) {
                Map<Integer, UUID> table = occupants.get(tableId);
                table.remove(seat, playerId);
                if (table.isEmpty()) occupants.remove(tableId);
            }
        }

        public Optional<Integer> getSeatNumber(UUID playerId) {
            return Optional.ofNullable(assignments.get(playerId));
        }
    }

    private static final class FakeScheduler {
        private final List<FakeTask> tasks = new ArrayList<>();
        private BukkitTask schedule(Runnable action, long delayTicks) {
            FakeTask task = new FakeTask(tasks.size() + 1, action, delayTicks);
            tasks.add(task);
            return task;
        }
        private void runNextActive() {
            tasks.stream().filter(task -> !task.isCancelled() && !task.executed).findFirst()
                    .orElseThrow(() -> new AssertionError("No active scheduled task"))
                    .runRegardlessOfCancellation();
        }
    }

    private static final class FakeTask implements BukkitTask {
        private final int id;
        private final Runnable action;
        private final long delayTicks;
        private boolean cancelled;
        private boolean executed;
        private FakeTask(int id, Runnable action, long delayTicks) {
            this.id = id;
            this.action = action;
            this.delayTicks = delayTicks;
        }
        private void runRegardlessOfCancellation() {
            if (executed) throw new AssertionError("Task already executed");
            executed = true;
            action.run();
        }
        public int getTaskId() { return id; }
        public Plugin getOwner() { return null; }
        public boolean isSync() { return true; }
        public void cancel() { cancelled = true; }
        public boolean isCancelled() { return cancelled; }
    }

    private static final class MemoryRepository implements PlayerRepository {
        private final Map<UUID, CorePlayer> players = new HashMap<>();
        public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(players.get(id)); }
        public void save(CorePlayer player) { players.put(player.getUniqueId(), player); }
        public boolean exists(UUID id) { return players.containsKey(id); }
    }
}
