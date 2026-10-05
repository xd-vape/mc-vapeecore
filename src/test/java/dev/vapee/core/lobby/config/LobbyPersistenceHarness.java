package dev.vapee.core.lobby.config;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.LobbySpawn;
import dev.vapee.core.persistence.PersistenceFaults;
import dev.vapee.core.persistence.SafeFileWriter;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static dev.vapee.core.persistence.PersistenceFaults.Fault;

public final class LobbyPersistenceHarness {
    private static int checks;
    private static final LobbySpawn SPAWN = new LobbySpawn("world", 1, 64, 3, 4, 5);
    private static final String ORIGINAL = "player:\n  gamemode: SURVIVAL\noperator-extension: {keep: true}\n";

    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("vapeecore-lobby-persistence-");
        try {
            for (Fault fault : args.length == 0 ? Fault.values() : new Fault[]{Fault.valueOf(args[0])}) {
                if (fault != Fault.MIGRATION_BACKUP) scenario(directory.resolve(fault.name()), fault);
            }
            serializationAndMissing(directory);
        } finally {
            try (var paths = Files.walk(directory)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
            }
        }
        System.out.println("LobbyPersistenceHarness passed " + checks + " checks.");
    }

    private static void scenario(Path directory, Fault fault) throws Exception {
        Files.createDirectories(directory);
        Path target = directory.resolve("lobby.yml");
        Files.writeString(target, ORIGINAL);
        List<LogRecord> logs = new ArrayList<>();
        Logger logger = logger(logs);
        PersistenceFaults access = new PersistenceFaults(fault);
        SafeFileWriter writer = new SafeFileWriter(logger, access);
        LobbyConfig config = new LobbyConfig(target, logger, writer);
        config.initialize();
        var before = config.getState();
        LobbyService service = new LobbyService(plugin(logger), config);
        World world = (World) Proxy.newProxyInstance(World.class.getClassLoader(), new Class<?>[]{World.class},
                (p, m, a) -> m.getName().equals("getName") ? "world" : null);
        boolean success = List.of(Fault.NONE, Fault.UNSUPPORTED, Fault.TEMP_CLEANUP, Fault.BACKUP_CLEANUP).contains(fault);
        boolean failed = false;
        try { service.setSpawn(new Location(world, 1, 64, 3, 4, 5)); }
        catch (RuntimeException expected) { failed = true; }
        check(failed != success, fault + " outcome");
        check(config.getSpawn().isPresent() == success && service.getSpawn().isPresent() == success,
                fault + " config and service publish only after commit");
        if (success) {
            check(config.getSpawn().orElseThrow().equals(SPAWN), fault + " requested spawn published");
            check(config.prepareReloadState().spawn().orElseThrow().equals(SPAWN), fault + " production readback");
            check(YamlConfiguration.loadConfiguration(target.toFile()).getBoolean("operator-extension.keep"),
                    fault + " unrelated lobby content preserved");
        } else {
            check(config.getState() == before, fault + " exact old runtime snapshot");
            if (fault != Fault.RESTORE) check(Files.readString(target).equals(ORIGINAL), fault + " old file preserved");
        }
        check(access.candidate != null && access.candidate.getParent().equals(target.getParent())
                && access.candidate.getFileName().toString().startsWith(".vapeecore-lobby.yml-"), fault + " own sibling temp");
        if (fault != Fault.TEMP_CLEANUP && fault != Fault.WRITE_AND_CLEANUP) check(!Files.exists(access.candidate), fault + " temp removed");
        if (fault == Fault.WRITE_AND_CLEANUP) check(Files.exists(access.candidate), "failed temp cleanup retains only own candidate for inspection");
        if (fault == Fault.ATOMIC_IO) check(!access.events.contains("replace") && !access.events.contains("backup"),
                "ordinary atomic IOException never selects fallback");
        if (List.of(Fault.WRITE, Fault.WRITE_AND_CLEANUP, Fault.CLOSE, Fault.INVALID_YAML, Fault.INVALID_SPAWN).contains(fault)) {
            check(!access.events.contains("atomic"), fault + " rejected before replace");
        }
        if (fault == Fault.NONE || fault == Fault.UNSUPPORTED) {
            check(access.events.indexOf("close") < access.events.indexOf("atomic"), "close precedes commit");
            check(!Files.exists(SafeFileWriter.recoveryFile(target)), "successful commit leaves no recovery copy");
        }
        if (fault == Fault.UNSUPPORTED) check(access.events.indexOf("backup") < access.events.indexOf("replace"),
                "fallback protects target before replacement");
        if (fault == Fault.REPLACE) check(access.events.contains("restore") && !Files.exists(SafeFileWriter.recoveryFile(target)),
                "damaged fallback target restored, backup cleaned");
        if (fault == Fault.RESTORE) {
            check(Files.readString(SafeFileWriter.recoveryFile(target)).equals(ORIGINAL), "failed restore retains good backup");
            check(logs.stream().anyMatch(r -> r.getMessage().contains("Could not restore") && r.getThrown() != null),
                    "restore failure visible with cause");
            access.fault = Fault.NONE;
            expectFailure(() -> config.saveSpawn(SPAWN), "unresolved recovery blocks another save");
            check(Files.readString(SafeFileWriter.recoveryFile(target)).equals(ORIGINAL), "retry preserves recovery copy");
        }
        if (fault == Fault.TEMP_CLEANUP || fault == Fault.BACKUP_CLEANUP || fault == Fault.WRITE_AND_CLEANUP) {
            check(logs.stream().anyMatch(r -> r.getMessage().contains("Could not clean") && r.getThrown() != null),
                    "post-commit cleanup failure logged without false rollback");
        }
    }

    private static void serializationAndMissing(Path directory) throws Exception {
        Path target = directory.resolve("missing.yml");
        PersistenceFaults access = new PersistenceFaults(Fault.NONE);
        SafeFileWriter writer = new SafeFileWriter(logger(new ArrayList<>()), access);
        expectFailure(() -> writer.write(target, () -> { throw new IllegalStateException("serialization failure"); },
                path -> { }, path -> { }), "serialization failure propagates before file open");
        check(access.candidate == null && !Files.exists(target), "serialization opens no file");
        Files.writeString(target, ORIGINAL);
        expectFailure(() -> writer.write(target, () -> { throw new IllegalStateException("serialization failure"); },
                path -> { }, path -> { }), "serialization failure with existing target");
        check(Files.readString(target).equals(ORIGINAL) && access.candidate == null, "serialization failure preserves existing destination");
        for (Fault fault : List.of(Fault.NONE, Fault.UNSUPPORTED, Fault.REPLACE)) {
            Files.deleteIfExists(target);
            access.fault = fault;
            access.events.clear();
            boolean failed = false;
            try { writer.write(target, () -> "valid: true\n", path -> {
                try { new YamlConfiguration().load(path.toFile()); }
                catch (org.bukkit.configuration.InvalidConfigurationException e) { throw new IOException(e); }
            }, path -> { }); } catch (IOException expected) { failed = true; }
            check(failed == (fault == Fault.REPLACE), "missing destination " + fault);
            check(Files.exists(target) != failed, "missing destination restored to absence on failure");
            check(!access.events.contains("backup") && !Files.exists(SafeFileWriter.recoveryFile(target)),
                    "missing destination has no fabricated backup");
        }
    }

    private static JavaPlugin plugin(Logger logger) throws Exception {
        var field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        JavaPlugin plugin = (JavaPlugin) ((sun.misc.Unsafe) field.get(null)).allocateInstance(PluginShell.class);
        var log = JavaPlugin.class.getDeclaredField("logger"); log.setAccessible(true); log.set(plugin, logger);
        return plugin;
    }
    public static final class PluginShell extends JavaPlugin { }

    private static Logger logger(List<LogRecord> records) {
        Logger logger = Logger.getAnonymousLogger(); logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            public void publish(LogRecord record) { records.add(record); }
            public void flush() { }
            public void close() { }
        });
        return logger;
    }
    private static void expectFailure(Action action, String label) throws Exception {
        boolean failed = false;
        try { action.run(); } catch (IOException | RuntimeException expected) { failed = true; }
        check(failed, label);
    }
    @FunctionalInterface private interface Action { void run() throws Exception; }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
