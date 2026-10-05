package dev.vapee.core.persistence;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Technical commit boundary for lobby and player YAML only; callers own all schema rules. */
public final class SafeFileWriter {
    private final Logger logger;
    private final FileAccess files;

    public SafeFileWriter(Logger logger) {
        this(logger, new FileAccess() { });
    }

    public SafeFileWriter(Logger logger, FileAccess files) {
        this.logger = Objects.requireNonNull(logger, "logger");
        this.files = Objects.requireNonNull(files, "files");
    }

    public static Path recoveryFile(Path target) {
        return target.resolveSibling(target.getFileName() + ".vapeecore-recovery.bak");
    }

    public void requireReconciled(Path target) throws IOException {
        Path backup = recoveryFile(target);
        if (Files.exists(backup, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("Recovery file requires operator reconciliation before writing " + target + ": " + backup);
        }
    }

    public void write(Path target, Supplier<String> serialize, FileAction validate,
                      FileAction beforeReplace) throws IOException {
        requireReconciled(target);
        String content = serialize.get(); // No file opened until serialization has completed.
        Path temporary = null;
        try {
            temporary = Files.createTempFile(target.getParent(), ".vapeecore-" + target.getFileName() + "-", ".tmp");
            try (Writer writer = files.openWriter(temporary)) {
                writer.write(content);
            }
            validate.run(temporary);
            if (!Files.readString(temporary, StandardCharsets.UTF_8).equals(content)) {
                throw new IOException("Written candidate differs from serialized content: " + temporary);
            }
            beforeReplace.run(target);
            replace(temporary, target);
        } finally {
            cleanup(temporary);
        }
    }

    private void replace(Path temporary, Path target) throws IOException {
        try {
            files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Path backup = recoveryFile(target);
            boolean existed = !Files.notExists(target);
            if (existed) {
                if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
                    throw new IOException("Cannot protect non-regular destination " + target);
                }
                // No REPLACE_EXISTING: never truncate a previous unresolved recovery copy.
                files.copy(target, backup);
            }
            try {
                files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException | RuntimeException failure) {
                try {
                    if (existed) {
                        files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        files.deleteIfExists(target);
                    }
                } catch (IOException | RuntimeException restoration) {
                    failure.addSuppressed(restoration);
                    logger.log(Level.SEVERE, "Could not restore " + target + "; recovery copy retained at " + backup, restoration);
                    throw failure;
                }
                if (existed) cleanup(backup);
                throw failure;
            }
            if (existed) cleanup(backup);
        }
    }

    private void cleanup(Path path) {
        if (path == null) return;
        try {
            files.deleteIfExists(path);
        } catch (IOException | RuntimeException exception) {
            // Cleanup cannot undo a committed file or falsely trigger domain rollback.
            logger.log(Level.WARNING, "Could not clean VapeeCore persistence artifact " + path, exception);
        }
    }

    @FunctionalInterface
    public interface FileAction {
        void run(Path path) throws IOException;
    }

    /** Narrow external NIO seam: production uses these defaults, tests inject real I/O failures. */
    public interface FileAccess {
        default Writer openWriter(Path path) throws IOException {
            return Files.newBufferedWriter(path, StandardCharsets.UTF_8);
        }
        default void move(Path source, Path target, CopyOption... options) throws IOException {
            Files.move(source, target, options);
        }
        default void copy(Path source, Path target, CopyOption... options) throws IOException {
            Files.copy(source, target, options);
        }
        default void deleteIfExists(Path path) throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
