package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.persistence.SafeFileWriter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/** Main-thread editor transaction. No scheduler, alternate data store or reload participant. */
final class NavigatorStore {
    record Snapshot(long revision, byte[] bytes, NavigatorConfig.State state) {
        Snapshot { bytes = bytes.clone(); }
        @Override public byte[] bytes() { return bytes.clone(); }
    }
    private final NavigatorConfig config;
    private final SafeFileWriter writer;
    private final Consumer<NavigatorConfig.State> apply;
    private long revision;
    private boolean recoveryRequired;
    boolean isActive() { return config.getState().enabled(); }

    NavigatorStore(NavigatorConfig config, Logger logger, Consumer<NavigatorConfig.State> apply) {
        this(config, new SafeFileWriter(logger), apply);
    }
    NavigatorStore(NavigatorConfig config, SafeFileWriter writer, Consumer<NavigatorConfig.State> apply) {
        this.config = config; this.writer = writer; this.apply = apply;
    }
    Snapshot begin() throws IOException {
        byte[] bytes = read();
        return new Snapshot(revision, bytes, NavigatorConfig.parseForEditing(new String(bytes, StandardCharsets.UTF_8)));
    }
    private byte[] read() throws IOException {
        if (recoveryRequired) throw new IOException("Navigator recovery is required; inspect protected backups before restarting.");
        writer.requireReconciled(config.file());
        if (!Files.isRegularFile(config.file(), LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Navigator must be a regular file, not a link.");
        byte[] bytes = Files.readAllBytes(config.file());
        // Reject malformed UTF-8 rather than losing original bytes during rollback serialization.
        StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes));
        return bytes;
    }
    private void unchanged(Snapshot base) throws IOException {
        if (base.revision() != revision || !Arrays.equals(base.bytes(), read()))
            throw new IOException("Navigator changed externally or another administrator saved. Discard and reopen the editor.");
    }
    Path save(Snapshot base, NavigatorConfig.State draft) throws IOException {
        String candidate = NavigatorDocument.write(draft);
        var validated = NavigatorConfig.parse(candidate);
        if (!validated.enabled()) throw new IllegalArgumentException("Editor Save must explicitly activate V2.");
        unchanged(base);
        Path backups = config.file().resolveSibling("navigator-backups");
        if (Files.exists(backups, LinkOption.NOFOLLOW_LINKS) && !Files.isDirectory(backups, LinkOption.NOFOLLOW_LINKS))
            throw new IOException("Navigator backup path must be a real directory.");
        Files.createDirectories(backups);
        Path backup = backups.resolve("navigator-" + UUID.randomUUID() + ".yml");
        Files.write(backup, base.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        if (!Arrays.equals(base.bytes(), Files.readAllBytes(backup))) throw new IOException("Backup verification failed.");
        var previous = config.getState();
        writer.write(config.file(), () -> candidate,
                path -> NavigatorConfig.parse(Files.readString(path)), path -> unchanged(base));
        try {
            config.apply(validated);
            apply.accept(validated);
        } catch (RuntimeException failure) {
            config.apply(previous); // Pointer rollback cannot call plugin code or fail.
            try {
                byte[] expected = candidate.getBytes(StandardCharsets.UTF_8);
                if (!Arrays.equals(expected, read())) throw new IOException("File changed during runtime rollback.");
                // Restore the exact bytes, including original whitespace and disabled composition.
                writer.write(config.file(), () -> new String(base.bytes(), StandardCharsets.UTF_8), path -> { }, path -> {
                    if (!Arrays.equals(expected, read())) throw new IOException("File changed during rollback.");
                });
            } catch (IOException | RuntimeException restoration) {
                recoveryRequired = true;
                config.apply(NavigatorConfig.unavailable());
                try {
                    Files.write(SafeFileWriter.recoveryFile(config.file()), base.bytes(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                } catch (IOException | RuntimeException markerFailure) { restoration.addSuppressed(markerFailure); }
                failure.addSuppressed(new IOException("Disk rollback failed; original retained at " + backup, restoration));
            }
            throw failure;
        }
        revision++;
        return backup;
    }
}
