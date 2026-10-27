package dev.vapee.core.persistence;

import static dev.vapee.core.persistence.PersistenceFaults.Fault;
import static dev.vapee.core.player.repository.PersistenceDiagnosticFixture.contains;

import dev.vapee.core.player.repository.PersistenceDiagnosticFixture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Actual writer replacement/restore/cleanup boundaries and byte-exact evidence. */
public final class SafeFileWriterDiagnosticHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            commits();
            precommit();
            restoration();
            recovery();
            uncertain();
        } else
            switch (args[0]) {
                case "commits" -> commits();
                case "precommit" -> precommit();
                case "restoration" -> restoration();
                case "recovery" -> recovery();
                case "uncertain" -> uncertain();
                default -> throw new AssertionError(args[0]);
            }
        System.out.println("SafeFileWriterDiagnosticHarness passed " + checks + " checks.");
    }

    private static void commits() throws Exception {
        for (Fault mode :
                List.of(Fault.NONE, Fault.UNSUPPORTED, Fault.TEMP_CLEANUP, Fault.BACKUP_CLEANUP))
            for (boolean throwing : List.of(false, true))
                try (var f = new PersistenceDiagnosticFixture()) {
                    Path target = f.directory.resolve("commit.yml");
                    byte[] prior =
                            "prior bytes\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    Files.write(target, prior);
                    f.access.fault(mode);
                    f.throwWarning = throwing;
                    Exception error =
                            write(
                                    f,
                                    target,
                                    () -> "committed bytes\n",
                                    p -> {
                                        check(
                                                Files.readString(p).equals("committed bytes\n"),
                                                "real candidate validation reads complete bytes");
                                        f.access.events.add("validated");
                                    },
                                    p -> f.access.events.add("beforeReplace"));
                    check(
                            error == null,
                            "completed replacement survives cleanup and WARNING failure");
                    check(
                            Files.readString(target).equals("committed bytes\n"),
                            "actual completed destination bytes");
                    check(
                            f.access.commits.equals(List.of(target)),
                            "cleanup does not retry a completed replacement");
                    check(
                            f.access.events.indexOf("validated")
                                            < f.access.events.indexOf("beforeReplace")
                                    && f.access.events.indexOf("beforeReplace")
                                            < f.access.events.indexOf("atomic"),
                            "validation and source protection precede atomic move");
                    boolean fallback = mode == Fault.UNSUPPORTED || mode == Fault.BACKUP_CLEANUP;
                    check(
                            f.access.events.contains("replace") == fallback,
                            "fallback selected only for unsupported atomic move");
                    Path backup = SafeFileWriter.recoveryFile(target);
                    if (mode == Fault.BACKUP_CLEANUP) {
                        check(
                                Files.exists(backup)
                                        && Arrays.equals(prior, Files.readAllBytes(backup)),
                                "obsolete fallback backup retained byte-exact after cleanup"
                                        + " failure");
                        check(
                                blocked(new SafeFileWriter(f.logger, f.access), target),
                                "successful fallback with retained marker still requires"
                                        + " reconciliation");
                    } else
                        check(
                                !Files.exists(backup),
                                "no unexpected recovery artifact after normal commit");
                    if (mode == Fault.TEMP_CLEANUP || mode == Fault.BACKUP_CLEANUP) {
                        check(
                                f.logs.stream().anyMatch(r -> r.getLevel().intValue() == 900),
                                "cleanup warning remains attempted");
                        check(
                                !Files.exists(f.access.delegate.candidate),
                                "successful real move already consumed candidate");
                        if (throwing)
                            check(
                                    contains(f.access.errors.getLast(), f.diagnostics.getLast()),
                                    "cleanup diagnostic preserved secondarily on cleanup error");
                    }
                }
        try (var f = new PersistenceDiagnosticFixture()) {
            Path p = f.directory.resolve("runtime.yml");
            Files.writeString(p, "old");
            f.access.runtimeCleanup = true;
            f.throwWarning = true;
            check(
                    write(f, p, () -> "new", x -> {}, x -> {}) == null
                            && Files.readString(p).equals("new"),
                    "runtime cleanup and diagnostic cannot fail confirmed commit");
            check(
                    contains(f.access.cleanupRuntime, f.diagnostics.getLast()),
                    "runtime cleanup keeps secondary diagnostic");
        }
    }

    private static void precommit() throws Exception {
        for (Fault mode :
                List.of(Fault.WRITE, Fault.WRITE_AND_CLEANUP, Fault.CLOSE, Fault.ATOMIC_IO))
            try (var f = new PersistenceDiagnosticFixture()) {
                Path target = f.directory.resolve("failure.yml");
                byte[] prior = "known prior\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                Files.write(target, prior);
                f.access.fault(mode);
                f.throwWarning = true;
                Exception error = write(f, target, () -> "replacement", p -> {}, p -> {});
                check(
                        error != null && contains(error, f.access.errors.getFirst()),
                        "true precommit IO remains primary and visible");
                check(
                        Arrays.equals(prior, Files.readAllBytes(target)),
                        "true precommit file stays byte-exact");
                check(f.access.commits.isEmpty(), "failed precommit never reports physical commit");
                check(
                        !f.access.events.contains("replace")
                                && !Files.exists(SafeFileWriter.recoveryFile(target)),
                        "ordinary IO must not select fallback");
                if (mode == Fault.WRITE_AND_CLEANUP)
                    check(
                            Files.exists(f.access.delegate.candidate),
                            "failed candidate cleanup preserves actual remaining artifact");
            }
        try (var f = new PersistenceDiagnosticFixture()) {
            Path target = f.directory.resolve("validation.yml");
            Files.writeString(target, "old");
            IOException invalid = new IOException("candidate semantic validation refused");
            Exception error =
                    write(
                            f,
                            target,
                            () -> "invalid semantics",
                            p -> {
                                throw invalid;
                            },
                            p -> {});
            check(
                    error == invalid && Files.readString(target).equals("old"),
                    "candidate validation failure propagates before commit");
            check(
                    !f.access.events.contains("atomic"),
                    "validation refusal cannot enter replacement");
            IOException conflict = new IOException("source changed");
            error =
                    write(
                            f,
                            target,
                            () -> "new",
                            p -> {},
                            p -> {
                                Files.writeString(p, "external edit");
                                throw conflict;
                            });
            check(
                    error == conflict && Files.readString(target).equals("external edit"),
                    "source protection failure preserves external bytes");
            check(!f.access.events.contains("atomic"), "source conflict cannot enter replacement");
            f.access.fault(Fault.INVALID_SPAWN);
            error = write(f, target, () -> "exact serialized text", p -> {}, p -> {});
            check(
                    error instanceof IOException
                            && error.getMessage().contains("differs from serialized"),
                    "exact serialized candidate comparison remains enforced");
            check(
                    Files.readString(target).equals("external edit"),
                    "candidate text mismatch preserves destination");
        }
    }

    private static void restoration() throws Exception {
        for (Fault mode : List.of(Fault.REPLACE, Fault.RESTORE))
            for (boolean throwing : List.of(false, true))
                try (var f = new PersistenceDiagnosticFixture()) {
                    Path target = f.directory.resolve("restore.yml");
                    byte[] prior =
                            "whole original recovery bytes\n"
                                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    Files.write(target, prior);
                    f.access.fault(mode);
                    f.throwSevere = throwing;
                    Exception error = write(f, target, () -> "new candidate", p -> {}, p -> {});
                    IOException original =
                            f.access.errors.stream()
                                    .filter(x -> x.getMessage().contains("replacement failure"))
                                    .findFirst()
                                    .orElseThrow();
                    check(
                            error == original,
                            "original replacement error remains primary despite restore"
                                    + " diagnostics");
                    check(
                            f.access.events.indexOf("backup") < f.access.events.indexOf("replace")
                                    && f.access.events.indexOf("replace")
                                            < f.access.events.indexOf("restore"),
                            "backup replacement restoration actual ordering");
                    Path backup = SafeFileWriter.recoveryFile(target);
                    if (mode == Fault.RESTORE) {
                        IOException restoration =
                                f.access.errors.stream()
                                        .filter(x -> x.getMessage().contains("backup/restore"))
                                        .findFirst()
                                        .orElseThrow();
                        check(
                                Arrays.asList(error.getSuppressed()).contains(restoration),
                                "restoration failure remains suppressed");
                        if (throwing)
                            check(
                                    Arrays.asList(error.getSuppressed())
                                            .contains(f.diagnostics.getLast()),
                                    "SEVERE diagnostic is secondary to replacement failure");
                        check(
                                Files.isRegularFile(backup)
                                        && Arrays.equals(prior, Files.readAllBytes(backup)),
                                "failed restoration preserves byte-exact recovery evidence");
                        check(
                                Files.readString(target).equals("partial destination"),
                                "damaged destination is not mislabeled restored");
                        check(
                                blocked(new SafeFileWriter(f.logger, f.access), target),
                                "failed restore blocks later writes");
                        check(
                                !f.access.events.stream()
                                        .anyMatch(x -> x.equals("cleanup:" + backup.getFileName())),
                                "failed restore must not delete recovery evidence");
                    } else {
                        check(
                                Arrays.equals(prior, Files.readAllBytes(target)),
                                "successful restoration restores exact prior file");
                        check(
                                !Files.exists(backup),
                                "completed restoration permits obsolete backup cleanup");
                    }
                    check(
                            f.access.commits.isEmpty(),
                            "failed fallback never converted to successful commit");
                }
    }

    private static void recovery() throws Exception {
        try (var f = new PersistenceDiagnosticFixture()) {
            Path target = f.directory.resolve("blocked.yml"),
                    backup = SafeFileWriter.recoveryFile(target);
            Files.writeString(target, "current");
            Files.writeString(backup, "unresolved evidence");
            f.access.fault(Fault.UNSUPPORTED);
            Exception error = write(f, target, () -> "new", p -> {}, p -> {});
            check(error instanceof IOException, "unresolved recovery marker blocks writer");
            check(
                    Files.readString(backup).equals("unresolved evidence")
                            && Files.readString(target).equals("current"),
                    "existing recovery bytes cannot be overwritten");
            check(f.access.events.isEmpty(), "recovery refusal precedes candidate acquisition");
        }
        try (var f = new PersistenceDiagnosticFixture()) {
            Path target = f.directory.resolve("concurrent.yml"),
                    backup = SafeFileWriter.recoveryFile(target);
            Files.writeString(target, "prior");
            f.access.fault(Fault.UNSUPPORTED);
            Exception error =
                    write(
                            f,
                            target,
                            () -> "new",
                            p -> {},
                            p -> Files.writeString(backup, "concurrent recovery evidence"));
            check(
                    error instanceof IOException,
                    "concurrent recovery evidence refuses fallback publication");
            check(
                    Files.readString(target).equals("prior")
                            && Files.readString(backup).equals("concurrent recovery evidence"),
                    "new recovery evidence cannot be overwritten during fallback protection");
            check(
                    f.access.commits.isEmpty(),
                    "recovery protection failure leaves no confirmed commit");
        }
    }

    private static void uncertain() throws Exception {
        try (var f = new PersistenceDiagnosticFixture()) {
            Path target = f.directory.resolve("uncertain.yml");
            Files.writeString(target, "old");
            f.access.atomicAfterEffect = true;
            Exception error = write(f, target, () -> "new", p -> {}, p -> {});
            check(
                    error instanceof IOException && error == f.access.errors.getFirst(),
                    "after-effect move failure must not manufacture success");
            check(
                    Files.readString(target).equals("new"),
                    "uncertain return distinguished from independently observed file effect");
            check(
                    !f.access.events.contains("replace") && f.access.commits.size() == 1,
                    "uncertain atomic failure never retried as unsupported fallback");
        }
    }

    private static Exception write(
            PersistenceDiagnosticFixture f,
            Path p,
            java.util.function.Supplier<String> value,
            SafeFileWriter.FileAction validate,
            SafeFileWriter.FileAction protect) {
        try {
            new SafeFileWriter(f.logger, f.access).write(p, value, validate, protect);
            return null;
        } catch (IOException | RuntimeException e) {
            return e;
        }
    }

    private static boolean blocked(SafeFileWriter writer, Path p) {
        try {
            writer.requireReconciled(p);
            return false;
        } catch (IOException expected) {
            return true;
        }
    }

    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
}
