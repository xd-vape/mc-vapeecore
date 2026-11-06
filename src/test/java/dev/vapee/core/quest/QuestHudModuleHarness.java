package dev.vapee.core.quest;

import static dev.vapee.core.quest.QuestHudTestSupport.*;

import dev.vapee.core.activity.*;
import dev.vapee.core.lobby.player.*;
import dev.vapee.core.module.ModuleRuntimeFixture;

import net.kyori.adventure.text.Component;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.*;

import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;

/** Actual QuestModule, producer instances, services, HandlerLists, scheduler handles and cleanup retries. */
public final class QuestHudModuleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        bindingAndProducers();
        acquisitionFailures();
        cleanupFailures();
        xpOwnershipFailure();
        composition();
        System.out.println("QuestHudModuleHarness passed " + checks + " checks.");
    }

    static final class FailingSet<T> extends LinkedHashSet<T> {
        boolean addFailure, removeFailure;

        public boolean add(T value) {
            boolean changed = super.add(value);
            if (addFailure) throw new IllegalStateException("add after effect");
            return changed;
        }

        public boolean remove(Object value) {
            if (removeFailure) throw new IllegalStateException("remove refused");
            return super.remove(value);
        }
    }

    static final class Context {
        final FailingSet<LobbyPlayerStateListener> lobbyListeners = new FailingSet<>();
        final FailingSet<ActivityMembershipListener> activityListeners = new FailingSet<>();
        final Set<UUID> build = new HashSet<>();
        final Map<UUID, UUID> memberships = new HashMap<>();
        final LobbyPlayerStateService lobby =
                ModuleRuntimeFixture.dependency(
                        LobbyPlayerStateService.class,
                        Map.of("buildPlayers", build, "stateListeners", lobbyListeners));
        final ActivityService activity =
                ModuleRuntimeFixture.dependency(
                        ActivityService.class,
                        Map.of(
                                "playerMemberships",
                                memberships,
                                "membershipListeners",
                                activityListeners));
    }

    static QuestProgressHud hud(QuestLifecycleFixture f) {
        return (QuestProgressHud)
                HandlerList.getRegisteredListeners(f.runtime.plugin).stream()
                        .map(h -> h.getListener())
                        .filter(QuestProgressHud.class::isInstance)
                        .findFirst()
                        .orElseThrow();
    }

    private static long handlers(QuestLifecycleFixture f) {
        return HandlerList.getRegisteredListeners(f.runtime.plugin).stream()
                .filter(h -> h.getListener() instanceof QuestProgressHud)
                .count();
    }

    private static int[] clock(QuestLifecycleFixture f) {
        int[] tick = {100};
        var before = f.runtime.serverCalls;
        f.runtime.serverCalls =
                (name, args) -> name.equals("getCurrentTick") ? tick[0] : before.apply(name, args);
        return tick;
    }

    private static Actor actor(QuestLifecycleFixture f, Fixture actors) {
        var actor = actors.actor();
        f.players.loadPlayer(actor.id, "HudPlayer");
        f.runtime.online.add(actor.player);
        return actor;
    }

    private static void bindingAndProducers() throws Exception {
        try (var f = new QuestLifecycleFixture();
                var actors = new Fixture()) {
            int[] tick = clock(f);
            Context context = new Context();
            f.module.enable();
            f.module.startMovement(context.lobby, context.activity);
            f.module.startHud(context.lobby, context.activity);
            QuestProgressHud old = hud(f);
            var task = f.tasks.get(1);
            check(
                    f.activeTasks() == 2
                            && task.period == 5
                            && f.tasks.getFirst().period == 100
                            && handlers(f) == 9,
                    "one shared five-tick HUD task/listener alongside unchanged hundred-tick producer/flush task");
            check(
                    context.lobbyListeners.size() == 2 && context.activityListeners.size() == 2,
                    "HUD and movement borrow existing context owners once each");
            try {
                f.module.startHud(context.lobby, context.activity);
                throw new AssertionError("duplicate binding accepted");
            } catch (IllegalStateException expected) {
                check(
                        f.activeTasks() == 2 && handlers(f) == 9,
                        "duplicate binding cannot create extra task or handlers");
            }
            Actor actor = actor(f, actors);
            var definitions =
                    List.of(
                            new QuestDefinition(
                                    "walk",
                                    "Walk",
                                    "Test",
                                    QuestProgressKey.of("movement:walk"),
                                    2,
                                    7),
                            new QuestDefinition(
                                    "time",
                                    "Time",
                                    "Test",
                                    QuestProgressKey.of("playtime:minute"),
                                    2,
                                    3));
            f.module.getDefinitionRegistry().replaceAll(definitions);
            f.module.getQuestService().replaceAssignments(actor.id, List.of("walk", "time"));
            f.listener().onPlayerJoin(new PlayerJoinEvent(actor.player, Component.empty()));
            var producer =
                    (QuestMovementProducer)
                            HandlerList.getRegisteredListeners(f.runtime.plugin).stream()
                                    .map(h -> h.getListener())
                                    .filter(QuestMovementProducer.class::isInstance)
                                    .findFirst()
                                    .orElseThrow();
            for (double distance : List.of(.6, .4)) {
                tick[0]++;
                var from = actor.at.clone();
                actor.at.add(distance, 0, 0);
                producer.onMove(new PlayerMoveEvent(actor.player, from, actor.at.clone()));
            }
            check(
                    actor.bars.isEmpty() && actor.xp.isEmpty(),
                    "actual bound movement producer queues central progress without per-event packets");
            task.action.run();
            check(
                    actor.bar().equals("Walk · 1/2 · 50%") && actor.clientExp == .5F,
                    "actual module observer and scheduled task render movement progress");
            tick[0]++;
            var from = actor.at.clone();
            actor.at.add(1, 0, 0);
            producer.onMove(new PlayerMoveEvent(actor.player, from, actor.at.clone()));
            tick[0] += 5;
            task.action.run();
            check(
                    actor.bar().endsWith("100%")
                            && actor.chat.size() == 1
                            && f.players.getPlayer(actor.id).orElseThrow().getWallet().getCoins()
                                    == 7,
                    "actual producer completion keeps single chat feedback and original seven-coin reward");
            actor.statTicks = 1200;
            tick[0] += 10;
            f.tasks.getFirst().action.run();
            task.action.run();
            check(
                    actor.bar().equals("Time · 1/2 · 50%")
                            && f.disk.findByUniqueId(actor.id)
                                            .orElseThrow()
                                            .getQuestState()
                                            .snapshot()
                                            .get("time")
                                            .progress()
                                    == 1,
                    "unchanged actual playtime producer and original flush pipeline reach the same generic HUD");
            check(
                    actor.mutations == 0 && actor.realExp == .25F && actor.realLevel == 12,
                    "real module wiring never mutates server XP");
            context.lobbyListeners.forEach(l -> l.beforeStateChange(actor.player));
            context.build.add(actor.id);
            context.lobbyListeners.forEach(l -> l.afterStateChange(actor.id));
            check(
                    old.trackedPlayers() == 0 && actor.clientExp == actor.realExp,
                    "actual borrowed BUILD callbacks release owned XP without a move");
            f.module
                    .getProgressReporter()
                    .report(actor.id, QuestProgressKey.of("playtime:minute"), 1);
            tick[0] += 5;
            task.action.run();
            check(
                    old.trackedPlayers() == 0,
                    "BUILD context rejects processed generic progress presentation");
            context.build.clear();
            context.lobbyListeners.forEach(l -> l.afterStateChange(actor.id));
            f.module.getQuestService().replaceAssignments(actor.id, List.of("walk"));
            f.module
                    .getProgressReporter()
                    .report(actor.id, QuestProgressKey.of("movement:walk"), 1);
            tick[0] += 5;
            task.action.run();
            f.runtime.messages.sendActionBar(actor.player, Component.text("Navigator foreground"));
            int sent = actor.bars.size();
            f.module
                    .getProgressReporter()
                    .report(actor.id, QuestProgressKey.of("movement:walk"), 1);
            tick[0] += 5;
            task.action.run();
            check(
                    actor.bars.size() == sent && actor.bar().equals("Navigator foreground"),
                    "production MessageService subscription protects foreground output");
            for (int n = 0; n < 5; n++) actor(f, actors);
            check(f.activeTasks() == 2, "more online players never allocate HUD tasks");
            f.module.disable();
            check(
                    f.activeTasks() == 0
                            && handlers(f) == 0
                            && context.lobbyListeners.isEmpty()
                            && context.activityListeners.isEmpty()
                            && old.released(),
                    "disable releases task, listener, both contexts and all display state");
            check(
                    field(f.module, "pendingCleanup") == null,
                    "successful cleanup leaves no pending ownership");
            f.module.enable();
            f.module.startHud(context.lobby, context.activity);
            var fresh = hud(f);
            int packets = actor.xp.size();
            task.action.run();
            old.tick();
            check(
                    fresh != old && f.activeTasks() == 2 && actor.xp.size() == packets,
                    "re-enable uses fresh HUD; retired task and callbacks stay inert");
            f.module.disable();
        }
    }

    private static void acquisitionFailures() throws Exception {
        for (boolean after : List.of(false, true))
            try (var f = new QuestLifecycleFixture()) {
                Context context = new Context();
                f.module.enable();
                f.refuseRegistrationBefore = !after;
                f.refuseRegistrationAfter = after;
                try {
                    f.module.startHud(context.lobby, context.activity);
                    throw new AssertionError("registration accepted");
                } catch (IllegalStateException expected) {
                    check(
                            handlers(f) == 0
                                    && f.activeTasks() == 1
                                    && context.lobbyListeners.isEmpty()
                                    && context.activityListeners.isEmpty(),
                            "before/after-effect handler registration failure rolls back acquired HUD resources");
                }
                f.refuseRegistrationBefore = f.refuseRegistrationAfter = false;
                f.module.startHud(context.lobby, context.activity);
                f.module.disable();
            }
        try (var f = new QuestLifecycleFixture()) {
            Context context = new Context();
            f.module.enable();
            f.refuseScheduling = true;
            try {
                f.module.startHud(context.lobby, context.activity);
                throw new AssertionError("scheduler accepted");
            } catch (IllegalStateException expected) {
                check(
                        handlers(f) == 0
                                && f.activeTasks() == 1
                                && context.lobbyListeners.isEmpty(),
                        "HUD scheduling failure detaches already acquired subscriptions and handlers");
            }
            f.refuseScheduling = false;
            f.module.startHud(context.lobby, context.activity);
            f.module.disable();
        }
        try (var f = new QuestLifecycleFixture()) {
            Context context = new Context();
            f.module.enable();
            context.activityListeners.addFailure = true;
            try {
                f.module.startHud(context.lobby, context.activity);
                throw new AssertionError("observer accepted");
            } catch (IllegalStateException expected) {
                check(
                        context.activityListeners.isEmpty()
                                && context.lobbyListeners.isEmpty()
                                && handlers(f) == 0,
                        "observer add-after-effect failure is released using original identity");
            }
            context.activityListeners.addFailure = false;
            f.module.startHud(context.lobby, context.activity);
            f.module.disable();
        }
    }

    private static void cleanupFailures() throws Exception {
        for (String failure : List.of("task", "observer", "handlers", "foreground", "progress"))
            try (var f = new QuestLifecycleFixture()) {
                Context context = new Context();
                f.module.enable();
                var foreground = new FailingSet<Consumer<Player>>();
                ModuleRuntimeFixture.set(f.runtime.messages, "actionBarObservers", foreground);
                var progress = new FailingSet<QuestProgressListener>();
                ModuleRuntimeFixture.set(f.module.getQuestService(), "progressListeners", progress);
                f.module.startHud(context.lobby, context.activity);
                var old = hud(f);
                QuestLifecycleFixture.RefusingHandlers refused = null;
                if (failure.equals("task")) f.tasks.get(1).refuse = true;
                if (failure.equals("observer")) context.activityListeners.removeFailure = true;
                if (failure.equals("foreground")) foreground.removeFailure = true;
                if (failure.equals("progress")) progress.removeFailure = true;
                if (failure.equals("handlers")) {
                    refused = new QuestLifecycleFixture.RefusingHandlers(old, f.runtime.plugin);
                    f.refusingLists.add(refused);
                }
                try {
                    f.module.disable();
                    throw new AssertionError("cleanup should fail: " + failure);
                } catch (IllegalStateException expected) {
                    check(
                            field(f.module, "pendingCleanup") != null,
                            "unconfirmed " + failure + " release remains an owned pending cleanup");
                }
                try {
                    f.module.enable();
                    throw new AssertionError("unsafe restart accepted");
                } catch (IllegalStateException expected) {
                    check(true, "incomplete " + failure + " cleanup blocks re-enable");
                }
                f.tasks.get(1).refuse = false;
                context.activityListeners.removeFailure =
                        foreground.removeFailure = progress.removeFailure = false;
                if (refused != null) refused.refuse = false;
                f.module.disable();
                check(
                        f.activeTasks() == 0
                                && handlers(f) == 0
                                && progress.isEmpty()
                                && foreground.isEmpty()
                                && context.activityListeners.isEmpty(),
                        "explicit retry confirms all original HUD releases after " + failure);
            }
    }

    private static void xpOwnershipFailure() throws Exception {
        try (var f = new QuestLifecycleFixture();
                var actors = new Fixture()) {
            clock(f);
            Context context = new Context();
            f.module.enable();
            f.module.startHud(context.lobby, context.activity);
            var actor = actor(f, actors);
            f.module
                    .getDefinitionRegistry()
                    .replaceAll(
                            List.of(
                                    new QuestDefinition(
                                            "q",
                                            "Q",
                                            "Test",
                                            QuestProgressKey.of("test:q"),
                                            100,
                                            7)));
            f.module.getQuestService().assignQuest(actor.id, "q");
            f.module.getProgressReporter().report(actor.id, QuestProgressKey.of("test:q"), 1);
            f.tasks.get(1).action.run();
            actor.xpFailures = -1;
            actor.xpAfter = true;
            try {
                f.module.disable();
                throw new AssertionError("XP release should fail");
            } catch (IllegalStateException expected) {
                check(
                        field(f.module, "pendingCleanup") != null
                                && handlers(f) == 0
                                && f.activeTasks() == 0,
                        "failed XP restoration retains exact client ownership despite independent successful resource cleanup");
            }
            try {
                f.module.enable();
                throw new AssertionError("restart accepted with owned XP pending");
            } catch (IllegalStateException expected) {
                check(true, "lost XP cleanup ownership is detected before re-enable");
            }
            actor.xpFailures = 0;
            actor.realExp = .8F;
            actor.realLevel = 77;
            f.module.disable();
            check(
                    actor.xp.getLast().equals(new Xp(.8F, 77)) && actor.mutations == 0,
                    "cleanup retry restores newly read real XP, not an old cached value");
        }
    }

    private static void composition() throws Exception {
        String core = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        check(
                core.indexOf("questModule.startHud(") > core.indexOf("moduleManager.enableAll();")
                        && core.contains(
                                "questModule.startHud(lobbyModule.getLobbyPlayerStateService(), activityModule.getActivityService());"),
                "actual plugin binds generic HUD after existing context owners start");
        String editor =
                Files.readString(
                        Path.of(
                                "src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorEditor.java"));
        String menu =
                Files.readString(
                        Path.of(
                                "src/main/java/dev/vapee/core/lobby/experience/navigator/NavigatorMenu.java"));
        check(
                editor.contains("messages.sendActionBar(player,")
                        && menu.contains("messageService.sendActionBar(player,"),
                "both existing Navigator actionbar paths participate in foreground arbitration");
    }

    private static Object field(Object owner, String name) throws Exception {
        var field = owner.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(owner);
    }

    private static void check(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
}
