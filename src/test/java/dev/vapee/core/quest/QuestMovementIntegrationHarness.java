package dev.vapee.core.quest;

import static dev.vapee.core.quest.QuestMovementTestSupport.*;

import dev.vapee.core.quest.daily.DailyQuestConfig;

import java.nio.file.Files;

/** Actual configurable definitions, quest transitions, economy, reward retry and borrowed player persistence. */
public final class QuestMovementIntegrationHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        configured();
        rewards();
        missing();
        assignmentIdentity();
        configuredFinalKeys();
        finalKeyRewards();
        finalDailyLifecycle();
        System.out.println("QuestMovementIntegrationHarness passed " + checks + " checks.");
    }

    private static void configured() throws Exception {
        var directory = Files.createTempDirectory("vapeecore-movement-yaml-");
        var file = directory.resolve("daily-quests.yml");
        String source =
                """
                config-version: 1
                enabled: true
                quests-per-day: 2
                reset: {time: '00:00', timezone: UTC}
                quests:
                  walk_100:
                    name: {en: Small Walk, de: Kleiner Spaziergang}
                    description: {en: Walk 100 blocks., de: Laufe 100 Blöcke.}
                    progress-key: movement:walk
                    target: 100
                    reward-coins: 10
                  walk_500:
                    name: {en: Long Walk, de: Langer Spaziergang}
                    description: {en: Walk 500 blocks., de: Laufe 500 Blöcke.}
                    progress-key: movement:walk
                    target: 500
                    reward-coins: 20
                  future:
                    name: Future integration
                    description: Not implemented by movement
                    progress-key: future:generic
                    target: 1
                    reward-coins: 3
                """;
        try {
            Files.writeString(file, source);
            Fixture f = new Fixture();
            Actor a = f.actor();
            var config = new DailyQuestConfig(file, f.domain.logger);
            config.initialize();
            f.domain.registry.replaceAll(config.getState().definitions());
            f.assign(a, "walk_100", "walk_500");
            for (int i = 0; i < 399; i++) f.step(a, 0.25);
            check(
                    f.progress(a, "walk_100") == 99 && f.coins(a) == 0,
                    "99.75 valid blocks do not prematurely complete a YAML target");
            f.step(a, 0.25);
            check(
                    f.status(a, "walk_100") == QuestStatus.COMPLETED && f.coins(a) == 10,
                    "the exact configured hundredth block grants the configured reward");
            check(
                    f.progress(a, "walk_500") == 100
                            && f.status(a, "walk_500") == QuestStatus.ACTIVE,
                    "second definition with the same key requires no second Java quest");
            for (int i = 0; i < 1600; i++) f.step(a, 0.25);
            check(
                    f.progress(a, "walk_500") == 500 && f.coins(a) == 30 && f.feedback.size() == 2,
                    "both configured targets complete exactly once through real RewardService");
            for (int i = 0; i < 100; i++) f.step(a, 1);
            check(
                    f.coins(a) == 30 && f.feedback.size() == 2 && f.producer.trackedPlayers() == 0,
                    "completed movement quests cannot farm rewards or maintain unnecessary samples");
            f.domain.quests.flushAllOrThrow();
            check(
                    f.domain.repository.persisted.get(a.id).get("walk_500").status()
                            == QuestStatus.COMPLETED,
                    "existing quest flush persists completed movement progress");
            check(
                    Files.readString(file).equals(source),
                    "reading definitions never inserts quests or rewrites operator YAML");
            f.assign(a, "future");
            f.step(a, 1);
            check(
                    f.progress(a, "future") == 0 && f.producer.trackedPlayers() == 0,
                    "future keys stay legal and are not falsely produced by movement");
            f.reporter.report(a.id, QuestProgressKey.of("future:generic"), 1);
            check(
                    f.status(a, "future") == QuestStatus.COMPLETED && f.coins(a) == 33,
                    "the existing generic progress integration remains open to other producers");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void rewards() {
        Fixture f = new Fixture();
        Actor a = f.actor();
        f.define("pending", QuestMovementProducer.WALK, 1, 7);
        f.assign(a, "pending");
        f.domain.economy.setCoins(a.id, Long.MAX_VALUE);
        f.step(a, 1);
        check(
                f.progress(a, "pending") == 1
                        && f.status(a, "pending") == QuestStatus.REWARD_PENDING
                        && f.coins(a) == Long.MAX_VALUE,
                "rejected coin overflow retains the existing pending-reward contract");
        int messages = f.feedback.size();
        f.step(a, 1);
        check(
                f.status(a, "pending") == QuestStatus.REWARD_PENDING
                        && f.feedback.size() == messages
                        && f.coins(a) == Long.MAX_VALUE,
                "repeated failed movement reward retry neither spams completion nor duplicates coins");
        f.domain.economy.setCoins(a.id, 0);
        f.step(a, 1);
        check(
                f.status(a, "pending") == QuestStatus.COMPLETED && f.coins(a) == 7,
                "a later valid progress unit delegates retry to the existing QuestService");
        f.step(a, 1);
        f.domain.quests.retryPendingRewards(a.id);
        check(
                f.coins(a) == 7 && f.feedback.size() == messages + 1,
                "completed reward is idempotent across later movement and explicit retry");
        f.domain.repository.fail = true;
        try {
            f.domain.quests.flushAllOrThrow();
            throw new AssertionError("completed reward flush should fail");
        } catch (IllegalStateException expected) {
            check(
                    f.domain.quests.isDirty(a.id)
                            && f.status(a, "pending") == QuestStatus.COMPLETED
                            && f.coins(a) == 7,
                    "failed deferred save retains completed quest and credited coins, rather than granting twice");
        }
        f.domain.rewards.flushAll();
        f.step(a, 1);
        check(
                f.coins(a) == 7,
                "movement after failed reward flush cannot repeat the completed grant");
        f.domain.repository.fail = false;
        f.domain.quests.flushAllOrThrow();
        f.domain.rewards.flushAll();
        f.define("dirty", QuestMovementProducer.WALK, 1000, 4);
        f.assign(a, "dirty");
        f.domain.quests.flushAllOrThrow();
        int saves = f.domain.repository.saves;
        for (int i = 0; i < 100; i++) f.step(a, 0.25);
        check(
                f.progress(a, "dirty") == 25 && f.domain.repository.saves == saves,
                "frequent samples do not add per-event repository writes");
        f.domain.repository.fail = true;
        try {
            f.domain.quests.flushAllOrThrow();
            throw new AssertionError("flush should fail");
        } catch (IllegalStateException expected) {
            check(
                    f.domain.quests.isDirty(a.id) && f.progress(a, "dirty") == 25,
                    "failed batch save retains authoritative dirty movement progress");
        }
        f.domain.repository.fail = false;
        f.domain.quests.flushAllOrThrow();
        check(
                !f.domain.quests.isDirty(a.id)
                        && f.domain.repository.persisted.get(a.id).get("dirty").progress() == 25,
                "later existing flush succeeds without a second progress store");
    }

    private static void missing() {
        Fixture f = new Fixture();
        Actor a = f.actor();
        f.define("walk", QuestMovementProducer.WALK, 10, 1);
        f.step(a, 0.8);
        check(
                f.producer.trackedPlayers() == 0,
                "no assignment means no retained distance or rotation baseline");
        f.assign(a, "walk");
        f.step(a, 0.2);
        check(
                f.progress(a, "walk") == 0,
                "pre-assignment travel cannot contribute a hidden fraction");
        var original = f.domain.players.getPlayer(a.id).orElseThrow();
        f.domain.players.unloadPlayer(a.id);
        f.step(a, 1);
        check(
                original.getQuestState().snapshot().get("walk").progress() == 0
                        && f.producer.trackedPlayers() == 0,
                "unloaded profiles receive no movement and clear temporary measurements");
        f.domain.players.loadPlayer(a.id, "Walker");
        f.step(a, 0.8);
        check(f.progress(a, "walk") == 0, "profile reload starts with a new fraction");
        f.step(a, 0.2);
        check(
                f.progress(a, "walk") == 1,
                "loaded profile can resume progress through the existing owner");
    }

    private static void assignmentIdentity() {
        Fixture f = new Fixture();
        Actor a = f.actor();
        f.define("walk", QuestMovementProducer.WALK, 100, 1);
        f.assign(a, "walk");
        Object token =
                f.domain.players.getPlayer(a.id).orElseThrow().getQuestState().assignmentToken();
        f.step(a, 0.6);
        f.domain.quests.flushAllOrThrow();
        f.step(a, 0.4);
        check(f.progress(a, "walk") == 1, "normal flush does not discard fractional distance");
        check(
                f.domain.players.getPlayer(a.id).orElseThrow().getQuestState().assignmentToken()
                        == token,
                "ordinary progress and persistence do not replace the assignment identity");
        f.step(a, 0.6);
        f.domain.quests.clearAssignments(a.id);
        f.assign(a, "walk");
        f.step(a, 0.4);
        check(
                f.progress(a, "walk") == 0,
                "clear and same-ID reassignment invalidate a previous fraction even at zero stored progress");
        f.step(a, 0.6);
        check(f.progress(a, "walk") == 1, "only new assignment travel reaches a whole block");
        f.define("sprint", QuestMovementProducer.SPRINT, 100, 1);
        f.domain.quests.assignQuest(a.id, "sprint");
        a.sprint = true;
        f.step(a, 1);
        check(
                f.progress(a, "walk") == 2 && f.progress(a, "sprint") == 1,
                "new concurrent key remains independently configurable");
    }

    private static void configuredFinalKeys() throws Exception {
        var directory = Files.createTempDirectory("vapeecore-final-movement-yaml-");
        var file = directory.resolve("daily-quests.yml");
        String source =
                """
                config-version: 1
                enabled: true
                quests-per-day: 3
                quests:
                  back_50:
                    name: {en: Backward Fifty, de: Fuenfzig rueckwaerts}
                    description: Configurable
                    progress-key: movement:backward
                    target: 50
                    reward-coins: 5
                  back_100:
                    name: Backward Hundred
                    description: Configurable
                    progress-key: movement:backward
                    target: 100
                    reward-coins: 10
                  toggle_20:
                    name: Sneak Activations
                    description: Configurable
                    progress-key: movement:sneak_toggle
                    target: 20
                    reward-coins: 20
                """;
        try {
            Files.writeString(file, source);
            Fixture f = new Fixture();
            Actor a = f.actor();
            var config = new DailyQuestConfig(file, f.domain.logger);
            config.initialize();
            f.domain.registry.replaceAll(config.getState().definitions());
            f.assign(a, "back_50", "back_100", "toggle_20");
            a.input = false;
            a.backward = true;
            for (int i = 0; i < 199; i++) f.move(a, 0, 0, -.25, 0);
            check(
                    f.progress(a, "back_50") == 49
                            && f.progress(a, "back_100") == 49
                            && f.coins(a) == 0,
                    "both YAML backward targets share one measured signal without rounding early");
            f.move(a, 0, 0, -.25, 0);
            check(
                    f.status(a, "back_50") == QuestStatus.COMPLETED
                            && f.progress(a, "back_100") == 50
                            && f.coins(a) == 5,
                    "configured fifty-block target completes independently");
            for (int i = 0; i < 50; i++) f.move(a, 0, 0, -1, 0);
            check(
                    f.status(a, "back_100") == QuestStatus.COMPLETED && f.coins(a) == 15,
                    "same producer completes configured hundred-block target once");
            for (int i = 1; i <= 20; i++) {
                f.tick += 10;
                f.toggle(a, true, false);
                f.toggle(a, false, false);
                check(
                        f.progress(a, "toggle_20") == i,
                        "each stationary activation advances YAML-owned toggle target");
            }
            check(
                    f.status(a, "toggle_20") == QuestStatus.COMPLETED
                            && f.coins(a) == 35
                            && f.feedback.size() == 3,
                    "all three reference shapes complete through existing reward and localized feedback");
            f.tick += 10;
            f.toggle(a, true, false);
            f.move(a, 0, 0, -1, 0);
            f.domain.quests.retryPendingRewards(a.id);
            check(
                    f.coins(a) == 35 && f.feedback.size() == 3 && f.producer.trackedPlayers() == 0,
                    "completed final keys never reward twice or retain unused samples");
            f.domain.quests.flushAllOrThrow();
            var saved = f.domain.repository.persisted.get(a.id);
            check(
                    saved.get("back_100").progress() == 100
                            && saved.get("toggle_20").progress() == 20,
                    "existing whole-profile flush retains both new key results");
            check(
                    Files.readString(file).equals(source),
                    "producer never rewrites configured quest definitions");
        } finally {
            Files.deleteIfExists(file);
            Files.deleteIfExists(directory);
        }
    }

    private static void finalKeyRewards() {
        for (var key :
                java.util.List.of(
                        QuestMovementProducer.BACKWARD, QuestMovementProducer.SNEAK_TOGGLE)) {
            Fixture f = new Fixture();
            Actor a = f.actor();
            a.input = false;
            a.backward = true;
            f.define("pending", key, 1, 7);
            f.assign(a, "pending");
            f.domain.economy.setCoins(a.id, Long.MAX_VALUE);
            finalUnit(f, a, key);
            check(
                    f.progress(a, "pending") == 1
                            && f.status(a, "pending") == QuestStatus.REWARD_PENDING
                            && f.coins(a) == Long.MAX_VALUE,
                    "new key delegates overflow to unchanged Pending Reward contract");
            int feedback = f.feedback.size();
            finalUnit(f, a, key);
            check(
                    f.status(a, "pending") == QuestStatus.REWARD_PENDING
                            && f.feedback.size() == feedback,
                    "failed retry does not duplicate completion feedback");
            f.domain.economy.setCoins(a.id, 0);
            finalUnit(f, a, key);
            check(
                    f.status(a, "pending") == QuestStatus.COMPLETED && f.coins(a) == 7,
                    "later valid event delegates successful reward retry");
            finalUnit(f, a, key);
            f.domain.quests.retryPendingRewards(a.id);
            check(
                    f.coins(a) == 7 && f.feedback.size() == feedback + 1,
                    "completed new-key payout is idempotent");
            f.domain.repository.fail = true;
            try {
                f.domain.quests.flushAllOrThrow();
                throw new AssertionError("flush accepted");
            } catch (IllegalStateException expected) {
                check(
                        f.domain.quests.isDirty(a.id) && f.coins(a) == 7,
                        "failed final-key save retains authoritative dirty state and one payout");
            }
            f.domain.repository.fail = false;
            f.domain.quests.flushAllOrThrow();
            check(
                    f.domain.repository.persisted.get(a.id).get("pending").status()
                            == QuestStatus.COMPLETED,
                    "normal retry flush persists completed final key");
            f.assign(a, "pending");
            finalUnit(f, a, key);
            check(
                    f.coins(a) == 14,
                    "fresh explicit assignment starts a fresh quest without duplicating old payout");
        }
    }

    private static void finalDailyLifecycle() {
        Fixture f = new Fixture();
        Actor a = f.actor();
        a.input = false;
        a.backward = true;
        f.define("back", QuestMovementProducer.BACKWARD, 100, 1);
        f.define("toggle", QuestMovementProducer.SNEAK_TOGGLE, 100, 1);
        var configuration =
                new dev.vapee.core.quest.daily.DailyQuestConfig.State(
                        true,
                        2,
                        java.time.LocalTime.MIDNIGHT,
                        "UTC",
                        java.time.ZoneId.of("UTC"),
                        f.domain.registry.snapshot());
        var daily =
                new dev.vapee.core.quest.daily.DailyQuestService(
                        f.domain.players,
                        f.domain.quests,
                        f.domain.registry,
                        () -> configuration,
                        f.domain.logger);
        var today = java.time.Instant.parse("2026-11-08T12:00:00Z");
        var tomorrow = today.plusSeconds(86400);
        check(
                daily.syncPlayer(a.id, today)
                        == dev.vapee.core.quest.daily.DailyQuestSyncResult.INITIALIZED,
                "actual DailyQuestService assigns both final keys");
        f.move(a, 0, 0, -.6, 0);
        f.toggle(a, true, false);
        daily.syncPlayer(a.id, today);
        f.move(a, 0, 0, -.4, 0);
        f.toggle(a, true, false);
        check(
                f.progress(a, "back") == 1 && f.progress(a, "toggle") == 1,
                "same-cycle Daily sync preserves valid fraction and duplicate guard");
        f.move(a, 0, 0, -.6, 0);
        check(
                daily.forcePlayer(a.id, today)
                        == dev.vapee.core.quest.daily.DailyQuestSyncResult.ROTATED,
                "actual Force reconciles compatible final-key assignments");
        f.move(a, 0, 0, -.4, 0);
        f.toggle(a, true, false);
        check(
                f.progress(a, "back") == 1 && f.progress(a, "toggle") == 1,
                "Force retains committed numeric progress but invalidates old observations");
        var state = f.domain.players.getPlayer(a.id).orElseThrow();
        var before = state.getQuestState().snapshot();
        var cycle = state.getDailyQuestState().cycleId();
        f.domain.repository.fail = true;
        check(
                daily.forcePlayer(a.id, tomorrow)
                                == dev.vapee.core.quest.daily.DailyQuestSyncResult.ASSIGNMENT_FAILED
                        && state.getQuestState().snapshot().equals(before)
                        && state.getDailyQuestState().cycleId().equals(cycle),
                "failed actual Force-save restores final-key state and previous cycle");
        f.domain.repository.fail = false;
        check(
                daily.syncPlayer(a.id, tomorrow)
                        == dev.vapee.core.quest.daily.DailyQuestSyncResult.ROTATED,
                "next daily cycle resets both YAML-owned final keys");
        f.move(a, 0, 0, -.6, 0);
        f.toggle(a, true, false);
        check(
                f.progress(a, "back") == 0 && f.progress(a, "toggle") == 0,
                "daily reset cannot carry fractional distance or recount already-active sneak");
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        f.move(a, 0, 0, -.4, 0);
        check(
                f.progress(a, "back") == 1 && f.progress(a, "toggle") == 1 && f.coins(a) == 0,
                "fresh post-reset travel and transition work without premature payout");
    }

    private static void finalUnit(Fixture f, Actor a, QuestProgressKey key) {
        if (key.equals(QuestMovementProducer.BACKWARD)) f.move(a, 0, 0, -1, 0);
        else {
            f.toggle(a, false, false);
            f.tick += 10;
            f.toggle(a, true, false);
        }
    }

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
}
