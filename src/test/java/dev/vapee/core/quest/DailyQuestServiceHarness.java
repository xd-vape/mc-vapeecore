package dev.vapee.core.quest;

import dev.vapee.core.economy.CoinWallet;
import dev.vapee.core.onlinereward.OnlineRewardProgress;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.repository.PlayerRepository;
import dev.vapee.core.player.settings.PlayerSettings;
import dev.vapee.core.player.social.PlayerSocial;
import dev.vapee.core.quest.daily.DailyQuestConfig;
import dev.vapee.core.quest.daily.DailyQuestCycleId;
import dev.vapee.core.quest.daily.DailyQuestService;
import dev.vapee.core.quest.daily.DailyQuestSyncResult;
import dev.vapee.core.quest.daily.PlayerDailyQuestState;
import dev.vapee.core.reward.RewardResult;
import dev.vapee.core.reward.RewardSource;
import dev.vapee.core.reward.RewardStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

public final class DailyQuestServiceHarness {

    private static final Instant TODAY = Instant.parse("2026-09-22T12:00:00Z");
    private static final Instant TOMORROW = Instant.parse("2026-09-23T12:00:00Z");
    private static int checks;

    private DailyQuestServiceHarness() {
    }

    public static void main(String[] args) {
        testInitializationSameCycleAndRotation();
        testPendingProtection();
        testDisabledEmptyAndFewerDefinitions();
        System.out.println("DailyQuestServiceHarness passed " + checks + " checks.");
    }

    private static void testInitializationSameCycleAndRotation() {
        Fixture fixture = new Fixture(6, 4, true);
        UUID playerId = fixture.load(PlayerQuestState.empty(), PlayerDailyQuestState.uninitialized());
        check(fixture.service.syncPlayer(UUID.randomUUID(), TODAY) == DailyQuestSyncResult.PLAYER_NOT_LOADED,
                "unloaded player is skipped safely");
        check(fixture.service.syncPlayer(playerId, TODAY) == DailyQuestSyncResult.INITIALIZED,
                "first sync initializes only the current daily cycle");
        Map<String, PlayerQuestProgress> initial = fixture.state(playerId).snapshot();
        check(initial.size() == 4 && initial.values().stream().allMatch(progress ->
                        progress.progress() == 0L && progress.status() == QuestStatus.ACTIVE),
                "first assignment has four distinct ACTIVE quests at zero progress");
        check(fixture.cycle(playerId).equals("2026-09-22") && fixture.quests.isDirty(playerId)
                        && fixture.granter.calls.isEmpty(),
                "successful assignment sets cycle and QuestService dirty without rewarding");

        String firstId = initial.keySet().iterator().next();
        fixture.quests.addProgress(playerId, QuestProgressKey.of("test:" + firstId), 2L);
        fixture.quests.flushPlayer(playerId);
        Map<String, PlayerQuestProgress> progressed = fixture.state(playerId).snapshot();
        check(!fixture.quests.isDirty(playerId), "QuestService flush saves daily state with assignments");
        check(fixture.service.syncPlayer(playerId, TODAY) == DailyQuestSyncResult.CURRENT
                        && fixture.state(playerId).snapshot().equals(progressed)
                        && !fixture.quests.isDirty(playerId),
                "same-cycle sync preserves exact progress and does not mark dirty");

        fixture.configure(3, true);
        check(fixture.service.syncPlayer(playerId, TODAY) == DailyQuestSyncResult.CURRENT
                        && fixture.state(playerId).snapshot().size() == 4,
                "quests-per-day reload does not reroll the current cycle");
        check(fixture.service.syncPlayer(playerId, TOMORROW) == DailyQuestSyncResult.ROTATED,
                "next-day sync rotates assignments once");
        check(fixture.state(playerId).snapshot().size() == 3
                        && fixture.state(playerId).snapshot().values().stream().allMatch(progress ->
                        progress.status() == QuestStatus.ACTIVE && progress.progress() == 0L)
                        && fixture.cycle(playerId).equals("2026-09-23"),
                "new cycle applies the new slot count and resets old ACTIVE progress");
        check(fixture.granter.calls.isEmpty(), "daily selection never grants coins directly");

        Fixture missed = new Fixture(6, 4, true);
        UUID missedId = missed.load(PlayerQuestState.of(List.of(
                new PlayerQuestProgress("quest_0", 10L, QuestStatus.COMPLETED)
        )), PlayerDailyQuestState.initialized(DailyQuestCycleId.parse("2026-09-19")));
        check(missed.service.syncPlayer(missedId, TOMORROW) == DailyQuestSyncResult.ROTATED
                        && missed.cycle(missedId).equals("2026-09-23")
                        && missed.state(missedId).snapshot().values().stream().allMatch(progress ->
                        progress.status() == QuestStatus.ACTIVE && progress.progress() == 0L),
                "missed days are not processed and old COMPLETED state is replaced");

        Fixture restored = new Fixture(6, 4, true);
        UUID restoredId = restored.load(PlayerQuestState.of(progressed.values()),
                PlayerDailyQuestState.initialized(DailyQuestCycleId.parse("2026-09-22")));
        check(restored.service.syncPlayer(restoredId, TODAY) == DailyQuestSyncResult.CURRENT
                        && restored.state(restoredId).snapshot().equals(progressed)
                        && !restored.quests.isDirty(restoredId),
                "restart with persisted cycle and assignments never rerolls or resets progress");
    }

