package dev.vapee.core.clan;

import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.clan.command.ClanCommand;
import dev.vapee.core.clan.gui.ClanMenu;
import dev.vapee.core.clan.gui.ClanMenuListener;
import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.module.ModuleRuntimeFixture;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

/** Exercises the real module, real PluginCommand and actual Paper HandlerLists. */
public final class ClanModuleLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("retry")) {
            startupFault(ClanModule.EnableStage.RUNTIME_PUBLISHED);
            System.out.println("ClanModuleLifecycleHarness passed " + checks + " checks.");
            return;
        }
        if (args.length == 1 && args[0].equals("late-log")) {
            lateLogging();
            System.out.println("ClanModuleLifecycleHarness passed " + checks + " checks.");
            return;
        }
        if (args.length == 1 && args[0].equals("cleanup")) {
            cleanupFailures();
            System.out.println("ClanModuleLifecycleHarness passed " + checks + " checks.");
            return;
        }
        lifecycle();
        for (var stage : ClanModule.EnableStage.values()) startupFault(stage);
        externalFaults();
        observerFaults();
        cleanupFailures();
        lateLogging();
        refusedCleanupAndBindings();
        System.out.println("ClanModuleLifecycleHarness passed " + checks + " checks.");
    }

    /** Existing integration entry point uses behavior instead of cleanup source spelling. */
    public static int verifyCleanup() throws Exception {
        int before = checks;
        try (var f = new Fixture()) {
            var module = f.module(stage -> {});
            module.enable();
            f.capture();
            f.active(module);
            module.disable();
            f.inactive(module);
        }
        return checks - before;
    }

    private static void lifecycle() throws Exception {
        try (var f = new Fixture()) {
            ClanModule module = f.module(stage -> {});
            module.disable();
            module.enable();
            f.capture();
            f.active(module);
            var firstService = module.getClanService();
            var firstMenu = f.menu;
            var firstListener = f.runtime.registered.getLast();
            var firstExecutor = f.runtime.command.getExecutor();
            RuntimeException duplicate = null;
            try {
                module.enable();
            } catch (RuntimeException expected) {
                duplicate = expected;
            }
            check(
                    f.runtime.handlerCount() == 5 && f.locale.size() == 1,
                    "double enable cannot duplicate listener or observer");
            check(duplicate instanceof IllegalStateException, "double enable rejected");
            check(
                    module.getClanService() == firstService
                            && f.menu == firstMenu
                            && f.runtime.command.getExecutor() == firstExecutor,
                    "double enable preserves usable runtime and bindings");

            UUID a = new UUID(0, 101), b = new UUID(0, 102);
            f.runtime.players.loadPlayer(a, "First");
            f.runtime.players.loadPlayer(b, "Second");
            check(
                    firstService.createClan(a, "Lifecycle Clan", "LIFE") == ClanResult.SUCCESS
                            && firstService.inviteMember(a, b) == ClanResult.SUCCESS
                            && firstService.acceptInvite(
                                            b, firstService.getClanOf(a).orElseThrow().id())
                                    == ClanResult.SUCCESS,
                    "actual service and repository accept disposable clan");
            byte[] persisted = Files.readAllBytes(f.directory.resolve("clans.yml"));
            var own = f.view(firstMenu, "Owned", false, false);
            var foreign = f.view(firstMenu, "Foreign", true, false);
            module.disable();
            f.inactive(module);
            check(
                    own.closes == 1 && foreign.closes == 0,
                    "disable closes only owned current views");
            check(
                    own.handlersDuringClose == 5,
                    "close handlers remain installed during view shutdown");
            check(
                    Arrays.equals(persisted, Files.readAllBytes(f.directory.resolve("clans.yml"))),
                    "disable does not rewrite durable clans");
            int removals = f.locale.removals;
            module.disable();
            check(
                    f.locale.removals == removals && own.closes == 1,
                    "repeated disable has no duplicate effects");
            var freshIdentity = new PlayerIdentityService(f.runtime.players);
            set(f.identity, "identityService", freshIdentity);
            var external =
                    new ClanService(
                            new FileClanRepository(f.directory.resolve("clans.yml")),
                            ClanLimits.defaults(),
                            java.time.Clock.systemUTC(),
                            UUID::randomUUID);
            external.renameClan(a, "External Clan");
            module.enable();
            f.capture();
            f.active(module);
            check(
                    module.getClanService() != firstService
                            && f.menu != firstMenu
                            && f.runtime.registered.getLast() != firstListener
                            && f.runtime.command.getExecutor() != firstExecutor,
                    "re-enable creates four fresh runtime objects");
            check(
                    get(f.menu, "identities") == freshIdentity
                            && get(f.runtime.command.getExecutor(), "identities") == freshIdentity,
                    "re-enable captures currently available borrowed identity service");
            check(
                    module.getClanService().getClanOf(b).orElseThrow().ownerId().equals(a)
                            && module.getClanService()
                                    .getClanOf(a)
                                    .orElseThrow()
                                    .name()
                                    .equals("External Clan"),
                    "fresh service reloads persisted clans");
            check(
                    ((Set<?>) get(firstService, "changeListeners")).isEmpty(),
                    "old service has no menu observer");
            check(((Map<?, ?>) get(firstMenu, "active")).isEmpty(), "old menu remains detached");
            module.disable();
            f.inactive(module);
        }
    }

    private static void startupFault(ClanModule.EnableStage stage) throws Exception {
        try (var f = new Fixture()) {
            var fail = new AtomicBoolean(true);
            RuntimeException original = new IllegalStateException("injected " + stage);
            ClanModule[] reference = new ClanModule[1];
            var module =
                    f.module(
                            point -> {
                                f.capture();
                                if (point == stage && fail.get()) {
                                    if (point == ClanModule.EnableStage.RUNTIME_PUBLISHED) {
                                        check(
                                                get(reference[0], "service") == f.service,
                                                "publication stage actually reached");
                                        check(
                                                failure(reference[0]::getClanService)
                                                        instanceof NullPointerException,
                                                "service remains unavailable before enable completes");
                                    }
                                    throw original;
                                }
                            });
            reference[0] = module;
            check(
                    failure(module::enable) == original,
                    "original startup exception preserved: " + stage);
            f.inactive(module);
            module.disable();
            fail.set(false);
            module.enable();
            f.capture();
            f.active(module);
            if (stage == ClanModule.EnableStage.RUNTIME_PUBLISHED) {
                RuntimeException duplicate = null;
                try {
                    module.enable();
                } catch (RuntimeException expected) {
                    duplicate = expected;
                }
                check(
                        f.runtime.handlerCount() == 5 && f.locale.size() == 1,
                        "retry cannot duplicate listener or observer");
                check(
                        duplicate instanceof IllegalStateException,
                        "duplicate enable after retry rejected");
            }
            module.disable();
            f.inactive(module);
        }
    }

    private static void externalFaults() throws Exception {
        for (boolean missing : List.of(true, false)) {
            try (var f = new Fixture()) {
                f.runtime.missingCommand = missing;
                f.runtime.failRegistration = missing ? 0 : 1;
                var module = f.module(stage -> {});
                RuntimeException thrown = failure(module::enable);
                if (missing)
                    check(
                            thrown instanceof NullPointerException
                                    && thrown.getMessage().contains("plugin.yml"),
                            "missing descriptor fails clearly");
                else {
                    f.capture();
                    check(
                            thrown.getMessage().contains("registration"),
                            "real registration fails after side effect");
                }
                f.inactive(module);
                f.runtime.missingCommand = false;
                f.runtime.failRegistration = 0;
                module.enable();
                f.capture();
                f.active(module);
                module.disable();
                f.inactive(module);
            }
        }
        try (var f = new Fixture()) {
            var module = f.module(stage -> {});
            set(f.identity, "identityService", null);
            check(
                    failure(module::enable) instanceof NullPointerException,
                    "inactive dependency fails before registration");
            f.inactive(module);
        }
    }

    private static void observerFaults() throws Exception {
        for (boolean localeFailure : List.of(true, false)) {
            try (var f = new Fixture()) {
                var changes = new ModuleRuntimeFixture.FaultSet<ClanChangeListener>();
                if (localeFailure) f.locale.failAdd = 1;
                else changes.failAdd = 1;
                var module =
                        f.module(
                                stage -> {
                                    f.capture();
                                    if (stage == ClanModule.EnableStage.COMPLETER_BOUND)
                                        set(f.service, "changeListeners", changes);
                                });
                RuntimeException expected =
                        localeFailure ? f.locale.startupFailure : changes.startupFailure;
                check(
                        failure(module::enable) == expected,
                        "partial observer add preserves original exception");
                f.inactive(module);
                check(changes.isEmpty(), "partially installed change subscription removed");
                f.locale.failAdd = 0;
                changes.failAdd = 0;
                module.enable();
                f.capture();
                f.active(module);
                module.disable();
                f.inactive(module);
            }
        }
        try (var f = new Fixture()) {
            ClanModule[] reference = new ClanModule[1];
            f.locale.afterAdd =
                    () ->
                            check(
                                    failure(reference[0]::enable) instanceof IllegalStateException,
                                    "reentrant startup cannot install a second runtime");
            reference[0] = f.module(stage -> {});
            reference[0].enable();
            f.capture();
            f.active(reference[0]);
            reference[0].disable();
            f.inactive(reference[0]);
        }
    }

    private static void cleanupFailures() throws Exception {
        for (boolean startup : List.of(false, true)) {
            try (var f = new Fixture()) {
                var changes = new ModuleRuntimeFixture.FaultSet<ClanChangeListener>();
                var original = new IllegalStateException("primary late startup failure");
                var views = new ArrayList<View>();
                var module =
                        f.module(
                                stage -> {
                                    f.capture();
                                    if (stage == ClanModule.EnableStage.COMPLETER_BOUND)
                                        set(f.service, "changeListeners", changes);
                                    if (stage == ClanModule.EnableStage.RUNTIME_PUBLISHED) {
                                        views.add(f.view(f.menu, "FailClose", false, true));
                                        views.add(f.view(f.menu, "GoodClose", false, false));
                                        f.locale.failRemove = f.locale.removals + 1;
                                        changes.failRemove = changes.removals + 1;
                                        if (startup) throw original;
                                    }
                                });
                RuntimeException actual;
                if (startup) actual = failure(module::enable);
                else {
                    module.enable();
                    actual = failure(module::disable);
                }
                check(
                        !startup || actual == original,
                        "cleanup cannot mask original startup exception");
                check(
                        contains(actual, f.locale.cleanupFailure)
                                && contains(actual, changes.cleanupFailure),
                        "both independent unsubscribe failures remain visible with context");
                check(
                        f.runtime.handlerCount() == 0
                                && f.runtime.command.getExecutor() == f.runtime.plugin
                                && f.runtime.command.getTabCompleter() == null,
                        "menu cleanup failure cannot skip independent handler and command release");
                f.inactive(module);
                check(
                        views.stream().allMatch(view -> view.closes == 1),
                        "one failed close does not skip another view");
                check(
                        f.warnings.size() == 1 && f.warnings.getFirst().contains("ClanMenu"),
                        "inventory close failure is reported");
                module.disable();
                f.inactive(module);
            }
        }
    }

    private static void lateLogging() throws Exception {
        try (var f = new Fixture()) {
            var original = new IllegalStateException("injected final log failure");
            Handler fault =
                    new Handler() {
                        public void publish(LogRecord record) {
                            if (record.getMessage().equals("Clan module enabled.")) throw original;
                        }

                        public void flush() {}

                        public void close() {}
                    };
            f.runtime.logger.addHandler(fault);
            var module = f.module(stage -> f.capture());
            check(
                    failure(module::enable) == original,
                    "final startup logging is inside rollback boundary");
            f.inactive(module);
            f.runtime.logger.removeHandler(fault);
            module.enable();
            f.capture();
            f.active(module);
            module.disable();
            f.inactive(module);
        }
    }

    private static void refusedCleanupAndBindings() throws Exception {
        try (var f = new Fixture()) {
            var module = f.module(stage -> {});
            module.enable();
            f.capture();
            var retained =
                    new java.util.LinkedHashSet<Consumer<Player>>() {
                        boolean refused = true;

                        @Override
                        public boolean remove(Object value) {
                            if (refused)
                                throw new IllegalStateException("persistent external refusal");
                            return super.remove(value);
                        }
                    };
            retained.addAll(f.locale);
            set(f.runtime.messages.localization(), "localeListeners", retained);
            var failure = failure(module::disable);
            check(
                    containsMessage(failure, "locale observer") && retained.size() == 1,
                    "module reports real residual observer after refused release");
            check(
                    f.runtime.handlerCount() == 0
                            && f.runtime.command.getExecutor() == f.runtime.plugin
                            && f.runtime.command.getTabCompleter() == null,
                    "refused observer cannot prevent independent handler/command cleanup");
            check(
                    failure(module::getClanService) instanceof NullPointerException,
                    "failed cleanup cannot expose service");
            int registrations = f.runtime.registered.size();
            failure(module::enable);
            check(
                    f.runtime.registered.size() == registrations,
                    "unresolved cleanup blocks acquiring another runtime");
            retained.refused = false;
            module.enable();
            f.capture();
            check(
                    retained.size() == 1 && get(module, "pendingCleanup") == null,
                    "module retry removes previous callback before fresh activation");
            module.disable();
            check(retained.isEmpty(), "module retry clears eventual observer registration");
        }
        try (var f = new Fixture()) {
            var module = f.module(stage -> {});
            module.enable();
            f.capture();
            var blocked = new RefusingHandlers(f.runtime.registered.getLast(), f.runtime.plugin);
            try {
                RuntimeException thrown = failure(module::disable);
                check(
                        contains(thrown, blocked.failure) && f.runtime.handlerCount() == 1,
                        "real refused HandlerList release is reported without fake success");
                check(
                        f.runtime.command.getExecutor() == f.runtime.plugin
                                && f.runtime.command.getTabCompleter() == null
                                && f.locale.isEmpty(),
                        "listener removal failure cannot skip independent command or observer cleanup");
                blocked.refused = false;
                module.disable();
                f.inactive(module);
                check(
                        get(module, "pendingCleanup") == null,
                        "successful disable retry releases pending cleanup references");
            } finally {
                blocked.refused = false;
                blocked.target = null;
            }
        }
        try (var f = new Fixture()) {
            var module = f.module(stage -> {});
            module.enable();
            f.capture();
            org.bukkit.command.CommandExecutor foreignExecutor =
                    (sender, command, label, arguments) -> true;
            org.bukkit.command.TabCompleter foreignCompleter =
                    (sender, command, label, arguments) -> List.of();
            f.runtime.command.setExecutor(foreignExecutor);
            f.runtime.command.setTabCompleter(foreignCompleter);
            module.disable();
            check(
                    f.runtime.command.getExecutor() == foreignExecutor
                            && f.runtime.command.getTabCompleter() == foreignCompleter,
                    "cleanup never clears replacement bindings owned by someone else");
            check(
                    f.runtime.handlerCount() == 0 && f.locale.isEmpty(),
                    "foreign bindings do not prevent own handler/observer release");
        }
    }

    private static boolean containsMessage(Throwable error, String part) {
        if (error.getMessage() != null && error.getMessage().contains(part)) return true;
        if (error.getCause() != null && containsMessage(error.getCause(), part)) return true;
        return Arrays.stream(error.getSuppressed()).anyMatch(child -> containsMessage(child, part));
    }

    private static final class RefusingHandlers extends org.bukkit.event.HandlerList {
        org.bukkit.event.Listener target;
        boolean refused = true;
        final RuntimeException failure =
                new IllegalStateException("external HandlerList unregister refused");

        RefusingHandlers(org.bukkit.event.Listener target, org.bukkit.plugin.Plugin plugin) {
            this.target = target;
            register(
                    new org.bukkit.plugin.RegisteredListener(
                            target,
                            (listener, event) -> {},
                            org.bukkit.event.EventPriority.NORMAL,
                            plugin,
                            false));
        }

        @Override
        public synchronized void unregister(org.bukkit.event.Listener listener) {
            if (refused && listener == target) throw failure;
            super.unregister(listener);
        }
    }

    private static boolean contains(Throwable root, Throwable expected) {
        if (root == expected) return true;
        if (root.getCause() != null && contains(root.getCause(), expected)) return true;
        return Arrays.stream(root.getSuppressed()).anyMatch(child -> contains(child, expected));
    }

    private static RuntimeException failure(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            return exception;
        }
        throw new AssertionError("expected lifecycle failure");
    }

    private static final class Fixture implements AutoCloseable {
        final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture("clan");
        final Path directory = Files.createTempDirectory("vapeecore-clan-lifecycle-");
        final IdentityModule identity =
                ModuleRuntimeFixture.dependency(
                        IdentityModule.class,
                        Map.of("identityService", new PlayerIdentityService(runtime.players)));
        final ModuleRuntimeFixture.FaultSet<Consumer<Player>> locale =
                new ModuleRuntimeFixture.FaultSet<>();
        final List<String> warnings = new ArrayList<>();
        ClanService service;
        ClanMenu menu;

        Fixture() throws Exception {
            set(runtime.plugin, "dataFolder", directory.toFile());
            set(runtime.messages.localization(), "localeListeners", locale);
            runtime.logger.addHandler(
                    new Handler() {
                        public void publish(LogRecord record) {
                            if (record.getLevel().intValue()
                                    >= java.util.logging.Level.WARNING.intValue())
                                warnings.add(record.getMessage());
                        }

                        public void flush() {}

                        public void close() {}
                    });
        }

        ClanModule module(Consumer<ClanModule.EnableStage> checkpoint) {
            return new ClanModule(
                    runtime.plugin,
                    new ConfigService(runtime.plugin),
                    identity,
                    runtime.messages,
                    new CommandHelpRenderer(runtime.messages),
                    checkpoint);
        }

        void capture() {
            var listener = runtime.registered.getLast();
            service = (ClanService) get(listener, "clans");
            menu = (ClanMenu) get(listener, "menu");
        }

        void active(ClanModule module) {
            check(
                    runtime.registered.getLast() instanceof ClanMenuListener
                            && runtime.handlerCount() == 5,
                    "exactly one real listener with five handlers");
            check(
                    runtime.command.getExecutor() instanceof ClanCommand
                            && runtime.command.getTabCompleter() == runtime.command.getExecutor(),
                    "intended command pair bound");
            check(
                    module.getClanService() == service
                            && get(module, "menu") == menu
                            && get(runtime.command.getExecutor(), "clans") == service,
                    "all bindings use current service");
            check(
                    locale.size() == 1 && ((Set<?>) get(service, "changeListeners")).size() == 1,
                    "exactly one subscription per observer source");
        }

        void inactive(ClanModule module) {
            check(runtime.handlerCount() == 0, "listener rollback leaves no registered handler");
            check(
                    runtime.command.getExecutor() == runtime.plugin,
                    "executor cleanup restores Bukkit fallback");
            check(
                    runtime.command.getTabCompleter() == null,
                    "completer cleanup removes obsolete executor");
            check(locale.isEmpty(), "locale observer removed");
            if (service != null)
                check(
                        ((Set<?>) get(service, "changeListeners")).isEmpty(),
                        "clan observer removed");
            if (menu != null)
                check(((Map<?, ?>) get(menu, "active")).isEmpty(), "owned view registry cleared");
            check(
                    List.of("service", "menu", "listener", "command", "binding").stream()
                            .allMatch(field -> get(module, field) == null),
                    "all published runtime references released");
            check(
                    failure(module::getClanService) instanceof NullPointerException,
                    "inactive getters fail closed");
        }

        @SuppressWarnings("unchecked")
        View view(ClanMenu owner, String name, boolean foreign, boolean failClose) {
            var view = new View(runtime, owner, name, foreign, failClose);
            ((Map<UUID, Inventory>) get(owner, "active")).put(view.id, view.owned);
            runtime.online.add(view.player);
            return view;
        }

        @Override
        public void close() throws Exception {
            // All ownership assertions above run before the fixture's safety teardown.
            runtime.close();
            try (var paths = Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList())
                    Files.delete(path);
            }
        }
    }

    private static final class View {
        final UUID id;
        final Inventory owned;
        final Player player;
        int closes;
        int handlersDuringClose;

        View(
                ModuleRuntimeFixture runtime,
                ClanMenu menu,
                String name,
                boolean foreign,
                boolean failClose) {
            id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var holder =
                    new dev.vapee.core.clan.gui.ClanMenuHolder(
                            id, dev.vapee.core.clan.gui.ClanMenuView.OVERVIEW, 0, Map.of());
            owned =
                    ModuleRuntimeFixture.proxy(
                            Inventory.class,
                            (method, args) ->
                                    switch (method) {
                                        case "getHolder" -> holder;
                                        case "getSize" -> ClanMenu.DEFAULT_LAYOUT.menu().size();
                                        default -> null;
                                    });
            try {
                var bind = holder.getClass().getDeclaredMethod("bind", Inventory.class);
                bind.setAccessible(true);
                bind.invoke(holder, owned);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
            Inventory current =
                    foreign ? ModuleRuntimeFixture.proxy(Inventory.class, (m, a) -> null) : owned;
            InventoryView inventoryView =
                    ModuleRuntimeFixture.proxy(
                            InventoryView.class,
                            (method, args) -> method.equals("getTopInventory") ? current : null);
            player =
                    ModuleRuntimeFixture.proxy(
                            Player.class,
                            (method, args) ->
                                    switch (method) {
                                        case "getUniqueId" -> id;
                                        case "isOnline" -> true;
                                        case "getOpenInventory" -> inventoryView;
                                        case "closeInventory" -> {
                                            closes++;
                                            handlersDuringClose = runtime.handlerCount();
                                            var event =
                                                    new org.bukkit.event.inventory
                                                            .InventoryCloseEvent(inventoryView);
                                            for (var registered :
                                                    org.bukkit.event.inventory.InventoryCloseEvent
                                                            .getHandlerList()
                                                            .getRegisteredListeners()) {
                                                if (registered.getPlugin() == runtime.plugin
                                                        && registered.getListener()
                                                                instanceof
                                                                ClanMenuListener closeListener) {
                                                    closeListener.onInventoryClose(event);
                                                }
                                            }
                                            check(
                                                    !((Map<?, ?>) get(menu, "active"))
                                                            .containsKey(id),
                                                    "registered close handler detaches exact owned view before unregistration");
                                            if (failClose)
                                                throw new IllegalStateException(
                                                        "injected inventory close failure");
                                            yield null;
                                        }
                                        default -> throw new AssertionError(method);
                                    });
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
