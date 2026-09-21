package dev.vapee.core.quest;

import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.onlinereward.OnlineRewardProgress;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import dev.vapee.core.reward.RewardResult;
import dev.vapee.core.reward.RewardSource;
import dev.vapee.core.reward.RewardStatus;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

public final class QuestServiceHarness {

    private static int checks;

    private QuestServiceHarness() {
    }

    public static void main(String[] args) {
        testAssignmentApiAndAtomicReplacement();
        testExactMultiQuestProgressAndCompletion();
        testRewardPendingRetryAndExceptionSafety();
        testDirtyBatchingFailureIsolationAndUnload();
        testUnknownDefinitionsAndProgrammerInput();
        System.out.println("QuestServiceHarness passed " + checks + " checks.");
    }

    private static void testAssignmentApiAndAtomicReplacement() {
        Fixture fixture = new Fixture();
        fixture.define(
                definition("first", "mine:block:any", 10L, 100L),
                definition("second", "playtime:minute", 30L, 200L)
        );
        UUID unloaded = UUID.randomUUID();
        check(fixture.quests.assignQuest(unloaded, "first")
                        == QuestAssignmentResult.PLAYER_NOT_LOADED,
                "unloaded player assignment returns a controlled result");

        UUID playerId = fixture.load(PlayerQuestState.empty());
        check(fixture.quests.assignQuest(playerId, "missing")
                        == QuestAssignmentResult.DEFINITION_NOT_FOUND,
                "unknown definition assignment returns a controlled result");
        check(fixture.quests.assignQuest(playerId, "first") == QuestAssignmentResult.ASSIGNED,
                "loaded player receives a known quest");
        PlayerQuestProgress first = fixture.progress(playerId, "first");
        check(first.progress() == 0L && first.status() == QuestStatus.ACTIVE
                        && fixture.quests.isDirty(playerId),
                "new assignment starts ACTIVE at zero and marks the player dirty");

        fixture.quests.addProgress(playerId, key("mine:block:any"), 2L);
        check(fixture.quests.assignQuest(playerId, "first")
                        == QuestAssignmentResult.ALREADY_ASSIGNED
                        && fixture.progress(playerId, "first").progress() == 2L,
                "duplicate assignment never resets existing progress");

        check(fixture.quests.replaceAssignments(playerId, List.of("second", "first"))
                        == QuestAssignmentResult.REPLACED,
                "valid assignment collection replaces the old state atomically");
        check(fixture.state(playerId).snapshot().keySet().stream().toList()
                        .equals(List.of("first", "second"))
                        && fixture.progress(playerId, "first").progress() == 0L
                        && fixture.progress(playerId, "second").status() == QuestStatus.ACTIVE,
                "replacement creates deterministic fresh ACTIVE assignments");

        Map<String, PlayerQuestProgress> beforeInvalidReplace = fixture.state(playerId).snapshot();
        check(fixture.quests.replaceAssignments(playerId, List.of("first", "first"))
                        == QuestAssignmentResult.DUPLICATE_QUEST_ID,
                "duplicate replacement IDs are rejected as a domain result");
        check(fixture.state(playerId).snapshot().equals(beforeInvalidReplace),
                "duplicate replacement causes no partial mutation");
        check(fixture.quests.replaceAssignments(playerId, List.of("first", "missing"))
                        == QuestAssignmentResult.DEFINITION_NOT_FOUND,
                "unknown replacement definition rejects the whole collection");
        check(fixture.state(playerId).snapshot().equals(beforeInvalidReplace),
                "unknown replacement definition leaves every old assignment intact");

        List<QuestView> views = fixture.quests.getActiveQuests(playerId).orElseThrow();
        check(views.stream().map(view -> view.definition().id()).toList()
                        .equals(List.of("first", "second"))
                        && views.stream().allMatch(view -> view.currentProgress() == 0L),
                "read API returns deterministic definition-backed quest snapshots");
        expectUnsupported(() -> views.add(views.getFirst()),
                "active quest snapshot is immutable");

        check(fixture.quests.clearAssignments(playerId) == QuestAssignmentResult.CLEARED
                        && fixture.state(playerId).isEmpty(),
                "clearAssignments removes current state and marks a real change");
        fixture.quests.flushPlayer(playerId);
        check(fixture.quests.clearAssignments(playerId) == QuestAssignmentResult.NO_CHANGE
                        && !fixture.quests.isDirty(playerId),
                "clearing an empty state is a clean no-op");
        check(fixture.quests.clearAssignments(unloaded)
                        == QuestAssignmentResult.PLAYER_NOT_LOADED,
                "clearAssignments reports an unloaded player without offline access");
    }

