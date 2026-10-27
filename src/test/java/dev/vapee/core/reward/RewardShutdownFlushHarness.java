package dev.vapee.core.reward;

import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.proxy;

import dev.vapee.core.onlinereward.OnlineRewardModule;
import dev.vapee.core.quest.QuestDefinition;
import dev.vapee.core.quest.QuestModule;
import dev.vapee.core.quest.QuestProgressKey;
import dev.vapee.core.quest.QuestStatus;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/**
 * Actual lifecycle, HandlerLists, owners and files; observations precede fixture safety teardown.
 */
public final class RewardShutdownFlushHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            switch (args[0]) {
                case "normal" -> normal();
                case "failure" -> failureAndRetry();
                case "strict" -> strictFailures();
                case "cancel" -> cancellation();
                case "listener" -> listeners();
                case "stale" -> stale();
                case "fallback" -> fallback();
                case "uncertain" -> uncertain();
                default -> throw new AssertionError(args[0]);
            }
        } else {
            normal();
            failureAndRetry();
            strictFailures();
            publicCompatibility();
            cancellation();
            listeners();
            stale();
            handoff();
            fallback();
            startup();
            consumers();
            uncertain();
        }
        System.out.println("RewardShutdownFlushHarness passed " + checks + " checks.");
    }

    private static void normal() throws Exception {
        try (var f = new RewardShutdownFixture()) {
            f.start();
            f.pair();
            var r = f.module.getRewardService();
            check(
                    f.tasks.size() == 1
                            && f.tasks.getFirst().period == 20
                            && f.tasks.getFirst().delay == 20
                            && f.tasks.getFirst().isSync(),
                    "one unchanged synchronous 20 tick batch");
            check(f.rewardHandlers() == 1, "one actual LOWEST quit listener");
            f.module.disable();
            check(
                    failure(f.playerOwner::getPlayerService) == null && f.players.isLoaded(f.a),
                    "Reward preserves borrowed Player owner and cache");
            check(f.repository.attempts.isEmpty(), "clean shutdown writes no profiles");
            released(f);
            f.module.enable();
            r = f.module.getRewardService();
            r.grantCoins(f.a, 4, RewardSource.SYSTEM, "aggregate:first");
            r.grantCoins(f.a, 7, RewardSource.SYSTEM, "aggregate:second");
            check(r.isDirty(f.a) && f.stored(f.a) == 0, "grants remain deferred");
            f.module.disable();
            check(
                    f.repository.attempts.equals(List.of(f.a)) && f.stored(f.a) == 11,
                    "aggregated shutdown grant saves exactly once");
            check(
                    r.dirtyPlayerCount() == 0 && f.cold(f.a).getWallet().getCoins() == 11,
                    "completed commit acknowledged and independently reloaded");
            released(f);
            int n = f.repository.attempts.size(), c = f.tasks.getLast().cancellations;
            f.module.disable();
            check(
                    f.repository.attempts.size() == n && f.tasks.getLast().cancellations == c,
                    "repeated complete disable inert");
        }
    }

    private static void failureAndRetry() throws Exception {
        try (var f = new RewardShutdownFixture()) {
            f.start();
            f.pair();
            var r = f.module.getRewardService();
            var aggregate = f.players.getPlayer(f.a).orElseThrow();
            r.grantCoins(f.a, 11, RewardSource.SYSTEM, "failure:A");
            r.grantCoins(f.b, 22, RewardSource.SYSTEM, "failure:B");
            byte[] original = f.bytes(f.a);
            var saveFailure = f.repository.fail(f.a);
            RuntimeException error = failure(f.module::disable);
            check(error != null, "ordinary failed shutdown reported incomplete");
            check(
                    contains(error, saveFailure),
                    "original save cause retained through module aggregate");
            check(
                    r.isDirty(f.a) && !r.isDirty(f.b),
                    "failed UUID retained and successful UUID clean");
            check(
                    f.repository.attempts.equals(List.of(f.a, f.b)),
                    "every snapshot UUID attempted once");
            check(
                    Arrays.equals(original, f.bytes(f.a)) && f.stored(f.b) == 22,
                    "failed target unchanged and independent B committed");
            check(
                    aggregate.getWallet().getCoins() == 11
                            && f.players.getPlayer(f.a).orElseThrow() == aggregate,
                    "original wallet and aggregate retained");
            check(failure(f.module::getRewardService) != null, "pending getter closed");
            Object pending = get(f.module, "pendingCleanup");
            check(
                    pending != null
                            && get(pending, "service") == r
                            && Boolean.TRUE.equals(get(pending, "flushPending")),
                    "exact original service and flush obligation retained");
            check(
                    f.rewardHandlers() == 0 && f.running() == 0,
                    "failed flush does not skip independent resource release");
            check(
                    failure(f.module::enable) != null
                            && f.tasks.size() == 1
                            && get(f.module, "pendingCleanup") == pending,
                    "pending enable rejects without new resource acquisition or lost state");
            f.repository.failures.clear();
            int n = f.repository.attempts.size();
            f.module.disable();
            check(
                    f.repository
                            .attempts
                            .subList(n, f.repository.attempts.size())
                            .equals(List.of(f.a)),
                    "inactive retry attempts only unresolved UUID");
            check(
                    f.stored(f.a) == 11 && aggregate.getWallet().getCoins() == 11,
                    "retry saves without re-granting coins");
            check(
                    !r.isDirty(f.a) && f.cold(f.a).getWallet().getCoins() == 11,
                    "repaired state agrees with cold repository read");
            released(f);
            f.module.enable();
            check(
                    f.module.getRewardService() != r && f.tasks.size() == 2,
                    "fresh activation only after completed cleanup");
            f.module.disable();
            released(f);
        }
    }

    private static void strictFailures() throws Exception {
        try (var f = new RewardShutdownFixture()) {
            f.start();
            f.pair();
            UUID c = UUID.fromString("65c00000-0000-0000-0000-000000000003");
            f.load(c);
            var r = f.module.getRewardService();
            for (UUID id : List.of(f.a, f.b, c))
                r.grantCoins(id, 11, RewardSource.SYSTEM, "strict:batch");
            var a = f.repository.fail(f.a);
            var b = f.repository.fail(f.b);
            int[] diagnostics = {0};
            f.runtime.logger.addHandler(
                    new Handler() {
                        public void publish(LogRecord record) {
                            if (record.getLevel().intValue() >= 900) {
                                diagnostics[0]++;
                                throw new IllegalStateException("diagnostic refused");
                            }
                        }

                        public void flush() {}

                        public void close() {}
                    });
            RuntimeException error = failure(r::flushAllOrThrow);
            check(
                    f.repository.attempts.equals(List.of(f.a, f.b, c)) && f.stored(c) == 11,
                    "strict failure cannot skip independent profile");
            check(error instanceof RewardService.FlushFailures, "typed strict batch failure");
            var failures = ((RewardService.FlushFailures) error).failures();
            check(
                    failures.equals(Map.of(f.a, a, f.b, b)),
                    "exact failed UUID to original cause mapping");
            check(
                    failure(() -> failures.put(c, a)) instanceof UnsupportedOperationException,
                    "failure mapping immutable");
            check(
                    contains(error, a) && contains(error, b),
                    "all original causes present in exception chain");
            check(
                    r.isDirty(f.a) && r.isDirty(f.b) && !r.isDirty(c),
                    "strict batch retains only failed dirty markers");
            check(diagnostics[0] == 0, "strict save loop independent of throwing WARNING logger");
            f.repository.failures.clear();
            f.module.disable();
            released(f);
        }
    }

    private static void publicCompatibility() throws Exception {
        try (var f = new RewardShutdownFixture()) {
            f.start();
            f.pair();
            var r = f.module.getRewardService();
            r.grantCoins(f.a, 11, RewardSource.SYSTEM, "public:A");
            r.grantCoins(f.b, 22, RewardSource.SYSTEM, "public:B");
            f.repository.fail(f.a);
            check(
                    failure(r::flushAll) == null && r.isDirty(f.a) && f.stored(f.b) == 22,
                    "public normal batch remains log-and-continue");
            check(
                    f.logs.stream()
                            .anyMatch(
                                    log ->
                                            log.getMessage()
                                                    .contains("remains dirty for a later retry")),
                    "public failed save keeps existing warning");
            f.repository.failures.clear();
            f.tasks.getFirst().callback.run();
            check(f.stored(f.a) == 11, "active task retains normal batch behavior");
            int n = f.repository.attempts.size();
            f.tasks.getFirst().callback.run();
            check(
                    n == f.repository.attempts.size(),
                    "unchanged repeat callback saves and grants nothing");
            f.module.disable();
            released(f);
        }
    }

    private static void cancellation() throws Exception {
        for (int mode = 0; mode < 4; mode++)
            try (var f = new RewardShutdownFixture()) {
                f.start();
                f.pair();
                var r = f.module.getRewardService();
                r.grantCoins(f.a, 11, RewardSource.SYSTEM, "cancel");
                var task = f.tasks.getFirst();
                task.refuse = mode == 0;
                task.afterEffect = mode == 1;
                task.ineffective = mode == 2;
                task.unknownStatus = mode == 3;
                task.duringCancel =
                        () -> {
                            check(
                                    failure(f.module::getRewardService) != null,
                                    "getter revoked before cancellation IO");
                            check(
                                    failure(f.module::enable) != null,
                                    "enable during cleanup rejected before acquisition");
                            check(
                                    failure(f.module::disable) != null,
                                    "reentrant disable cannot replace cleanup state");
                        };
                RuntimeException error = failure(f.module::disable);
                check(error != null, "unconfirmed or throwing cancellation reported");
                check(
                        f.stored(f.a) == 11 && !r.isDirty(f.a),
                        "cancel failure cannot skip strict flush");
                check(f.rewardHandlers() == 0, "cancel failure cannot skip listener release");
                if (mode != 1) {
                    Object pending = get(f.module, "pendingCleanup");
                    check(
                            pending != null && get(pending, "task") == task,
                            "exact unconfirmed cancellation handle retained");
                    check(
                            failure(f.module::enable) != null && f.tasks.size() == 1,
                            "unresolved cancellation blocks acquisition");
                } else
                    check(
                            get(f.module, "pendingCleanup") == null && task.cancelled,
                            "confirmed after-effect cancellation released despite reported"
                                    + " exception");
                int n = f.repository.attempts.size();
                task.callback.run();
                check(n == f.repository.attempts.size(), "retired physical callback inert");
                task.refuse = false;
                task.ineffective = false;
                task.unknownStatus = false;
                f.module.disable();
                check(
                        n == f.repository.attempts.size(),
                        "resource retry does not repeat completed flush");
                released(f);
            }
    }

    private static void listeners() throws Exception {
        for (boolean after : List.of(false, true))
            try (var f = new RewardShutdownFixture()) {
                f.start();
                f.pair();
                var r = f.module.getRewardService();
                r.grantCoins(f.a, 11, RewardSource.SYSTEM, "listener");
                var listener = f.listener();
                var refusal = f.refuseListener(after);
                RuntimeException error = failure(f.module::disable);
                check(
                        error != null && contains(error, refusal.failure),
                        "unregister original cause retained");
                check(
                        f.stored(f.a) == 11 && !r.isDirty(f.a),
                        "listener failure cannot skip completed strict flush");
                check(f.running() == 0, "listener failure cannot skip task cancellation");
                check(
                        (get(f.module, "pendingCleanup") != null) == !after,
                        "actual listener state confirms after-effect release");
                r.grantCoins(f.a, 3, RewardSource.SYSTEM, "captured:old-reference");
                int n = f.repository.attempts.size();
                listener.onPlayerQuit(
                        new PlayerQuitEvent(
                                f.actor(f.a), net.kyori.adventure.text.Component.empty()));
                check(
                        f.repository.attempts.size() == n && r.isDirty(f.a),
                        "retired listener cannot flush captured old service");
                refusal.refuse = false;
                f.module.disable();
                released(f);
            }
        try (var f = new RewardShutdownFixture()) {
            f.start();
            f.pair();
            var r = f.module.getRewardService();
            r.grantCoins(f.a, 11, RewardSource.SYSTEM, "both-fail");
            f.repository.fail(f.a);
            var refusal = f.refuseListener(false);
            check(
                    failure(f.module::disable) != null && r.isDirty(f.a) && f.rewardHandlers() > 0,
                    "unregister refusal cannot erase failed dirty work");
            f.repository.failures.clear();
            int n = f.repository.attempts.size();
            f.listener()
                    .onPlayerQuit(
                            new PlayerQuitEvent(
                                    f.actor(f.a), net.kyori.adventure.text.Component.empty()));
            check(
                    n == f.repository.attempts.size() && f.stored(f.a) == 0,
                    "pending dirty work requires explicit retry not stale event");
            refusal.refuse = false;
            f.module.disable();
            check(f.stored(f.a) == 11, "explicit combined listener and flush retry");
            released(f);
        }
    }

    private static void stale() throws Exception {
        try (var f = new RewardShutdownFixture()) {
            f.start();
            f.pair();
            var oldTask = f.tasks.getFirst();
            var oldListener = f.listener();
            f.module.disable();
            f.module.enable();
            var fresh = f.module.getRewardService();
            fresh.grantCoins(f.a, 11, RewardSource.SYSTEM, "fresh");
            int[] reads = {0};
            Player actor =
                    proxy(
                            Player.class,
                            (m, a) -> {
                                reads[0]++;
                                throw new AssertionError("retired actor IO " + m);
                            });
            oldTask.callback.run();
            oldListener.onPlayerQuit(
                    new PlayerQuitEvent(actor, net.kyori.adventure.text.Component.empty()));
            check(
                    reads[0] == 0 && f.repository.attempts.isEmpty() && fresh.isDirty(f.a),
                    "old task and listener isolated from replacement runtime");
            f.tasks.getLast().callback.run();
            check(f.stored(f.a) == 11 && !fresh.isDirty(f.a), "current generation still flushes");
            f.module.disable();
            released(f);
        }
    }

    private static void handoff() throws Exception {
        try (var f = new RewardShutdownFixture()) {
            f.start();
            f.pair();
            var r = f.module.getRewardService();
            r.grantCoins(f.a, 11, RewardSource.SYSTEM, "handoff");
            f.players.unloadPlayer(f.a);
            int n = f.repository.attempts.size();
            f.module.disable();
            check(
                    f.stored(f.a) == 11 && !r.isDirty(f.a) && f.repository.attempts.size() == n,
                    "successful save-before-eviction handoff has no duplicate write");
            released(f);
        }
        try (var f = new RewardShutdownFixture()) {
            f.start();
            f.pair();
            var r = f.module.getRewardService();
            r.grantCoins(f.a, 11, RewardSource.SYSTEM, "unload-failure");
            var cause = f.repository.fail(f.a);
            check(
                    failure(() -> f.players.unloadPlayer(f.a)) == cause && f.players.isLoaded(f.a),
                    "failed unload keeps original aggregate");
            check(
                    failure(f.module::disable) != null && r.isDirty(f.a) && f.stored(f.a) == 0,
                    "failed unload cannot fake persistence handoff");
            f.repository.failures.clear();
            f.module.disable();
            released(f);
        }
    }

    private static void fallback() throws Exception {
        for (boolean permanent : List.of(false, true))
            try (var f = new RewardShutdownFixture()) {
                f.manager.enableAll();
                f.attachRepository();
                f.pair();
                var r = f.module.getRewardService();
                var original = f.players.getPlayer(f.a).orElseThrow();
                r.grantCoins(f.a, 11, RewardSource.SYSTEM, "fallback:A");
                r.grantCoins(f.b, 22, RewardSource.SYSTEM, "fallback:B");
                f.repository.fail(f.a);
                if (!permanent)
                    f.runtime.logger.addHandler(
                            new Handler() {
                                public void publish(LogRecord record) {
                                    if (record.getMessage().equals("Disabling module Economy."))
                                        f.repository.failures.clear();
                                }

                                public void flush() {}

                                public void close() {}
                            });
                f.manager.disableAll();
                check(
                        f.manager.getEnabledModules().isEmpty() && f.stored(f.b) == 22,
                        "manager retains reverse shutdown and independent Player fallback");
                check(
                        get(f.module, "pendingCleanup") != null && r.isDirty(f.a),
                        "Reward pending remains distinct from possible Player commit");
                int n = f.repository.attempts.size();
                f.manager.disableAll();
                check(
                        n == f.repository.attempts.size(),
                        "ordinary manager does not automatically retry Reward");
                if (permanent) {
                    check(
                            f.stored(f.a) == 0
                                    && f.players.getPlayer(f.a).orElseThrow() == original
                                    && get(f.playerOwner, "pendingCleanup") != null,
                            "double failure retains original Player aggregate over stale disk");
                    f.repository.failures.clear();
                    f.module.disable();
                    f.playerOwner.disable();
                    check(
                            original.getWallet().getCoins() == 11 && !f.players.isLoaded(f.a),
                            "consumer-first explicit retry preserves wallet and releases original"
                                    + " owner");
                } else {
                    check(
                            f.stored(f.a) == 11 && !f.players.isLoaded(f.a),
                            "successful Player fallback persisted same aggregate");
                    f.module.disable();
                    check(
                            n == f.repository.attempts.size(),
                            "completed Player owner handoff acknowledges dirty work without"
                                    + " reconstruction");
                }
                check(
                        f.cold(f.a).getWallet().getCoins() == 11,
                        "independent cold aggregate agrees after recovery");
                released(f);
            }
    }

    private static void startup() throws Exception {
        RuntimeException original = new IllegalStateException("original startup failure");
        boolean[] fail = {true};
        try (var f =
                new RewardShutdownFixture(
                        stage -> {
                            if (fail[0] && stage == RewardModule.EnableStage.RUNTIME_PUBLISHED)
                                throw original;
                        })) {
            f.enableOwners();
            f.pair();
            check(
                    failure(f.module::enable) == original,
                    "startup rollback preserves original exception");
            check(
                    f.repository.attempts.isEmpty() && f.rewardHandlers() == 0 && f.running() == 0,
                    "startup rollback never invents Reward persistence");
            released(f);
            fail[0] = false;
            boolean[] rejected = {false};
            f.afterSchedule =
                    () -> {
                        rejected[0] = failure(f.module::enable) != null;
                    };
            f.module.enable();
            check(
                    rejected[0] && f.tasks.size() == 2 && f.rewardHandlers() == 1,
                    "reentrant enable rejected before another acquisition");
            var service = f.module.getRewardService();
            check(
                    failure(f.module::enable) != null && f.module.getRewardService() == service,
                    "duplicate enable preserves active service");
            f.module.disable();
            released(f);
        }
    }

    private static void consumers() throws Exception {
        try (var f = new RewardShutdownFixture()) {
            f.start();
            f.pair();
            var q = new QuestModule(f.runtime.plugin, f.playerOwner, f.module, f.runtime.messages);
            q.enable();
            q.getDefinitionRegistry()
                    .replaceAll(
                            List.of(
                                    new QuestDefinition(
                                            "audit",
                                            "Audit",
                                            "Audit",
                                            QuestProgressKey.of("playtime:minute"),
                                            2,
                                            5)));
            q.getQuestService().assignQuest(f.a, "audit");
            q.getQuestService().addProgress(f.a, QuestProgressKey.of("playtime:minute"), 2);
            q.disable();
            var online =
                    new OnlineRewardModule(
                            f.runtime.plugin,
                            f.config,
                            f.playerOwner,
                            f.module,
                            f.runtime.messages);
            online.enable();
            online.getOnlineRewardService().process(f.a, 0);
            online.getOnlineRewardService().process(f.a, 1200);
            online.disable();
            f.module.disable();
            var saved = f.cold(f.a);
            check(
                    saved.getWallet().getCoins() == 12,
                    "real Quest and OnlineReward keep configured grant contracts");
            check(
                    saved.getQuestState().snapshot().get("audit").status() == QuestStatus.COMPLETED
                            && saved.getOnlineRewardProgress()
                                            .getProcessedPlaytimeTicks()
                                            .orElseThrow()
                                    == 1200,
                    "same aggregate preserves Quest completion and online baseline");
            released(f);
        }
    }

    private static void uncertain() throws Exception {
        try (var f = new RewardShutdownFixture()) {
            f.start();
            f.pair();
            var r = f.module.getRewardService();
            r.grantCoins(f.a, 11, RewardSource.SYSTEM, "uncertain");
            var cause = f.repository.fail(f.a);
            f.repository.afterEffect.add(f.a);
            RuntimeException error = failure(f.module::disable);
            check(
                    error != null && contains(error, cause) && r.isDirty(f.a),
                    "after-commit failure retains uncertain work and original cause");
            check(
                    f.stored(f.a) == 11 && f.repository.successes.equals(List.of(f.a)),
                    "physical commit independently observed despite failed return");
            check(
                    error.getMessage().contains("uncertain"),
                    "incomplete result does not assert proven uncommitted write");
            f.repository.failures.clear();
            f.module.disable();
            check(
                    f.stored(f.a) == 11
                            && f.players.getPlayer(f.a).orElseThrow().getWallet().getCoins() == 11,
                    "uncertain retry repeats persistence without monetary mutation");
            released(f);
        }
    }

    private static void released(RewardShutdownFixture f) {
        check(
                get(f.module, "pendingCleanup") == null,
                "all known Reward cleanup obligations released");
        check(f.rewardHandlers() == 0, "owned Reward listener released");
        check(f.running() == 0, "owned tasks cancelled");
        check(failure(f.module::getRewardService) != null, "retired getter unavailable");
    }

    private static RuntimeException failure(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static boolean contains(Throwable root, Throwable target) {
        if (root == null) return false;
        if (root == target) return true;
        if (contains(root.getCause(), target)) return true;
        for (Throwable s : root.getSuppressed()) if (contains(s, target)) return true;
        return false;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
