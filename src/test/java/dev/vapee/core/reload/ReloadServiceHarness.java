package dev.vapee.core.reload;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Exercises the production coordinator through observable participant calls and results. */
public final class ReloadServiceHarness {
    private static int checks;

    public static void main(String[] args) {
        Fixture success = new Fixture();
        success.assertSuccess();
        success.assertSuccess();

        Fixture prepare = new Fixture();
        prepare.prepareFailure = "B";
        prepare.assertResult(prepare.service.reload(), ReloadResult.Status.PREPARE_FAILED, "B", 1, 0);
        prepare.assertCalls("prepare A", "prepare B");
        prepare.assertCause(prepare.failure, "prepare", "B");
        // Prepare is read/candidate construction: no plan was applied, so no rollback is owned.
        prepare.recover();

        Fixture apply = new Fixture();
        apply.applyFailure = "B";
        apply.assertResult(apply.service.reload(), ReloadResult.Status.APPLY_FAILED, "B", 3, 1);
        apply.assertCalls("prepare A", "prepare B", "prepare C", "apply A", "apply B", "rollback B", "rollback A");
        apply.assertCause(apply.failure, "apply", "B");
        apply.recover();

        Fixture reverse = new Fixture();
        reverse.applyFailure = "C";
        reverse.assertResult(reverse.service.reload(), ReloadResult.Status.APPLY_FAILED, "C", 3, 2);
        reverse.assertCalls("prepare A", "prepare B", "prepare C", "apply A", "apply B", "apply C",
                "rollback C", "rollback B", "rollback A");
        reverse.recover();

        // Failed plan rollback is distinct from rollback of successfully applied predecessors.
        Fixture failedPlan = new Fixture();
        failedPlan.applyFailure = "A";
        failedPlan.rollbackFailures = List.of("A");
        failedPlan.assertResult(failedPlan.service.reload(), ReloadResult.Status.ROLLBACK_INCOMPLETE, "A", 3, 0);
        failedPlan.assertCalls("prepare A", "prepare B", "prepare C", "apply A", "rollback A");
        failedPlan.assertCause(failedPlan.failure, "apply", "A");
        failedPlan.assertCause(failedPlan.rollbackFailure, "roll back", "A");
        failedPlan.recover();

        for (List<String> failingRollbacks : List.of(List.of("C"), List.of("B"), List.of("C", "B"))) {
            Fixture isolation = new Fixture();
            isolation.applyFailure = "C";
            isolation.rollbackFailures = failingRollbacks;
            isolation.assertResult(isolation.service.reload(), ReloadResult.Status.ROLLBACK_INCOMPLETE, "C", 3, 2);
            isolation.assertCalls("prepare A", "prepare B", "prepare C", "apply A", "apply B", "apply C",
                    "rollback C", "rollback B", "rollback A");
            isolation.assertCause(isolation.failure, "apply", "C");
            for (String name : failingRollbacks) isolation.assertCause(isolation.rollbackFailure, "roll back", name);
            check(isolation.logs.stream().anyMatch(log -> log.getMessage().contains("rollback was incomplete")),
                    "incomplete rollback advice is visible");
            isolation.recover();
        }

        // Two nested attempts in each phase also detect a nested call incorrectly clearing running.
        for (String phase : List.of("prepare", "apply", "rollback")) {
            Fixture nested = new Fixture();
            nested.reenterAt = phase + " A";
            if (phase.equals("rollback")) nested.applyFailure = "A";
            ReloadResult result = nested.service.reload();
            nested.assertResult(result, phase.equals("rollback") ? ReloadResult.Status.APPLY_FAILED
                    : ReloadResult.Status.SUCCESS, phase.equals("rollback") ? "A" : "", 3,
                    phase.equals("rollback") ? 0 : 3);
            check(nested.nestedCalls == 2, "both real nested calls executed during " + phase);
            if (phase.equals("rollback")) {
                nested.assertCalls("prepare A", "prepare B", "prepare C", "apply A", "rollback A");
            } else {
                nested.assertCalls("prepare A", "prepare B", "prepare C", "apply A", "apply B", "apply C");
            }
            nested.recover();
        }
        System.out.println("ReloadServiceHarness passed " + checks + " checks.");
    }

    private static final class Fixture {
        final List<String> calls = new ArrayList<>();
        final List<LogRecord> logs = new ArrayList<>();
        final RuntimeException failure = new IllegalStateException("injected participant failure");
        final RuntimeException rollbackFailure = new IllegalStateException("injected rollback failure");
        final ReloadService service;
        String prepareFailure = "";
        String applyFailure = "";
        List<String> rollbackFailures = List.of();
        String reenterAt = "";
        int nestedCalls;

        Fixture() {
            Logger logger = Logger.getAnonymousLogger();
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                @Override public void publish(LogRecord record) { logs.add(record); }
                @Override public void flush() { }
                @Override public void close() { }
            });
            service = new ReloadService(logger, List.of(participant("A"), participant("B"), participant("C")));
        }

        ReloadParticipant participant(String name) {
            return new ReloadParticipant() {
                @Override public String getReloadName() { return name; }
                @Override public ReloadPlan prepareReload() {
                    visit("prepare", name);
                    if (prepareFailure.equals(name)) throw failure;
                    return ReloadPlan.of(() -> {
                        visit("apply", name);
                        if (applyFailure.equals(name)) throw failure;
                    }, () -> {
                        visit("rollback", name);
                        if (rollbackFailures.contains(name)) throw rollbackFailure;
                    });
                }
            };
        }

        void visit(String phase, String name) {
            calls.add(phase + " " + name);
            if (!reenterAt.equals(phase + " " + name)) return;
            for (int attempt = 0; attempt < 2; attempt++) {
                int previousCalls = calls.size();
                assertResult(service.reload(), ReloadResult.Status.ALREADY_RUNNING, "", 0, 0);
                check(calls.size() == previousCalls, "nested call must not enter participant chain");
                nestedCalls++;
            }
        }

        void recover() {
            prepareFailure = "";
            applyFailure = "";
            rollbackFailures = List.of();
            reenterAt = "";
            assertSuccess();
        }

        void assertSuccess() {
            calls.clear();
            assertResult(service.reload(), ReloadResult.Status.SUCCESS, "", 3, 3);
            assertCalls("prepare A", "prepare B", "prepare C", "apply A", "apply B", "apply C");
        }

        void assertCalls(String... expected) {
            check(calls.equals(List.of(expected)), "observed order: " + calls + "; expected " + List.of(expected));
        }

        void assertCause(Throwable cause, String action, String name) {
            check(logs.stream().anyMatch(log -> log.getThrown() == cause
                    && log.getMessage().contains(action) && log.getMessage().endsWith(name + ".")),
                    "original cause and participant context visible for " + action + " " + name);
        }

        void assertResult(ReloadResult result, ReloadResult.Status status, String failed, int prepared, int applied) {
            check(result.status() == status, "result status expected " + status + " but was " + result.status());
            check(result.isSuccess() == (status == ReloadResult.Status.SUCCESS), "success classification");
            check(result.failedComponent().equals(failed), "failed component");
            check(result.preparedComponents() == prepared, "prepared count");
            check(result.appliedComponents() == applied, "applied count");
            check(result.durationMillis() >= 0, "nonnegative duration without timing assumption");
        }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