    private static void testExactMultiQuestProgressAndCompletion() {
        Fixture fixture = new Fixture();
        fixture.define(
                definition("first", "mine:block:any", 3L, 100L),
                definition("other", "mine:block:stone", 3L, 300L),
                definition("second", "mine:block:any", 3L, 200L)
        );
        UUID playerId = fixture.load(PlayerQuestState.empty());
        fixture.quests.replaceAssignments(playerId, List.of("first", "other", "second"));
        fixture.quests.flushPlayer(playerId);
        fixture.repository.resetCounters();

        QuestProgressResult firstSignal = fixture.quests.addProgress(
                playerId,
                key("mine:block:any"),
                1L
        );
        QuestProgressResult secondSignal = fixture.quests.addProgress(
                playerId,
                key("mine:block:any"),
                1L
        );
        check(firstSignal.status() == QuestProgressResult.Status.PROCESSED
                        && firstSignal.updatedQuests() == 2
                        && secondSignal.updatedQuests() == 2,
                "one exact progress signal updates every matching active quest");
        check(fixture.progress(playerId, "first").progress() == 2L
                        && fixture.progress(playerId, "second").progress() == 2L
                        && fixture.progress(playerId, "other").progress() == 0L,
                "nonmatching progress key remains untouched without prefix matching");
        check(fixture.granter.calls.isEmpty(),
                "active progress below target performs no reward call");

        QuestProgressResult completion = fixture.quests.addProgress(
                playerId,
                key("mine:block:any"),
                Long.MAX_VALUE
        );
        check(completion.updatedQuests() == 2
                        && completion.completedQuestIds().equals(List.of("first", "second"))
                        && completion.rewardPendingQuestIds().isEmpty(),
                "overflow-sized progress caps and completes both matching quests");
        check(fixture.progress(playerId, "first").progress() == 3L
                        && fixture.progress(playerId, "second").progress() == 3L
                        && fixture.progress(playerId, "first").status() == QuestStatus.COMPLETED
                        && fixture.progress(playerId, "second").status() == QuestStatus.COMPLETED,
                "completed progress never exceeds target and stores COMPLETED");
        check(fixture.granter.calls.size() == 2
                        && fixture.granter.calls.get(0).source() == RewardSource.QUEST
                        && fixture.granter.calls.get(0).reason().equals("quest:first")
                        && fixture.granter.calls.get(0).coins() == 100L
                        && fixture.granter.calls.get(1).reason().equals("quest:second")
                        && fixture.granter.calls.get(1).coins() == 200L,
                "simultaneous completions use separate exact QUEST reward grants");

        int callsAfterCompletion = fixture.granter.calls.size();
        QuestProgressResult extra = fixture.quests.addProgress(
                playerId,
                key("mine:block:any"),
                100L
        );
        check(extra.status() == QuestProgressResult.Status.NO_CHANGE
                        && fixture.granter.calls.size() == callsAfterCompletion
                        && fixture.progress(playerId, "first").progress() == 3L,
                "completed quests ignore all future progress without duplicate rewards");

        fixture.quests.flushPlayer(playerId);
        QuestProgressResult prefix = fixture.quests.addProgress(playerId, key("mine:block"), 1L);
        check(prefix.status() == QuestProgressResult.Status.NO_MATCHING_QUEST
                        && !fixture.quests.isDirty(playerId),
                "shorter prefix key is not a wildcard and creates no dirty state");
    }

