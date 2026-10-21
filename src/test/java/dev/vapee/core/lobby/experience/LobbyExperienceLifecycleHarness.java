package dev.vapee.core.lobby.experience;

import static dev.vapee.core.module.ModuleRuntimeFixture.allocate;
import static dev.vapee.core.module.ModuleRuntimeFixture.dependency;
import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.proxy;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.LobbySpawn;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.experience.player.PlayerMenu;
import dev.vapee.core.lobby.experience.player.PlayerMenuHolder;
import dev.vapee.core.lobby.item.LobbyItemRegistry;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.lobby.message.LobbyMessageService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.lobby.warp.WarpConfig;
import dev.vapee.core.lobby.warp.WarpModule;
import dev.vapee.core.lobby.warp.WarpService;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.module.ModuleRuntimeFixture.FaultSet;
import dev.vapee.core.settings.SettingsModule;
import dev.vapee.core.settings.visibility.SettingsModuleLifecycleHarness;
import dev.vapee.core.utility.UtilityModule;
import dev.vapee.core.utility.UtilityService;
import dev.vapee.core.visibility.VisibilityModule;
import dev.vapee.core.visibility.VisibilityService;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Actual owning module lifecycle; only Paper/services outside its ownership are controlled. */
public final class LobbyExperienceLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        verifyLifecycle();
        System.out.println("LobbyExperienceLifecycleHarness passed " + checks + " checks.");
    }

    public static int verifyLifecycle() throws Exception {
        int before = checks;
        for (var stage : LobbyExperienceModule.EnableStage.values()) {
            try (var f = new Fixture()) {
                RuntimeException primary = new IllegalStateException("injected " + stage);
                var failure = new boolean[] {true};
                var module =
                        f.module(
                                point -> {
                                    if (point == stage && failure[0]) throw primary;
                                });
                int settingsHandlers = f.runtime.handlerCount();
                RuntimeException actual = null;
                try {
                    module.enable();
                } catch (RuntimeException expected) {
                    actual = expected;
                }
                check(actual == primary, "startup exception identity at " + stage);
                f.inactive(module, settingsHandlers);
                failure[0] = false;
                module.enable();
                check(f.registry.isActive(), "retry activates registry");
                check(
                        f.runtime.handlerCount() > settingsHandlers,
                        "retry registers actual lobby handlers");
                check(
                        f.count(f.runtime.messages.localization(), "localeListeners") == 6,
                        "exactly three Settings plus Player/Navigator/module locale subscriptions");
                check(f.count(f.warps, "changeListeners") == 1, "retry owns one warp subscription");
                f.glideSubscriptions(1);
                Object oldParent = get(module, "playerMenu");
                f.backCallbacks(true);
                module.disable();
                f.inactive(module, settingsHandlers);
                check(
                        ((Map<?, ?>) get(oldParent, "active")).isEmpty(),
                        "old Player Menu active registry released");
                module.disable();
                check(
                        f.runtime.handlerCount() == settingsHandlers,
                        "second disable preserves Settings handlers");
            }
        }
        try (var f = new Fixture()) {
            LobbyExperienceModule[] owning = new LobbyExperienceModule[1];
            var actors = new java.util.ArrayList<MenuActor>();
            var queued = new java.util.ArrayList<Runnable>();
            var scheduler =
                    proxy(
                            org.bukkit.scheduler.BukkitScheduler.class,
                            (name, args) -> {
                                if (name.equals("runTaskLater")) {
                                    queued.add((Runnable) args[1]);
                                    return proxy(
                                            org.bukkit.scheduler.BukkitTask.class,
                                            (method, arguments) -> null);
                                }
                                throw new AssertionError(name);
                            });
            f.runtime.serverCalls =
                    (name, args) ->
                            switch (name) {
                                case "getScheduler" -> scheduler;
                                case "createInventory" ->
                                        inventory((InventoryHolder) args[0], (int) args[1]);
                                default -> throw new AssertionError(name);
                            };
            RuntimeException primary =
                    new IllegalStateException("failure with owned views and queued work");
            owning[0] =
                    f.module(
                            stage -> {
                                if (stage != LobbyExperienceModule.EnableStage.RUNTIME_ACTIVE)
                                    return;
                                for (String field : List.of("playerMenu", "navigatorMenu")) {
                                    var actor = new MenuActor(f, get(owning[0], field), field);
                                    actors.add(actor);
                                }
                                var listener =
                                        (LobbyExperienceListener)
                                                get(owning[0], "experienceListener");
                                listener.onPlayerRespawn(
                                        new org.bukkit.event.player.PlayerRespawnEvent(
                                                actors.get(0).player,
                                                new org.bukkit.Location(
                                                        actors.get(0).world, 0, 80, 0),
                                                false));
                                throw primary;
                            });
            int settingsHandlers = f.runtime.handlerCount();
            try {
                owning[0].enable();
                throw new AssertionError("late failure missing");
            } catch (RuntimeException actual) {
                check(actual == primary, "owned view cleanup preserves startup exception");
            }
            f.inactive(owning[0], settingsHandlers);
            for (MenuActor actor : actors) actor.closedAndInert();
            check(queued.size() == 1, "real experience listener queued synchronization");
            queued.forEach(Runnable::run);
            check(true, "queued synchronization is inert after failed enable");
        }
        try (var f = new Fixture()) {
            var module = f.module(stage -> {});
            int settingsHandlers = f.runtime.handlerCount();
            module.enable();
            f.runtime.serverCalls =
                    (name, args) -> {
                        if (name.equals("createInventory"))
                            return inventory((InventoryHolder) args[0], (int) args[1]);
                        throw new AssertionError(name);
                    };
            var parent = new MenuActor(f, get(module, "playerMenu"), "normal-parent");
            var navigator = new MenuActor(f, get(module, "navigatorMenu"), "normal-navigator");
            module.disable();
            parent.closedAndInert();
            navigator.closedAndInert();
            f.inactive(module, settingsHandlers);
            module.disable();
        }
        // A locale cleanup error must not interrupt menus, callback, glide or Bukkit cleanup.
        try (var f = new Fixture()) {
            var locales = new FaultSet<Object>();
            locales.addAll((Set<?>) get(f.runtime.messages.localization(), "localeListeners"));
            set(f.runtime.messages.localization(), "localeListeners", locales);
            RuntimeException primary = new IllegalStateException("late startup failure");
            var module =
                    f.module(
                            stage -> {
                                if (stage == LobbyExperienceModule.EnableStage.RUNTIME_ACTIVE) {
                                    locales.failRemove = 1;
                                    throw primary;
                                }
                            });
            int settingsHandlers = f.runtime.handlerCount();
            try {
                module.enable();
                throw new AssertionError("missing startup failure");
            } catch (RuntimeException actual) {
                check(
                        actual == primary
                                && actual.getSuppressed().length == 1
                                && actual.getSuppressed()[0].getCause() == locales.cleanupFailure,
                        "startup remains primary and cleanup failure is suppressed");
            }
            f.inactive(module, settingsHandlers);
        }
        return checks - before;
    }

    private static final class Fixture implements AutoCloseable {
        final ModuleRuntimeFixture runtime = new ModuleRuntimeFixture();
        final SettingsModule settings = SettingsModuleLifecycleHarness.module(runtime);
        final LobbyItemRegistry registry = new LobbyItemRegistry();
        final LobbyConfig config =
                new LobbyConfig(Path.of("target/unused-lifecycle-lobby.yml"), runtime.logger);
        final LobbyService lobby = new LobbyService(runtime.plugin, config);
        final LobbyItemService items =
                new LobbyItemService(
                        runtime.plugin,
                        lobby,
                        registry,
                        () ->
                                registry.entries().values().stream()
                                        .collect(
                                                java.util.stream.Collectors.toMap(
                                                        LobbyItemRegistry.Entry::id,
                                                        LobbyItemRegistry.Entry::defaults)),
                        runtime.messages.localization());
        final LobbyPlayerStateService states =
                new LobbyPlayerStateService(runtime.plugin, config, lobby, items);
        final WarpService warps =
                new WarpService(
                        new WarpConfig(
                                Path.of("target/unused-lifecycle-warps.yml"), runtime.logger),
                        name -> null,
                        new TreeMap<>());
        final UtilityService utility = new UtilityService(() -> true, List::of, player -> 20);
        final ActivityService activities =
                new ActivityService(runtime.plugin.getServer(), runtime.players, runtime.logger);
        final LobbyModule lobbyModule =
                dependency(
                        LobbyModule.class,
                        Map.of(
                                "itemRegistry",
                                registry,
                                "lobbyService",
                                lobby,
                                "lobbyItemService",
                                items,
                                "lobbyPlayerStateService",
                                states,
                                "lobbyMessageService",
                                new LobbyMessageService(runtime.plugin, config, runtime.messages)));
        int closes;

        Fixture() throws Exception {
            settings.enable();
        }

        LobbyExperienceModule module(Consumer<LobbyExperienceModule.EnableStage> fault) {
            return new LobbyExperienceModule(
                    runtime.plugin,
                    lobbyModule,
                    runtime.playerModule,
                    settings,
                    dependency(WarpModule.class, Map.of("warpService", warps)),
                    dependency(
                            VisibilityModule.class,
                            Map.of("service", allocate(VisibilityService.class))),
                    dependency(ActivityModule.class, Map.of("activityService", activities)),
                    allocate(FriendModule.class),
                    dependency(UtilityModule.class, Map.of("utilityService", utility)),
                    runtime.messages,
                    fault);
        }

        void inactive(LobbyExperienceModule module, int handlers) {
            check(!registry.isActive(), "failed/disabled registry inactive");
            check(runtime.handlerCount() == handlers, "only unrelated Settings handlers remain");
            for (var listener : runtime.registered) {
                if (listener instanceof LobbyExperienceListener) {
                    check(
                            Boolean.FALSE.equals(get(listener, "active")),
                            "queued experience work deactivated");
                }
                if (listener instanceof dev.vapee.core.lobby.experience.glide.LobbyGlideListener) {
                    Object glide = get(listener, "glide");
                    check(
                            Boolean.FALSE.equals(get(glide, "enabled"))
                                    && ((Map<?, ?>) get(glide, "states")).isEmpty()
                                    && ((Map<?, ?>) get(glide, "pending")).isEmpty(),
                            "old glide runtime disabled without pending tasks or states");
                }
            }
            check(
                    count(runtime.messages.localization(), "localeListeners") == 3,
                    "only Settings locales remain");
            check(count(warps, "changeListeners") == 0, "navigator warp observer removed");
            glideSubscriptions(0);
            @SuppressWarnings("unchecked")
            Predicate<Player> eligible = (Predicate<Player>) get(states, "itemRefreshEligibility");
            check(!eligible.test(null), "item refresh eligibility fails closed");
            for (String name :
                    List.of(
                            "playerMenu",
                            "playerMenuListener",
                            "playerSelectionListener",
                            "navigatorMenu",
                            "navigatorListener",
                            "itemListener",
                            "experienceListener",
                            "visibilityAction",
                            "glideService",
                            "glideListener")) {
                check(get(module, name) == null, "runtime reference released: " + name);
            }
            backCallbacks(false);
        }

        void backCallbacks(boolean active) {
            UUID id = UUID.randomUUID();
            runtime.players.loadPlayer(id, "Navigation");
            org.bukkit.World world =
                    proxy(
                            org.bukkit.World.class,
                            (name, args) -> name.equals("getName") ? "lobby" : null);
            lobby.applySpawn(Optional.of(new LobbySpawn("lobby", 0, 80, 0, 0, 0)));
            Inventory[] current = new Inventory[1];
            Player player =
                    proxy(
                            Player.class,
                            (name, args) ->
                                    switch (name) {
                                        case "getUniqueId" -> id;
                                        case "isOnline", "hasPermission" -> true;
                                        case "getWorld" -> world;
                                        case "locale" -> Locale.US;
                                        case "closeInventory" -> {
                                            closes++;
                                            current[0] = null;
                                            yield null;
                                        }
                                        case "openInventory" -> {
                                            current[0] = (Inventory) args[0];
                                            yield view(current);
                                        }
                                        case "getOpenInventory" -> view(current);
                                        default ->
                                                throw new AssertionError(
                                                        "unexpected navigation player: " + name);
                                    });
            runtime.serverCalls =
                    (name, args) -> {
                        if (name.equals("createInventory"))
                            return inventory((InventoryHolder) args[0], (int) args[1]);
                        throw new AssertionError(name);
                    };
            if (active) {
                Object menu = get(get(settings, "settingsListener"), "backOpener");
                // Only the ItemStack renderer is a fake Paper boundary; parent.open and holders are
                // real.
                for (var field : menu.getClass().getDeclaredFields()) {
                    field.setAccessible(true);
                    if (get(menu, field.getName()) instanceof PlayerMenu parent) {
                        set(
                                parent,
                                "items",
                                (Function<dev.vapee.core.ui.UiItemSpec, ItemStack>)
                                        spec -> new TestItem());
                    }
                }
            }
            for (String listener : List.of("settingsListener", "visibilitySettingsListener")) {
                @SuppressWarnings("unchecked")
                Consumer<Player> back =
                        (Consumer<Player>)
                                get(
                                        get(settings, listener),
                                        listener.equals("settingsListener")
                                                ? "backOpener"
                                                : "settingsMenuOpener");
                int previous = closes;
                back.accept(player);
                check(
                        active
                                ? current[0] != null
                                        && current[0].getHolder() instanceof PlayerMenuHolder
                                        && closes == previous
                                : closes == previous + 1 && current[0] == null,
                        listener
                                + (active
                                        ? " returns to active parent"
                                        : " safe fallback closes without retained parent"));
                if (!active)
                    check(
                            Arrays.stream(back.getClass().getDeclaredFields())
                                    .noneMatch(
                                            field ->
                                                    PlayerMenu.class.isAssignableFrom(
                                                            field.getType())),
                            "fallback captures no stale parent");
            }
        }

        org.bukkit.inventory.InventoryView view(Inventory[] current) {
            return proxy(
                    org.bukkit.inventory.InventoryView.class,
                    (name, args) -> name.equals("getTopInventory") ? current[0] : null);
        }

        void glideSubscriptions(int expected) {
            check(
                    count(utility, "flightListeners") == expected,
                    "owned flight subscriptions=" + expected);
            check(
                    count(states, "stateListeners") == expected,
                    "owned state subscriptions=" + expected);
            check(
                    count(activities, "membershipListeners") == expected,
                    "owned activity subscriptions=" + expected);
        }

        int count(Object service, String name) {
            return ((Set<?>) get(service, name)).size();
        }

        @Override
        public void close() throws Exception {
            settings.disable();
            runtime.close();
        }
    }

    private static final class MenuActor {
        final UUID id = UUID.randomUUID();
        final org.bukkit.World world =
                proxy(
                        org.bukkit.World.class,
                        (name, args) -> name.equals("getName") ? "lobby" : null);
        final Inventory bottom = inventory(null, 36);
        final Player player;
        final Object menu;
        Inventory current = bottom;
        final Inventory owned;

        MenuActor(Fixture f, Object menu, String name) {
            this.menu = menu;
            int handlersWhileOpen = f.runtime.handlerCount();
            f.runtime.players.loadPlayer(id, name);
            f.lobby.applySpawn(Optional.of(new LobbySpawn("lobby", 0, 80, 0, 0, 0)));
            player =
                    proxy(
                            Player.class,
                            (method, args) ->
                                    switch (method) {
                                        case "getUniqueId" -> id;
                                        case "getName" -> name;
                                        case "getWorld" -> world;
                                        case "isOnline", "hasPermission" -> true;
                                        case "locale" -> Locale.US;
                                        case "getOpenInventory" -> view();
                                        case "openInventory" -> {
                                            current = (Inventory) args[0];
                                            yield view();
                                        }
                                        case "closeInventory" -> {
                                            check(
                                                    f.runtime.handlerCount() == handlersWhileOpen,
                                                    "owned menu closes before Bukkit handlers are unregistered");
                                            current = bottom;
                                            yield null;
                                        }
                                        default -> throw new AssertionError(method);
                                    });
            f.runtime.online.add(player);
            try {
                if (menu instanceof PlayerMenu) {
                    set(
                            menu,
                            "items",
                            (Function<dev.vapee.core.ui.UiItemSpec, ItemStack>)
                                    spec -> new TestItem());
                } else {
                    Class<?> renderer =
                            Arrays.stream(menu.getClass().getDeclaredClasses())
                                    .filter(type -> type.getSimpleName().equals("ItemRenderer"))
                                    .findFirst()
                                    .orElseThrow();
                    set(menu, "itemRenderer", proxy(renderer, (method, args) -> new TestItem()));
                }
                menu.getClass().getMethod("open", Player.class).invoke(menu, player);
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
            owned = current;
            check(
                    owned != bottom && owned.getHolder() != null,
                    "real owning menu opened before late failure");
        }

        org.bukkit.inventory.InventoryView view() {
            return proxy(
                    org.bukkit.inventory.InventoryView.class,
                    (name, args) ->
                            switch (name) {
                                case "getTopInventory" -> current;
                                case "getBottomInventory" -> bottom;
                                case "getPlayer" -> player;
                                default -> throw new AssertionError(name);
                            });
        }

        void closedAndInert() throws Exception {
            check(current == bottom, "late failure closes currently owned inventory");
            Object holder = owned.getHolder();
            current = owned;
            boolean active =
                    (boolean)
                            menu.getClass()
                                    .getMethod(
                                            "isActive",
                                            Player.class,
                                            Inventory.class,
                                            holder.getClass())
                                    .invoke(menu, player, owned, holder);
            check(!active, "replayed stale inventory is inert after module rollback");
        }
    }

    private static Inventory inventory(InventoryHolder holder, int size) {
        ItemStack[] items = new ItemStack[size];
        return proxy(
                Inventory.class,
                (name, args) ->
                        switch (name) {
                            case "getHolder" -> holder;
                            case "getSize" -> size;
                            case "clear" -> {
                                Arrays.fill(items, null);
                                yield null;
                            }
                            case "setItem" -> {
                                items[(int) args[0]] = (ItemStack) args[1];
                                yield null;
                            }
                            case "getItem" -> items[(int) args[0]];
                            default -> throw new AssertionError("unexpected inventory: " + name);
                        });
    }

    private static final class TestItem extends ItemStack {
        TestItem() {
            super();
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
