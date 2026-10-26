package dev.vapee.core.quest.daily;

import static dev.vapee.core.module.ModuleRuntimeFixture.get;
import static dev.vapee.core.module.ModuleRuntimeFixture.proxy;
import static dev.vapee.core.module.ModuleRuntimeFixture.set;

import dev.vapee.core.module.CoreModule;
import dev.vapee.core.module.ModuleManager;
import dev.vapee.core.quest.QuestCompletionFixture;
import dev.vapee.core.quest.daily.command.DailyQuestCommand;
import dev.vapee.core.quest.daily.menu.DailyQuestMenu;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import dev.vapee.core.reload.ReloadResult;
import dev.vapee.core.reload.ReloadService;

import org.bukkit.command.CommandExecutor;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredListener;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Runs real lifecycle paths and inspects resources before fixture safety teardown. */
public final class DailyQuestModuleLifecycleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length != 0) {
            switch (args[0]) {
                case "late" -> lateFailure();
                case "duplicate" -> duplicate();
                case "reentrant" -> reentrant();
                case "listener" -> registrations();
                case "foreign-executor" -> foreignBindings(true);
                case "foreign-completer" -> foreignBindings(false);
                case "stale" -> stale();
                case "pending" -> taskCleanup();
                case "registry" -> catalog();
                case "command-api" -> commandApi();
                case "reload-api" -> reloadApi();
                default -> throw new AssertionError(args[0]);
            }
        } else {
            normal();
            lateFailure();
            duplicate();
            reentrant();
            registrations();
            foreignBindings(true);
            foreignBindings(false);
            observers();
            taskCleanup();
            stale();
            catalog();
            reload();
            syncIsolation();
            reloadViews();
            manager();
        }
        System.out.println("DailyQuestModuleLifecycleHarness passed " + checks + " checks.");
    }

    private static void normal() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var module = f.module();
            check(fails(module::getDailyQuestService) != null, "getter closed before enable");
            check(fails(module::prepareReload) != null, "reload closed before enable");
            module.enable();
            var service = module.getDailyQuestService();
            check(service != null, "successful runtime published");
            check(
                    f.runtime.handlerCount() == 5 && f.runtime.registered.size() == 2,
                    "two listeners own five actual handlers");
            check(
                    f.running() == 1
                            && f.tasks.getFirst().period == 1200
                            && f.tasks.getFirst().delay == 1200,
                    "exact synchronous 1200 tick task");
            check(
                    f.runtime.command.getExecutor() instanceof DailyQuestCommand
                            && f.runtime.command.getTabCompleter()
                                    == f.runtime.command.getExecutor(),
                    "real command bindings installed");
            check(f.locales.size() == 1 && f.changes.size() == 1, "one exact observer each");
            check(f.registry.findById("daily").isPresent(), "configured catalog installed");
            var id = f.load("First");
            f.tasks.getFirst().callback.run();
            check(
                    f.players
                            .getPlayer(id)
                            .orElseThrow()
                            .getDailyQuestState()
                            .cycleId()
                            .isPresent(),
                    "main task initializes loaded player");
            var snapshot = f.players.getPlayer(id).orElseThrow().getQuestState().snapshot();
            f.tasks.getFirst().callback.run();
            check(
                    f.players
                            .getPlayer(id)
                            .orElseThrow()
                            .getQuestState()
                            .snapshot()
                            .equals(snapshot),
                    "same cycle stable");
            check(
                    ((Set<?>) get(f.quests, "dirtyPlayers")).contains(id),
                    "dirty tracking remains with Quest");
            var before = f.registry.snapshot();
            module.disable();
            released(f, module);
            check(f.registry.snapshot().equals(before), "normal shutdown keeps borrowed catalog");
            check(
                    f.players.getPlayer(id).isPresent()
                            && ((Set<?>) get(f.quests, "dirtyPlayers")).contains(id),
                    "borrowed aggregate and dirty markers retained");
            int cancellations = f.tasks.getFirst().cancels;
            module.disable();
            check(
                    f.tasks.getFirst().cancels == cancellations && f.registration == 2,
                    "repeated disable inert");
        }
    }

    private static void lateFailure() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var previous = QuestCompletionFixture.definition("previous", "test:key", 3, 1);
            f.registry.replaceAll(List.of(previous));
            var module = f.module();
            f.failLog = true;
            RuntimeException seen = fails(module::enable);
            check(seen == f.startup, "original late logger exception retained");
            released(f, module);
            check(
                    f.registry.snapshot().equals(List.of(previous)),
                    "late failure restores exact previous definitions");
            var id = f.load("AfterFailure");
            f.tasks.getFirst().callback.run();
            join(f, id);
            check(
                    f.players.getPlayer(id).orElseThrow().getDailyQuestState().cycleId().isEmpty(),
                    "failed-start callbacks inert");
            f.failLog = false;
            module.enable();
            module.disable();
            released(f, module);
        }
    }

    private static void duplicate() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var module = f.module();
            module.enable();
            var service = module.getDailyQuestService();
            check(
                    fails(module::enable) instanceof IllegalStateException,
                    "duplicate enable rejected");
            check(
                    module.getDailyQuestService() == service
                            && f.running() == 1
                            && f.registration == 2,
                    "duplicate preserves exact runtime");
            check(
                    f.locales.additions == 1 && f.changes.additions == 1,
                    "duplicate acquires no observers");
            module.disable();
            released(f, module);
        }
    }

    private static void reentrant() throws Exception {
        for (boolean logging : List.of(false, true))
            try (var f = new DailyQuestLifecycleFixture()) {
                var module = f.module();
                int[] nested = {0};
                Runnable attempt =
                        () -> {
                            if (nested[0]++ == 0) {
                                check(
                                        fails(module::enable) instanceof IllegalStateException,
                                        "reentrant enable rejected before acquisition");
                                check(
                                        fails(module::getDailyQuestService) != null,
                                        "getter closed during startup");
                                check(
                                        fails(module::disable) instanceof IllegalStateException,
                                        "reentrant disable rejected");
                            }
                        };
                if (logging) f.duringLog = attempt;
                else f.duringRegistration = attempt;
                module.enable();
                check(
                        f.registration == 2 && f.running() == 1 && f.changes.size() == 1,
                        "one runtime after reentry");
                module.disable();
                released(f, module);
            }
    }

    private static void registrations() throws Exception {
        for (int listener : List.of(1, 2))
            for (boolean after : List.of(false, true)) {
                try (var f = new DailyQuestLifecycleFixture()) {
                    f.failRegistration = listener;
                    f.registrationAfter = after;
                    var module = f.module();
                    check(
                            fails(module::enable) == f.startup,
                            "listener boundary preserves original exception");
                    released(f, module);
                    check(f.registry.size() == 0, "partial registration restores catalog");
                }
            }
        for (boolean after : List.of(false, true))
            try (var f = new DailyQuestLifecycleFixture()) {
                var module = f.module();
                RefusingHandlers[] refusal = {null};
                f.failRegistration = 2;
                f.registrationAfter = true;
                f.duringRegistration =
                        () -> {
                            if (f.registration == 2)
                                refusal[0] =
                                        new RefusingHandlers(
                                                f.runtime.registered.getFirst(),
                                                f.runtime.plugin,
                                                after);
                        };
                RuntimeException seen = fails(module::enable);
                check(
                        seen == f.startup && seen.getSuppressed().length > 0,
                        "unregister cannot mask startup exception");
                check(
                        fails(module::getDailyQuestService) != null,
                        "getter closed on listener cleanup failure");
                check(
                        f.locales.isEmpty() && f.changes.isEmpty() && f.registry.size() == 0,
                        "unregister failure does not skip independent cleanup");
                var id = f.load("RefusedJoin");
                join(f, id);
                check(
                        f.players
                                .getPlayer(id)
                                .orElseThrow()
                                .getDailyQuestState()
                                .cycleId()
                                .isEmpty(),
                        "refused old join inert");
                if (!after) {
                    check(
                            f.runtime.handlerCount() > 0 && get(module, "pendingCleanup") != null,
                            "exact unresolved listener retained");
                    check(
                            fails(module::enable) != null && f.registration == 2,
                            "pending listener blocks acquisition");
                } else
                    check(
                            f.runtime.handlerCount() == 0 && get(module, "pendingCleanup") == null,
                            "after-effect unregister confirmed released");
                refusal[0].refuse = false;
                f.failRegistration = 0;
                f.duringRegistration = () -> {};
                module.disable();
                released(f, module);
                module.enable();
                module.disable();
                released(f, module);
            }
        try (var f = new DailyQuestLifecycleFixture()) {
            f.runtime.missingCommand = true;
            var module = f.module();
            check(fails(module::enable) != null, "missing descriptor rejected");
            released(f, module);
            check(f.registry.size() == 0, "missing descriptor restores registry");
        }
    }

    private static void foreignBindings(boolean executor) throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var module = f.module();
            module.enable();
            CommandExecutor foreign = (s, c, l, a) -> true;
            TabCompleter completer = (s, c, l, a) -> List.of("foreign");
            if (executor) f.runtime.command.setExecutor(foreign);
            else f.runtime.command.setTabCompleter(completer);
            module.disable();
            check(
                    executor
                            ? f.runtime.command.getExecutor() == foreign
                            : f.runtime.command.getTabCompleter() == completer,
                    executor ? "foreign executor preserved" : "foreign completer preserved");
            check(
                    executor
                            ? f.runtime.command.getTabCompleter() == null
                            : f.runtime.command.getExecutor() == f.runtime.plugin,
                    "other owned binding independently released");
            check(
                    f.runtime.handlerCount() == 0 && f.running() == 0,
                    "foreign descriptor remains with Bukkit; resources released");
        }
    }

    private static void observers() throws Exception {
        for (boolean locale : List.of(false, true))
            for (boolean after : List.of(false, true)) {
                try (var f = new DailyQuestLifecycleFixture()) {
                    var faults = locale ? f.locales : f.changes;
                    faults.failAdd = true;
                    faults.addAfter = after;
                    var module = f.module();
                    check(
                            fails(module::enable) == faults.fault,
                            "observer add preserves original exception");
                    released(f, module);
                }
            }
        for (boolean locale : List.of(false, true))
            try (var f = new DailyQuestLifecycleFixture()) {
                var module = f.module();
                module.enable();
                var faults = locale ? f.locales : f.changes;
                faults.failRemove = true;
                check(fails(module::disable) != null, "observer refusal reported");
                check(
                        faults.size() == 1 && get(module, "pendingCleanup") != null,
                        "uncertain observer retained for retry");
                check(
                        locale ? f.changes.isEmpty() : f.locales.isEmpty(),
                        "independent observer removed");
                check(
                        f.runtime.handlerCount() == 0
                                && f.running() == 0
                                && f.runtime.command.getExecutor() == f.runtime.plugin,
                        "observer failure does not skip handlers tasks commands");
                check(
                        fails(module::getDailyQuestService) != null
                                && fails(module::enable) != null,
                        "incomplete menu not active");
                faults.failRemove = false;
                module.disable();
                released(f, module);
                module.enable();
                module.disable();
                released(f, module);
            }
    }

    private static void taskCleanup() throws Exception {
        for (int mode = 0; mode < 4; mode++)
            try (var f = new DailyQuestLifecycleFixture()) {
                var module = f.module();
                module.enable();
                var task = f.tasks.getFirst();
                task.refuse = mode == 0;
                task.throwAfter = mode == 1;
                task.ineffective = mode == 2;
                task.statusFailure = mode == 3;
                check(fails(module::disable) != null, "task cancellation failure reported");
                check(
                        fails(module::getDailyQuestService) != null,
                        "getter closed during incomplete shutdown");
                check(
                        f.runtime.handlerCount() == 0 && f.locales.isEmpty() && f.changes.isEmpty(),
                        "task error cannot skip independent releases");
                var id = f.load("RetiredTask" + mode);
                task.callback.run();
                check(
                        f.players
                                .getPlayer(id)
                                .orElseThrow()
                                .getDailyQuestState()
                                .cycleId()
                                .isEmpty(),
                        "refused main callback inert");
                if (mode != 1) {
                    check(
                            get(module, "pendingCleanup") != null && fails(module::enable) != null,
                            "unconfirmed handle retained blocks new runtime");
                    CommandExecutor foreign = (s, c, l, a) -> true;
                    f.runtime.command.setExecutor(foreign);
                    task.refuse = false;
                    task.ineffective = false;
                    task.statusFailure = false;
                    module.disable();
                    check(
                            f.runtime.command.getExecutor() == foreign,
                            "cleanup retry preserves newer command");
                    f.runtime.command.setExecutor(null);
                } else
                    check(
                            get(module, "pendingCleanup") == null,
                            "confirmed after-effect cancellation does not stay pending");
                module.disable();
                released(f, module);
            }
        for (boolean after : List.of(false, true))
            try (var f = new DailyQuestLifecycleFixture()) {
                f.failSchedule = true;
                f.scheduleAfter = after;
                var module = f.module();
                check(fails(module::enable) == f.startup, "scheduling failure original preserved");
                check(
                        f.runtime.handlerCount() == 0 && f.locales.isEmpty() && f.changes.isEmpty(),
                        "scheduling failure releases known resources");
                check(
                        f.running() == (after ? 1 : 0),
                        "unknown scheduler handle limitation measured");
                if (after) {
                    var id = f.load("UnknownHandle");
                    f.tasks.getFirst().callback.run();
                    check(
                            f.players
                                    .getPlayer(id)
                                    .orElseThrow()
                                    .getDailyQuestState()
                                    .cycleId()
                                    .isEmpty(),
                            "unknown task has no authority");
                }
            }
    }

    private static void stale() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var module = f.module();
            module.enable();
            var oldTask = f.tasks.getFirst();
            var oldListener = (DailyQuestListener) f.runtime.registered.getFirst();
            var oldCommand = (DailyQuestCommand) f.runtime.command.getExecutor();
            var oldService = module.getDailyQuestService();
            var oldConfig = (DailyQuestConfig) get(get(module, "runtime"), "config");
            int[] oldReads = {0};
            set(
                    oldService,
                    "configState",
                    (java.util.function.Supplier<DailyQuestConfig.State>)
                            () -> {
                                oldReads[0]++;
                                return oldConfig.getState();
                            });
            var oldLocale = f.locales.iterator().next();
            var oldChange = f.changes.iterator().next();
            DailyQuestMenu oldMenu = (DailyQuestMenu) get(get(module, "runtime"), "menu");
            module.disable();
            f.config("fresh", 20);
            module.enable();
            UUID id = f.load("FreshRuntime");
            int[] reads = {0};
            Player player =
                    proxy(
                            Player.class,
                            (m, a) -> {
                                reads[0]++;
                                if (m.equals("getUniqueId")) return id;
                                throw new AssertionError("stale command read " + m);
                            });
            oldTask.callback.run();
            check(oldReads[0] == 0, "old main cannot synchronize any retired service");
            oldListener.onPlayerJoin(
                    new PlayerJoinEvent(player, net.kyori.adventure.text.Component.empty()));
            oldCommand.onCommand(player, f.runtime.command, "quests", new String[0]);
            oldLocale.accept(player);
            oldChange.onQuestsCommitted(id);
            oldMenu.open(player, 0);
            check(reads[0] == 0, "all retired entry points inert before player IO");
            check(
                    f.players.getPlayer(id).orElseThrow().getDailyQuestState().cycleId().isEmpty(),
                    "old main cannot use replacement runtime");
            f.tasks.getLast().callback.run();
            check(
                    f.players
                            .getPlayer(id)
                            .orElseThrow()
                            .getQuestState()
                            .snapshot()
                            .containsKey("fresh"),
                    "new callback owns fresh definitions");
            check(
                    f.running() == 1 && f.locales.size() == 1 && f.changes.size() == 1,
                    "restart keeps exact resource inventory");
            module.disable();
            released(f, module);
        }
    }

    private static void catalog() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var module = f.module();
            f.failSchedule = true;
            var foreign = QuestCompletionFixture.definition("foreign", "test:key", 3, 1);
            f.duringRegistration =
                    () -> {
                        if (f.registration == 2) f.registry.replaceAll(List.of(foreign));
                    };
            RuntimeException error = fails(module::enable);
            check(
                    error == f.startup && error.getSuppressed().length > 0,
                    "catalog conflict attached to original start error");
            check(
                    f.registry.snapshot().equals(List.of(foreign)),
                    "external catalog preserved during rollback");
            check(
                    get(module, "pendingCleanup") != null && fails(module::enable) != null,
                    "catalog conflict remains visible with previous evidence");
            var pending = get(module, "pendingCleanup");
            set(f.registry, "definitions", get(pending, "installedDefinitions"));
            module.disable();
            check(f.registry.size() == 0, "explicit ownership reconciliation allows catalog retry");
            released(f, module);
        }
        try (var f = new DailyQuestLifecycleFixture()) {
            Files.writeString(
                    f.directory.resolve("daily-quests.yml"), "enabled: false\nquests: {}\n");
            var module = f.module();
            module.enable();
            check(f.registry.size() == 0, "disabled empty catalog valid");
            module.disable();
            released(f, module);
        }
        try (var f = new DailyQuestLifecycleFixture()) {
            f.config("invalid", 0);
            var module = f.module();
            check(fails(module::enable) != null, "invalid startup config rejected");
            released(f, module);
            check(f.registration == 0, "invalid config acquires no handlers");
        }
    }

    private static void reload() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var module = f.module();
            module.enable();
            var service = module.getDailyQuestService();
            var task = f.tasks.getFirst();
            var id = f.load("Reloaded");
            task.callback.run();
            f.quests.flushPlayer(id);
            byte[] before = Files.readAllBytes(f.directory.resolve("players").resolve(id + ".yml"));
            Object state = get(get(module, "runtime"), "config");
            f.config("next", 20);
            var plan = module.prepareReload();
            check(f.registry.findById("daily").isPresent(), "prepare does not mutate catalog");
            plan.apply();
            check(f.registry.findById("next").isPresent(), "reload applies registry");
            check(
                    f.tasks.size() == 1
                            && f.tasks.getFirst() == task
                            && module.getDailyQuestService() == service
                            && f.locales.additions == 1
                            && f.changes.additions == 1,
                    "reload preserves tasks services observers");
            task.callback.run();
            check(
                    f.players
                            .getPlayer(id)
                            .orElseThrow()
                            .getQuestState()
                            .snapshot()
                            .containsKey("daily"),
                    "reload does not reroll same cycle");
            var fresh = f.load("NewAfterReload");
            task.callback.run();
            check(
                    f.players
                            .getPlayer(fresh)
                            .orElseThrow()
                            .getQuestState()
                            .snapshot()
                            .containsKey("next"),
                    "next sync uses new catalog");
            plan.rollback();
            check(f.registry.findById("daily").isPresent(), "exact catalog rollback");
            f.config("invalid", 0);
            check(fails(module::prepareReload) != null, "invalid reload preparation fails");
            check(f.registry.findById("daily").isPresent(), "invalid reload leaves catalog usable");
            f.config("retry", 30);
            var coordinator = new ReloadService(f.runtime.logger, List.of(module));
            check(coordinator.reload().isSuccess(), "reload retry succeeds");
            check(
                    Arrays.equals(
                            before,
                            Files.readAllBytes(
                                    f.directory.resolve("players").resolve(id + ".yml"))),
                    "reload never saves player aggregate");
            var stalePlan = module.prepareReload();
            module.disable();
            check(
                    fails(stalePlan::apply) != null && fails(stalePlan::rollback) != null,
                    "retired reload plan cannot mutate registry");
            released(f, module);
        }
    }

    private static void manager() throws Exception {
        for (boolean failed : List.of(false, true))
            try (var f = new DailyQuestLifecycleFixture()) {
                var daily = f.module();
                var order = new ArrayList<String>();
                var manager = new ModuleManager(f.runtime.logger);
                manager.register(
                        new CoreModule() {
                            public String getName() {
                                return "BorrowedOwner";
                            }

                            public void enable() {
                                order.add("owner enable");
                            }

                            public void disable() {
                                order.add("owner disable");
                                check(
                                        f.runtime.handlerCount() == 0 && f.locales.isEmpty(),
                                        "DailyQuest retires before borrowed owner");
                            }
                        });
                manager.register(daily);
                f.failLog = failed;
                if (failed)
                    check(
                            fails(manager::enableAll) == f.startup,
                            "manager preserves failed-start error");
                else {
                    manager.enableAll();
                    manager.disableAll();
                }
                released(f, daily);
                check(
                        order.equals(List.of("owner enable", "owner disable")),
                        "manager normal/rollback reverse order");
                check(manager.getEnabledModules().isEmpty(), "manager stopped list cleared");
            }
    }

    private static void syncIsolation() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var module = f.module();
            module.enable();
            var a = f.load("SyncA");
            var b = f.load("SyncB");
            var c = f.load("SyncC");
            Object config = get(get(module, "runtime"), "config");
            int[] calls = {0};
            set(
                    module.getDailyQuestService(),
                    "configState",
                    (java.util.function.Supplier<DailyQuestConfig.State>)
                            () -> {
                                if (calls[0]++ == 0)
                                    throw new IllegalStateException("one player sync failed");
                                return ((DailyQuestConfig) config).getState();
                            });
            f.runtime.logger.addHandler(
                    new java.util.logging.Handler() {
                        public void publish(java.util.logging.LogRecord r) {
                            if (r.getMessage().startsWith("Daily quest sync failed"))
                                throw new IllegalStateException("diagnostic failed");
                        }

                        public void flush() {}

                        public void close() {}
                    });
            f.tasks.getFirst().callback.run();
            check(
                    calls[0] == 3,
                    "sync and diagnostic failure cannot skip independent loaded players");
            check(
                    List.of(a, b, c).stream()
                                    .filter(
                                            id ->
                                                    f.players
                                                            .getPlayer(id)
                                                            .orElseThrow()
                                                            .getDailyQuestState()
                                                            .cycleId()
                                                            .isPresent())
                                    .count()
                            == 2,
                    "two unaffected players synchronized");
            module.disable();
            released(f, module);
        }
    }

    private static void reloadViews() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var module = f.module();
            module.enable();
            var menu = (DailyQuestMenu) get(get(module, "runtime"), "menu");
            var viewer = new View(f, menu, false);
            var other = new View(f, menu, true);
            var before = f.registry.snapshot();
            f.config("reloaded", 30);
            var coordinator = new ReloadService(f.runtime.logger, List.of(module));
            check(
                    coordinator.reload().isSuccess(),
                    "reload closes views with best effort physical close");
            check(
                    viewer.closed == 1
                            && other.closed == 0
                            && ((Map<?, ?>) get(menu, "active")).isEmpty(),
                    "reload detaches all views and preserves foreign top inventory");
            check(
                    f.registry.findById("reloaded").isPresent() && f.tasks.size() == 1,
                    "reload installs catalog without timer churn");
            var failedClose = new View(f, menu, false);
            failedClose.fail = true;
            f.config("afterclose", 40);
            check(
                    coordinator.reload().isSuccess() && ((Map<?, ?>) get(menu, "active")).isEmpty(),
                    "physical close failure is best effort UI effect with detached authority");
            check(
                    f.registry.findById("afterclose").isPresent(),
                    "close warning does not roll back valid configuration");
            var rollbackView = new View(f, menu, false);
            f.config("rolledback", 50);
            ReloadParticipant later =
                    new ReloadParticipant() {
                        public String getReloadName() {
                            return "later";
                        }

                        public ReloadPlan prepareReload() {
                            return ReloadPlan.of(
                                    () -> {
                                        throw new IllegalStateException("later apply");
                                    },
                                    () -> {});
                        }
                    };
            check(
                    new ReloadService(f.runtime.logger, List.of(module, later)).reload().status()
                            == ReloadResult.Status.APPLY_FAILED,
                    "later apply failure rolls back DailyQuest");
            check(
                    f.registry.findById("afterclose").isPresent() && rollbackView.closed == 1,
                    "state rollback restores catalog but never reopens closed view");
            module.disable();
            released(f, module);
        }
    }

    private static final class View {
        int closed;
        boolean fail;
        org.bukkit.inventory.Inventory top;

        @SuppressWarnings("unchecked")
        View(DailyQuestLifecycleFixture f, DailyQuestMenu menu, boolean foreign) throws Exception {
            UUID id = f.load("View" + UUID.randomUUID());
            var ctor =
                    dev.vapee.core.quest.daily.menu.DailyQuestInventoryHolder.class
                            .getDeclaredConstructor(UUID.class, int.class);
            ctor.setAccessible(true);
            var holder = ctor.newInstance(id, 0);
            var inventory = dev.vapee.core.quest.daily.menu.QuestMenuFixture.inventory(holder, 36);
            var bind =
                    holder.getClass()
                            .getDeclaredMethod("bind", org.bukkit.inventory.Inventory.class);
            bind.setAccessible(true);
            bind.invoke(holder, inventory);
            top =
                    foreign
                            ? dev.vapee.core.quest.daily.menu.QuestMenuFixture.inventory(null, 36)
                            : inventory;
            Player player =
                    proxy(
                            Player.class,
                            (m, a) ->
                                    switch (m) {
                                        case "getUniqueId" -> id;
                                        case "isOnline" -> true;
                                        case "getOpenInventory" ->
                                                proxy(
                                                        org.bukkit.inventory.InventoryView.class,
                                                        (n, b) ->
                                                                n.equals("getTopInventory")
                                                                        ? top
                                                                        : null);
                                        case "closeInventory" -> {
                                            if (fail)
                                                throw new IllegalStateException("close failed");
                                            closed++;
                                            top = null;
                                            yield null;
                                        }
                                        default -> throw new AssertionError(m);
                                    });
            f.runtime.online.add(player);
            ((Map<UUID, org.bukkit.inventory.Inventory>) get(menu, "active")).put(id, inventory);
        }
    }

    /** Supplemental genuine final-PluginCommand bytecode overlay supplies only external faults. */
    private static void commandApi() throws Exception {
        Class<?> hooks = Class.forName("dev.vapee.core.quest.daily.ApiFaults");
        for (String setter : List.of("setExecutor", "setTabCompleter"))
            for (boolean after : List.of(false, true))
                try (var f = new DailyQuestLifecycleFixture()) {
                    hooks.getField("method").set(null, setter);
                    hooks.getField("after").setBoolean(null, after);
                    hooks.getField("calls").setInt(null, 0);
                    hooks.getField("failOn").setInt(null, 1);
                    var module = f.module();
                    check(
                            fails(module::enable) == hooks.getField("fault").get(null),
                            "real command setter boundary retains original error");
                    released(f, module);
                    check(f.registry.size() == 0, "setter failure restores catalog");
                    hooks.getField("method").set(null, "");
                }
        for (String setter : List.of("setExecutor", "setTabCompleter"))
            for (boolean after : List.of(false, true))
                try (var f = new DailyQuestLifecycleFixture()) {
                    hooks.getField("method").set(null, setter);
                    hooks.getField("after").setBoolean(null, after);
                    hooks.getField("calls").setInt(null, 0);
                    hooks.getField("failOn").setInt(null, 2);
                    f.failLog = true;
                    var module = f.module();
                    RuntimeException error = fails(module::enable);
                    check(
                            error == f.startup && error.getSuppressed().length > 0,
                            "setter cleanup failure suppressed on original startup error");
                    check(
                            f.runtime.handlerCount() == 0
                                    && f.locales.isEmpty()
                                    && f.changes.isEmpty()
                                    && f.running() == 0,
                            "setter cleanup failure cannot skip unrelated release");
                    check(
                            (get(module, "pendingCleanup") != null) == !after,
                            "command before/after effect ownership distinguished");
                    hooks.getField("method").set(null, "");
                    module.disable();
                    released(f, module);
                }
    }

    private static void reloadApi() throws Exception {
        Class<?> hooks = Class.forName("dev.vapee.core.quest.daily.ApiFaults");
        for (String method : List.of("replaceAll", "applyState"))
            for (boolean after : List.of(false, true))
                try (var f = new DailyQuestLifecycleFixture()) {
                    hooks.getField("method").set(null, "");
                    var module = f.module();
                    module.enable();
                    var before = f.registry.snapshot();
                    var config = (DailyQuestConfig) get(get(module, "runtime"), "config");
                    var state = config.getState();
                    f.config("next", 20);
                    hooks.getField("method").set(null, method);
                    hooks.getField("after").setBoolean(null, after);
                    hooks.getField("calls").setInt(null, 0);
                    hooks.getField("failOn").setInt(null, 1);
                    var coordinator = new ReloadService(f.runtime.logger, List.of(module));
                    check(
                            coordinator.reload().status() == ReloadResult.Status.APPLY_FAILED,
                            "registry/config apply failure enters coordinated rollback");
                    check(
                            config.getState() == state && f.registry.snapshot().equals(before),
                            "apply failure restores exact previous config and definitions");
                    check(
                            f.tasks.size() == 1 && f.locales.size() == 1 && f.changes.size() == 1,
                            "reload failure preserves operational resources");
                    hooks.getField("method").set(null, "");
                    check(
                            coordinator.reload().isSuccess()
                                    && f.registry.findById("next").isPresent(),
                            "second reload after application failure succeeds");
                    module.disable();
                    released(f, module);
                }
        for (String method : List.of("replaceAll", "applyState"))
            try (var f = new DailyQuestLifecycleFixture()) {
                hooks.getField("method").set(null, "");
                var module = f.module();
                module.enable();
                f.config("next", 20);
                hooks.getField("method").set(null, method);
                hooks.getField("after").setBoolean(null, false);
                hooks.getField("calls").setInt(null, 0);
                hooks.getField("failOn").setInt(null, 2);
                ReloadParticipant later =
                        new ReloadParticipant() {
                            public String getReloadName() {
                                return "later";
                            }

                            public ReloadPlan prepareReload() {
                                return ReloadPlan.of(
                                        () -> {
                                            throw new IllegalStateException("later apply failure");
                                        },
                                        () -> {});
                            }
                        };
                check(
                        new ReloadService(f.runtime.logger, List.of(module, later))
                                        .reload()
                                        .status()
                                == ReloadResult.Status.ROLLBACK_INCOMPLETE,
                        "external rollback refusal remains explicit incomplete status");
                hooks.getField("method").set(null, "");
                check(
                        new ReloadService(f.runtime.logger, List.of(module)).reload().isSuccess(),
                        "explicit next valid reload restores complete state after external"
                                + " failure");
                module.disable();
                released(f, module);
            }
    }

    public static boolean verifiesTaskCleanup() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var m = f.module();
            m.enable();
            m.disable();
            return f.tasks.size() == 1 && f.tasks.getFirst().cancelled && f.running() == 0;
        }
    }

    public static boolean verifiesRollback() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            f.failLog = true;
            var m = f.module();
            return fails(m::enable) == f.startup
                    && f.runtime.handlerCount() == 0
                    && f.registry.size() == 0
                    && f.runtime.command.getExecutor() == f.runtime.plugin
                    && f.runtime.command.getTabCompleter() == null;
        }
    }

    public static boolean verifiesShutdown() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var m = f.module();
            m.enable();
            m.disable();
            return f.runtime.handlerCount() == 0
                    && f.locales.isEmpty()
                    && f.changes.isEmpty()
                    && f.runtime.command.getExecutor() == f.runtime.plugin
                    && f.runtime.command.getTabCompleter() == null;
        }
    }

    private static void join(DailyQuestLifecycleFixture f, UUID id) {
        Player p = proxy(Player.class, (m, a) -> m.equals("getUniqueId") ? id : null);
        if (!f.runtime.registered.isEmpty())
            ((DailyQuestListener) f.runtime.registered.getFirst())
                    .onPlayerJoin(
                            new PlayerJoinEvent(p, net.kyori.adventure.text.Component.empty()));
    }

    private static void released(DailyQuestLifecycleFixture f, DailyQuestModule module) {
        check(f.runtime.handlerCount() == 0, "all exact handlers released");
        check(f.running() == 0, "all acquired main tasks cancelled");
        check(f.locales.isEmpty() && f.changes.isEmpty(), "all owned observers released");
        check(
                f.runtime.command.getExecutor() == f.runtime.plugin
                        && f.runtime.command.getTabCompleter() == null,
                "owned command bindings released");
        check(fails(module::getDailyQuestService) != null, "getter closed after retirement");
        check(get(module, "pendingCleanup") == null, "no hidden unresolved cleanup");
    }

    private static RuntimeException fails(Runnable action) {
        try {
            action.run();
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    private static final class RefusingHandlers extends HandlerList {
        private final Listener owned;
        private final boolean after;
        boolean refuse = true;

        RefusingHandlers(Listener owned, Plugin plugin, boolean after) {
            this.owned = owned;
            this.after = after;
            register(
                    new RegisteredListener(
                            owned, (l, e) -> {}, EventPriority.NORMAL, plugin, false));
        }

        @Override
        public synchronized void unregister(Listener listener) {
            if (listener == owned && refuse && !after)
                throw new IllegalStateException("unregister refused");
            super.unregister(listener);
            if (listener == owned && refuse)
                throw new IllegalStateException("unregister after effect");
        }
    }
}
