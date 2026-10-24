package dev.vapee.core.friend;

import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.command.help.CommandHelpRenderer;
import dev.vapee.core.config.ConfigService;
import dev.vapee.core.friend.command.FriendCommand;
import dev.vapee.core.friend.gui.FriendMenu;
import dev.vapee.core.friend.gui.FriendMenuListener;
import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.social.SocialService;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;

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

/** Exercises the real module, real PluginCommand and actual Paper HandlerLists. */
public final class FriendModuleLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("retry")) {
            startupFault(FriendModule.EnableStage.RUNTIME_PUBLISHED);
            System.out.println("FriendModuleLifecycleHarness passed " + checks + " checks.");
            return;
        }
        lifecycle();
        for (var stage : FriendModule.EnableStage.values()) startupFault(stage);
        externalFaults();
        observerFaults();
        cleanupFailures();
        lateLogging();
        System.out.println("FriendModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static void lifecycle() throws Exception {
        try (var f = new Fixture()) {
            FriendModule module = f.module(stage -> {});
            module.disable();
            module.enable();
            f.capture();
            f.active(module);
            var firstService = module.getFriendService();
            var firstMenu = module.getFriendMenu();
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
                    module.getFriendService() == firstService
                            && module.getFriendMenu() == firstMenu
                            && f.runtime.command.getExecutor() == firstExecutor,
                    "double enable preserves usable runtime and bindings");

            UUID a = new UUID(0, 101), b = new UUID(0, 102);
            f.runtime.players.loadPlayer(a, "First");
            f.runtime.players.loadPlayer(b, "Second");
            check(
                    firstService.sendRequest(a, b) == FriendResult.SUCCESS
                            && firstService.acceptRequest(b, a) == FriendResult.SUCCESS,
                    "actual policy and repository accept disposable friendship");
            byte[] persisted = Files.readAllBytes(f.directory.resolve("friends.yml"));
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
                    Arrays.equals(
                            persisted, Files.readAllBytes(f.directory.resolve("friends.yml"))),
                    "disable does not rewrite durable friendships");
            int removals = f.locale.removals;
            module.disable();
            check(
                    f.locale.removals == removals && own.closes == 1,
                    "repeated disable has no duplicate effects");
            module.enable();
            f.capture();
            f.active(module);
            check(
                    module.getFriendService() != firstService
                            && module.getFriendMenu() != firstMenu
                            && f.runtime.registered.getLast() != firstListener
                            && f.runtime.command.getExecutor() != firstExecutor,
                    "re-enable creates four fresh runtime objects");
            check(
                    module.getFriendService().getFriends(a).equals(List.of(b)),
                    "fresh service reloads persisted friendships");
            check(
                    ((Set<?>) get(firstService, "changeListeners")).isEmpty(),
                    "old service has no menu observer");
            check(
                    ((Map<?, ?>) get(firstMenu, "activeInventories")).isEmpty(),
                    "old menu remains detached");
            module.disable();
            f.inactive(module);
        }
    }

    private static void startupFault(FriendModule.EnableStage stage) throws Exception {
        try (var f = new Fixture()) {
            var fail = new AtomicBoolean(true);
            RuntimeException original = new IllegalStateException("injected " + stage);
            FriendModule[] reference = new FriendModule[1];
            var module =
                    f.module(
                            point -> {
                                f.capture();
                                if (point == stage && fail.get()) {
                                    if (point == FriendModule.EnableStage.RUNTIME_PUBLISHED) {
                                        check(
                                                get(reference[0], "friendService") == f.service,
                                                "publication stage actually reached");
                                        check(
                                                failure(reference[0]::getFriendService)
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
            if (stage == FriendModule.EnableStage.RUNTIME_PUBLISHED) {
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
                var changes = new ModuleRuntimeFixture.FaultSet<FriendChangeListener>();
                if (localeFailure) f.locale.failAdd = 1;
                else changes.failAdd = 1;
                var module =
                        f.module(
                                stage -> {
                                    f.capture();
                                    if (stage == FriendModule.EnableStage.COMPLETER_BOUND)
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
            FriendModule[] reference = new FriendModule[1];
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
                var changes = new ModuleRuntimeFixture.FaultSet<FriendChangeListener>();
                var original = new IllegalStateException("primary late startup failure");
                var views = new ArrayList<View>();
                var module =
                        f.module(
                                stage -> {
                                    f.capture();
                                    if (stage == FriendModule.EnableStage.COMPLETER_BOUND)
                                        set(f.service, "changeListeners", changes);
                                    if (stage == FriendModule.EnableStage.RUNTIME_PUBLISHED) {
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
                f.inactive(module);
                check(
                        views.stream().allMatch(view -> view.closes == 1),
                        "one failed close does not skip another view");
                check(
                        f.warnings.size() == 1 && f.warnings.getFirst().contains("FriendMenu"),
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
                            if (record.getMessage().equals("Friend module enabled."))
                                throw original;
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
        final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture("friend");
        final Path directory = Files.createTempDirectory("vapeecore-friend-lifecycle-");
        final IdentityModule identity =
                ModuleRuntimeFixture.dependency(
                        IdentityModule.class,
                        Map.of("identityService", new PlayerIdentityService(runtime.players)));
        final SocialModule social =
                ModuleRuntimeFixture.dependency(
                        SocialModule.class,
                        Map.of(
                                "socialService",
                                new SocialService(runtime.players, runtime.logger)));
        final ModuleRuntimeFixture.FaultSet<Consumer<Player>> locale =
                new ModuleRuntimeFixture.FaultSet<>();
        final List<String> warnings = new ArrayList<>();
        FriendService service;
        FriendMenu menu;

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

        FriendModule module(Consumer<FriendModule.EnableStage> checkpoint) {
            return new FriendModule(
                    runtime.plugin,
                    new ConfigService(runtime.plugin),
                    runtime.playerModule,
                    social,
                    identity,
                    runtime.messages,
                    new CommandHelpRenderer(runtime.messages),
                    checkpoint);
        }

        void capture() {
            var listener = runtime.registered.getLast();
            service = (FriendService) get(listener, "friends");
            menu = (FriendMenu) get(listener, "menu");
        }

        void active(FriendModule module) {
            check(
                    runtime.registered.getLast() instanceof FriendMenuListener
                            && runtime.handlerCount() == 5,
                    "exactly one real listener with five handlers");
            check(
                    runtime.command.getExecutor() instanceof FriendCommand
                            && runtime.command.getTabCompleter() == runtime.command.getExecutor(),
                    "intended command pair bound");
            check(
                    module.getFriendService() == service
                            && module.getFriendMenu() == menu
                            && get(runtime.command.getExecutor(), "friends") == service,
                    "all bindings use current service");
            check(
                    locale.size() == 1 && ((Set<?>) get(service, "changeListeners")).size() == 1,
                    "exactly one subscription per observer source");
        }

        void inactive(FriendModule module) {
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
                        "friend observer removed");
            if (menu != null)
                check(
                        ((Map<?, ?>) get(menu, "activeInventories")).isEmpty(),
                        "owned view registry cleared");
            check(
                    List.of("friendService", "friendMenu", "friendMenuListener", "friendCommand")
                            .stream()
                            .allMatch(field -> get(module, field) == null),
                    "all published runtime references released");
            check(
                    failure(module::getFriendService) instanceof NullPointerException
                            && failure(module::getFriendMenu) instanceof NullPointerException,
                    "inactive getters fail closed");
        }

        @SuppressWarnings("unchecked")
        View view(FriendMenu owner, String name, boolean foreign, boolean failClose) {
            var view = new View(runtime, name, foreign, failClose);
            ((Map<UUID, Inventory>) get(owner, "activeInventories")).put(view.id, view.owned);
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
        final Inventory owned = ModuleRuntimeFixture.proxy(Inventory.class, (method, args) -> null);
        final Player player;
        int closes;
        int handlersDuringClose;

        View(ModuleRuntimeFixture runtime, String name, boolean foreign, boolean failClose) {
            id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
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
