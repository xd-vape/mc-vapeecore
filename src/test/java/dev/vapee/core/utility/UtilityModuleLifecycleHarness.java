package dev.vapee.core.utility;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.module.ModuleManager;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import net.kyori.adventure.text.Component;

import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/** Faults enter the real module through local I/O seams; no Paper registry or plugin boot. */
public final class UtilityModuleLifecycleHarness {
    private static final List<String> NAMES = List.of("build", "fly", "speed", "gamemode", "tp", "tphere",
            "heal", "feed", "ping", "clear", "invsee", "enderchest");
    private static int checks;
    private static final TabExecutor EXECUTOR = new TabExecutor() {
        public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                                 String label, String[] args) { return true; }
        public List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command,
                                          String alias, String[] args) { return List.of(); }
    };

    public static void main(String[] args) throws Exception {
        normal();
        for (int index : List.of(0, 5, 11)) {
            for (String stage : List.of("missing", "constructor", "executor", "completer")) failed(stage + ":" + NAMES.get(index));
        }
        for (String stage : List.of("service.factory", "listener.factory", "invsee.factory",
                "register:utility", "register:invsee", "log")) failed(stage);
        for (String cleanup : List.of("executor:build", "completer:tphere", "unregister:utility",
                "unregister:invsee", "invsee.cleanup", "service.cleanup")) cleanupFailure(cleanup);
        combinedFailures();
        manager();
        wiring();
        System.out.println("UtilityModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static void normal() throws Exception {
        Fixture f = new Fixture();
        f.module.disable(); empty(f);
        f.module.enable();
        check(f.lookups.equals(NAMES) && f.executors.size() == 12 && f.completers.size() == 12,
                "success acquires all twelve command pairs in order");
        check(f.listeners.size() == 2 && f.module.getUtilityService() == f.service && !f.premature,
                "both listeners installed before service publication");
        UtilityService first = f.service;
        try { f.module.enable(); throw new AssertionError("double enable accepted"); }
        catch (IllegalStateException expected) { check(f.module.getUtilityService() == first, "double enable preserves active state"); }
        queueJoin(f);
        f.module.disable(); empty(f);
        check(f.events.indexOf("invsee.cleanup") < f.events.indexOf("unregister:invsee")
                && f.events.indexOf("unregister:invsee") < f.events.indexOf("unregister:utility")
                && f.events.indexOf("service.cleanup") < f.events.indexOf("executor.clear:enderchest"),
                "services/listeners cleaned before reverse command cleanup");
        check(f.events.indexOf("executor.clear:enderchest") < f.events.indexOf("executor.clear:build"),
                "commands released in reverse acquisition order");
        f.queued.forEach(Runnable::run);
        check(f.playerCalls == 0, "disabled listener cancels queued join normalization");
        int events = f.events.size(); f.module.disable();
        check(f.events.size() == events, "repeat disable performs no stale cleanup");
        f.reset();
        f.module.enable(); check(f.service != first, "re-enable creates a fresh service");
        f.module.disable(); empty(f);
    }

    private static void failed(String stage) throws Exception {
        Fixture f = new Fixture(); f.fail = stage;
        RuntimeException caught = failure(f.module::enable);
        if (stage.startsWith("missing:")) check(caught instanceof NullPointerException
                && caught.getMessage().contains(stage.substring(8)), "missing command retains contextual failure");
        else check(caught == f.original, "original enable exception survives " + stage);
        empty(f);
        check(!f.premature, "failed enable never publishes fields " + stage);
        check((f.service == null || f.events.contains("service.cleanup"))
                && (f.invsee == null || f.events.contains("invsee.cleanup")),
                "every constructed service cleanup attempted " + stage);
        if (f.listener != null) {
            queueJoin(f);
            f.queued.forEach(Runnable::run);
            check(f.playerCalls == 0, "failed enable deactivates its UtilityListener " + stage);
        }
        if (stage.startsWith("completer:")) {
            String name = stage.substring(10);
            check(f.events.contains("executor.clear:" + name) && f.events.contains("completer.clear:" + name),
                    "half-installed pair cleaned after completer failure " + name);
        }
        f.reset(); f.module.enable();
        check(f.executors.size() == 12 && f.listeners.size() == 2 && f.module.getUtilityService() == f.service,
                "same module re-enables after " + stage);
        f.module.disable(); empty(f);
    }

    private static void cleanupFailure(String cleanup) throws Exception {
        Fixture f = new Fixture(); f.fail = "register:invsee"; f.cleanupFailures.put(cleanup, new IllegalStateException(cleanup));
        RuntimeException caught = failure(f.module::enable);
        check(caught == f.original && List.of(caught.getSuppressed()).equals(List.of(f.cleanupFailures.get(cleanup))),
                "secondary cleanup failure suppressed on original enable failure " + cleanup);
        empty(f);
        check(f.events.contains("executor.clear:build") && f.events.contains("completer.clear:build")
                && f.events.contains("service.cleanup") && f.events.contains("unregister:utility"),
                "all later cleanup operations attempted after " + cleanup);
        f.reset(); f.module.enable(); f.module.disable(); empty(f);

        Fixture shutdown = new Fixture(); shutdown.module.enable();
        RuntimeException injected = new IllegalStateException(cleanup);
        shutdown.cleanupFailures.put(cleanup, injected);
        check(failure(shutdown.module::disable) == injected, "normal disable surfaces first cleanup error " + cleanup);
        empty(shutdown);
        int events = shutdown.events.size(); shutdown.module.disable();
        check(shutdown.events.size() == events, "failed disable still forgets local ownership " + cleanup);
    }

    private static void combinedFailures() throws Exception {
        Fixture f = new Fixture(); f.fail = "register:invsee";
        for (String key : List.of("invsee.cleanup", "unregister:invsee", "unregister:utility",
                "service.cleanup", "executor:enderchest", "completer:enderchest", "executor:build")) {
            f.cleanupFailures.put(key, new IllegalStateException(key));
        }
        var caught = failure(f.module::enable);
        check(caught == f.original && List.of(caught.getSuppressed()).equals(new ArrayList<>(f.cleanupFailures.values())),
                "multiple cleanup failures remain ordered on the original exception");
        empty(f);
        Fixture self = new Fixture(); self.fail = "register:invsee";
        self.cleanupFailures.put("invsee.cleanup", self.original);
        check(failure(self.module::enable) == self.original && self.original.getSuppressed().length == 0,
                "same throwable cannot cause self-suppression to abort rollback");
        empty(self);
    }

    private static void manager() throws Exception {
        Fixture f = new Fixture(); f.fail = "completer:tphere";
        List<String> events = new ArrayList<>();
        ModuleManager manager = new ModuleManager(f.logger);
        manager.register(tracker("First", events)); manager.register(tracker("Second", events));
        manager.register(f.module); manager.register(tracker("Last", events));
        check(failure(manager::enableAll) == f.original, "actual manager propagates original Utility failure");
        check(events.equals(List.of("+First", "+Second", "-Second", "-First"))
                && manager.getEnabledModules().isEmpty(), "manager rolls back successful predecessors only");
        empty(f);
        f.reset(); manager.enableAll(); check(manager.getEnabledModules().size() == 4, "manager retry succeeds");
        manager.disableAll(); empty(f);
    }

    private static void wiring() throws Exception {
        String source = Files.readString(Path.of("src/main/java/dev/vapee/core/utility/UtilityModule.java"));
        var matcher = java.util.regex.Pattern.compile("register\\.accept\\(\"([^\"]+)\"").matcher(source);
        List<String> names = new ArrayList<>();
        while (matcher.find()) names.add(matcher.group(1));
        check(names.equals(NAMES), "production still explicitly registers the same twelve names");
        check(source.contains("command::setExecutor") && source.contains("command::setTabCompleter")
                && source.contains("registerEvents(listener, plugin)") && source.contains("HandlerList.unregisterAll(listener)"),
                "production adapters delegate to real Paper setters and listeners");
    }

    private static void queueJoin(Fixture f) {
        Player player = (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[]{Player.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getUniqueId")) return new UUID(0, 1);
                    if (method.getName().equals("isOnline")) { f.playerCalls++; return true; }
                    throw new AssertionError("Unexpected player call: " + method.getName());
                });
        f.listener.onPlayerJoin(new PlayerJoinEvent(player, Component.empty()));
        check(f.queued.size() == 1, "real UtilityListener queues join work");
    }

    private static void empty(Fixture f) throws Exception {
        check(f.executors.isEmpty() && f.completers.isEmpty() && f.listeners.isEmpty(),
                "all external test hooks released");
        for (String field : List.of("registeredCommands", "registeredListeners")) {
            var ref = UtilityModule.class.getDeclaredField(field); ref.setAccessible(true);
            check(((List<?>) ref.get(f.module)).isEmpty(), field + " cleared");
        }
        boolean unpublished = true;
        for (String field : List.of("utilityService", "utilityListener", "invseeService")) {
            var ref = UtilityModule.class.getDeclaredField(field); ref.setAccessible(true);
            unpublished &= ref.get(f.module) == null;
        }
        check(unpublished, "all three runtime fields null");
        check(failure(f.module::getUtilityService) instanceof IllegalStateException, "disabled service unavailable");
    }

    private static final class Fixture implements UtilityModule.Hooks {
        final RuntimeException original = new IllegalStateException("injected enable failure");
        final Map<String, RuntimeException> cleanupFailures = new LinkedHashMap<>();
        final Map<String, CommandExecutor> executors = new LinkedHashMap<>();
        final Map<String, TabCompleter> completers = new LinkedHashMap<>();
        final List<Listener> listeners = new ArrayList<>();
        final List<String> lookups = new ArrayList<>(), events = new ArrayList<>();
        final List<Runnable> queued = new ArrayList<>();
        final Logger logger = Logger.getAnonymousLogger();
        UtilityService service;
        UtilityListener listener;
        InvseeService invsee;
        final UtilityModule module;
        String fail = "";
        boolean premature;
        int playerCalls;

        Fixture() {
            logger.setUseParentHandlers(false);
            logger.addHandler(new Handler() {
                public void publish(LogRecord record) { if (fail.equals("log")) throw original; }
                public void flush() { }
                public void close() { }
            });
            module = new UtilityModule(() -> {
                fault("service.factory");
                return service = new UtilityService(() -> true, () -> {
                    cleanup("service.cleanup"); return List.of();
                }, player -> 20);
            }, utility -> {
                fault("listener.factory");
                return listener = new UtilityListener(utility, queued::add, () -> true);
            }, () -> {
                fault("invsee.factory");
                return invsee = new InvseeService(() -> {
                    cleanup("invsee.cleanup"); return true;
                }, id -> null, (holder, size, title) -> { throw new AssertionError("unused inventory factory"); });
            }, (utility, views, register) -> {
                for (String name : NAMES) {
                    fault("constructor:" + name);
                    register.accept(name, EXECUTOR);
                }
            }, this, logger);
        }

        public UtilityModule.CommandHook command(String name) {
            lookups.add(name); unpublished();
            if (fail.equals("missing:" + name)) return null;
            return new UtilityModule.CommandHook(value -> {
                if (value == null) {
                    executors.remove(name); events.add("executor.clear:" + name); cleanupFault("executor:" + name);
                } else { executors.put(name, value); fault("executor:" + name); }
            }, value -> {
                if (value == null) {
                    completers.remove(name); events.add("completer.clear:" + name); cleanupFault("completer:" + name);
                } else { completers.put(name, value); fault("completer:" + name); }
            });
        }
        public void register(Listener value) {
            unpublished(); listeners.add(value);
            fault("register:" + (value instanceof InvseeService ? "invsee" : "utility"));
        }
        public void unregister(Listener value) {
            listeners.remove(value); cleanup("unregister:" + (value instanceof InvseeService ? "invsee" : "utility"));
        }
        void unpublished() {
            try { module.getUtilityService(); premature = true; } catch (IllegalStateException expected) { }
        }
        void fault(String stage) { if (fail.equals(stage)) throw original; }
        void cleanup(String stage) { events.add(stage); cleanupFault(stage); }
        void cleanupFault(String stage) {
            if (cleanupFailures.containsKey(stage)) throw cleanupFailures.get(stage);
        }
        void reset() { fail = ""; cleanupFailures.clear(); events.clear(); lookups.clear(); queued.clear(); }
    }

    private static CoreModule tracker(String name, List<String> events) {
        return new CoreModule() {
            public String getName() { return name; }
            public void enable() { events.add("+" + name); }
            public void disable() { events.add("-" + name); }
        };
    }
    private static RuntimeException failure(Runnable action) {
        try { action.run(); } catch (RuntimeException expected) { return expected; }
        throw new AssertionError("Expected failure");
    }
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
