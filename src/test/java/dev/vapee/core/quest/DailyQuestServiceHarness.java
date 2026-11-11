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
import dev.vapee.core.quest.daily.DailyQuestSelection;
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

    public static void main(String[] args) throws Exception {
        testInitializationSameCycleAndRotation();
        testPendingProtection();
        testDisabledEmptyAndFewerDefinitions();
        testSelectionLifecycle();
        testDisabledPending();
        testRetiredRankQuests();
        testEmptySelection();
        testSelectionPersistence();
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
        private final Logger dailyLogger = logger();
        private final DailyQuestService service = new DailyQuestService(
                players, quests, registry, config::get, dailyLogger
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

        private void selection(DailyQuestSelection rules) {
            var current = config.get();
            config.set(new DailyQuestConfig.State(current.enabled(), current.questsPerDay(), current.resetTime(),
                    current.timezoneSetting(), current.zone(), definitions, rules));
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
        private boolean fail;

        @Override public Optional<CorePlayer> findByUniqueId(UUID id) { return Optional.ofNullable(players.get(id)); }
        @Override public void save(CorePlayer player) { if (fail) throw new IllegalStateException("test save failure"); players.put(player.getUniqueId(), player); }
        @Override public boolean exists(UUID id) { return players.containsKey(id); }
    }

    private static void testSelectionLifecycle() {
        Fixture f = new Fixture(6, 4, true);
        UUID id = f.load(PlayerQuestState.empty(), PlayerDailyQuestState.uninitialized());
        f.service.syncPlayer(id, TODAY);
        var initial = f.state(id).snapshot();
        var ids = new ArrayList<>(initial.keySet());
        String retained = ids.getFirst(), disabled = ids.get(1);
        f.quests.addProgress(id, QuestProgressKey.of("test:" + retained), 2);
        f.quests.addProgress(id, QuestProgressKey.of("test:" + disabled), 3);
        f.quests.flushPlayer(id);
        var progressed = f.state(id).snapshot();
        var rules = new HashMap<String, DailyQuestSelection.Quest>();
        for (var def : f.definitions) rules.put(def.id(), new DailyQuestSelection.Quest(def.id().equals(retained), "movement", "walking"));
        f.selection(new DailyQuestSelection(rules, 1L, 1L));
        check(f.service.syncPlayer(id, TODAY) == DailyQuestSyncResult.CURRENT && f.state(id).snapshot().equals(progressed) && !f.quests.isDirty(id),
                "same-cycle metadata reload preserves exact assignments/progress and avoids dirty writes");
        check(f.registry.findById(disabled).isPresent() && f.quests.getActiveQuests(id).orElseThrow().size() == 4,
                "disabled assigned definitions remain available to existing views");
        f.quests.addProgress(id, QuestProgressKey.of("test:" + disabled), 7);
        check(f.state(id).snapshot().get(disabled).status() == QuestStatus.COMPLETED && f.granter.calls.size() == 1,
                "disabled same-cycle task still completes through original QuestService");
        f.quests.addProgress(id, QuestProgressKey.of("test:" + disabled), 100);
        check(f.granter.calls.size() == 1, "disabled completion cannot pay twice");
        var beforeForce = f.state(id).snapshot(); var beforeCycle = f.cycle(id);
        f.repository.fail = true;
        check(f.service.forcePlayer(id, TOMORROW) == DailyQuestSyncResult.ASSIGNMENT_FAILED && f.state(id).snapshot().equals(beforeForce) && f.cycle(id).equals(beforeCycle),
                "new-selection force save failure restores old progress and cycle");
        f.repository.fail = false;
        check(f.service.forcePlayer(id, TODAY) == DailyQuestSyncResult.ROTATED && f.state(id).snapshot().keySet().equals(java.util.Set.of(retained))
                        && f.state(id).snapshot().get(retained).progress() == 2 && f.granter.calls.size() == 1,
                "explicit force adopts metadata selection and preserves overlapping progress without rewards");
        check(f.service.syncPlayer(id, TOMORROW) == DailyQuestSyncResult.ROTATED && f.state(id).snapshot().keySet().equals(java.util.Set.of(retained))
                        && f.state(id).snapshot().get(retained).progress() == 0,
                "normal next-cycle rotation uses new eligibility and limits with the established reset contract");
    }

    private static void testDisabledPending() {
        Fixture f = new Fixture(6, 4, true);
        UUID id = f.load(PlayerQuestState.of(List.of(new PlayerQuestProgress("quest_0", 10, QuestStatus.REWARD_PENDING))),
                PlayerDailyQuestState.initialized(DailyQuestCycleId.parse("2026-09-21")));
        f.selection(new DailyQuestSelection(Map.of("quest_0", new DailyQuestSelection.Quest(false, null, null)), null, null));
        f.granter.outcomes.addLast(RewardStatus.BALANCE_OVERFLOW);
        var pending = f.state(id).snapshot();
        check(f.service.forcePlayer(id, TODAY) == DailyQuestSyncResult.BLOCKED_PENDING_REWARD && f.granter.calls.isEmpty(),
                "force still refuses disabled pending tasks without bypassing retry ownership");
        check(f.service.syncPlayer(id, TODAY) == DailyQuestSyncResult.BLOCKED_PENDING_REWARD && f.state(id).snapshot().equals(pending)
                        && f.cycle(id).equals("2026-09-21") && f.granter.calls.size() == 1,
                "disabled pending definition survives unsuccessful retry and blocks erasure");
        check(f.service.syncPlayer(id, TODAY) == DailyQuestSyncResult.ROTATED && !f.state(id).snapshot().containsKey("quest_0")
                        && f.registry.findById("quest_0").isPresent() && f.granter.calls.size() == 2,
                "successful disabled pending retry allows rotation excluding the old definition");
        f.service.syncPlayer(id, TODAY);
        check(f.granter.calls.size() == 2, "disabled pending reward is not duplicated after successful retry");
    }

    private static void testEmptySelection() {
        Fixture f = new Fixture(3, 4, true);
        var rules = new HashMap<String, DailyQuestSelection.Quest>();
        for (var def : f.definitions) rules.put(def.id(), new DailyQuestSelection.Quest(false, null, null));
        f.selection(new DailyQuestSelection(rules, null, null));
        var warnings = new ArrayList<String>();
        f.dailyLogger.addHandler(new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) { warnings.add(record.getMessage()); }
            public void flush() { } public void close() { }
        });
        UUID fresh = f.load(PlayerQuestState.empty(), PlayerDailyQuestState.uninitialized());
        for (int n = 0; n < 8; n++) check(f.service.syncPlayer(fresh, TODAY) == DailyQuestSyncResult.NO_DEFINITIONS,
                "all disabled behaves like no selectable catalog, never assignment failure");
        check(f.cycle(fresh) == null && f.state(fresh).isEmpty() && !f.quests.isDirty(fresh) && warnings.size() == 1,
                "empty selection advances no cycle, writes no state and warns once per snapshot");
        UUID existing = f.load(PlayerQuestState.of(List.of(new PlayerQuestProgress("quest_0", 3, QuestStatus.ACTIVE))),
                PlayerDailyQuestState.initialized(DailyQuestCycleId.parse("2026-09-22")));
        check(f.service.syncPlayer(existing, TODAY) == DailyQuestSyncResult.CURRENT && f.state(existing).snapshot().get("quest_0").progress() == 3,
                "all-disabled reload leaves same-cycle tasks intact");
        check(f.service.syncPlayer(existing, TOMORROW) == DailyQuestSyncResult.NO_DEFINITIONS && f.cycle(existing).equals("2026-09-22")
                        && f.state(existing).snapshot().get("quest_0").progress() == 3 && warnings.size() == 1,
                "zero selectable tasks preserves prior state across a boundary like the existing empty-catalog contract");
        check(f.service.forcePlayer(existing, TOMORROW) == DailyQuestSyncResult.ROTATED && f.state(existing).isEmpty()
                        && f.cycle(existing).equals("2026-09-23"),
                "explicit force may reconcile to an empty eligible selection and commit the current cycle");
        f.selection(new DailyQuestSelection(rules, 1L, null)); f.service.syncPlayer(fresh, TODAY);
        check(warnings.size() == 2, "a newly published constrained snapshot can issue one fresh diagnostic");
    }

    private static void testRetiredRankQuests() {
        Fixture f = new Fixture(3, 3, true);
        var retiredKey = QuestProgressKey.of("lobby:ranks_check");
        var definitions = List.of(
                new QuestDefinition("check_ranks", "Old ranks", "Fixture", retiredKey, 10, 5),
                new QuestDefinition("another_rank_list", "Another old ranks", "Fixture", retiredKey, 2, 5),
                new QuestDefinition("check_rank", "Old rank", "Fixture",
                        QuestProgressKey.of("lobby:rank_check"), 1, 5),
                new QuestDefinition("check_profile", "Current profile", "Fixture",
                        QuestProgressKey.of("lobby:settings_open"), 1, 5),
                f.definitions.getFirst());
        var rules = new DailyQuestSelection(Map.of(
                "check_ranks", new DailyQuestSelection.Quest(true, "lobby", "information"),
                "check_profile", new DailyQuestSelection.Quest(true, "lobby", "information")), null, 1L);
        f.registry.replaceAll(definitions);
        f.config.set(new DailyQuestConfig.State(true, 3, LocalTime.MIDNIGHT, "UTC",
                ZoneId.of("UTC"), definitions, rules));
        var current = PlayerDailyQuestState.initialized(DailyQuestCycleId.parse("2026-09-22"));
        UUID fresh = f.load(PlayerQuestState.empty(), PlayerDailyQuestState.uninitialized());
        check(f.service.syncPlayer(fresh, TODAY) == DailyQuestSyncResult.INITIALIZED
                        && f.state(fresh).snapshot().keySet().equals(java.util.Set.of("check_profile", "quest_0")),
                "both retired keys are excluded from new assignments, independent of enabled and ID");
        var active = new PlayerQuestProgress("check_ranks", 3, QuestStatus.ACTIVE);
        var completed = new PlayerQuestProgress("check_rank", 1, QuestStatus.COMPLETED);
        UUID existing = f.load(PlayerQuestState.of(List.of(active, completed)), current);
        var previous = f.state(existing).snapshot();
        check(f.service.syncPlayer(existing, TODAY) == DailyQuestSyncResult.CURRENT
                        && f.state(existing).snapshot().equals(previous) && !f.quests.isDirty(existing),
                "same-cycle sync keeps legacy active and completed entries with no save");
        check(f.service.forcePlayer(existing, TODAY) == DailyQuestSyncResult.ROTATED
                        && f.state(existing).snapshot().get("check_ranks").equals(active)
                        && f.state(existing).snapshot().get("check_rank").equals(completed)
                        && f.state(existing).snapshot().keySet().equals(java.util.Set.of(
                                "check_ranks", "check_rank", "quest_0")) && f.granter.calls.isEmpty(),
                "same-cycle force reserves retained legacy slots and group limit without touching rewards");
        check(f.service.syncPlayer(existing, TOMORROW) == DailyQuestSyncResult.ROTATED
                        && f.state(existing).snapshot().keySet().equals(java.util.Set.of("check_profile", "quest_0")),
                "normal rotation fills slots with supported quests");
        UUID pending = f.load(PlayerQuestState.of(List.of(
                        new PlayerQuestProgress("check_ranks", 10, QuestStatus.REWARD_PENDING))),
                PlayerDailyQuestState.initialized(DailyQuestCycleId.parse("2026-09-22")));
        f.granter.outcomes.addLast(RewardStatus.BALANCE_OVERFLOW);
        check(f.service.forcePlayer(pending, TOMORROW) == DailyQuestSyncResult.BLOCKED_PENDING_REWARD
                        && f.granter.calls.isEmpty(),
                "force cannot delete or pay a legacy pending reward");
        check(f.service.syncPlayer(pending, TOMORROW) == DailyQuestSyncResult.BLOCKED_PENDING_REWARD
                        && f.state(pending).snapshot().get("check_ranks").status() == QuestStatus.REWARD_PENDING,
                "legacy definition remains available for a failed retry and protects rotation");
        check(f.service.syncPlayer(pending, TOMORROW) == DailyQuestSyncResult.ROTATED
                        && f.granter.calls.equals(List.of(pending, pending))
                        && !f.state(pending).snapshot().containsKey("check_ranks"),
                "successful pending payout unblocks rotation and legacy quest stays retired");
        f.quests.retryPendingRewards(pending);
        check(f.granter.calls.size() == 2, "legacy reward is not paid again");

        f.config.set(new DailyQuestConfig.State(true, 3, LocalTime.MIDNIGHT, "UTC",
                ZoneId.of("UTC"), List.of(definitions.getFirst())));
        UUID remaining = f.load(PlayerQuestState.of(List.of(active)),
                PlayerDailyQuestState.initialized(DailyQuestCycleId.parse("2026-09-22")));
        check(f.service.syncPlayer(remaining, TOMORROW) == DailyQuestSyncResult.CURRENT
                        && f.service.forcePlayer(remaining, TOMORROW) == DailyQuestSyncResult.NO_DEFINITIONS
                        && f.state(remaining).snapshot().equals(Map.of("check_ranks", active))
                        && f.cycle(remaining).equals("2026-09-22"),
                "an exclusively retired catalog keeps existing progress accessible after the reset");
    }

    private static void testSelectionPersistence() throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("vapeecore-selection-player-");
        try {
            var log = logger(); var repository = new dev.vapee.core.player.repository.FilePlayerRepository(directory, log);repository.initialize();
            var players = new PlayerService(repository, log); var registry = new QuestDefinitionRegistry();
            var definitions = List.of(new QuestDefinition("stored", "Stored", "Test", QuestProgressKey.of("test:stored"), 10, 5)); registry.replaceAll(definitions);
            int[] rewards = {0};
            var quests = new QuestService(registry, players, (id, amount, source, reason) -> { rewards[0]++;return new RewardResult(RewardStatus.SUCCESS, amount, OptionalLong.of(amount)); }, log);
            var config = new AtomicReference<>(new DailyQuestConfig.State(true, 4, LocalTime.MIDNIGHT, "UTC", ZoneId.of("UTC"), definitions));
            var service = new DailyQuestService(players, quests, registry, config::get, log);
            UUID id = UUID.randomUUID();players.loadPlayer(id, "SelectionPlayer");service.syncPlayer(id, TODAY);quests.addProgress(id, QuestProgressKey.of("test:stored"), 2);quests.flushAllOrThrow();
            var file = directory.resolve(id + ".yml");byte[] committed = java.nio.file.Files.readAllBytes(file);
            config.set(new DailyQuestConfig.State(true, 4, LocalTime.MIDNIGHT, "UTC", ZoneId.of("UTC"), definitions,
                    new DailyQuestSelection(Map.of("stored", new DailyQuestSelection.Quest(false, "movement", "walking")), 1L, 1L)));
            check(service.syncPlayer(id, TODAY) == DailyQuestSyncResult.CURRENT && java.util.Arrays.equals(committed, java.nio.file.Files.readAllBytes(file)),
                    "metadata update leaves existing player YAML bytes untouched without migration");
            players.unloadPlayer(id);players.loadPlayer(id, "SelectionPlayer");
            check(service.syncPlayer(id, TODAY) == DailyQuestSyncResult.CURRENT && players.getPlayer(id).orElseThrow().getQuestState().snapshot().get("stored").progress() == 2,
                    "persisted cycle and disabled assignment survive reload through the real file repository");
            quests.addProgress(id, QuestProgressKey.of("test:stored"), 8);quests.flushAllOrThrow();players.unloadPlayer(id);players.loadPlayer(id, "SelectionPlayer");
            quests.addProgress(id, QuestProgressKey.of("test:stored"), 100);
            check(rewards[0] == 1 && players.getPlayer(id).orElseThrow().getQuestState().snapshot().get("stored").status() == QuestStatus.COMPLETED,
                    "disabled completion persists and cannot pay twice after profile reload");
            var yaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file.toFile());
            check(yaml.getConfigurationSection("quests").getKeys(false).equals(java.util.Set.of("active", "daily"))
                            && !java.nio.file.Files.readString(file).contains("walking"), "selection metadata creates no player schema or persistence fields");
        } finally {
            try (var files = java.nio.file.Files.list(directory)) { for (var file : files.toList()) java.nio.file.Files.deleteIfExists(file); }
            java.nio.file.Files.deleteIfExists(directory);
        }
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
