package dev.vapee.core.quest;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.economy.EconomyModule;
import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerListener;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.repository.FilePlayerRepository;
import dev.vapee.core.reward.RewardModule;

import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/** Real Quest lifecycle and file commits, including failures before fixture safety teardown. */
public final class QuestModuleLifecycleHarness {
    private static int checks;
    private static final QuestProgressKey PLAYTIME = QuestProgressKey.of("playtime:minute");

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            switch (args[0]) {
                case "late-log" -> lateLog();
                case "duplicate" -> duplicate();
                case "reentrant" -> reentrant();
                case "stale-task" -> staleTask();
                case "cancel" -> cancellation();
                case "unregister" -> unregister();
                case "failed-save" -> failedSaves();
                case "stale-listener" -> staleListener();
                case "getters" -> transitions();
                case "final-sample" -> finalSample();
                default -> throw new IllegalArgumentException(args[0]);
            }
        } else {
            normal();
            missing();
            registration();
            scheduling();
            lateLog();
            duplicate();
            reentrant();
            cancellation();
            unregister();
            staleTask();
            staleListener();
            transitions();
            finalSample();
            failedSaves();
            sampleFailures();
            outerFlush();
            interruptedRead();
            pendingReward();
            quit();
            ownersAndFallback();
            managerRollback();
            producerExceptionBoundary();
        }
        System.out.println("QuestModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static void normal() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            unavailable(f.module);
            var actor = f.actor("Normal", 1199);
            f.module.enable();
            check(
                    f.handlers() == 2 && f.tasks.size() == 1 && f.activeTasks() == 1,
                    "one real join/quit listener and one shared task");
            var task = f.tasks.getFirst();
            check(
                    task.owner == f.runtime.plugin
                            && task.isSync()
                            && task.delay == 100
                            && task.period == 100,
                    "exact synchronous hundred-tick initial delay and period");
            f.define("normal", 5, 7);
            QuestService service = f.module.getQuestService();
            service.assignQuest(actor.id, "normal");
            actor.ticks = 1200;
            task.action.run();
            check(
                    f.progress(actor, "normal").progress() == 1
                            && f.stored(actor).getQuestState().snapshot().get("normal").progress()
                                    == 1
                            && !service.isDirty(actor.id),
                    "active scheduled callback samples and commits partial progress");
            check(
                    f.runtime.sentMessages == 0 && f.core(actor).getWallet().getCoins() == 0,
                    "partial progress grants no coins or completion feedback");
            f.module.disable();
            released(f);
            int cancellations = task.cancellations;
            int saves = f.repository.attempts.values().stream().mapToInt(Integer::intValue).sum();
            f.module.disable();
            check(
                    task.cancellations == cancellations
                            && saves
                                    == f.repository.attempts.values().stream()
                                            .mapToInt(Integer::intValue)
                                            .sum(),
                    "successful repeated disable has no resource or persistence effects");
        }
    }

    private static void missing() throws Exception {
        for (boolean player : List.of(true, false))
            try (var f = new QuestLifecycleFixture()) {
                ModuleRuntimeFixture.set(
                        player ? f.playerOwner : f.rewardOwner,
                        player ? "playerService" : "rewardService",
                        null);
                failure(f.module::enable);
                unavailable(f.module);
                check(
                        f.handlers() == 0 && f.tasks.isEmpty(),
                        "missing borrowed dependency acquires no external resource");
            }
    }

    private static void registration() throws Exception {
        for (boolean after : List.of(false, true))
            try (var f = new QuestLifecycleFixture()) {
                f.refuseRegistrationBefore = !after;
                f.refuseRegistrationAfter = after;
                check(
                        failure(f.module::enable) == f.registrationFailure,
                        "original registration exception identity preserved");
                released(f);
                check(f.tasks.isEmpty(), "registration failure schedules nothing");
            }
        try (var f = new QuestLifecycleFixture()) {
            f.afterRegistration = () -> f.refuseUnregister();
            f.refuseRegistrationAfter = true;
            var thrown = failure(f.module::enable);
            check(
                    thrown == f.registrationFailure
                            && contains(thrown, f.refusingLists.getFirst().failure),
                    "refused partial listener rollback is suppressed on original registration"
                            + " exception");
            check(
                    f.handlers() == 1 && pending(f) != null,
                    "unconfirmed partial listener ownership remains retained");
            unavailable(f.module);
            var actor = f.actor("Orphan", 0);
            int reads = actor.reads;
            f.listener().onPlayerJoin(join(actor));
            f.listener().onPlayerQuit(quit(actor));
            check(
                    actor.reads == reads && f.repository.attempts.isEmpty(),
                    "partially registered inactive listener cannot seed sample or flush");
            f.refusingLists.getFirst().refuse = false;
            f.module.disable();
            released(f);
        }
    }

    private static void scheduling() throws Exception {
        for (boolean noHandle : List.of(false, true))
            try (var f = new QuestLifecycleFixture()) {
                var actor = f.actor("Scheduler", 0);
                if (noHandle)
                    f.afterSchedule =
                            () -> {
                                throw f.schedulingFailure;
                            };
                else f.refuseScheduling = true;
                check(
                        failure(f.module::enable) == f.schedulingFailure,
                        "scheduler failure identity preserved");
                unavailable(f.module);
                check(f.handlers() == 0, "scheduler failure removes known listener");
                if (noHandle) {
                    check(
                            f.activeTasks() == 1 && f.tasks.getFirst().cancellations == 0,
                            "unreturned handle has no exact cancellation ownership");
                    int reads = actor.reads;
                    f.tasks.getFirst().action.run();
                    check(
                            actor.reads == reads,
                            "unknown-handle callback remains inert after failed startup");
                } else check(f.tasks.isEmpty(), "pre-registration schedule failure owns no task");
            }
    }

    private static void lateLog() throws Exception {
        for (boolean refuse : List.of(false, true))
            try (var f = new QuestLifecycleFixture()) {
                var actor = f.actor("Late", 0);
                RuntimeException original = new IllegalStateException("final Quest logger");
                QuestPlaytimeProducer[] captured = {null};
                f.afterRegistration =
                        () ->
                                captured[0] =
                                        (QuestPlaytimeProducer)
                                                ModuleRuntimeFixture.get(f.listener(), "playtime");
                Handler logger =
                        handler(
                                record -> {
                                    if (record.getMessage().startsWith("Quest module enabled")) {
                                        unavailable(f.module);
                                        if (refuse) {
                                            f.tasks.getFirst().refuse = true;
                                            f.refuseUnregister();
                                        }
                                        f.tasks.getFirst().action.run();
                                        throw original;
                                    }
                                });
                f.runtime.logger.addHandler(logger);
                check(
                        failure(f.module::enable) == original,
                        "original late startup exception preserved by identity");
                check(
                        f.tasks.getFirst().cancellations == 1,
                        "late logging failure rolls back acquired task");
                check(
                        f.activeTasks() == (refuse ? 1 : 0) && f.handlers() == (refuse ? 1 : 0),
                        "late logging failure reconciles actual task and listener ownership");
                unavailable(f.module);
                check(
                        captured[0].trackedPlayers() == 0
                                && f.core(actor).getQuestState().isEmpty()
                                && f.core(actor).getWallet().getCoins() == 0
                                && f.repository.attempts.isEmpty()
                                && f.runtime.sentMessages == 0,
                        "failed startup clears samples without inventing rewards feedback or flush"
                                + " work");
                if (refuse)
                    check(
                            contains(original, f.tasks.getFirst().cancelFailure)
                                    && contains(original, f.refusingLists.getFirst().failure),
                            "independent startup cleanup errors remain suppressed diagnostics");
                f.runtime.logger.removeHandler(logger);
                f.tasks.getFirst().refuse = false;
                f.refusingLists.forEach(list -> list.refuse = false);
                f.module.disable();
                released(f);
            }
    }

    private static void duplicate() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            f.module.enable();
            var service = f.module.getQuestService();
            var registry = f.module.getDefinitionRegistry();
            RuntimeException error = null;
            try {
                f.module.enable();
            } catch (RuntimeException expected) {
                error = expected;
            }
            check(
                    error != null
                            && f.module.getQuestService() == service
                            && f.module.getDefinitionRegistry() == registry
                            && f.handlers() == 2
                            && f.tasks.size() == 1,
                    "duplicate enable preserves active runtime and its single registrations");
            f.module.disable();
        }
    }

    private static void reentrant() throws Exception {
        for (String point : List.of("registration", "schedule", "logger"))
            try (var f = new QuestLifecycleFixture()) {
                Runnable reentry =
                        () -> {
                            RuntimeException error = null;
                            try {
                                f.module.enable();
                            } catch (RuntimeException expected) {
                                error = expected;
                            }
                            check(
                                    error != null && f.tasks.size() <= 1,
                                    "reentrant enable acquires no second runtime");
                            unavailable(f.module);
                        };
                if (point.equals("registration"))
                    f.afterRegistration =
                            () -> {
                                f.afterRegistration = () -> {};
                                reentry.run();
                            };
                if (point.equals("schedule"))
                    f.afterSchedule =
                            () -> {
                                f.afterSchedule = () -> {};
                                reentry.run();
                            };
                if (point.equals("logger"))
                    f.runtime.logger.addHandler(
                            handler(
                                    record -> {
                                        if (record.getMessage().startsWith("Quest module enabled"))
                                            reentry.run();
                                    }));
                f.module.enable();
                check(
                        f.handlers() == 2 && f.tasks.size() == 1,
                        "outer startup remains valid after rejected reentry");
                f.module.disable();
                released(f);
            }
    }

    private static void cancellation() throws Exception {
        for (String mode : List.of("before", "after", "silent", "unknown"))
            try (var f = new QuestLifecycleFixture()) {
                var actor = f.actor("Cancel", 0);
                f.module.enable();
                f.define("cancel", 10, 7);
                var service = f.module.getQuestService();
                service.assignQuest(actor.id, "cancel");
                service.addProgress(actor.id, PLAYTIME, 2);
                var task = f.tasks.getFirst();
                task.refuse = mode.equals("before");
                task.afterEffect = mode.equals("after");
                task.silent = mode.equals("silent");
                task.unknownStatus = mode.equals("unknown");
                RuntimeException error = failure(f.module::disable);
                check(
                        error.getSuppressed().length > 0,
                        "unconfirmed or throwing cancellation is reported");
                check(
                        f.stored(actor).getQuestState().snapshot().get("cancel") != null
                                && f.stored(actor)
                                                .getQuestState()
                                                .snapshot()
                                                .get("cancel")
                                                .progress()
                                        == 2
                                && !service.isDirty(actor.id),
                        "cancel refusal cannot skip independent final Quest flush");
                check(f.handlers() == 0, "cancellation failure cannot skip listener release");
                unavailable(f.module);
                int reads = actor.reads;
                task.action.run();
                check(actor.reads == reads, "refused task callback is inert after shutdown");
                if (!mode.equals("after")) {
                    check(
                            pending(f) != null,
                            "unconfirmed cancellation retains exact owned resources");
                    failure(f.module::enable);
                    check(f.tasks.size() == 1, "pending cleanup blocks fresh acquisition");
                } else
                    check(
                            pending(f) == null && task.cancelled,
                            "confirmed after-effect cancellation releases handle while reporting"
                                    + " error");
                task.refuse = false;
                task.afterEffect = false;
                task.silent = false;
                task.unknownStatus = false;
                f.module.disable();
                released(f);
                f.module.enable();
                check(
                        f.module.getQuestService() != service
                                && f.tasks.size() == 2
                                && f.activeTasks() == 1,
                        "successful cleanup retry permits one fresh runtime");
                f.module.disable();
                released(f);
            }
    }

    private static void unregister() throws Exception {
        for (boolean after : List.of(false, true))
            try (var f = new QuestLifecycleFixture()) {
                var actor = f.actor("Unregister", 0);
                f.module.enable();
                f.define("unregister", 10, 7);
                var service = f.module.getQuestService();
                service.assignQuest(actor.id, "unregister");
                service.addProgress(actor.id, PLAYTIME, 2);
                var refusal = f.refuseUnregister();
                refusal.refuse = !after;
                refusal.throwAfterEffect = after;
                check(
                        contains(failure(f.module::disable), refusal.failure),
                        "listener cancellation failure retains original diagnostic");
                check(
                        f.stored(actor).getQuestState().snapshot().get("unregister") != null
                                && f.stored(actor)
                                                .getQuestState()
                                                .snapshot()
                                                .get("unregister")
                                                .progress()
                                        == 2
                                && f.activeTasks() == 0,
                        "listener refusal cannot skip final Quest flush or task cancellation");
                unavailable(f.module);
                if (!after)
                    check(
                            f.handlers() == 1 && pending(f) != null,
                            "refused unregister stays owned and retryable");
                else
                    check(
                            f.handlers() == 0 && pending(f) == null,
                            "confirmed after-effect unregister releases exact listener");
                refusal.refuse = false;
                refusal.throwAfterEffect = false;
                f.module.disable();
                released(f);
            }
    }

    private static void staleTask() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            var actor = f.actor("StaleTask", 0);
            f.module.enable();
            var service = f.module.getQuestService();
            var registry = f.module.getDefinitionRegistry();
            var listener = f.listener();
            var task = f.tasks.getFirst();
            f.module.disable();
            int reads = actor.reads;
            task.action.run();
            check(actor.reads == reads, "retired task does not sample after disable");
            f.module.enable();
            f.define("fresh", 10, 7);
            f.module.getQuestService().assignQuest(actor.id, "fresh");
            check(
                    f.module.getQuestService() != service
                            && f.module.getDefinitionRegistry() != registry
                            && f.listener() != listener,
                    "completed restart replaces registry service and listener identities");
            actor.ticks = 1200;
            reads = actor.reads;
            f.repository.attempts.clear();
            task.action.run();
            check(
                    actor.reads == reads
                            && f.repository.attempts.isEmpty()
                            && f.progress(actor, "fresh").progress() == 0,
                    "old callback cannot sample or flush a new runtime generation");
            f.tasks.getLast().action.run();
            check(
                    f.progress(actor, "fresh").progress() == 1,
                    "fresh task alone progresses new generation");
            f.module.disable();
        }
    }

    private static void staleListener() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            var actor = f.actor("StaleListener", 0);
            f.module.enable();
            f.define("retained", 10, 7);
            var service = f.module.getQuestService();
            var original = f.core(actor);
            var listener = f.listener();
            service.assignQuest(actor.id, "retained");
            service.addProgress(actor.id, PLAYTIME, 2);
            var refusal = f.refuseUnregister();
            f.repository.failures.put(actor.id, new IllegalStateException("pending profile"));
            failure(f.module::disable);
            unavailable(f.module);
            check(
                    service.isDirty(actor.id) && f.core(actor) == original,
                    "incomplete cleanup retains original dirty aggregate");
            f.repository.failures.clear();
            f.repository.attempts.clear();
            actor.ticks = 1200;
            int reads = actor.reads;
            listener.onPlayerJoin(join(actor));
            listener.onPlayerQuit(quit(actor));
            check(
                    actor.reads == reads && f.repository.attempts.isEmpty(),
                    "retired listener cannot retry pending persistence or sample");
            check(
                    f.stored(actor).getQuestState().isEmpty(),
                    "stale listener cannot manufacture committed state");
            refusal.refuse = false;
            f.module.disable();
            check(
                    f.stored(actor).getQuestState().snapshot().get("retained").progress() == 2
                            && !service.isDirty(actor.id),
                    "explicit cleanup retry saves retained original values without replaying final"
                            + " sample");
            f.module.enable();
            f.define("retained", 10, 7);
            var freshPlaytime = playtime(f.listener());
            int count = freshPlaytime.trackedPlayers();
            reads = actor.reads;
            f.repository.attempts.clear();
            listener.onPlayerJoin(join(actor));
            listener.onPlayerQuit(quit(actor));
            check(
                    actor.reads == reads
                            && f.repository.attempts.isEmpty()
                            && freshPlaytime.trackedPlayers() == count,
                    "old listener cannot seed sample forget or flush a fresh runtime");
            f.module.disable();
        }
    }

    private static void transitions() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            var actor = f.actor("Transitions", 0);
            f.afterRegistration =
                    () -> {
                        unavailable(f.module);
                        failure(f.module::disable);
                    };
            f.afterSchedule =
                    () -> {
                        unavailable(f.module);
                        int reads = actor.reads;
                        f.tasks.getFirst().action.run();
                        check(actor.reads == reads, "pre-activation task is inert");
                    };
            f.runtime.logger.addHandler(
                    handler(
                            record -> {
                                if (record.getMessage().startsWith("Quest module enabled"))
                                    unavailable(f.module);
                            }));
            f.module.enable();
            var task = f.tasks.getFirst();
            task.beforeCancel =
                    () -> {
                        unavailable(f.module);
                        failure(f.module::enable);
                        failure(f.module::disable);
                    };
            f.module.disable();
            unavailable(f.module);
        }
    }

    private static void finalSample() throws Exception {
        for (int minutes : List.of(0, 1, 3))
            try (var f = new QuestLifecycleFixture()) {
                var actor = f.actor("FinalSample", 1199);
                f.module.enable();
                f.define("final", minutes == 0 ? 5 : minutes == 1 ? 1 : 2, 7);
                var service = f.module.getQuestService();
                var producer = playtime(f.listener());
                service.assignQuest(actor.id, "final");
                service.flushAll();
                f.core(actor).getOnlineRewardProgress().setProcessedPlaytimeTicks(9876);
                actor.ticks = minutes == 0 ? 1199 : minutes * 1200;
                f.module.disable();
                long expected = minutes == 0 ? 0 : minutes == 1 ? 1 : 2;
                var stored = f.stored(actor);
                check(
                        stored.getQuestState().snapshot().get("final").progress() == expected,
                        "normal shutdown captures final available completed minutes");
                check(
                        stored.getQuestState().snapshot().get("final").status()
                                == (minutes == 0 ? QuestStatus.ACTIVE : QuestStatus.COMPLETED),
                        "final sample preserves existing completion semantics");
                check(
                        f.core(actor).getWallet().getCoins() == (minutes == 0 ? 0 : 7)
                                && stored.getWallet().getCoins() == (minutes == 0 ? 0 : 7),
                        "shutdown completion uses existing Reward grant and aggregate persistence");
                check(
                        f.runtime.sentMessages == 0,
                        "shutdown sampling is silent before any completion feedback");
                check(
                        producer.trackedPlayers() == 0 && !service.isDirty(actor.id),
                        "successful shutdown clears samples only after complete persistence");
                check(
                        f.core(actor)
                                        .getOnlineRewardProgress()
                                        .getProcessedPlaytimeTicks()
                                        .orElseThrow()
                                == 9876,
                        "Quest lifecycle leaves independent OnlineRewardProgress intact");
                released(f);
            }
    }

    private static void failedSaves() throws Exception {
        for (int failed : List.of(1, 2))
            try (var f = new QuestLifecycleFixture()) {
                var a = f.actor("FailA", 0);
                var b = f.actor("FailB", 0);
                var c = f.actor("SavedC", 0);
                f.module.enable();
                f.define("save", 10, 7);
                var service = f.module.getQuestService();
                List<UUID> committed = new ArrayList<>();
                service.addChangeListener(committed::add);
                int value = 0;
                for (var actor : List.of(a, b, c)) {
                    service.assignQuest(actor.id, "save");
                    service.addProgress(actor.id, PLAYTIME, ++value);
                }
                RuntimeException causeA = new IllegalStateException("actual A save refused");
                RuntimeException causeB = new IllegalStateException("actual B save refused");
                f.repository.failures.put(a.id, causeA);
                if (failed == 2) f.repository.failures.put(b.id, causeB);
                RuntimeException error = null;
                try {
                    f.module.disable();
                } catch (RuntimeException expected) {
                    error = expected;
                }
                check(error != null, "incomplete Quest save is reported to lifecycle caller");
                check(
                        contains(error, causeA) && (failed == 1 || contains(error, causeB)),
                        "incomplete save preserves exact UUID causes");
                QuestService.FlushFailures failures = findFlushFailure(error);
                check(
                        failures.failures().get(a.id) == causeA
                                && failures.failures().size() == failed,
                        "strict flush exposes exact failed UUID mapping");
                check(
                        f.repository.attempts.keySet().containsAll(List.of(a.id, b.id, c.id)),
                        "one or several failed saves cannot skip later profiles");
                check(
                        service.isDirty(a.id)
                                && (failed == 1 || service.isDirty(b.id))
                                && !service.isDirty(c.id),
                        "incomplete flush retains unresolved dirty tracking");
                check(
                        f.stored(a).getQuestState().isEmpty()
                                && f.stored(c).getQuestState().snapshot().get("save").progress()
                                        == 3,
                        "retained memory is distinguished from real independent disk commits");
                check(
                        !committed.contains(a.id) && committed.contains(c.id),
                        "failed save cannot emit successful Quest commit notification");
                CorePlayer original = f.core(a);
                unavailable(f.module);
                failure(f.module::enable);
                check(
                        ModuleRuntimeFixture.get(service, "playerService") == f.players
                                && f.core(a) == original
                                && f.progress(a, "save").progress() == 1,
                        "retry keeps original borrowed aggregate rather than reloading stale disk");
                f.repository.failures.clear();
                f.module.disable();
                check(
                        !service.isDirty(a.id)
                                && f.stored(a).getQuestState().snapshot().get("save").progress()
                                        == 1,
                        "successful same-process retry commits original unresolved progress");
                check(
                        committed.stream().filter(c.id::equals).count() == 1,
                        "successful prior profile is not spuriously recommitted by Quest retry");
                released(f);
                try (var files = Files.list(f.directory.resolve("players"))) {
                    check(files.count() == 3, "retry introduces no additional persistent files");
                }
            }
    }

    private static void sampleFailures() throws Exception {
        for (String mode : List.of("roster", "player", "logger"))
            try (var f = new QuestLifecycleFixture()) {
                var a = f.actor("SampleA", 0);
                var b = f.actor("SampleB", 0);
                f.module.enable();
                f.define("sampling", 1, 7);
                var service = f.module.getQuestService();
                service.assignQuest(a.id, "sampling");
                service.assignQuest(b.id, "sampling");
                a.ticks = 1200;
                b.ticks = 1200;
                RuntimeException stat = new IllegalStateException("player A sample unavailable");
                RuntimeException diagnostic =
                        new IllegalStateException("sample warning logger unavailable");
                if (mode.equals("roster")) f.refuseRoster = true;
                else a.readFailure = stat;
                Handler logger =
                        handler(
                                record -> {
                                    if (record.getMessage()
                                            .startsWith("Quest playtime sample failed"))
                                        throw diagnostic;
                                });
                if (mode.equals("logger")) f.runtime.logger.addHandler(logger);
                RuntimeException failure = failure(f.module::disable);
                check(
                        contains(failure, mode.equals("roster") ? f.rosterFailure : stat),
                        "shutdown sampling failure preserves original cause");
                if (mode.equals("logger"))
                    check(
                            contains(failure, diagnostic),
                            "sample diagnostic failure remains observable without skipping other"
                                    + " players");
                check(
                        f.handlers() == 0 && f.activeTasks() == 0 && pending(f) != null,
                        "sampling failure still releases external resources and retains retry"
                                + " state");
                check(
                        f.stored(b).getQuestState().snapshot().get("sampling").progress()
                                == (mode.equals("roster") ? 0 : 1),
                        "roster or individual sample failure cannot skip independent dirty-profile"
                                + " flush");
                check(
                        f.runtime.sentMessages == 0,
                        "even failing shutdown sampling sends no feedback");
                long coins = f.core(b).getWallet().getCoins();
                f.refuseRoster = false;
                a.readFailure = null;
                f.runtime.logger.removeHandler(logger);
                f.module.disable();
                check(
                        f.stored(a).getQuestState().snapshot().get("sampling").status()
                                        == QuestStatus.COMPLETED
                                && f.stored(a).getWallet().getCoins() == 7
                                && f.core(b).getWallet().getCoins() == 7,
                        "sampling retry persists completed state and grants each Quest only once");
                check(
                        coins == 0 || f.core(b).getWallet().getCoins() == coins,
                        "retry does not repeat already granted reward");
                released(f);
            }
    }

    private static void outerFlush() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            var actor = f.actor("OuterFlush", 0);
            f.module.enable();
            f.define("outer", 10, 7);
            var service = f.module.getQuestService();
            service.assignQuest(actor.id, "outer");
            FaultySnapshot dirty = new FaultySnapshot();
            dirty.add(actor.id);
            ModuleRuntimeFixture.set(service, "dirtyPlayers", dirty);
            check(
                    contains(failure(f.module::disable), dirty.failure),
                    "unexpected outer flush exception is reported");
            check(
                    dirty.contains(actor.id)
                            && pending(f) != null
                            && f.handlers() == 0
                            && f.activeTasks() == 0,
                    "outer flush error retains bookkeeping without skipping independent releases");
            dirty.refuse = false;
            f.module.disable();
            released(f);
            check(
                    f.stored(actor).getQuestState().snapshot().containsKey("outer"),
                    "outer flush retry uses original retained state");
        }
    }

    private static void interruptedRead() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            var actor = f.actor("Interrupted", 0);
            f.module.enable();
            f.define("interrupted", 10, 7);
            f.module.getQuestService().assignQuest(actor.id, "interrupted");
            var producer = playtime(f.listener());
            actor.ticks = 1200;
            actor.onRead =
                    () -> {
                        actor.onRead = () -> {};
                        f.module.disable();
                    };
            f.tasks.getFirst().action.run();
            check(
                    producer.trackedPlayers() == 0
                            && f.stored(actor)
                                            .getQuestState()
                                            .snapshot()
                                            .get("interrupted")
                                            .progress()
                                    == 1,
                    "callback revoked during statistic read cannot resurrect retired samples or"
                            + " duplicate final progress");
            released(f);
        }
    }

    private static void pendingReward() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            var actor = f.actor("Pending", 0);
            f.module.enable();
            f.define("pending", 1, 7);
            var service = f.module.getQuestService();
            service.assignQuest(actor.id, "pending");
            new EconomyService(f.players).setCoins(actor.id, Long.MAX_VALUE);
            actor.ticks = 1200;
            f.repository.failures.put(
                    actor.id, new IllegalStateException("pending state disk failure"));
            failure(f.module::disable);
            check(
                    f.progress(actor, "pending").status() == QuestStatus.REWARD_PENDING
                            && service.isDirty(actor.id),
                    "REWARD_PENDING state and dirty tracking survive failed save");
            f.repository.failures.clear();
            f.module.disable();
            check(
                    f.stored(actor).getQuestState().snapshot().get("pending").status()
                                    == QuestStatus.REWARD_PENDING
                            && f.stored(actor).getWallet().getCoins() == Long.MAX_VALUE,
                    "cleanup retry persists pending status without attempting another payout");
            new EconomyService(f.players).setCoins(actor.id, 0);
            f.module.enable();
            f.define("pending", 1, 7);
            check(
                    f.core(actor).getWallet().getCoins() == 0,
                    "startup never retries pending rewards automatically");
            f.module.getQuestService().retryPendingRewards(actor.id);
            f.module.disable();
            check(
                    f.stored(actor).getQuestState().snapshot().get("pending").status()
                                    == QuestStatus.COMPLETED
                            && f.stored(actor).getWallet().getCoins() == 7,
                    "existing explicit pending-reward API remains compatible");
        }
    }

    private static void quit() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            var actor = f.actor("Quit", 1199);
            f.module.enable();
            f.define("quit", 10, 7);
            var service = f.module.getQuestService();
            service.assignQuest(actor.id, "quit");
            check(
                    QuestListener.class
                                            .getMethod("onPlayerJoin", PlayerJoinEvent.class)
                                            .getAnnotation(org.bukkit.event.EventHandler.class)
                                            .priority()
                                    == EventPriority.MONITOR
                            && QuestListener.class
                                            .getMethod("onPlayerQuit", PlayerQuitEvent.class)
                                            .getAnnotation(org.bukkit.event.EventHandler.class)
                                            .priority()
                                    == EventPriority.LOWEST
                            && PlayerListener.class
                                            .getMethod("onPlayerQuit", PlayerQuitEvent.class)
                                            .getAnnotation(org.bukkit.event.EventHandler.class)
                                            .priority()
                                    == EventPriority.NORMAL,
                    "existing join and quit priorities remain MONITOR LOWEST NORMAL");
            actor.ticks = 1200;
            f.listener().onPlayerQuit(quit(actor));
            check(
                    f.stored(actor).getQuestState().snapshot().get("quit").progress() == 1
                            && playtime(f.listener()).trackedPlayers() == 0,
                    "quit samples before flushing and forgets its runtime baseline");
            f.players.unloadPlayer(actor.id);
            check(
                    !f.players.isLoaded(actor.id)
                            && f.stored(actor).getQuestState().snapshot().get("quit").progress()
                                    == 1,
                    "Player unload follows successful Quest commit and save-before-eviction");
            f.module.disable();
        }
    }

    private static void ownersAndFallback() throws Exception {
        for (boolean playerAlsoFails : List.of(false, true))
            try (var f = new QuestLifecycleFixture()) {
                ModuleRuntimeFixture.set(f.runtime.plugin, "dataFolder", f.directory.toFile());
                ConfigService config = new ConfigService(f.runtime.plugin);
                PlayerModule owner = new PlayerModule(f.runtime.plugin, config, f.runtime.messages);
                EconomyModule economy = ModuleRuntimeFixture.allocate(EconomyModule.class);
                RewardModule rewards = new RewardModule(f.runtime.plugin, owner, economy);
                QuestModule quests =
                        new QuestModule(f.runtime.plugin, owner, rewards, f.runtime.messages);
                List<String> stopped = new ArrayList<>();
                ModuleManager manager = new ModuleManager(f.runtime.logger);
                manager.register(owner);
                manager.register(
                        new CoreModule() {
                            public String getName() {
                                return "Economy";
                            }

                            public void enable() {
                                ModuleRuntimeFixture.set(
                                        economy,
                                        "economyService",
                                        new EconomyService(owner.getPlayerService()));
                            }

                            public void disable() {
                                stopped.add("Economy");
                            }
                        });
                manager.register(rewards);
                manager.register(quests);
                manager.register(
                        new CoreModule() {
                            public String getName() {
                                return "DailyQuestConsumer";
                            }

                            public void enable() {
                                check(
                                        quests.getQuestService() != null
                                                && quests.getDefinitionRegistry() != null,
                                        "consumer borrows fully active registry and service");
                            }

                            public void disable() {
                                check(
                                        quests.getQuestService() != null,
                                        "DailyQuest consumer stops while Quest is still active");
                                stopped.add("DailyQuest");
                            }
                        });
                manager.enableAll();
                var players = owner.getPlayerService();
                var actor = f.actor("Owners", 0, players);
                CorePlayer original = players.getPlayer(actor.id).orElseThrow();
                var actualDisk =
                        (FilePlayerRepository) ModuleRuntimeFixture.get(players, "repository");
                var fault = new QuestLifecycleFixture.FaultRepository(actualDisk);
                ModuleRuntimeFixture.set(players, "repository", fault);
                quests.getDefinitionRegistry()
                        .replaceAll(
                                List.of(
                                        new QuestDefinition(
                                                "owners", "owners", "owners", PLAYTIME, 10, 7)));
                var service = quests.getQuestService();
                service.assignQuest(actor.id, "owners");
                service.addProgress(actor.id, PLAYTIME, 2);
                RuntimeException error =
                        new IllegalStateException("same original profile save failure");
                fault.failures.put(actor.id, error);
                if (!playerAlsoFails) {
                    // Fail only Quest's first save; PlayerModule's later established fallback
                    // succeeds.
                    fault.beforeSave =
                            id -> {
                                if (fault.attempts.get(id) > 1) fault.failures.remove(id);
                            };
                }
                var oldListener =
                        (QuestListener)
                                ModuleRuntimeFixture.get(
                                        ModuleRuntimeFixture.get(quests, "runtime"), "listener");
                var oldTask =
                        (org.bukkit.scheduler.BukkitTask)
                                ModuleRuntimeFixture.get(
                                        ModuleRuntimeFixture.get(quests, "runtime"), "task");
                manager.disableAll();
                check(
                        manager.getEnabledModules().isEmpty()
                                && stopped.getFirst().equals("DailyQuest")
                                && oldTask.isCancelled(),
                        "actual manager stops consumer before Quest and continues reverse shutdown"
                                + " after flush failure");
                unavailable(quests);
                check(
                        ModuleRuntimeFixture.get(service, "playerService") == players,
                        "retired Quest retains original borrowed PlayerService");
                if (playerAlsoFails) {
                    check(
                            players.getPlayer(actor.id).orElseThrow() == original
                                    && ModuleRuntimeFixture.get(owner, "pendingCleanup") != null,
                            "PlayerModule also retains exact aggregate after failed fallback");
                    check(
                            actualDisk
                                    .findByUniqueId(actor.id)
                                    .orElseThrow()
                                    .getQuestState()
                                    .isEmpty(),
                            "retained memory is not mistaken for durable fallback");
                    failure(owner::getPlayerService);
                    fault.failures.clear();
                    quests.disable();
                    owner.disable();
                } else {
                    check(
                            players.getLoadedPlayers().isEmpty()
                                    && actualDisk
                                                    .findByUniqueId(actor.id)
                                                    .orElseThrow()
                                                    .getQuestState()
                                                    .snapshot()
                                                    .get("owners")
                                                    .progress()
                                            == 2,
                            "PlayerModule independently saves same aggregate before cache release");
                    quests.disable();
                }
                check(
                        !service.isDirty(actor.id)
                                && ModuleRuntimeFixture.get(quests, "pendingCleanup") == null,
                        "explicit Quest retry reconciles successful original-owner persistence"
                                + " handoff");
                manager.enableAll();
                var fresh = owner.getPlayerService();
                fresh.loadPlayer(actor.id, "Owners");
                check(
                        fresh != players && quests.getQuestService() != service,
                        "complete ordered restart acquires fresh owners and Quest service");
                oldListener.onPlayerJoin(join(actor));
                oldListener.onPlayerQuit(quit(actor));
                check(
                        fresh.isLoaded(actor.id),
                        "retired listener cannot unload or replace fresh owner state");
                manager.disableAll();
                try (var files = Files.list(f.directory.resolve("players"))) {
                    check(
                            files.count() == 1,
                            "full owner restart retains exactly the intended profile format");
                }
            }
    }

    private static void managerRollback() throws Exception {
        for (boolean failedQuest : List.of(false, true))
            try (var f = new QuestLifecycleFixture()) {
                ModuleManager manager = new ModuleManager(f.runtime.logger);
                List<String> stopped = new ArrayList<>();
                manager.register(
                        owner(
                                "Player",
                                () ->
                                        check(
                                                f.handlers() == 0 && f.activeTasks() == 0,
                                                "Player rollback follows Quest resource release"),
                                stopped));
                manager.register(
                        owner(
                                "Reward",
                                () ->
                                        check(
                                                f.handlers() == 0 && f.activeTasks() == 0,
                                                "Reward rollback follows Quest resource release"),
                                stopped));
                manager.register(f.module);
                RuntimeException original = new IllegalStateException("manager startup fault");
                if (failedQuest)
                    f.runtime.logger.addHandler(
                            handler(
                                    record -> {
                                        if (record.getMessage().startsWith("Quest module enabled"))
                                            throw original;
                                    }));
                else
                    manager.register(
                            new CoreModule() {
                                public String getName() {
                                    return "DailyQuest";
                                }

                                public void enable() {
                                    throw original;
                                }

                                public void disable() {
                                    throw new AssertionError(
                                            "failed consumer not owned by manager");
                                }
                            });
                check(
                        failure(manager::enableAll) == original
                                && stopped.equals(List.of("Reward", "Player")),
                        "manager rollback preserves original failure and reverse dependency order");
                released(f);
            }
    }

    private static void producerExceptionBoundary() {
        int[] reports = {0};
        UUID id = UUID.randomUUID();
        QuestPlaytimeProducer producer =
                new QuestPlaytimeProducer(
                        player -> true,
                        (player, minutes) -> {
                            reports[0]++;
                            throw new IllegalStateException("before report effect");
                        },
                        java.util.logging.Logger.getAnonymousLogger());
        producer.seed(id, 0);
        failure(() -> producer.sample(id, 1200));
        producer.sample(id, 1200);
        check(
                reports[0] == 1,
                "existing sample-before-report exception boundary remains explicit and is not"
                        + " claimed transactional");
    }

    /**
     * Stronger replacement for the legacy callback-spelling and sequential-cleanup source checks.
     */
    public static void assertTaskAndShutdownLifecycle() throws Exception {
        normal();
        finalSample();
        cancellation();
    }

    private static CoreModule owner(String name, Runnable disable, List<String> stopped) {
        return new CoreModule() {
            public String getName() {
                return name;
            }

            public void enable() {}

            public void disable() {
                stopped.add(name);
                disable.run();
            }
        };
    }

    private static QuestPlaytimeProducer playtime(QuestListener listener) {
        return (QuestPlaytimeProducer) ModuleRuntimeFixture.get(listener, "playtime");
    }

    private static Object pending(QuestLifecycleFixture f) {
        return ModuleRuntimeFixture.get(f.module, "pendingCleanup");
    }

    private static PlayerJoinEvent join(QuestLifecycleFixture.Actor actor) {
        return new PlayerJoinEvent(actor.player, net.kyori.adventure.text.Component.empty());
    }

    private static PlayerQuitEvent quit(QuestLifecycleFixture.Actor actor) {
        return new PlayerQuitEvent(actor.player, net.kyori.adventure.text.Component.empty());
    }

    private static void released(QuestLifecycleFixture f) {
        unavailable(f.module);
        check(
                f.handlers() == 0
                        && f.activeTasks() == 0
                        && pending(f) == null
                        && ModuleRuntimeFixture.get(f.module, "runtime") == null,
                "owned resources released before fixture teardown");
    }

    private static void unavailable(QuestModule module) {
        int rejected = 0;
        try {
            module.getQuestService();
        } catch (RuntimeException expected) {
            rejected++;
        }
        try {
            module.getDefinitionRegistry();
        } catch (RuntimeException expected) {
            rejected++;
        }
        try {
            module.getProgressReporter();
        } catch (RuntimeException expected) {
            rejected++;
        }
        check(rejected == 3, "inactive lifecycle getters reject retired runtime");
    }

    private static RuntimeException failure(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected controlled failure");
        } catch (RuntimeException expected) {
            return expected;
        }
    }

    private static boolean contains(Throwable root, Throwable expected) {
        return root == expected
                || root.getCause() != null && contains(root.getCause(), expected)
                || Arrays.stream(root.getSuppressed()).anyMatch(child -> contains(child, expected));
    }

    private static QuestService.FlushFailures findFlushFailure(Throwable root) {
        if (root instanceof QuestService.FlushFailures failure) return failure;
        if (root.getCause() != null) {
            var found = findFlushFailure(root.getCause());
            if (found != null) return found;
        }
        for (Throwable child : root.getSuppressed()) {
            var found = findFlushFailure(child);
            if (found != null) return found;
        }
        return null;
    }

    private static Handler handler(Consumer<LogRecord> publish) {
        return new Handler() {
            public void publish(LogRecord record) {
                publish.accept(record);
            }

            public void flush() {}

            public void close() {}
        };
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class FaultySnapshot extends LinkedHashSet<UUID> {
        boolean refuse = true;
        final RuntimeException failure = new IllegalStateException("outer dirty snapshot failure");

        public Object[] toArray() {
            if (refuse) throw failure;
            return super.toArray();
        }
    }
}