    private static void testRewardPendingRetryAndExceptionSafety() {
        Fixture fixture = new Fixture();
        fixture.define(definition("winner", "blackjack:win", 1L, 500L));
        UUID playerId = fixture.load(PlayerQuestState.empty());
        fixture.quests.assignQuest(playerId, "winner");
        fixture.granter.enqueueFailure(RewardStatus.BALANCE_OVERFLOW);

        QuestProgressResult failed = fixture.quests.addProgress(
                playerId,
                key("blackjack:win"),
                1L
        );
        check(failed.rewardPendingQuestIds().equals(List.of("winner"))
                        && fixture.progress(playerId, "winner").progress() == 1L
                        && fixture.progress(playerId, "winner").status() == QuestStatus.REWARD_PENDING,
                "normal reward failure preserves target progress as REWARD_PENDING");
        check(fixture.granter.calls.size() == 1,
                "initial completion performs exactly one reward attempt");

        fixture.quests.flushPlayer(playerId);
        fixture.granter.enqueueFailure(RewardStatus.PLAYER_NOT_LOADED);
        QuestProgressResult pendingSignal = fixture.quests.addProgress(
                playerId,
                key("blackjack:win"),
                100L
        );
        check(pendingSignal.updatedQuests() == 0
                        && pendingSignal.rewardPendingQuestIds().equals(List.of("winner"))
                        && fixture.progress(playerId, "winner").progress() == 1L
                        && !fixture.quests.isDirty(playerId),
                "extra signal retries pending reward without progress growth or needless dirty state");

        fixture.granter.enqueueSuccess();
        QuestProgressResult retried = fixture.quests.retryPendingRewards(playerId);
        check(retried.completedQuestIds().equals(List.of("winner"))
                        && fixture.progress(playerId, "winner").status() == QuestStatus.COMPLETED
                        && fixture.quests.isDirty(playerId),
                "successful pending retry transitions the quest to COMPLETED");
        int callsAfterSuccess = fixture.granter.calls.size();
        check(fixture.quests.retryPendingRewards(playerId).status()
                        == QuestProgressResult.Status.NO_CHANGE
                        && fixture.granter.calls.size() == callsAfterSuccess,
                "completed pending reward cannot be paid twice");

        Fixture exceptional = new Fixture();
        exceptional.define(definition("exception", "activity:complete", 1L, 50L));
        UUID exceptionalId = exceptional.load(PlayerQuestState.empty());
        exceptional.quests.assignQuest(exceptionalId, "exception");
        exceptional.granter.enqueueException();
        QuestProgressResult exceptionResult = exceptional.quests.addProgress(
                exceptionalId,
                key("activity:complete"),
                1L
        );
        check(exceptionResult.rewardPendingQuestIds().equals(List.of("exception"))
                        && exceptional.progress(exceptionalId, "exception").status()
                        == QuestStatus.REWARD_PENDING,
                "unexpected reward exception is contained and leaves durable pending state");
    }

    private static void testDirtyBatchingFailureIsolationAndUnload() {
        Fixture fixture = new Fixture();
        fixture.define(definition("long_running", "test:progress", Long.MAX_VALUE, 1L));
        UUID playerA = fixture.load(PlayerQuestState.empty());
        fixture.quests.assignQuest(playerA, "long_running");
        fixture.quests.flushPlayer(playerA);
        fixture.repository.resetCounters();

        for (int index = 0; index < 100; index++) {
            fixture.quests.addProgress(playerA, key("test:progress"), 1L);
        }
        check(fixture.progress(playerA, "long_running").progress() == 100L
                        && fixture.repository.totalSaveAttempts() == 0,
                "one hundred progress signals mutate memory without one hundred saves");
        fixture.quests.flushPlayer(playerA);
        check(fixture.repository.saveAttempts(playerA) == 1
                        && fixture.repository.persistedProgress(playerA, "long_running") == 100L
                        && !fixture.quests.isDirty(playerA),
                "one quest flush persists all one hundred signals exactly once");
        fixture.quests.flushPlayer(playerA);
        check(fixture.repository.saveAttempts(playerA) == 1,
                "flushPlayer is a no-op for clean quest state");

        UUID playerB = fixture.load(PlayerQuestState.empty());
        fixture.quests.assignQuest(playerA, "long_running");
        fixture.quests.assignQuest(playerB, "long_running");
        fixture.quests.addProgress(playerA, key("test:progress"), 1L);
        fixture.quests.addProgress(playerB, key("test:progress"), 2L);
        fixture.repository.fail(playerA);
        fixture.quests.flushAll();
        check(fixture.quests.isDirty(playerA)
                        && !fixture.quests.isDirty(playerB)
                        && fixture.repository.persistedProgress(playerB, "long_running") == 2L,
                "flushAll isolates one save failure and persists other dirty players");
        fixture.repository.recover(playerA);
        fixture.quests.flushAll();
        check(!fixture.quests.isDirty(playerA)
                        && fixture.repository.persistedProgress(playerA, "long_running") == 101L,
                "later flush retries formerly failed quest persistence");

        fixture.quests.addProgress(playerB, key("test:progress"), 1L);
        fixture.players.unloadPlayer(playerB);
        int savesAfterUnload = fixture.repository.saveAttempts(playerB);
        fixture.quests.flushPlayer(playerB);
        check(!fixture.quests.isDirty(playerB)
                        && fixture.repository.saveAttempts(playerB) == savesAfterUnload,
                "already-unloaded player clears quest dirty state without offline loading or another save");
    }

