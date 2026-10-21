package dev.vapee.core.settings.visibility;

import dev.vapee.core.identity.IdentityModule;
import dev.vapee.core.identity.PlayerIdentityService;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.module.ModuleRuntimeFixture;
import dev.vapee.core.presentation.PresentationModule;
import dev.vapee.core.presentation.PresentationService;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.settings.SettingsInventoryHolder;
import dev.vapee.core.settings.SettingsListener;
import dev.vapee.core.settings.SettingsModule;
import dev.vapee.core.settings.command.SettingsCommand;
import dev.vapee.core.visibility.VisibilityModule;
import dev.vapee.core.visibility.VisibilityService;
import java.util.Map;
import java.util.Set;
import org.bukkit.inventory.Inventory;

/** Real module registration/rollback/disable; menu ownership cleanup is exercised separately. */
public final class SettingsModuleLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        check(
                CoreModule.class.isAssignableFrom(SettingsModule.class)
                        && !ReloadParticipant.class.isAssignableFrom(SettingsModule.class),
                "Settings remains CoreModule without reload ownership");
        realLifecycle();
        observerRollback();
        inventoryRollback();
        var f = new VisibilityMenuFixture();
        var root = f.player("Root", true);
        var visibility = f.player("Visibility", true);
        var manage = f.player("Manage", true);
        f.rootMenu.open(root.player);
        f.visibilityMenu.open(visibility.player);
        f.visiblePlayersMenu.open(manage.player);
        Inventory rootInventory = root.open,
                visibilityInventory = visibility.open,
                manageInventory = manage.open;
        f.visibilityMenu.closeOpenInventories();
        f.visiblePlayersMenu.shutdown();
        f.rootMenu.closeOpenInventories();
        check(
                root.open == root.bottom
                        && visibility.open == visibility.bottom
                        && manage.open == manage.bottom,
                "cleanup closes three concurrently open own menu types");
        root.open = rootInventory;
        visibility.open = visibilityInventory;
        manage.open = manageInventory;
        check(
                !f.rootMenu.isActive(
                                root.player,
                                rootInventory,
                                (SettingsInventoryHolder) rootInventory.getHolder())
                        && !f.visibilityMenu.isActive(
                                visibility.player,
                                visibilityInventory,
                                (VisibilitySettingsHolder) visibilityInventory.getHolder())
                        && !f.visiblePlayersMenu.isActive(
                                manage.player,
                                manageInventory,
                                (VisiblePlayersHolder) manageInventory.getHolder()),
                "all active registries empty after cleanup");
        int saves = f.repository.saves;
        f.rootClick(root, rootInventory, 10, org.bukkit.event.inventory.ClickType.LEFT);
        f.visibilityClick(
                visibility, visibilityInventory, 9, org.bukkit.event.inventory.ClickType.LEFT);
        check(
                f.repository.saves == saves,
                "stale inventories remain inert after module-style cleanup");
        System.out.println("SettingsModuleLifecycleHarness passed " + checks + " checks.");
    }

    public static SettingsModule module(ModuleRuntimeFixture f) {
        // Only dependency modules are shells; SettingsModule and its menus/listeners are real.
        var presentation =
                ModuleRuntimeFixture.dependency(
                        PresentationModule.class,
                        Map.of(
                                "presentationService",
                                ModuleRuntimeFixture.allocate(PresentationService.class)));
        var identities =
                ModuleRuntimeFixture.dependency(
                        IdentityModule.class,
                        Map.of("identityService", new PlayerIdentityService(f.players)));
        var visibility =
                ModuleRuntimeFixture.dependency(
                        VisibilityModule.class,
                        Map.of("service", ModuleRuntimeFixture.allocate(VisibilityService.class)));
        var lobby =
                ModuleRuntimeFixture.dependency(
                        LobbyModule.class,
                        Map.of(
                                "lobbyItemService",
                                ModuleRuntimeFixture.allocate(LobbyItemService.class)));
        return new SettingsModule(
                f.plugin, f.playerModule, presentation, identities, visibility, lobby, f.messages);
    }

    private static void realLifecycle() throws Exception {
        try (var f = new ModuleRuntimeFixture()) {
            var module = module(f);
            module.enable();
            check(
                    module.getSettingsMenu() != null
                            && module.getVisibilitySettingsMenu() != null
                            && ModuleRuntimeFixture.get(module, "visiblePlayersMenu")
                                    instanceof VisiblePlayersMenu,
                    "real enable owns all three menu types");
            check(
                    f.registered.size() == 3
                            && f.registered.stream()
                                    .map(Object::getClass)
                                    .collect(java.util.stream.Collectors.toSet())
                                    .equals(
                                            Set.of(
                                                    SettingsListener.class,
                                                    VisibilitySettingsListener.class,
                                                    VisiblePlayersListener.class)),
                    "real enable registers exactly the three owning listeners");
            check(
                    f.handlerCount() > 0,
                    "event handlers are actually registered in Paper HandlerLists");
            check(
                    f.command.getExecutor() instanceof SettingsCommand
                            && f.command.getTabCompleter() == f.command.getExecutor(),
                    "real command and completion binding");
            check(
                    ((Set<?>) ModuleRuntimeFixture.get(f.settings, "changeListeners")).size() == 3,
                    "all menu observers subscribed after enable");
            var menus =
                    java.util.List.of(
                            module.getSettingsMenu(),
                            module.getVisibilitySettingsMenu(),
                            ModuleRuntimeFixture.get(module, "visiblePlayersMenu"));
            int registeredHandlers = f.handlerCount();
            var inventories = new VisibilityMenuFixture();
            var viewers = new java.util.ArrayList<VisibilityMenuFixture.TestPlayer>();
            for (Object menu : menus) {
                var viewer = inventories.player("Lifecycle" + viewers.size(), true);
                viewer.open =
                        inventories.inventory(
                                null, 27); // External inventory boundary; no rendering required.
                viewers.add(viewer);
                var proxy =
                        (org.bukkit.entity.Player)
                                java.lang.reflect.Proxy.newProxyInstance(
                                        org.bukkit.entity.Player.class.getClassLoader(),
                                        new Class<?>[] {org.bukkit.entity.Player.class},
                                        (target, method, args) -> {
                                            if (method.getName().equals("closeInventory")) {
                                                check(
                                                        f.handlerCount() == registeredHandlers,
                                                        "menus close before handlers are unregistered");
                                            }
                                            return method.invoke(viewer.player, args);
                                        });
                f.online.add(proxy);
                @SuppressWarnings("unchecked")
                var active =
                        (java.util.Map<java.util.UUID, Inventory>)
                                ModuleRuntimeFixture.get(menu, "activeInventories");
                active.put(viewer.id, viewer.open);
            }
            module.disable();
            check(
                    viewers.stream().allMatch(viewer -> viewer.open == viewer.bottom),
                    "real module disable closes every owned menu type");
            check(
                    menus.stream()
                            .allMatch(
                                    menu ->
                                            ((Map<?, ?>)
                                                            ModuleRuntimeFixture.get(
                                                                    menu, "activeInventories"))
                                                    .isEmpty()),
                    "real module disable empties all active inventory registries");
            check(f.handlerCount() == 0, "disable removes actual registered handlers");
            check(
                    f.command.getExecutor() == f.plugin && f.command.getTabCompleter() == null,
                    "disable resets command to Bukkit's plugin fallback and clears completion");
            check(
                    ((Set<?>) ModuleRuntimeFixture.get(f.settings, "changeListeners")).isEmpty(),
                    "disable shuts down every menu observer");
            for (String name :
                    new String[] {
                        "settingsMenu",
                        "visibilitySettingsMenu",
                        "visiblePlayersMenu",
                        "settingsListener",
                        "visibilitySettingsListener",
                        "visiblePlayersListener",
                        "settingsCommand",
                        "playerSettingsService",
                        "presentationService"
                    }) {
                check(
                        ModuleRuntimeFixture.get(module, name) == null,
                        "disable releases runtime reference " + name);
            }
            module.disable();
            check(f.handlerCount() == 0, "disable is idempotent");
            disabledGetter(module);
            module.enable();
            check(
                    f.handlerCount() > 0
                            && ((Set<?>) ModuleRuntimeFixture.get(f.settings, "changeListeners"))
                                            .size()
                                    == 3,
                    "re-enable restores registrations without duplicate observers");
            module.disable();
        }
        for (int failAt = 1; failAt <= 3; failAt++) {
            try (var f = new ModuleRuntimeFixture()) {
                f.failRegistration = failAt;
                var module = module(f);
                boolean failed = false;
                try {
                    module.enable();
                } catch (IllegalStateException expected) {
                    failed = true;
                }
                check(failed, "registration failure propagates at " + failAt);
                check(
                        f.handlerCount() == 0,
                        "failed enable releases partially registered handlers at " + failAt);
                check(
                        f.command.getExecutor() == f.plugin && f.command.getTabCompleter() == null,
                        "failed enable releases command bindings");
                check(
                        ((Set<?>) ModuleRuntimeFixture.get(f.settings, "changeListeners"))
                                .isEmpty(),
                        "failed enable leaves no menu observers");
                disabledGetter(module);
            }
        }
    }

    public static int observerRollback() throws Exception {
        int before = checks;
        for (boolean localeFault : new boolean[] {false, true}) {
            for (int failAt = 1; failAt <= 3; failAt++) {
                try (var f = new ModuleRuntimeFixture()) {
                    var observers = new ModuleRuntimeFixture.FaultSet<Object>();
                    Object source = localeFault ? f.messages.localization() : f.settings;
                    String field = localeFault ? "localeListeners" : "changeListeners";
                    ModuleRuntimeFixture.set(source, field, observers);
                    Object unrelated = new Object();
                    observers.add(unrelated);
                    observers.failAdd = failAt + 1;
                    observers.failRemove = 1;
                    var module = module(f);
                    RuntimeException actual = null;
                    try {
                        module.enable();
                    } catch (RuntimeException expected) {
                        actual = expected;
                    }
                    check(
                            actual == observers.startupFailure,
                            "observer failure remains primary " + localeFault + "/" + failAt);
                    check(
                            actual.getSuppressed().length > 0
                                    && actual.getSuppressed()[0].getCause()
                                            == observers.cleanupFailure,
                            "cleanup failure is observable without masking startup failure");
                    check(
                            f.handlerCount() == 0,
                            "observer failure unregisters every owned Bukkit handler");
                    check(
                            f.command.getExecutor() == f.plugin
                                    && f.command.getTabCompleter() == null,
                            "observer failure restores Bukkit command fallback");
                    check(
                            observers.size() == 1 && observers.contains(unrelated),
                            "only unrelated subscription survives rollback");
                    Set<?> other =
                            (Set<?>)
                                    ModuleRuntimeFixture.get(
                                            localeFault ? f.settings : f.messages.localization(),
                                            localeFault ? "changeListeners" : "localeListeners");
                    check(other.isEmpty(), "independent observer family also released");
                    for (String name :
                            new String[] {
                                "settingsMenu",
                                "visibilitySettingsMenu",
                                "visiblePlayersMenu",
                                "settingsListener",
                                "visibilitySettingsListener",
                                "visiblePlayersListener",
                                "settingsCommand",
                                "playerSettingsService",
                                "presentationService"
                            }) {
                        check(
                                ModuleRuntimeFixture.get(module, name) == null,
                                "failed enable clears " + name);
                    }
                    disabledGetter(module);
                    module.disable();
                    observers.failAdd = 0;
                    observers.failRemove = 0;
                    int registrations = f.registered.size();
                    module.enable();
                    check(
                            f.registered.size() == registrations + 3 && f.handlerCount() > 0,
                            "retry installs exactly three current listeners");
                    check(
                            observers.size() == 4 && other.size() == 3,
                            "retry owns exactly one set of menu observers");
                    module.disable();
                    check(
                            observers.size() == 1 && other.isEmpty() && f.handlerCount() == 0,
                            "retry disable preserves unrelated observer and releases its own resources");
                    module.disable();
                }
            }
        }
        return checks - before;
    }

    private static void inventoryRollback() throws Exception {
        for (boolean failedEnable : new boolean[] {true, false}) {
            try (var f = new ModuleRuntimeFixture()) {
                var inventories = new VisibilityMenuFixture();
                var viewers = new java.util.ArrayList<VisibilityMenuFixture.TestPlayer>();
                var menus = new java.util.ArrayList<Object>();
                var owned = new java.util.ArrayList<Inventory>();
                var observers = new ModuleRuntimeFixture.FaultSet<Object>();
                ModuleRuntimeFixture.set(f.settings, "changeListeners", observers);
                var module = module(f);
                f.serverCalls =
                        (method, args) -> {
                            if (method.equals("createInventory"))
                                return inventories.inventory(
                                        (org.bukkit.inventory.InventoryHolder) args[0],
                                        (int) args[1]);
                            throw new AssertionError(method);
                        };
                Runnable openOwnedViews =
                        () -> {
                            if (!viewers.isEmpty()) return;
                            try {
                                for (int index = 0; index < 3; index++) {
                                    Object listener = f.registered.get(index);
                                    Object menu =
                                            ModuleRuntimeFixture.get(
                                                    listener, index == 0 ? "settingsMenu" : "menu");
                                    Class<?> renderer =
                                            java.util.Arrays.stream(
                                                            menu.getClass().getDeclaredClasses())
                                                    .filter(
                                                            type ->
                                                                    type.getSimpleName()
                                                                            .equals("ItemRenderer"))
                                                    .findFirst()
                                                    .orElseThrow();
                                    ModuleRuntimeFixture.set(
                                            menu,
                                            "itemRenderer",
                                            ModuleRuntimeFixture.proxy(
                                                    renderer,
                                                    (method, args) ->
                                                            new VisibilityMenuFixture
                                                                    .VisibilitySpecItem(
                                                                    (dev.vapee.core.ui.UiItemSpec)
                                                                            args[0])));
                                    var viewer = inventories.player("Owned" + index, true);
                                    f.players.loadPlayer(viewer.id, viewer.name);
                                    f.online.add(viewer.player);
                                    menu.getClass()
                                            .getMethod("open", org.bukkit.entity.Player.class)
                                            .invoke(menu, viewer.player);
                                    viewers.add(viewer);
                                    menus.add(menu);
                                    owned.add(viewer.open);
                                    check(
                                            viewer.open.getHolder() != null,
                                            "actual menu open binds its own holder");
                                }
                                // A different inventory replaced one owned view before cleanup.
                                viewers.get(2).open = inventories.inventory(null, 27);
                            } catch (ReflectiveOperationException exception) {
                                throw new AssertionError(exception);
                            }
                        };
                observers.afterAdd = openOwnedViews;
                if (failedEnable) {
                    observers.failAdd = 3;
                    observers.failRemove = 1;
                    try {
                        module.enable();
                        throw new AssertionError("expected failed enable");
                    } catch (RuntimeException actual) {
                        check(
                                actual == observers.startupFailure,
                                "views do not mask failed enable");
                    }
                } else {
                    module.enable();
                    observers.failRemove = 1;
                    module.disable();
                }
                check(
                        viewers.get(0).open == viewers.get(0).bottom
                                && viewers.get(1).open == viewers.get(1).bottom,
                        "owned inventories close despite observer cleanup failure");
                check(
                        viewers.get(2).open != viewers.get(2).bottom
                                && viewers.get(2).closeCalls == 0,
                        "replaced unrelated current inventory is preserved");
                check(
                        f.handlerCount() == 0 && observers.isEmpty(),
                        "view cleanup releases observers and handlers");
                for (int index = 0; index < 3; index++) {
                    Object menu = menus.get(index);
                    var viewer = viewers.get(index);
                    check(
                            ((Map<?, ?>) ModuleRuntimeFixture.get(menu, "activeInventories"))
                                    .isEmpty(),
                            "actual module emptied menu registry");
                    Object holder = owned.get(index).getHolder();
                    viewer.open = owned.get(index);
                    var activeMethod =
                            menu.getClass()
                                    .getMethod(
                                            "isActive",
                                            org.bukkit.entity.Player.class,
                                            Inventory.class,
                                            holder.getClass());
                    check(
                            !(boolean)
                                    activeMethod.invoke(
                                            menu, viewer.player, owned.get(index), holder),
                            "stale bound inventory has no authority");
                    Inventory forged =
                            inventories.inventory(
                                    (org.bukkit.inventory.InventoryHolder) holder,
                                    owned.get(index).getSize());
                    viewer.open = forged;
                    check(
                            !(boolean) activeMethod.invoke(menu, viewer.player, forged, holder),
                            "forged inventory sharing holder has no authority");
                    var click =
                            new org.bukkit.event.inventory.InventoryClickEvent(
                                    inventories.view(viewer, forged),
                                    org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
                                    0,
                                    org.bukkit.event.inventory.ClickType.LEFT,
                                    org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
                    f.registered
                            .get(index)
                            .getClass()
                            .getMethod("onInventoryClick", click.getClass())
                            .invoke(f.registered.get(index), click);
                    check(
                            click.isCancelled(),
                            "stale registered listener still cancels forged inventory interaction");
                }
                module.disable();
            }
        }
    }

    private static void disabledGetter(SettingsModule module) {
        try {
            module.getSettingsMenu();
            throw new AssertionError("disabled module exposed its menu");
        } catch (NullPointerException expected) {
            check(true, "disabled getter fails closed");
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
