package dev.vapee.core.quest;

import dev.vapee.core.activity.ActivityMembershipListener;
import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.player.LobbyPlayerStateListener;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.module.ModuleRuntimeFixture;

import org.bukkit.event.HandlerList;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Movement binding belongs to the existing Quest runtime, including partial acquisition and cleanup retry. */
public final class QuestMovementModuleHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        normal();
        registration();
        removal();
        composition();
        System.out.println("QuestMovementModuleHarness passed " + checks + " checks.");
    }

    private static final class Context {
        final FailingSet<LobbyPlayerStateListener> lobbyListeners = new FailingSet<>();
        final FailingSet<ActivityMembershipListener> activityListeners = new FailingSet<>();
        final LobbyPlayerStateService lobby =
                ModuleRuntimeFixture.dependency(
                        LobbyPlayerStateService.class,
                        Map.of(
                                "buildPlayers",
                                new HashSet<UUID>(),
                                "stateListeners",
                                lobbyListeners));
        final ActivityService activities =
                ModuleRuntimeFixture.dependency(
                        ActivityService.class,
                        Map.of(
                                "playerMemberships",
                                new HashMap<UUID, UUID>(),
                                "membershipListeners",
                                activityListeners));
    }

    private static final class FailingSet<T> extends LinkedHashSet<T> {
        boolean failAdd, failRemove;

        @Override
        public boolean add(T value) {
            boolean changed = super.add(value);
            if (failAdd) throw new IllegalStateException("observer add after effect");
            return changed;
        }

        @Override
        public boolean remove(Object value) {
            if (failRemove) throw new IllegalStateException("observer removal refused");
            return super.remove(value);
        }
    }

    private static QuestMovementProducer producer(QuestLifecycleFixture f) {
        return (QuestMovementProducer)
                HandlerList.getRegisteredListeners(f.runtime.plugin).stream()
                        .map(h -> h.getListener())
                        .filter(QuestMovementProducer.class::isInstance)
                        .findFirst()
                        .orElseThrow();
    }

    private static long handlers(QuestLifecycleFixture f) {
        return HandlerList.getRegisteredListeners(f.runtime.plugin).stream()
                .filter(h -> h.getListener() instanceof QuestMovementProducer)
                .count();
    }

    private static void normal() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            Context c = new Context();
            f.module.enable();
            f.module.startMovement(c.lobby, c.activities);
            QuestMovementProducer old = producer(f);
            check(
                    handlers(f) == 14 && f.handlers() == 2 && f.activeTasks() == 1,
                    "one movement listener, original playtime listeners and no extra scheduler");
            check(
                    c.lobbyListeners.size() == 1 && c.activityListeners.size() == 1,
                    "borrows the existing context observers once");
            try {
                f.module.startMovement(c.lobby, c.activities);
                throw new AssertionError("duplicate accepted");
            } catch (IllegalStateException expected) {
                check(
                        handlers(f) == 14 && c.lobbyListeners.size() == 1,
                        "duplicate binding cannot duplicate progress handlers");
            }
            var movement = new QuestMovementTestSupport.Fixture();
            var actor = movement.actor();
            f.players.loadPlayer(actor.id, "Walker");
            f.runtime.online.add(actor.player);
            f.module
                    .getDefinitionRegistry()
                    .replaceAll(
                            List.of(
                                    new QuestDefinition(
                                            "walk",
                                            "Walk",
                                            "Walk",
                                            QuestMovementProducer.WALK,
                                            10,
                                            1)));
            f.module.getQuestService().assignQuest(actor.id, "walk");
            var originalCalls = f.runtime.serverCalls;
            int[] tick = {100};
            f.runtime.serverCalls =
                    (name, args) ->
                            name.equals("getCurrentTick")
                                    ? tick[0]
                                    : originalCalls.apply(name, args);
            var from = actor.at.clone();
            var to = from.clone().add(0.6, 0, 0);
            old.onMove(new org.bukkit.event.player.PlayerMoveEvent(actor.player, from, to));
            actor.at = to;
            check(
                    old.trackedPlayers() == 1,
                    "bound production producer retains only temporary measurement state");
            c.lobbyListeners.iterator().next().beforeStateChange(actor.player);
            check(old.trackedPlayers() == 0, "borrowed lobby observer reaches the real producer");
            tick[0]++;
            old.onMove(
                    new org.bukkit.event.player.PlayerMoveEvent(
                            actor.player, to, to.clone().add(0.4, 0, 0)));
            check(
                    f.players
                                    .getPlayer(actor.id)
                                    .orElseThrow()
                                    .getQuestState()
                                    .snapshot()
                                    .get("walk")
                                    .progress()
                            == 0,
                    "transition cannot join old and new fractions");
            c.activityListeners.iterator().next().beforeJoin(actor.player);
            check(
                    old.trackedPlayers() == 0,
                    "borrowed activity observer reaches the real producer");
            f.module.disable();
            check(
                    handlers(f) == 0
                            && c.lobbyListeners.isEmpty()
                            && c.activityListeners.isEmpty()
                            && old.trackedPlayers() == 0,
                    "disable releases both observers, all movement handlers and transient state");
            old.onMove(
                    new org.bukkit.event.player.PlayerMoveEvent(
                            actor.player, to, to.clone().add(1, 0, 0)));
            check(
                    old.trackedPlayers() == 0,
                    "retained old listener reference is inert after disable");
            f.module.enable();
            f.module.startMovement(c.lobby, c.activities);
            check(
                    producer(f) != old && handlers(f) == 14 && f.activeTasks() == 1,
                    "re-enable binds a new producer without leaking tasks or old listeners");
            f.module.disable();
        }
    }

    private static void registration() throws Exception {
        for (boolean after : List.of(false, true))
            try (var f = new QuestLifecycleFixture()) {
                Context c = new Context();
                f.module.enable();
                f.refuseRegistrationBefore = !after;
                f.refuseRegistrationAfter = after;
                try {
                    f.module.startMovement(c.lobby, c.activities);
                    throw new AssertionError("registration should fail");
                } catch (IllegalStateException expected) {
                    check(
                            handlers(f) == 0
                                    && c.lobbyListeners.isEmpty()
                                    && c.activityListeners.isEmpty(),
                            "registration failure releases observers and partially registered movement handlers");
                    check(
                            f.module.getQuestService() != null && f.activeTasks() == 1,
                            "failed movement binding does not replace the borrowed quest/reward runtime");
                }
                f.refuseRegistrationBefore = false;
                f.refuseRegistrationAfter = false;
                f.module.startMovement(c.lobby, c.activities);
                check(handlers(f) == 14, "explicit binding retry succeeds after confirmed cleanup");
                f.module.disable();
            }
        try (var f = new QuestLifecycleFixture()) {
            Context c = new Context();
            f.module.enable();
            c.lobbyListeners.failAdd = true;
            try {
                f.module.startMovement(c.lobby, c.activities);
                throw new AssertionError("observer should fail");
            } catch (IllegalStateException expected) {
                check(
                        c.lobbyListeners.isEmpty()
                                && c.activityListeners.isEmpty()
                                && handlers(f) == 0,
                        "observer acquisition that takes effect then throws is still released");
            }
            c.lobbyListeners.failAdd = false;
            f.module.startMovement(c.lobby, c.activities);
            f.module.disable();
        }
    }

    private static void removal() throws Exception {
        try (var f = new QuestLifecycleFixture()) {
            Context c = new Context();
            f.module.enable();
            f.module.startMovement(c.lobby, c.activities);
            var old = producer(f);
            c.activityListeners.failRemove = true;
            try {
                f.module.disable();
                throw new AssertionError("cleanup should fail");
            } catch (IllegalStateException expected) {
                check(
                        handlers(f) == 0
                                && c.lobbyListeners.isEmpty()
                                && c.activityListeners.size() == 1,
                        "failed observer removal remains an explicit owned cleanup obligation");
            }
            try {
                f.module.enable();
                throw new AssertionError("restart should be blocked");
            } catch (IllegalStateException expected) {
                check(
                        old.trackedPlayers() == 0,
                        "unresolved movement cleanup blocks re-enable and keeps retired producer inert");
            }
            c.activityListeners.failRemove = false;
            f.module.disable();
            check(
                    c.activityListeners.isEmpty(),
                    "disable retry releases the original borrowed observer");
            f.module.enable();
            f.module.startMovement(c.lobby, c.activities);
            f.module.disable();
        }
        try (var f = new QuestLifecycleFixture()) {
            Context c = new Context();
            f.module.enable();
            f.module.startMovement(c.lobby, c.activities);
            var refused = new QuestLifecycleFixture.RefusingHandlers(producer(f), f.runtime.plugin);
            f.refusingLists.add(refused);
            try {
                f.module.disable();
                throw new AssertionError("handler cleanup should fail");
            } catch (IllegalStateException expected) {
                check(
                        handlers(f) > 0
                                && c.lobbyListeners.isEmpty()
                                && c.activityListeners.isEmpty(),
                        "unconfirmed movement-handler release remains retryable without observer leaks");
            }
            refused.refuse = false;
            f.module.disable();
            check(
                    handlers(f) == 0,
                    "retry confirms release of the original failed movement listener");
        }
    }

    private static void composition() throws Exception {
        String root = Files.readString(Path.of("src/main/java/dev/vapee/core/VapeeCore.java"));
        check(
                root.indexOf("questModule.startMovement(")
                        > root.indexOf("moduleManager.enableAll()"),
                "movement context is borrowed after the original owners are available");
        check(
                root.contains(
                        "questModule.startMovement(lobbyModule.getLobbyPlayerStateService(), activityModule.getActivityService())"),
                "actual production root binds the existing context services");
    }

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
}
