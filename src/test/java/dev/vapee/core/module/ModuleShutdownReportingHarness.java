package dev.vapee.core.module;

import static dev.vapee.core.module.ModuleRuntimeFixture.allocate;
import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;
import static dev.vapee.core.module.ShutdownReport.Phase;
import static dev.vapee.core.module.ShutdownReport.Status;

import dev.vapee.core.VapeeCore;
import dev.vapee.core.quest.QuestDefinition;
import dev.vapee.core.quest.QuestModule;
import dev.vapee.core.quest.QuestProgressKey;
import dev.vapee.core.reward.RewardShutdownFixture;
import dev.vapee.core.reward.RewardSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Actual coordinator/root calls plus real persistence owners; no source-text behavioral oracle. */
public final class ModuleShutdownReportingHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            normal();
            registration();
            diagnostics();
            failures();
            rollback();
            exceptionGraphs();
            interrupted();
            reentrant();
            root();
            owners();
        } else
            switch (args[0]) {
                case "normal" -> normal();
                case "registration" -> registration();
                case "diagnostics" -> diagnostics();
                case "failures" -> failures();
                case "rollback" -> rollback();
                case "graphs" -> exceptionGraphs();
                case "interrupted" -> interrupted();
                case "root" -> root();
                case "owners" -> owners();
                default -> throw new AssertionError(args[0]);
            }
        System.out.println("ModuleShutdownReportingHarness passed " + checks + " checks.");
    }

    private static void normal() {
        try (var f = new F()) {
            check(
                    f.manager.getLastCleanupReport().isEmpty()
                            && f.manager.getLastRollbackReport().isEmpty(),
                    "new manager invents no cleanup report");
            f.manager.enableAll();
            check(
                    f.events.equals(List.of("+A", "+B", "+C"))
                            && f.manager.getEnabledModules().equals(List.of(f.a, f.b, f.c)),
                    "actual startup order and enabled membership");
            f.events.clear();
            f.manager.disableAll();
            var report = f.report();
            check(
                    f.events.equals(List.of("-C", "-B", "-A")),
                    "actual shutdown exact reverse order");
            check(
                    report.phase() == Phase.SHUTDOWN
                            && report.completedWithoutReportedErrors()
                            && report.allCallsReturnedNormally(),
                    "clean ordinary report accurately error-free");
            check(
                    report.outcomes().stream()
                            .map(ShutdownReport.ModuleOutcome::module)
                            .toList()
                            .equals(List.of(f.c, f.b, f.a)),
                    "report retains exact expected module instance order");
            check(
                    report.outcomes().stream()
                            .allMatch(
                                    x ->
                                            x.status() == Status.RETURNED_NORMALLY
                                                    && x.failure() == null),
                    "normal return recorded without a persistence claim");
            check(
                    report.incompleteOutcomes().isEmpty() && report.diagnostics().isEmpty(),
                    "clean report has no invented failures");
            check(
                    f.manager.getEnabledModules().isEmpty()
                            && Boolean.FALSE.equals(get(f.manager, "enabled")),
                    "manager bookkeeping cleared after all actual calls");
            check(
                    failure(() -> report.outcomes().clear())
                                    instanceof UnsupportedOperationException
                            && failure(() -> report.diagnostics().clear())
                                    instanceof UnsupportedOperationException,
                    "published report lists immutable");
            var source = new ArrayList<>(report.outcomes());
            var diagnostics = new ArrayList<ShutdownReport.DiagnosticFailure>();
            var copy = new ShutdownReport(Phase.SHUTDOWN, source, diagnostics, null);
            source.clear();
            diagnostics.add(
                    new ShutdownReport.DiagnosticFailure("external", null, null, f.diagnostic));
            check(
                    copy.outcomes().size() == 3 && copy.diagnostics().isEmpty(),
                    "report constructor copies source collections");
            int calls = f.events.size(), logs = f.records.size();
            f.manager.getObservedIncompleteOutcomes();
            f.manager.getLastShutdownReport();
            report.incompleteOutcomes();
            f.manager.disableAll();
            check(
                    f.events.size() == calls
                            && f.records.size() == logs
                            && f.manager.getLastCleanupReport().orElse(null) == report,
                    "repeated successful stop and inspection inert with same report");
            f.manager.enableAll();
            f.manager.disableAll();
            check(
                    f.report() != report
                            && f.report().completedWithoutReportedErrors()
                            && f.a.starts == 2
                            && f.a.stops == 2,
                    "clean new lifecycle produces its own fresh successful report");
        }
        Logger quiet = Logger.getAnonymousLogger();
        quiet.setUseParentHandlers(false);
        var empty = new ModuleManager(quiet);
        empty.disableAll();
        check(
                empty.getLastCleanupReport().isEmpty(),
                "never-started empty stop is not an invented attempt");
        empty.enableAll();
        empty.disableAll();
        check(
                empty.getLastShutdownReport().orElseThrow().outcomes().isEmpty()
                        && empty.getLastCleanupReport()
                                .orElseThrow()
                                .completedWithoutReportedErrors(),
                "real empty lifecycle has coherent complete report");
    }

    private static void registration() {
        try (var f = new F()) {
            f.a.failName = true;
            f.b.reportedName = "changed";
            check(
                    failure(() -> f.manager.register(new M(" a ", f.events)))
                            instanceof IllegalArgumentException,
                    "case insensitive duplicate uses validated original name");
            check(
                    failure(() -> f.manager.register(f.a)) instanceof IllegalArgumentException
                            && f.a.nameCalls == 1,
                    "same instance rejects without calling hostile name getter");
            M extra = new M(" Extra ", f.events);
            f.manager.register(extra);
            check(
                    f.manager.getModules().equals(List.of(f.a, f.b, f.c, extra)),
                    "cached names preserve registration order and module API");
            check(
                    failure(() -> f.manager.register(new M("  ", f.events)))
                            instanceof IllegalArgumentException,
                    "blank registration name rejected");
            M nullName = new M("Null", f.events);
            nullName.reportedName = null;
            check(
                    failure(() -> f.manager.register(nullName)) instanceof NullPointerException,
                    "null registration name rejected");
            f.manager.enableAll();
            check(
                    f.a.nameCalls == 1 && f.b.nameCalls == 1,
                    "startup does not repeat external name getters");
            check(
                    failure(() -> f.manager.register(new M("Late", f.events)))
                            instanceof IllegalStateException,
                    "registration after start remains rejected");
            f.manager.disableAll();
            var report = f.report();
            check(
                    report.outcomes().stream()
                            .map(ShutdownReport.ModuleOutcome::name)
                            .toList()
                            .equals(List.of("Extra", "C", "B", "A")),
                    "stable validated names survive changed or throwing getName");
            check(
                    f.a.stops == 1 && f.a.nameCalls == 1 && report.completedWithoutReportedErrors(),
                    "hostile name getter cannot skip exact module disable");
            check(
                    failure(() -> f.manager.getModules().clear())
                                    instanceof UnsupportedOperationException
                            && failure(() -> f.manager.getEnabledModules().clear())
                                    instanceof UnsupportedOperationException,
                    "existing public module lists remain snapshots");
        }
    }

    private static void diagnostics() {
        for (String message :
                List.of("Enabling 3 module(s).", "Enabling module B.", "Enabled 3 module(s)."))
            try (var f = new F()) {
                f.throwWhen = r -> r.getMessage().equals(message);
                check(
                        failure(f.manager::enableAll) == null
                                && f.a.starts == 1
                                && f.b.starts == 1
                                && f.c.starts == 1,
                        "startup INFO failure cannot skip module enable");
                var diagnostics = f.manager.getStartupDiagnostics();
                check(
                        diagnostics.size() == 1 && diagnostics.getFirst().failure() == f.diagnostic,
                        "startup diagnostic retained independently");
                check(
                        failure(diagnostics::clear) instanceof UnsupportedOperationException,
                        "startup diagnostic list immutable");
                f.throwWhen = r -> false;
                f.manager.disableAll();
                check(
                        f.report().completedWithoutReportedErrors(),
                        "new shutdown result does not inherit unrelated startup diagnostic status");
            }
        for (String message :
                List.of(
                        "Disabling 3 module(s).",
                        "Disabling module B.",
                        "All 3 module shutdown calls returned normally."))
            try (var f = new F()) {
                f.start();
                f.throwWhen = r -> r.getMessage().equals(message);
                check(
                        failure(f.manager::disableAll) == null,
                        "ordinary shutdown diagnostic failure does not escape coordinator");
                var report = f.report();
                check(
                        f.events.equals(List.of("-C", "-B", "-A")),
                        "shutdown INFO failure cannot skip any actual disable");
                check(
                        report.allCallsReturnedNormally()
                                && !report.completedWithoutReportedErrors()
                                && report.diagnostics().size() == 1,
                        "diagnostic failure distinct from normal module returns");
                check(
                        report.diagnostics().getFirst().failure() == f.diagnostic
                                && f.manager.getObservedIncompleteOutcomes().isEmpty(),
                        "INFO diagnostic is not invented module cleanup failure");
                check(
                        f.manager.getEnabledModules().isEmpty()
                                && Boolean.FALSE.equals(get(f.manager, "enabled")),
                        "logger errors cannot retain stale active bookkeeping");
                int calls = f.events.size();
                f.manager.disableAll();
                check(
                        f.events.size() == calls && f.report() == report,
                        "no-op stop preserves prior diagnostic report");
            }
    }

    private static void failures() {
        try (var f = new F()) {
            f.start();
            f.b.failDisable = true;
            f.c.failDisable = true;
            f.c.afterEffect = true;
            RuntimeException prior = new IllegalStateException("existing suppressed cleanup");
            f.b.cleanup.addSuppressed(prior);
            f.throwWhen = r -> r.getLevel().intValue() >= 1000;
            check(
                    failure(f.manager::disableAll) == null,
                    "SEVERE failure cannot abort independent cleanup");
            var report = f.report();
            check(
                    f.events.equals(List.of("-C", "-B", "-A")) && f.a.stops == 1,
                    "multiple failed modules still permit all predecessors");
            check(
                    report.outcomes().stream()
                            .map(ShutdownReport.ModuleOutcome::status)
                            .toList()
                            .equals(
                                    List.of(
                                            Status.THREW_EXCEPTION,
                                            Status.THREW_EXCEPTION,
                                            Status.RETURNED_NORMALLY)),
                    "multiple failures represented independently");
            check(
                    report.outcomes().get(0).failure() == f.c.cleanup
                            && report.outcomes().get(1).failure() == f.b.cleanup,
                    "exact original module exceptions retained");
            check(
                    Arrays.asList(report.outcomes().get(1).failure().getSuppressed())
                            .contains(prior),
                    "original suppressed cleanup chain retained");
            check(
                    !f.c.active && f.b.active,
                    "same threw status covers after-effect release and before-effect refusal"
                            + " honestly");
            check(
                    report.diagnostics().size() == 3
                            && report.diagnostics().stream()
                                    .allMatch(x -> x.failure() == f.diagnostic),
                    "module failures and exact diagnostic errors remain separate");
            check(
                    report.incompleteOutcomes().stream()
                            .map(ShutdownReport.ModuleOutcome::module)
                            .toList()
                            .equals(List.of(f.c, f.b)),
                    "report retains actual failed module identity");
            var retained = f.manager.getObservedIncompleteOutcomes();
            check(
                    retained.size() == 2
                            && retained.get(0).module() == f.c
                            && retained.get(1).module() == f.b,
                    "read-only observed failures distinguished from configured membership");
            check(
                    failure(retained::clear) instanceof UnsupportedOperationException,
                    "retained failure evidence is not mutable retry queue");
            int callbacks = f.events.size(), logs = f.records.size();
            f.manager.disableAll();
            f.manager.getObservedIncompleteOutcomes();
            f.manager.getLastCleanupReport();
            check(
                    f.events.size() == callbacks
                            && f.records.size() == logs
                            && f.report() == report,
                    "incomplete repeat and report inspection do not automatically retry");
        }
        try (var f = new F()) {
            f.start();
            f.b.failDisable = true;
            f.b.afterEffect = true;
            f.manager.disableAll();
            var old = f.report();
            f.b.failDisable = false;
            f.manager.enableAll();
            f.manager.disableAll();
            check(
                    f.report().completedWithoutReportedErrors()
                            && f.report() != old
                            && f.a.starts == 2,
                    "explicit supported restart after after-effect failure remains compatible");
            check(
                    f.manager.getObservedIncompleteOutcomes().stream()
                            .anyMatch(x -> x.module() == f.b && x.failure() == f.b.cleanup),
                    "explicit restart cannot silently erase historical failed identity");
        }
    }

    private static void rollback() {
        try (var f = new F()) {
            f.c.failEnable = true;
            f.b.failDisable = true;
            f.b.afterEffect = true;
            f.throwWhen = r -> r.getLevel().intValue() >= 1000;
            RuntimeException prior = new IllegalStateException("prior startup evidence");
            f.c.startup.addSuppressed(prior);
            RuntimeException error = failure(f.manager::enableAll);
            check(
                    error == f.c.startup,
                    "startup diagnostic cannot replace exact original startup exception");
            check(
                    f.events.equals(List.of("+A", "+B", "+C", "-B", "-A")),
                    "startup rollback always attempts successful predecessors in reverse order");
            check(
                    f.c.stops == 0 && f.c.ownCleanup == 1,
                    "failed startup module owns partial cleanup and is not manager-enabled");
            var report = f.manager.getLastRollbackReport().orElseThrow();
            check(
                    report.phase() == Phase.STARTUP_ROLLBACK
                            && report.startupFailure() == error
                            && report.outcomes().stream()
                                    .map(ShutdownReport.ModuleOutcome::module)
                                    .toList()
                                    .equals(List.of(f.b, f.a)),
                    "rollback phase and expected exact instances correctly attributed");
            check(
                    report.outcomes().getFirst().failure() == f.b.cleanup
                            && report.outcomes().getLast().status() == Status.RETURNED_NORMALLY,
                    "rollback failure cannot skip remaining predecessor");
            check(
                    contains(error, f.b.cleanup)
                            && contains(error, f.diagnostic)
                            && contains(error, prior),
                    "startup retains rollback and diagnostic secondary evidence");
            check(
                    error.getSuppressed().length == 3,
                    "shared diagnostic attached once without duplicate causes");
            check(
                    report.diagnostics().size() == 3 && !report.completedWithoutReportedErrors(),
                    "startup and rollback diagnostics accurately recorded");
            check(
                    f.manager.getEnabledModules().isEmpty()
                            && Boolean.FALSE.equals(get(f.manager, "enabled")),
                    "failed startup rollback clears enabled bookkeeping");
            f.manager.disableAll();
            check(
                    f.report() == report && f.manager.getLastShutdownReport().isEmpty(),
                    "no-op shutdown cannot relabel or erase rollback report");
            f.c.failEnable = false;
            f.b.failDisable = false;
            f.throwWhen = r -> false;
            f.manager.enableAll();
            f.manager.disableAll();
            check(
                    f.report().phase() == Phase.SHUTDOWN
                            && f.report().startupFailure() == null
                            && f.report().completedWithoutReportedErrors(),
                    "clean new lifecycle does not inherit stale rollback status");
            check(
                    f.manager.getLastRollbackReport().orElseThrow() == report,
                    "rollback remains separately inspectable after later normal shutdown");
        }
    }

    private static void exceptionGraphs() {
        for (int mode = 0; mode < 3; mode++)
            try (var f = new F()) {
                f.c.failEnable = true;
                f.b.failDisable = true;
                f.b.afterEffect = true;
                if (mode == 0) {
                    f.b.cleanup = f.c.startup;
                    f.diagnostic = f.c.startup;
                }
                if (mode == 1)
                    f.b.cleanup =
                            new IllegalStateException(
                                    "secondary already references primary", f.c.startup);
                if (mode == 2) {
                    f.c.startup.addSuppressed(f.b.cleanup);
                    f.c.startup.addSuppressed(f.diagnostic);
                }
                f.throwWhen = r -> r.getLevel().intValue() >= 1000;
                RuntimeException error = failure(f.manager::enableAll);
                check(
                        error == f.c.startup && f.a.stops == 1,
                        "self or cyclic secondary cannot replace startup or skip rollback");
                var report = f.manager.getLastRollbackReport().orElseThrow();
                check(
                        report.outcomes().getFirst().failure() == f.b.cleanup,
                        "unattached secondary remains observable in exact report");
                check(
                        !Arrays.asList(error.getSuppressed()).contains(error),
                        "startup never suppresses itself");
                check(
                        Arrays.stream(error.getSuppressed()).filter(x -> x == f.diagnostic).count()
                                <= 1,
                        "duplicate diagnostic cause not added repeatedly");
                if (mode == 1)
                    check(
                            !Arrays.asList(error.getSuppressed()).contains(f.b.cleanup),
                            "secondary pointing to primary cannot introduce new cycle");
            }
    }

    private static void interrupted() {
        for (boolean diagnostic : List.of(false, true))
            try (var f = new F()) {
                f.start();
                AssertionError fatal = new AssertionError("fatal must propagate");
                if (diagnostic)
                    f.onLog =
                            r -> {
                                if (r.getMessage().equals("Disabling module B.")) throw fatal;
                            };
                else
                    f.b.onDisable =
                            () -> {
                                throw fatal;
                            };
                Error actual = null;
                try {
                    f.manager.disableAll();
                } catch (Error e) {
                    actual = e;
                }
                check(
                        actual == fatal,
                        "fatal JVM Error is not handled as ordinary recoverable diagnostic");
                var report = f.report();
                check(
                        report.outcomes().getFirst().status() == Status.RETURNED_NORMALLY
                                && report.outcomes().get(1).status()
                                        == (diagnostic
                                                ? Status.NOT_ATTEMPTED
                                                : Status.OUTCOME_UNKNOWN)
                                && report.outcomes().getLast().status() == Status.NOT_ATTEMPTED,
                        "interrupted calls distinguish normal unknown and not-attempted");
                check(
                        !report.completedWithoutReportedErrors()
                                && f.manager.getObservedIncompleteOutcomes().size() == 2,
                        "interrupted report retains unconfirmed exact identities");
                int starts = f.a.starts, stops = f.b.stops;
                check(
                        failure(f.manager::enableAll) != null && f.a.starts == starts,
                        "new acquisition rejected after unknown interrupted cleanup");
                f.manager.disableAll();
                check(f.b.stops == stops, "interrupted stop is not an implicit retry queue");
            }
    }

    private static void reentrant() {
        try (var f = new F()) {
            f.a.onEnable =
                    () -> {
                        check(
                                failure(f.manager::enableAll) instanceof IllegalStateException
                                        && failure(f.manager::disableAll)
                                                instanceof IllegalStateException,
                                "manager rejects nested startup and shutdown");
                        check(
                                failure(() -> f.manager.register(new M("Nested", f.events)))
                                        instanceof IllegalStateException,
                                "registration rejected during startup");
                    };
            f.manager.enableAll();
            f.b.onDisable =
                    () -> {
                        check(
                                failure(f.manager::disableAll) instanceof IllegalStateException
                                        && failure(f.manager::enableAll)
                                                instanceof IllegalStateException,
                                "cleanup callbacks cannot duplicate current lifecycle attempt");
                    };
            f.manager.disableAll();
            check(
                    f.a.starts == 1
                            && f.b.stops == 1
                            && f.report().completedWithoutReportedErrors(),
                    "reentrancy guards preserve single real acquisition and cleanup");
        }
    }

    private static void root() {
        try (var f = new F()) {
            f.start();
            VapeeCore root = root(f);
            root.onDisable();
            check(
                    f.records.stream()
                            .anyMatch(
                                    r ->
                                            r.getMessage()
                                                    .equals(
                                                            "VapeeCore disabled; all module"
                                                                    + " shutdown calls returned"
                                                                    + " normally.")),
                    "root clean completion states actual method return semantics");
            check(
                    f.report().completedWithoutReportedErrors(),
                    "root keeps clean manager report inspectable");
        }
        try (var f = new F()) {
            f.start();
            f.b.failDisable = true;
            VapeeCore root = root(f);
            root.onDisable();
            check(
                    f.records.stream()
                                    .anyMatch(
                                            r ->
                                                    r.getLevel().intValue() >= 1000
                                                            && r.getMessage()
                                                                    .startsWith(
                                                                            "VapeeCore cleanup"
                                                                                + " incomplete"))
                            && f.records.stream()
                                    .noneMatch(
                                            r ->
                                                    r.getMessage()
                                                            .equals(
                                                                    "VapeeCore disabled; all module"
                                                                        + " shutdown calls returned"
                                                                        + " normally.")),
                    "root cannot log unconditional success after incomplete cleanup");
            check(
                    f.report().outcomes().get(1).failure() == f.b.cleanup,
                    "root diagnostic does not erase original cleanup outcome");
        }
        try (var f = new F()) {
            f.start();
            f.throwWhen = r -> r.getMessage().startsWith("VapeeCore ");
            VapeeCore root = root(f);
            check(
                    failure(root::onDisable) == null
                            && f.a.stops == 1
                            && f.b.stops == 1
                            && f.c.stops == 1,
                    "root logger failure cannot prevent actual manager cleanup");
            check(
                    f.report().allCallsReturnedNormally()
                            && f.report()
                                    .diagnostics()
                                    .getLast()
                                    .operation()
                                    .equals("root.completion")
                            && f.report().diagnostics().getLast().failure() == f.diagnostic,
                    "root completion diagnostic retained without changing module statuses");
            check(
                    get(root, "shutdownDiagnosticFailure") == f.diagnostic,
                    "root retains exact diagnostic even outside report publication");
        }
        try (var f = new F()) {
            VapeeCore root = allocate(VapeeCore.class);
            set(root, "logger", f.logger);
            f.throwWhen = r -> true;
            check(
                    failure(root::onDisable) == null
                            && get(root, "shutdownDiagnosticFailure") == f.diagnostic,
                    "null manager and throwing root logger handled safely");
            check(
                    f.manager.getLastCleanupReport().isEmpty(),
                    "null-root shutdown invents no manager outcome");
        }
        try (var f = new F()) {
            f.c.failEnable = true;
            failure(f.manager::enableAll);
            root(f).onDisable();
            check(
                    f.records.stream()
                            .anyMatch(
                                    r ->
                                            r.getMessage()
                                                    .equals(
                                                            "VapeeCore disabled after startup"
                                                                + " failure; predecessor rollback"
                                                                + " calls returned normally.")),
                    "root distinguishes completed predecessor rollback from successful startup");
        }
    }

    private static void owners() throws Exception {
        for (int mode = 0; mode < 5; mode++)
            try (var f = new RewardShutdownFixture()) {
                var events = new ArrayList<String>();
                M predecessor = new M("Predecessor", events);
                ModuleManager manager = new ModuleManager(f.runtime.logger);
                manager.register(predecessor);
                manager.register(f.playerOwner);
                manager.register(f.economyOwner);
                manager.register(f.module);
                QuestModule quest = null;
                if (mode == 2) {
                    quest =
                            new QuestModule(
                                    f.runtime.plugin, f.playerOwner, f.module, f.runtime.messages);
                    manager.register(quest);
                }
                manager.enableAll();
                f.attachRepository();
                f.pair();
                var rewards = f.module.getRewardService();
                var original = f.players.getPlayer(f.a).orElseThrow();
                Object originalPlayers = f.players;
                if (mode == 2) {
                    quest.getDefinitionRegistry()
                            .replaceAll(
                                    List.of(
                                            new QuestDefinition(
                                                    "shutdown",
                                                    "Shutdown",
                                                    "Shutdown",
                                                    QuestProgressKey.of("test:shutdown"),
                                                    10,
                                                    5)));
                    quest.getQuestService().assignQuest(f.a, "shutdown");
                    quest.getQuestService().assignQuest(f.b, "shutdown");
                    quest.getQuestService()
                            .addProgress(f.a, QuestProgressKey.of("test:shutdown"), 1);
                    quest.getQuestService()
                            .addProgress(f.b, QuestProgressKey.of("test:shutdown"), 2);
                } else {
                    rewards.grantCoins(f.a, 11, RewardSource.SYSTEM, "owner:A");
                    rewards.grantCoins(f.b, 22, RewardSource.SYSTEM, "owner:B");
                }
                RuntimeException storage = null;
                if (mode <= 2) storage = f.repository.fail(f.a);
                if (mode == 0 || mode == 2)
                    f.runtime.logger.addHandler(
                            new Handler() {
                                public void publish(LogRecord r) {
                                    if (r.getMessage().equals("Disabling module Economy."))
                                        f.repository.failures.clear();
                                }

                                public void flush() {}

                                public void close() {}
                            });
                if (mode == 3) f.tasks.getFirst().refuse = true;
                manager.disableAll();
                var report = manager.getLastCleanupReport().orElseThrow();
                String failedName = mode == 2 ? "Quest" : "Reward";
                var consumer =
                        report.outcomes().stream()
                                .filter(x -> x.name().equals(failedName))
                                .findFirst()
                                .orElseThrow();
                var player =
                        report.outcomes().stream()
                                .filter(x -> x.name().equals("Player"))
                                .findFirst()
                                .orElseThrow();
                check(
                        predecessor.stops == 1 && manager.getEnabledModules().isEmpty(),
                        "real owner failure cannot skip remaining predecessors");
                if (mode == 4)
                    check(
                            report.completedWithoutReportedErrors(),
                            "real successful owners produce normal-call report");
                else
                    check(
                            consumer.status() == Status.THREW_EXCEPTION
                                    && manager.getObservedIncompleteOutcomes().stream()
                                            .anyMatch(x -> x.module() == consumer.module()),
                            "real failing owner identity retained despite later fallback");
                if (mode <= 2)
                    check(
                            contains(consumer.failure(), storage),
                            "real owner report keeps original persistence cause tree");
                if (mode == 1) {
                    check(
                            player.status() == Status.THREW_EXCEPTION
                                    && contains(player.failure(), storage)
                                    && f.players.getPlayer(f.a).orElseThrow() == original
                                    && f.players == originalPlayers,
                            "Reward and Player failures keep original cache identity and separate"
                                    + " errors");
                    check(
                            f.stored(f.a) == 0 && original.getWallet().getCoins() == 11,
                            "double failure distinguishes stale file from original RAM");
                    check(
                            failure(manager::enableAll) != null
                                    && f.players.getPlayer(f.a).orElseThrow() == original,
                            "explicit restart still respects original Player pending guard");
                } else
                    check(
                            player.status() == Status.RETURNED_NORMALLY,
                            "successful later Player fallback remains a distinct normal return");
                if (mode == 0)
                    check(
                            f.stored(f.a) == 11
                                    && f.cold(f.a).getWallet().getCoins() == 11
                                    && consumer.status() == Status.THREW_EXCEPTION,
                            "Player rescue cannot relabel earlier Reward failure as success");
                if (mode == 2)
                    check(
                            f.cold(f.a).getQuestState().snapshot().get("shutdown").progress() == 1
                                    && consumer.status() == Status.THREW_EXCEPTION,
                            "Player rescue cannot relabel earlier Quest failure as success");
                if (mode == 3) {
                    check(
                            f.stored(f.a) == 11
                                    && contains(
                                            consumer.failure(), f.tasks.getFirst().cancelFailure)
                                    && f.running() == 1,
                            "resource failure can coexist with committed wallet and retained"
                                    + " physical handle");
                    int n = f.repository.attempts.size();
                    f.tasks.getFirst().callback.run();
                    check(
                            f.repository.attempts.size() == n,
                            "retired remaining callback stays inert under manager report");
                }
                int saves = f.repository.attempts.size();
                byte[] bytes = f.bytes(f.a);
                manager.getObservedIncompleteOutcomes();
                report.outcomes();
                report.diagnostics();
                manager.getLastRollbackReport();
                manager.disableAll();
                check(
                        saves == f.repository.attempts.size()
                                && Arrays.equals(bytes, f.bytes(f.a))
                                && f.players == originalPlayers,
                        "report inspection and repeated stop never save replace services or replay"
                                + " rewards");
                f.repository.failures.clear();
                f.tasks.getFirst().refuse = false;
                if (mode == 2) quest.disable();
                f.module.disable();
                if (mode == 1) f.playerOwner.disable();
                check(
                        f.cold(f.a).getWallet().getCoins() == (mode == 2 ? 0 : 11),
                        "explicit owner-only recovery preserves exact wallet without replay");
            }
    }

    private static VapeeCore root(F f) {
        VapeeCore root = allocate(VapeeCore.class);
        set(root, "logger", f.logger);
        set(root, "moduleManager", f.manager);
        return root;
    }

    private static final class F implements AutoCloseable {
        final List<String> events = new ArrayList<>();
        final List<LogRecord> records = new ArrayList<>();
        final Logger logger = Logger.getAnonymousLogger();
        RuntimeException diagnostic = new IllegalStateException("controlled diagnostic");
        Predicate<LogRecord> throwWhen = r -> false;
        java.util.function.Consumer<LogRecord> onLog = r -> {};
        final ModuleManager manager;
        final M a, b, c;

        F() {
            logger.setUseParentHandlers(false);
            logger.addHandler(
                    new Handler() {
                        public void publish(LogRecord r) {
                            records.add(r);
                            onLog.accept(r);
                            if (throwWhen.test(r)) throw diagnostic;
                        }

                        public void flush() {}

                        public void close() {}
                    });
            manager = new ModuleManager(logger);
            a = new M("A", events);
            b = new M("B", events);
            c = new M("C", events);
            manager.register(a);
            manager.register(b);
            manager.register(c);
        }

        void start() {
            manager.enableAll();
            events.clear();
            records.clear();
        }

        ShutdownReport report() {
            return manager.getLastCleanupReport().orElseThrow();
        }

        public void close() {
            for (Handler h : logger.getHandlers()) logger.removeHandler(h);
        }
    }

    private static final class M implements CoreModule {
        final String id;
        String reportedName;
        final List<String> events;
        RuntimeException startup = new IllegalStateException("original startup"),
                cleanup = new IllegalStateException("original cleanup");
        boolean active, failEnable, failDisable, failName, afterEffect;
        int starts, stops, ownCleanup, nameCalls;
        Runnable onEnable = () -> {}, onDisable = () -> {};

        M(String name, List<String> events) {
            id = name;
            reportedName = name;
            this.events = events;
        }

        public String getName() {
            nameCalls++;
            if (failName) throw new IllegalStateException("late getName failure");
            return reportedName;
        }

        public void enable() {
            if (active) throw new IllegalStateException("synthetic original runtime pending");
            starts++;
            events.add("+" + id);
            active = true;
            try {
                onEnable.run();
                if (failEnable) throw startup;
            } catch (RuntimeException e) {
                active = false;
                ownCleanup++;
                throw e;
            }
        }

        public void disable() {
            stops++;
            events.add("-" + id);
            onDisable.run();
            if (failDisable && !afterEffect) throw cleanup;
            active = false;
            if (failDisable) throw cleanup;
        }
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
        return contains(root, target, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean contains(Throwable root, Throwable target, Set<Throwable> seen) {
        if (root == null || target == null || !seen.add(root)) return false;
        if (root == target) return true;
        if (contains(root.getCause(), target, seen)) return true;
        for (Throwable s : root.getSuppressed()) if (contains(s, target, seen)) return true;
        return false;
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
