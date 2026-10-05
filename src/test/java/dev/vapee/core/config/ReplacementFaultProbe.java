package dev.vapee.core.config;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Substitutes only file operations. The real config replacement/state code is executed. */
public final class ReplacementFaultProbe {
    public enum Mode { ATOMIC, REPLACE, COPY, FAIL }

    public final Mode mode;
    public final List<String> calls = new ArrayList<>();
    public final List<Long> waits = new ArrayList<>();
    public final List<IOException> faults = new ArrayList<>();
    public final List<Long> threads = new ArrayList<>();
    public int commits;
    public Runnable beforeOperation = () -> { };
    public Runnable afterCommit = () -> { };
    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final boolean unsupported;

    public ReplacementFaultProbe(Mode mode, boolean unsupported) {
        this.mode = mode;
        this.unsupported = unsupported;
        // Initialize management access before the measured replacement boundaries.
        threadBean.getThreadInfo(Thread.currentThread().threadId());
    }

    public void move(Path source, Path target, CopyOption... options) throws IOException {
        boolean atomic = Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE);
        observe(atomic ? "atomic" : "replace");
        if (atomic && mode != Mode.ATOMIC) {
            fail(unsupported ? new AtomicMoveNotSupportedException(source.toString(), target.toString(), "injected")
                    : new IOException("injected atomic failure"));
        }
        if (!atomic && mode != Mode.REPLACE) fail(new IOException("injected move failure"));
        // A forced atomic success still performs a real replacement on hosts without atomic move support.
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        commits++;
        afterCommit.run();
    }

    public void copy(Path source, Path target) throws IOException {
        observe("copy");
        if (mode == Mode.FAIL) fail(new IOException("injected copy failure"));
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        commits++;
        afterCommit.run();
    }

    private void observe(String operation) {
        long thread = Thread.currentThread().threadId();
        calls.add(operation);
        threads.add(thread);
        // Thread.sleep increments the caller's waited count; no wall-clock threshold is used.
        waits.add(threadBean.getThreadInfo(thread).getWaitedCount());
        beforeOperation.run();
    }

    private void fail(IOException failure) throws IOException {
        faults.add(failure);
        throw failure;
    }
}
