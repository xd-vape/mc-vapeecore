package dev.vapee.core.persistence;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Real temporary files and NIO operations, with failures at external I/O boundaries only. */
public final class PersistenceFaults implements SafeFileWriter.FileAccess {
    public enum Fault { NONE, WRITE, WRITE_AND_CLEANUP, CLOSE, INVALID_YAML, INVALID_SPAWN, ATOMIC_IO,
        UNSUPPORTED, REPLACE, BACKUP, RESTORE, TEMP_CLEANUP, BACKUP_CLEANUP, MIGRATION_BACKUP }
    public Fault fault;
    public final List<String> events = new ArrayList<>();
    public Path candidate;
    public Runnable onClosed = () -> { };

    public PersistenceFaults(Fault fault) { this.fault = fault; }

    @Override public Writer openWriter(Path path) throws IOException {
        candidate = path;
        events.add("open");
        return new FilterWriter(SafeFileWriter.FileAccess.super.openWriter(path)) {
            @Override public void write(String text, int offset, int length) throws IOException {
                events.add("write");
                if (fault == Fault.WRITE || fault == Fault.WRITE_AND_CLEANUP) {
                    super.write(text, offset, Math.min(8, length));
                    throw new IOException("injected partial write");
                }
                super.write(text, offset, length);
            }
            @Override public void close() throws IOException {
                super.close();
                events.add("close");
                if (fault == Fault.CLOSE) throw new IOException("injected close");
                if (fault == Fault.INVALID_YAML) Files.writeString(path, "spawn: [broken\n");
                if (fault == Fault.INVALID_SPAWN) Files.writeString(path,
                        "spawn: {world: world, x: wrong, y: 64, z: 0, yaw: 0, pitch: 0}\n");
                onClosed.run();
            }
        };
    }

    @Override public void move(Path source, Path target, CopyOption... options) throws IOException {
        boolean atomic = Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE);
        events.add(atomic ? "atomic" : "replace");
        if (atomic) {
            if (fault == Fault.ATOMIC_IO) throw new IOException("injected ordinary atomic error");
            if (List.of(Fault.UNSUPPORTED, Fault.REPLACE, Fault.BACKUP, Fault.RESTORE,
                    Fault.BACKUP_CLEANUP).contains(fault)) {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "injected unsupported");
            }
        } else if (fault == Fault.REPLACE || fault == Fault.RESTORE) {
            // Model the dangerous allowed non-atomic failure: target already truncated.
            Files.writeString(target, "partial destination");
            throw new IOException("injected replacement failure after damage");
        }
        SafeFileWriter.FileAccess.super.move(source, target, options);
    }

    @Override public void copy(Path source, Path target, CopyOption... options) throws IOException {
        boolean restore = source.toString().endsWith(".vapeecore-recovery.bak");
        boolean migration = target.toString().endsWith(".vapeecore-pre-migration.bak");
        events.add(restore ? "restore" : migration ? "migration" : "backup");
        if ((fault == Fault.BACKUP && !restore) || (fault == Fault.RESTORE && restore)
                || (fault == Fault.MIGRATION_BACKUP && migration)) {
            throw new IOException("injected backup/restore copy failure");
        }
        SafeFileWriter.FileAccess.super.copy(source, target, options);
    }

    @Override public void deleteIfExists(Path path) throws IOException {
        events.add("cleanup:" + path.getFileName());
        if (((fault == Fault.TEMP_CLEANUP || fault == Fault.WRITE_AND_CLEANUP) && path.toString().endsWith(".tmp"))
                || (fault == Fault.BACKUP_CLEANUP && path.toString().endsWith(".bak"))) {
            throw new IOException("injected cleanup failure");
        }
        SafeFileWriter.FileAccess.super.deleteIfExists(path);
    }
}