    private static void testPendingProtection() {
        Fixture fixture = new Fixture(6, 4, true);
        UUID playerId = fixture.load(PlayerQuestState.of(List.of(
                new PlayerQuestProgress("quest_0", 10L, QuestStatus.REWARD_PENDING)
        )), PlayerDailyQuestState.initialized(DailyQuestCycleId.parse("2026-09-21")));
        fixture.granter.outcomes.addLast(RewardStatus.BALANCE_OVERFLOW);
        check(fixture.service.syncPlayer(playerId, TODAY) == DailyQuestSyncResult.BLOCKED_PENDING_REWARD
                        && fixture.cycle(playerId).equals("2026-09-21")
                        && fixture.state(playerId).snapshot().get("quest_0").status()
                        == QuestStatus.REWARD_PENDING,
                "failed pending reward retry blocks rotation without losing the old state");
        check(fixture.granter.calls.size() == 1, "blocked rotation attempts the pending reward once");
        check(fixture.service.syncPlayer(playerId, TODAY) == DailyQuestSyncResult.ROTATED
                        && fixture.cycle(playerId).equals("2026-09-22")
                        && fixture.granter.calls.size() == 2,
                "successful retry pays once and permits rotation on the next sync");
        check(fixture.service.syncPlayer(playerId, TODAY) == DailyQuestSyncResult.CURRENT
                        && fixture.granter.calls.size() == 2,
                "successful retry is not paid twice in the same cycle");

        Fixture unknown = new Fixture(6, 4, true);
        UUID unknownId = unknown.load(PlayerQuestState.of(List.of(
                new PlayerQuestProgress("removed", 1L, QuestStatus.REWARD_PENDING)
        )), PlayerDailyQuestState.initialized(DailyQuestCycleId.parse("2026-09-21")));
        check(unknown.service.syncPlayer(unknownId, TODAY) == DailyQuestSyncResult.BLOCKED_PENDING_REWARD
                        && unknown.cycle(unknownId).equals("2026-09-21")
                        && unknown.state(unknownId).snapshot().containsKey("removed")
                        && unknown.granter.calls.isEmpty(),
                "unknown pending definition blocks reset without inventing a reward");
    }