    private static void testUnknownDefinitionsAndProgrammerInput() {
        Fixture fixture = new Fixture();
        PlayerQuestState unknownState = PlayerQuestState.of(List.of(
                new PlayerQuestProgress("unknown", 137L, QuestStatus.ACTIVE),
                new PlayerQuestProgress("pending_unknown", 5L, QuestStatus.REWARD_PENDING)
        ));
        UUID playerId = fixture.load(unknownState);
        QuestProgressResult result = fixture.quests.addProgress(
                playerId,
                key("mine:block:any"),
                1L
        );
        check(result.status() == QuestProgressResult.Status.NO_MATCHING_QUEST
                        && fixture.progress(playerId, "unknown").progress() == 137L
                        && fixture.granter.calls.isEmpty(),
                "persisted assignment with unknown definition is retained and ignored safely");
        check(fixture.quests.retryPendingRewards(playerId).status()
                        == QuestProgressResult.Status.NO_CHANGE
                        && fixture.progress(playerId, "pending_unknown").status()
                        == QuestStatus.REWARD_PENDING,
                "unknown pending definition is neither deleted nor rewarded");
        check(fixture.quests.getActiveQuests(playerId).orElseThrow().isEmpty(),
                "definition-backed read view omits unknown persisted assignments");
        check(fixture.quests.addProgress(UUID.randomUUID(), key("test:key"), 1L).status()
                        == QuestProgressResult.Status.PLAYER_NOT_LOADED,
                "progress for unloaded player returns a controlled result");

        expectIllegalArgument(
                () -> fixture.quests.addProgress(playerId, key("test:key"), 0L),
                "zero progress amount is rejected"
        );
        expectIllegalArgument(
                () -> fixture.quests.addProgress(playerId, key("test:key"), -1L),
                "negative progress amount is rejected"
        );
        expectNullPointer(
                () -> fixture.quests.addProgress(playerId, null, 1L),
                "null progress key is rejected"
        );
        expectIllegalArgument(
                () -> fixture.quests.assignQuest(playerId, "Invalid"),
                "invalid programmer-provided quest ID is rejected"
        );
    }

    private static QuestDefinition definition(
            String id,
            String progressKey,
            long target,
            long rewardCoins
    ) {
        return new QuestDefinition(
                id,
                "Quest " + id,
                "Complete " + id,
                key(progressKey),
                target,
                rewardCoins
        );
    }

    private static QuestProgressKey key(String value) {
        return QuestProgressKey.of(value);
    }

