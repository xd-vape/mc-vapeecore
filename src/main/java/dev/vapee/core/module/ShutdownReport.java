package dev.vapee.core.module;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Immutable observations of calls, not proof of internal persistence or a retry authorization. */
public record ShutdownReport(
        Phase phase,
        List<ModuleOutcome> outcomes,
        List<DiagnosticFailure> diagnostics,
        RuntimeException startupFailure) {
    public ShutdownReport {
        Objects.requireNonNull(phase, "phase");
        outcomes = List.copyOf(outcomes);
        diagnostics = List.copyOf(diagnostics);
        if (phase == Phase.SHUTDOWN && startupFailure != null)
            throw new IllegalArgumentException("Shutdown cannot own a startup failure");
    }

    public enum Phase {
        SHUTDOWN,
        STARTUP_ROLLBACK
    }

    public enum Status {
        RETURNED_NORMALLY,
        THREW_EXCEPTION,
        NOT_ATTEMPTED,
        OUTCOME_UNKNOWN
    }

    public record ModuleOutcome(
            CoreModule module, String name, Status status, RuntimeException failure) {
        public ModuleOutcome {
            Objects.requireNonNull(module, "module");
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(status, "status");
            if ((status == Status.THREW_EXCEPTION) != (failure != null))
                throw new IllegalArgumentException(
                        "Only an observed RuntimeException has a failure reference");
        }
    }

    public record DiagnosticFailure(
            String operation, CoreModule module, String moduleName, RuntimeException failure) {
        public DiagnosticFailure {
            Objects.requireNonNull(operation, "operation");
            Objects.requireNonNull(failure, "failure");
            if ((module == null) != (moduleName == null))
                throw new IllegalArgumentException("Diagnostic module identity must be complete");
        }
    }

    public boolean allCallsReturnedNormally() {
        return outcomes.stream().allMatch(x -> x.status() == Status.RETURNED_NORMALLY);
    }

    public boolean completedWithoutReportedErrors() {
        return startupFailure == null && allCallsReturnedNormally() && diagnostics.isEmpty();
    }

    public List<ModuleOutcome> incompleteOutcomes() {
        return outcomes.stream().filter(x -> x.status() != Status.RETURNED_NORMALLY).toList();
    }

    public ShutdownReport withDiagnostic(DiagnosticFailure diagnostic) {
        var copy = new ArrayList<>(diagnostics);
        copy.add(Objects.requireNonNull(diagnostic));
        return new ShutdownReport(phase, outcomes, copy, startupFailure);
    }
}
