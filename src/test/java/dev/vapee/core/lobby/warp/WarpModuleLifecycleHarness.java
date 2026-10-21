package dev.vapee.core.lobby.warp;

import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.lobby.warp.command.WarpCommand;
import dev.vapee.core.module.ModuleRuntimeFixture;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.LogRecord;

/** Real module/PluginCommand acquisition, rollback, disable and retry. */
public final class WarpModuleLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        for (var stage : WarpModule.BindingStage.values()) {
            try (var f = new Fixture()) {
                var fail = new AtomicBoolean(true);
                RuntimeException primary = new IllegalStateException("injected " + stage);
                var acquired = new ArrayList<WarpService>();
                var module =
                        new WarpModule(
                                f.runtime.plugin,
                                f.runtime.messages,
                                new CommandHelpRenderer(f.runtime.messages),
                                point -> {
                                    if (point == stage && fail.get()) {
                                        acquired.add(
                                                (WarpService)
                                                        get(
                                                                f.runtime.command.getExecutor(),
                                                                "warpService"));
                                        throw primary;
                                    }
                                });
                try {
                    module.enable();
                    throw new AssertionError("failure did not fire");
                } catch (RuntimeException actual) {
                    check(actual == primary, "original startup failure preserved");
                }
                f.inactive(module);
                check(acquired.getFirst().getWarps().isEmpty(), "failed runtime service released");
                module.disable();
                fail.set(false);
                module.enable();
                f.active(module);
                WarpService first = module.getWarpService();
                first.setWarp("test", f.location);
                module.disable();
                f.inactive(module);
                check(first.getWarps().isEmpty(), "disable clears old service");
                module.disable();
                module.enable();
                f.active(module);
                check(
                        module.getWarpService() != first && module.getWarpService().hasWarp("test"),
                        "retry loads current persisted data into fresh service");
                check(
                        get(f.runtime.command.getExecutor(), "warpService")
                                == module.getWarpService(),
                        "executor references current runtime");
                module.disable();
                f.inactive(module);
            }
        }
        try (var f = new Fixture()) {
            var module =
                    new WarpModule(
                            f.runtime.plugin,
                            f.runtime.messages,
                            new CommandHelpRenderer(f.runtime.messages));
            RuntimeException primary = new IllegalStateException("injected final startup log");
            Handler fault =
                    new Handler() {
                        public void publish(LogRecord record) {
                            if (record.getMessage().startsWith("Warp module enabled"))
                                throw primary;
                        }

                        public void flush() {}

                        public void close() {}
                    };
            f.runtime.logger.addHandler(fault);
            try {
                module.enable();
                throw new AssertionError("late startup fault missing");
            } catch (RuntimeException actual) {
                check(actual == primary, "failure after publication remains primary");
            }
            f.inactive(module);
            f.runtime.logger.removeHandler(fault);
            module.enable();
            f.active(module);
            module.disable();
            f.inactive(module);
        }
        try (var f = new Fixture()) {
            f.runtime.missingCommand = true;
            var module =
                    new WarpModule(
                            f.runtime.plugin,
                            f.runtime.messages,
                            new CommandHelpRenderer(f.runtime.messages));
            try {
                module.enable();
                throw new AssertionError("missing descriptor accepted");
            } catch (NullPointerException expected) {
                check(
                        expected.getMessage().contains("warp")
                                && expected.getMessage().contains("plugin.yml"),
                        "actionable missing declaration failure");
            }
            f.inactive(module);
            module.disable();
        }
        System.out.println("WarpModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static final class Fixture implements AutoCloseable {
        final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture("warp");
        final java.nio.file.Path directory = Files.createTempDirectory("vapeecore-warp-lifecycle-");
        final org.bukkit.World world =
                ModuleRuntimeFixture.proxy(
                        org.bukkit.World.class,
                        (method, args) -> method.equals("getName") ? "world" : null);
        final org.bukkit.Location location = new org.bukkit.Location(world, 0, 70, 0);

        Fixture() throws Exception {
            set(runtime.plugin, "dataFolder", directory.toFile());
            set(runtime.plugin, "classLoader", WarpModuleLifecycleHarness.class.getClassLoader());
            runtime.serverCalls =
                    (method, args) -> {
                        if (method.equals("getWorld")) return world;
                        throw new AssertionError(method);
                    };
        }

        void active(WarpModule module) {
            check(
                    runtime.command.getExecutor() instanceof WarpCommand
                            && runtime.command.getTabCompleter() == runtime.command.getExecutor(),
                    "executor/completer installed together");
            check(
                    module.getWarpService() != null && runtime.handlerCount() == 0,
                    "module usable without unrelated event registrations");
        }

        void inactive(WarpModule module) {
            check(
                    runtime.command.getExecutor() == runtime.plugin
                            && runtime.command.getTabCompleter() == null,
                    "Bukkit fallback/completer cleanup");
            for (String field : List.of("warpConfig", "warpService", "warpCommand"))
                check(get(module, field) == null, "runtime field released: " + field);
            try {
                module.getWarpService();
                throw new AssertionError("inactive service exposed");
            } catch (NullPointerException expected) {
                check(true, "getter fails closed");
            }
        }

        @Override
        public void close() throws Exception {
            runtime.close();
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
