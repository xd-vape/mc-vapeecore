package dev.vapee.core.module;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ModuleManager {
    private final Logger logger;
    private final List<CoreModule> modules = new ArrayList<>();
    private final List<CoreModule> enabledModules = new ArrayList<>();
    private final Map<CoreModule, String> registeredNames = new IdentityHashMap<>();
    private boolean enabled, transitioning;
    private ShutdownReport lastCleanupReport, lastShutdownReport, lastRollbackReport;
    private List<ShutdownReport.ModuleOutcome> incompleteOutcomes = List.of();
    private List<ShutdownReport.DiagnosticFailure> startupDiagnostics = List.of();

    public ModuleManager(Logger logger) {
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void register(CoreModule module) {
        Objects.requireNonNull(module, "module");
        if (enabled || transitioning)
            throw new IllegalStateException(
                    "Modules cannot be registered after startup or during lifecycle changes");
        if (registeredNames.containsKey(module))
            throw new IllegalArgumentException(
                    "Module instance is already registered: " + registeredNames.get(module));
        String name = Objects.requireNonNull(module.getName(), "module name").trim();
        if (name.isEmpty()) throw new IllegalArgumentException("Module names must not be blank");
        if (registeredNames.values().stream().anyMatch(name::equalsIgnoreCase))
            throw new IllegalArgumentException(
                    "A module named '" + name + "' is already registered");
        modules.add(module);
        registeredNames.put(module, name);
    }

    public void enableAll() {
        requireIdle();
        if (enabled || !enabledModules.isEmpty())
            throw new IllegalStateException(
                    "Modules are already enabled or a startup was interrupted");
        if (incompleteOutcomes.stream()
                .anyMatch(
                        x ->
                                x.status() == ShutdownReport.Status.NOT_ATTEMPTED
                                        || x.status() == ShutdownReport.Status.OUTCOME_UNKNOWN))
            throw new IllegalStateException(
                    "An interrupted cleanup has unconfirmed calls; new acquisition is unsupported");
        // An explicit restart keeps the established contract; each original owner guards its
        // pending state.
        transitioning = true;
        var diagnostics = new ArrayList<ShutdownReport.DiagnosticFailure>();
        try {
            diagnose(
                    diagnostics,
                    "startup.begin",
                    null,
                    Level.INFO,
                    "Enabling " + modules.size() + " module(s).",
                    null);
            try {
                for (CoreModule module : modules) {
                    diagnose(
                            diagnostics,
                            "startup.module",
                            module,
                            Level.INFO,
                            "Enabling module " + registeredNames.get(module) + ".",
                            null);
                    module.enable();
                    enabledModules.add(module);
                }
                enabled = true;
            } catch (RuntimeException original) {
                diagnose(
                        diagnostics,
                        "startup.failure",
                        null,
                        Level.SEVERE,
                        "Module startup failed; rolling back enabled modules.",
                        original);
                cleanup(ShutdownReport.Phase.STARTUP_ROLLBACK, original, diagnostics);
                for (var outcome : lastRollbackReport.outcomes())
                    if (outcome.failure() != null) attachSecondary(original, outcome.failure());
                for (var diagnostic : lastRollbackReport.diagnostics())
                    attachSecondary(original, diagnostic.failure());
                throw original;
            }
            diagnose(
                    diagnostics,
                    "startup.complete",
                    null,
                    Level.INFO,
                    "Enabled " + enabledModules.size() + " module(s).",
                    null);
        } finally {
            startupDiagnostics = List.copyOf(diagnostics);
            transitioning = false;
        }
    }

    public void disableAll() {
        requireIdle();
        if (!enabled && enabledModules.isEmpty())
            return; // No new attempt, report erasure, diagnostic or retry.
        transitioning = true;
        try {
            cleanup(ShutdownReport.Phase.SHUTDOWN, null, List.of());
        } finally {
            transitioning = false;
        }
    }

    public List<CoreModule> getModules() {
        return List.copyOf(modules);
    }

    public List<CoreModule> getEnabledModules() {
        return List.copyOf(enabledModules);
    }

    public Optional<ShutdownReport> getLastCleanupReport() {
        return Optional.ofNullable(lastCleanupReport);
    }

    public Optional<ShutdownReport> getLastShutdownReport() {
        return Optional.ofNullable(lastShutdownReport);
    }

    public Optional<ShutdownReport> getLastRollbackReport() {
        return Optional.ofNullable(lastRollbackReport);
    }

    public List<ShutdownReport.ModuleOutcome> getObservedIncompleteOutcomes() {
        return incompleteOutcomes;
    }

    public List<ShutdownReport.DiagnosticFailure> getStartupDiagnostics() {
        return startupDiagnostics;
    }

    /** Attach a root completion diagnostic without running cleanup or changing module outcomes. */
    public void recordCompletionDiagnostic(RuntimeException failure) {
        requireIdle();
        Objects.requireNonNull(failure, "failure");
        if (lastCleanupReport == null) return;
        ShutdownReport prior = lastCleanupReport;
        ShutdownReport updated =
                prior.withDiagnostic(
                        new ShutdownReport.DiagnosticFailure(
                                "root.completion", null, null, failure));
        lastCleanupReport = updated;
        if (lastShutdownReport == prior) lastShutdownReport = updated;
        if (lastRollbackReport == prior) lastRollbackReport = updated;
    }

    private void cleanup(
            ShutdownReport.Phase phase,
            RuntimeException startupFailure,
            List<ShutdownReport.DiagnosticFailure> initialDiagnostics) {
        var diagnostics = new ArrayList<>(initialDiagnostics);
        var outcomes = new ArrayList<ShutdownReport.ModuleOutcome>();
        for (int i = enabledModules.size() - 1; i >= 0; i--) {
            CoreModule module = enabledModules.get(i);
            outcomes.add(
                    new ShutdownReport.ModuleOutcome(
                            module,
                            registeredNames.get(module),
                            ShutdownReport.Status.NOT_ATTEMPTED,
                            null));
        }
        String context = phase == ShutdownReport.Phase.SHUTDOWN ? "shutdown" : "rollback";
        try {
            diagnose(
                    diagnostics,
                    context + ".begin",
                    null,
                    Level.INFO,
                    (phase == ShutdownReport.Phase.SHUTDOWN ? "Disabling " : "Rolling back ")
                            + outcomes.size()
                            + " module(s).",
                    null);
            for (int i = 0; i < outcomes.size(); i++) {
                var pending = outcomes.get(i);
                CoreModule module = pending.module();
                diagnose(
                        diagnostics,
                        context + ".module",
                        module,
                        Level.INFO,
                        "Disabling module " + pending.name() + ".",
                        null);
                outcomes.set(
                        i,
                        new ShutdownReport.ModuleOutcome(
                                module,
                                pending.name(),
                                ShutdownReport.Status.OUTCOME_UNKNOWN,
                                null));
                try {
                    module.disable();
                    outcomes.set(
                            i,
                            new ShutdownReport.ModuleOutcome(
                                    module,
                                    pending.name(),
                                    ShutdownReport.Status.RETURNED_NORMALLY,
                                    null));
                } catch (RuntimeException failure) {
                    outcomes.set(
                            i,
                            new ShutdownReport.ModuleOutcome(
                                    module,
                                    pending.name(),
                                    ShutdownReport.Status.THREW_EXCEPTION,
                                    failure));
                    diagnose(
                            diagnostics,
                            context + ".failure",
                            module,
                            Level.SEVERE,
                            "Could not disable module "
                                    + pending.name()
                                    + " during "
                                    + context
                                    + ".",
                            failure);
                }
            }
            var observed = new ShutdownReport(phase, outcomes, diagnostics, startupFailure);
            if (!observed.allCallsReturnedNormally())
                diagnose(
                        diagnostics,
                        context + ".complete",
                        null,
                        Level.SEVERE,
                        "Module "
                                + context
                                + " incomplete; disable calls threw or remain unconfirmed: "
                                + observed.incompleteOutcomes().stream()
                                        .map(ShutdownReport.ModuleOutcome::name)
                                        .toList()
                                + ". No automatic retry is scheduled.",
                        null);
            else if (!diagnostics.isEmpty())
                diagnose(
                        diagnostics,
                        context + ".complete",
                        null,
                        Level.WARNING,
                        "All "
                                + outcomes.size()
                                + " module "
                                + context
                                + " calls returned normally; "
                                + diagnostics.size()
                                + " diagnostic operation(s) failed.",
                        null);
            else
                diagnose(
                        diagnostics,
                        context + ".complete",
                        null,
                        Level.INFO,
                        "All "
                                + outcomes.size()
                                + " module "
                                + context
                                + " calls returned normally.",
                        null);
        } finally {
            enabledModules.clear();
            enabled = false;
            lastCleanupReport = new ShutdownReport(phase, outcomes, diagnostics, startupFailure);
            if (phase == ShutdownReport.Phase.SHUTDOWN) lastShutdownReport = lastCleanupReport;
            else lastRollbackReport = lastCleanupReport;
            retainIncomplete(lastCleanupReport.incompleteOutcomes());
        }
    }

    private void retainIncomplete(List<ShutdownReport.ModuleOutcome> observed) {
        var retained = new ArrayList<>(incompleteOutcomes);
        for (var outcome : observed) {
            retained.removeIf(old -> old.module() == outcome.module());
            retained.add(outcome);
        }
        // Bounded by registered identities; these observations are not a retry queue.
        incompleteOutcomes = List.copyOf(retained);
    }

    private void diagnose(
            List<ShutdownReport.DiagnosticFailure> diagnostics,
            String operation,
            CoreModule module,
            Level level,
            String message,
            Throwable cause) {
        try {
            logger.log(level, message, cause);
        } catch (RuntimeException failure) {
            diagnostics.add(
                    new ShutdownReport.DiagnosticFailure(
                            operation,
                            module,
                            module == null ? null : registeredNames.get(module),
                            failure));
        }
    }

    private void requireIdle() {
        if (transitioning)
            throw new IllegalStateException("ModuleManager is already changing lifecycle");
    }

    private static void attachSecondary(Throwable primary, Throwable secondary) {
        try {
            if (primary != secondary
                    && !containsIdentity(primary, secondary)
                    && !containsIdentity(secondary, primary)) primary.addSuppressed(secondary);
        } catch (RuntimeException ignored) {
            /* An unreadable external cause tree cannot replace the primary; the report keeps both references. */
        }
    }

    private static boolean containsIdentity(Throwable root, Throwable target) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Throwable>();
        pending.add(root);
        while (!pending.isEmpty()) {
            Throwable current = pending.removeFirst();
            if (!seen.add(current)) continue;
            if (current == target) return true;
            Throwable cause = current.getCause();
            if (cause != null) pending.add(cause);
            for (Throwable suppressed : current.getSuppressed()) pending.add(suppressed);
        }
        return false;
    }
}
