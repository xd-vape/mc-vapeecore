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

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
}