    private static void testDisabledEmptyAndFewerDefinitions() {
        Fixture fixture = new Fixture(0, 4, false);
        PlayerQuestState existing = PlayerQuestState.of(List.of(
                new PlayerQuestProgress("legacy", 7L, QuestStatus.ACTIVE)
        ));
        UUID playerId = fixture.load(existing, PlayerDailyQuestState.uninitialized());
        check(fixture.service.syncPlayer(playerId, TODAY) == DailyQuestSyncResult.DISABLED
                        && fixture.cycle(playerId) == null && !fixture.quests.isDirty(playerId)
                        && fixture.state(playerId).snapshot().equals(existing.snapshot()),
                "disabled system never deletes or mutates existing player state");
        fixture.configure(4, true);
        check(fixture.service.syncPlayer(playerId, TODAY) == DailyQuestSyncResult.NO_DEFINITIONS
                        && fixture.cycle(playerId) == null && !fixture.quests.isDirty(playerId)
                        && fixture.state(playerId).snapshot().equals(existing.snapshot()),
                "enabled empty catalog does not advance cycle or clear assignments");

        Fixture fewer = new Fixture(2, 4, true);
        UUID fewerId = fewer.load(PlayerQuestState.empty(), PlayerDailyQuestState.uninitialized());
        check(fewer.service.syncPlayer(fewerId, TODAY) == DailyQuestSyncResult.INITIALIZED
                        && fewer.state(fewerId).snapshot().size() == 2
                        && fewer.cycle(fewerId).equals("2026-09-22"),
                "two available definitions fill two of four requested slots safely");
    }

    private static final class Fixture {
        private final MemoryRepository repository = new MemoryRepository();
        private final PlayerService players = new PlayerService(repository, logger());
        private final QuestDefinitionRegistry registry = new QuestDefinitionRegistry();
        private final MutableGranter granter = new MutableGranter();
        private final QuestService quests = new QuestService(registry, players, granter, logger());
        private final AtomicReference<DailyQuestConfig.State> config = new AtomicReference<>();
        private final DailyQuestService service = new DailyQuestService(
                players, quests, registry, config::get, logger()
        );
        private final List<QuestDefinition> definitions;

        private Fixture(int count, int slots, boolean enabled) {
            List<QuestDefinition> values = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                String id = "quest_" + index;
                values.add(new QuestDefinition(id, "Quest " + index, "Test", QuestProgressKey.of("test:" + id),
                        10L, 100L));
            }
            definitions = List.copyOf(values);
            registry.replaceAll(definitions);
            configure(slots, enabled);
        }

        private void configure(int slots, boolean enabled) {
            config.set(new DailyQuestConfig.State(enabled, slots, LocalTime.MIDNIGHT,
                    "UTC", ZoneId.of("UTC"), definitions));
        }

        private UUID load(PlayerQuestState quests, PlayerDailyQuestState daily) {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            repository.players.put(id, new CorePlayer(id, "Player", now, now,
                    PlayerSettings.defaults(), CoinWallet.empty(), PlayerSocial.empty(),
                    OnlineRewardProgress.uninitialized(), quests, daily));
            players.loadPlayer(id, "Player");
            return id;
        }

        private PlayerQuestState state(UUID id) {
            return players.getPlayer(id).orElseThrow().getQuestState();
        }

        private String cycle(UUID id) {
            return players.getPlayer(id).orElseThrow().getDailyQuestState().cycleId()
                    .map(DailyQuestCycleId::toString).orElse(null);
        }
    }

    private static final class MutableGranter implements QuestService.RewardGranter {
        private final Deque<RewardStatus> outcomes = new ArrayDeque<>();
        private final List<UUID> calls = new ArrayList<>();

        @Override
        public RewardResult grantCoins(UUID playerId, long coins, RewardSource source, String reason) {
            calls.add(playerId);
            RewardStatus status = outcomes.isEmpty() ? RewardStatus.SUCCESS : outcomes.removeFirst();
            return status == RewardStatus.SUCCESS
                    ? new RewardResult(status, coins, OptionalLong.of(coins))
                    : new RewardResult(status, 0L, OptionalLong.empty());
        }
    }

    private static final class MemoryRepository implements PlayerRepository {
        private final Map<UUID, CorePlayer> players = new HashMap<>();

        @Override public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(players.get(id)); }
        @Override public void save(CorePlayer player) { players.put(player.getUniqueId(), player); }
        @Override public boolean exists(UUID id) { return players.containsKey(id); }
    }

    private static Logger logger() {
        Logger logger = Logger.getLogger("DailyQuestServiceHarness-" + System.nanoTime());
        logger.setUseParentHandlers(false);
        return logger;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
