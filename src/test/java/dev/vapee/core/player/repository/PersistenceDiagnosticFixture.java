package dev.vapee.core.player.repository;

import static dev.vapee.core.persistence.PersistenceFaults.Fault;

import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.persistence.PersistenceFaults;
import dev.vapee.core.persistence.SafeFileWriter;
import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.FilterWriter;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Real repository, aggregates and NIO; only FileAccess and Logger outcomes are controlled. */
public final class PersistenceDiagnosticFixture implements AutoCloseable {
    public final Path directory = Files.createTempDirectory("vapeecore-persistence-diagnostic-");
    public final Access access = new Access();
    public final Logger logger = Logger.getAnonymousLogger();
    public final List<LogRecord> logs = new ArrayList<>();
    public final List<RuntimeException> diagnostics = new ArrayList<>();
    public final FilePlayerRepository repository;
    public final PlayerService players;
    public final EconomyService economy;
    public boolean throwWarning, throwSevere;
    public final UUID a = UUID.fromString("65e00000-0000-0000-0000-000000000001");
    public final UUID b = UUID.fromString("65e00000-0000-0000-0000-000000000002");

    public PersistenceDiagnosticFixture() throws Exception {
        logger.setUseParentHandlers(false);
        logger.addHandler(
                new Handler() {
                    public void publish(LogRecord record) {
                        logs.add(record);
                        access.events.add("log:" + record.getLevel());
                        if ((throwWarning && record.getLevel().intValue() == 900)
                                || (throwSevere && record.getLevel().intValue() >= 1000)) {
                            RuntimeException diagnostic =
                                    new IllegalStateException(
                                            "controlled "
                                                    + record.getLevel()
                                                    + " diagnostic "
                                                    + diagnostics.size());
                            diagnostics.add(diagnostic);
                            throw diagnostic;
                        }
                    }

                    public void flush() {}

                    public void close() {}
                });
        repository = new FilePlayerRepository(directory, logger, access);
        repository.initialize();
        players = new PlayerService(repository, logger);
        economy = new EconomyService(players);
        reset();
    }

    public CorePlayer load(UUID id, String name, long coins) {
        CorePlayer player = players.loadPlayer(id, name);
        if (coins != 0) economy.setCoins(id, coins);
        reset();
        return player;
    }

    public void reset() {
        access.events.clear();
        access.errors.clear();
        access.commits.clear();
        diagnostics.clear();
        logs.clear();
    }

    public Path file(UUID id) {
        return directory.resolve(id + ".yml");
    }

    public byte[] bytes(UUID id) throws IOException {
        return Files.readAllBytes(file(id));
    }

    public YamlConfiguration yaml(UUID id) {
        return YamlConfiguration.loadConfiguration(file(id).toFile());
    }

    public long stored(UUID id) {
        return yaml(id).getLong("economy.coins");
    }

    public CorePlayer cold(UUID id) {
        return new FilePlayerRepository(directory, quiet()).findByUniqueId(id).orElseThrow();
    }

    public static Logger quiet() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        return logger;
    }

    public void reconcileSuccessfulBackup(UUID id) throws IOException {
        Files.delete(SafeFileWriter.recoveryFile(file(id)));
    }

    public static RuntimeException failure(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    public static void strict(Object service, String method) {
        try {
            var m = service.getClass().getDeclaredMethod(method);
            m.setAccessible(true);
            m.invoke(service);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException r) throw r;
            throw new AssertionError(e.getCause());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static Object field(Object value, String name) {
        try {
            var f = value.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    public static boolean contains(Throwable root, Throwable target) {
        return contains(root, target, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean contains(Throwable root, Throwable target, Set<Throwable> seen) {
        if (root == null || target == null || !seen.add(root)) return false;
        if (root == target) return true;
        if (contains(root.getCause(), target, seen)) return true;
        for (Throwable s : root.getSuppressed()) if (contains(s, target, seen)) return true;
        return false;
    }

    public void close() throws Exception {
        for (Handler h : logger.getHandlers()) logger.removeHandler(h);
        try (var paths = Files.walk(directory)) {
            for (Path p : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(p);
        }
    }

    /** Reuses the existing fault injector and records the exact exceptions it actually throws. */
    public static final class Access implements SafeFileWriter.FileAccess {
        public final PersistenceFaults delegate = new PersistenceFaults(Fault.NONE);
        public final List<String> events = delegate.events;
        public final List<IOException> errors = new ArrayList<>();
        public final List<Path> commits = new ArrayList<>();
        public final Map<UUID, Fault> perPlayer = new LinkedHashMap<>();
        public Fault defaultFault = Fault.NONE;
        public boolean atomicAfterEffect, runtimeCleanup;
        public final RuntimeException cleanupRuntime =
                new IllegalStateException("controlled runtime cleanup failure");

        public void fault(Fault fault) {
            defaultFault = fault;
            delegate.fault = fault;
        }

        public Writer openWriter(Path path) throws IOException {
            String name = path.getFileName().toString();
            Fault selected = defaultFault;
            if (name.startsWith(".vapeecore-") && name.length() > 47) {
                try {
                    selected =
                            perPlayer.getOrDefault(
                                    UUID.fromString(name.substring(11, 47)), defaultFault);
                } catch (IllegalArgumentException ignored) {
                }
            }
            delegate.fault = selected;
            try {
                return new FilterWriter(delegate.openWriter(path)) {
                    public void write(String text, int offset, int length) throws IOException {
                        try {
                            out.write(text, offset, length);
                        } catch (IOException e) {
                            errors.add(e);
                            throw e;
                        }
                    }

                    public void close() throws IOException {
                        try {
                            super.close();
                        } catch (IOException e) {
                            errors.add(e);
                            throw e;
                        }
                    }
                };
            } catch (IOException e) {
                errors.add(e);
                throw e;
            }
        }

        public void move(Path source, Path target, CopyOption... options) throws IOException {
            try {
                delegate.move(source, target, options);
                commits.add(target);
                events.add("committed:" + target.getFileName());
                if (atomicAfterEffect
                        && Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE))
                    throw new IOException("controlled move failure after physical effect");
            } catch (IOException e) {
                errors.add(e);
                throw e;
            }
        }

        public void copy(Path source, Path target, CopyOption... options) throws IOException {
            try {
                delegate.copy(source, target, options);
            } catch (IOException e) {
                errors.add(e);
                throw e;
            }
        }

        public void deleteIfExists(Path path) throws IOException {
            if (runtimeCleanup && path.toString().endsWith(".tmp")) {
                events.add("cleanup:runtime");
                throw cleanupRuntime;
            }
            try {
                delegate.deleteIfExists(path);
            } catch (IOException e) {
                errors.add(e);
                throw e;
            }
        }
    }
}