    private static CorePlayer player(UUID playerId, PlayerQuestState questState) {
        Instant now = Instant.now();
        return new CorePlayer(
                playerId,
                "Player",
                now,
                now,
                PlayerSettings.defaults(),
                CoinWallet.empty(),
                PlayerSocial.empty(),
                OnlineRewardProgress.uninitialized(),
                questState
        );
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("QuestServiceHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void expectIllegalArgument(Runnable action, String message) {
        expect(IllegalArgumentException.class, action, message);
    }

    private static void expectNullPointer(Runnable action, String message) {
        expect(NullPointerException.class, action, message);
    }

    private static void expectUnsupported(Runnable action, String message) {
        expect(UnsupportedOperationException.class, action, message);
    }

    private static void expect(Class<? extends RuntimeException> type, Runnable action, String message) {
        try {
            action.run();
            throw new AssertionError(message);
        } catch (RuntimeException exception) {
            if (!type.isInstance(exception)) {
                throw new AssertionError(message, exception);
            }
            checks++;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static final class Fixture {

        private final MemoryRepository repository = new MemoryRepository();
        private final PlayerService players = new PlayerService(repository, logger());
        private final QuestDefinitionRegistry registry = new QuestDefinitionRegistry();
        private final MutableGranter granter = new MutableGranter();
        private final QuestService quests = new QuestService(registry, players, granter, logger());

        private void define(QuestDefinition... definitions) {
            registry.replaceAll(List.of(definitions));
        }

        private UUID load(PlayerQuestState state) {
            UUID playerId = UUID.randomUUID();
            repository.seed(player(playerId, state));
            players.loadPlayer(playerId, "Player");
            repository.resetCounters();
            return playerId;
        }

        private PlayerQuestState state(UUID playerId) {
            return players.getPlayer(playerId).orElseThrow().getQuestState();
        }

        private PlayerQuestProgress progress(UUID playerId, String questId) {
            return state(playerId).snapshot().get(questId);
        }
    }

    private record GrantCall(UUID playerId, long coins, RewardSource source, String reason) {
    }

    private static final class MutableGranter implements QuestService.RewardGranter {

        private final List<GrantCall> calls = new ArrayList<>();
        private final Deque<Object> outcomes = new ArrayDeque<>();

        private void enqueueSuccess() {
            outcomes.addLast(RewardStatus.SUCCESS);
        }

        private void enqueueFailure(RewardStatus status) {
            if (status == RewardStatus.SUCCESS) {
                throw new IllegalArgumentException("Use enqueueSuccess for SUCCESS");
            }
            outcomes.addLast(status);
        }

        private void enqueueException() {
            outcomes.addLast(new SyntheticRewardFailure());
        }

        @Override
        public RewardResult grantCoins(UUID playerId, long coins, RewardSource source, String reason) {
            calls.add(new GrantCall(playerId, coins, source, reason));
            Object outcome = outcomes.isEmpty() ? RewardStatus.SUCCESS : outcomes.removeFirst();
            if (outcome instanceof RuntimeException exception) {
                throw exception;
            }
            RewardStatus status = (RewardStatus) outcome;
            if (status == RewardStatus.SUCCESS) {
                return new RewardResult(status, coins, OptionalLong.of(1_000L));
            }
            return new RewardResult(status, 0L, OptionalLong.empty());
        }
    }

    private static final class MemoryRepository implements PlayerRepository {

        private final Map<UUID, CorePlayer> players = new HashMap<>();
        private final Map<UUID, Map<String, PlayerQuestProgress>> persisted = new HashMap<>();
        private final Map<UUID, Integer> saveAttempts = new HashMap<>();
        private final Set<UUID> failures = new HashSet<>();

        private void seed(CorePlayer player) {
            players.put(player.getUniqueId(), player);
            capture(player);
        }

        private void resetCounters() {
            saveAttempts.clear();
        }

        private void fail(UUID playerId) {
            failures.add(playerId);
        }

        private void recover(UUID playerId) {
            failures.remove(playerId);
        }

        private int saveAttempts(UUID playerId) {
            return saveAttempts.getOrDefault(playerId, 0);
        }

        private int totalSaveAttempts() {
            return saveAttempts.values().stream().mapToInt(Integer::intValue).sum();
        }

        private long persistedProgress(UUID playerId, String questId) {
            return persisted.get(playerId).get(questId).progress();
        }

        @Override
        public Optional<CorePlayer> findByUniqueId(UUID uniqueId) {
            return Optional.ofNullable(players.get(uniqueId));
        }

        @Override
        public void save(CorePlayer player) {
            UUID playerId = player.getUniqueId();
            saveAttempts.merge(playerId, 1, Integer::sum);
            if (failures.contains(playerId)) {
                throw new SyntheticPersistenceFailure();
            }
            players.put(playerId, player);
            capture(player);
        }

        @Override
        public boolean exists(UUID uniqueId) {
            return players.containsKey(uniqueId);
        }

        private void capture(CorePlayer player) {
            persisted.put(player.getUniqueId(), Map.copyOf(player.getQuestState().snapshot()));
        }
    }

    private static final class SyntheticRewardFailure extends RuntimeException {
    }

    private static final class SyntheticPersistenceFailure extends RuntimeException {
    }
}
