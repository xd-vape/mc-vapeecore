package dev.vapee.core.quest;

import static dev.vapee.core.quest.QuestMovementTestSupport.*;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import org.bukkit.GameMode;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.*;
import org.bukkit.util.Vector;

import java.util.List;
import java.util.function.Consumer;

public final class QuestMovementHarness {
    private static int checks;

    public static void main(String[] args) {
        if (args.length == 1 && args[0].equals("--gameplay")) {
            gameplay();
            System.out.println("QuestMovementHarness passed " + checks + " checks.");
            return;
        }
        if (args.length == 1 && args[0].equals("--client-readiness")) {
            clientReadiness();
            System.out.println("QuestMovementHarness passed " + checks + " checks.");
            return;
        }
        if (args.length == 1 && args[0].equals("--confirmed-sneak")) {
            confirmedSneak();
            System.out.println("QuestMovementHarness passed " + checks + " checks.");
            return;
        }
        if (args.length == 1 && args[0].equals("--velocity-guard")) {
            velocityGuard();
            System.out.println("QuestMovementHarness passed " + checks + " checks.");
            return;
        }
        if (args.length == 1 && args[0].equals("--lifecycle-guard")) {
            lifecycleGuard();
            System.out.println("QuestMovementHarness passed " + checks + " checks.");
            return;
        }
        if (args.length == 1 && args[0].equals("--sneak-reconciliation")) {
            sneakReconciliation();
            System.out.println("QuestMovementHarness passed " + checks + " checks.");
            return;
        }
        distances();
        turns();
        jumps();
        invalid();
        transitions();
        backwardGeometry();
        backwardRejections();
        toggleTransitions();
        toggleEligibility();
        finalMovementLifecycle();
        velocityGuard();
        lifecycleGuard();
        sneakReconciliation();
        confirmedSneak();
        clientReadiness();
        gameplay();
        System.out.println("QuestMovementHarness passed " + checks + " checks.");
    }

    private static Fixture fixture() {
        Fixture f = new Fixture();
        f.define("walk", QuestMovementProducer.WALK, 10000, 1);
        f.define("sprint", QuestMovementProducer.SPRINT, 10000, 1);
        f.define("sneak", QuestMovementProducer.SNEAK, 10000, 1);
        f.define("jump", QuestMovementProducer.JUMP, 10000, 1);
        f.define("turn", QuestMovementProducer.ROTATION, 10000, 1);
        return f;
    }

    private static Actor assigned(Fixture f) {
        Actor a = f.actor();
        f.assign(a, "walk", "sprint", "sneak", "jump", "turn");
        return a;
    }

    private static void distances() {
        Fixture f = fixture();
        Actor a = assigned(f);
        for (int i = 0; i < 3; i++) f.step(a, 0.25);
        check(f.progress(a, "walk") == 0, "only integral horizontal blocks are reported");
        f.step(a, 0.25);
        check(f.progress(a, "walk") == 1, "fractional walk accumulates exactly");
        a.sprint = true;
        f.step(a, 0.6);
        a.sprint = false;
        f.step(a, 0.2);
        a.sprint = true;
        f.step(a, 0.4);
        check(
                f.progress(a, "walk") == 2 && f.progress(a, "sprint") == 1,
                "walk includes sprint and sparse sprint fractions survive normal walking");
        a.sprint = false;
        a.sneak = true;
        f.step(a, 0.6);
        f.step(a, 0.4);
        check(
                f.progress(a, "walk") == 3 && f.progress(a, "sneak") == 1,
                "sneak is an additional subset, not extra walk credit");
        a.sprint = true;
        f.step(a, 1);
        check(
                f.progress(a, "sprint") == 1 && f.progress(a, "sneak") == 2,
                "sneak takes precedence for inconsistent overlapping movement flags");
        a.sprint = false;
        a.sneak = false;
        long before = f.progress(a, "walk");
        var event = f.step(a, 0.5);
        f.producer.onMove(event);
        f.tick++;
        f.producer.onMove(event);
        f.step(a, 0.5);
        check(
                f.progress(a, "walk") == before + 1,
                "replayed event cannot double-count distance or destroy the retained fraction");
        a.ground = false;
        before = f.progress(a, "walk");
        f.move(a, 0.8, 0.2, 0, a.at.getYaw());
        check(f.progress(a, "walk") == before, "airborne distance is not ground travel");
        a.ground = true;
        a.input = false;
        f.step(a, 1);
        check(
                f.progress(a, "walk") == before,
                "movement without directional input is not credited");
        a.input = true;
        a.allowFlight = true;
        f.step(a, 1);
        check(
                f.progress(a, "walk") == before + 1 && a.writes == 0,
                "armed lobby double-jump allowFlight does not disable walking or mutate glide"
                        + " flags");
        f.move(a, 0.6, 0, 0.8, a.at.getYaw());
        check(
                f.progress(a, "walk") == before + 2,
                "horizontal Euclidean diagonal, not Manhattan or 3D distance");
        before = f.progress(a, "walk");
        f.move(a, 0, 0.5, 0, a.at.getYaw());
        check(
                f.progress(a, "walk") == before && f.progress(a, "jump") == 0,
                "Y-only change never manufactures walk or jump progress");
    }

    private static void turns() {
        for (int direction : List.of(-1, 1)) {
            Fixture f = fixture();
            Actor a = assigned(f);
            a.at.setYaw(170);
            for (int i = 0; i < 3; i++) f.rotate(a, 90 * direction);
            check(f.progress(a, "turn") == 0, "three quarters do not complete a turn");
            f.rotate(a, 90 * direction);
            check(
                    f.progress(a, "turn") == 1,
                    "one continuously observed full turn in direction " + direction);
            for (int i = 0; i < 20; i++) {
                f.rotate(a, 20);
                f.rotate(a, -20);
            }
            check(f.progress(a, "turn") == 1, "small oscillations do not accumulate absolute yaw");
        }
        Fixture f = fixture();
        Actor a = assigned(f);
        a.at.setYaw(150);
        for (float yaw : new float[] {-120, -30, 60, 150}) {
            f.tick++;
            var from = a.at.clone();
            var to = from.clone();
            to.setYaw(yaw);
            f.producer.onMove(new PlayerMoveEvent(a.player, from, to));
            a.at = to;
        }
        check(f.progress(a, "turn") == 1, "signed -180/+180 wraparound is continuous");
        f.rotate(a, 180);
        for (int i = 0; i < 3; i++) f.rotate(a, 90);
        check(f.progress(a, "turn") == 1, "ambiguous extreme yaw is discarded rather than guessed");
        f.rotate(a, 90);
        check(f.progress(a, "turn") == 2, "new complete observed arc after rejection counts");
        int tick = f.tick;
        for (int i = 0; i < 40; i++) {
            var from = a.at.clone();
            var to = from.clone();
            to.setYaw(from.getYaw() + 45);
            f.producer.onMove(new PlayerMoveEvent(a.player, from, to));
            a.at = to;
        }
        check(
                f.tick == tick && f.progress(a, "turn") == 2,
                "same-tick synthetic event flood cannot generate unbounded rotations");
        f.tick += 41;
        for (int i = 0; i < 3; i++) f.rotate(a, 90);
        check(f.progress(a, "turn") == 2, "long observation gap resets the partial rotation");
        f.rotate(a, 90);
        check(f.progress(a, "turn") == 3, "fresh full rotation after gap");
    }

    private static void jumps() {
        Fixture f = fixture();
        Actor a = assigned(f);
        f.jump(a, false);
        check(
                f.progress(a, "jump") == 1,
                "real Paper jump plus matching accepted move counts once");
        f.ground(a);
        f.jump(a, true);
        check(f.progress(a, "jump") == 1, "cancelled Paper jump contributes nothing");
        f.ground(a);
        f.tick++;
        var from = a.at.clone();
        var to = from.clone().add(0, 0.42, 0);
        var jump = new PlayerJumpEvent(a.player, from, to);
        f.producer.onJump(jump);
        check(f.progress(a, "jump") == 1, "unconfirmed jump does not earn progress");
        var move = new PlayerMoveEvent(a.player, from, to);
        move.setCancelled(true);
        f.producer.onMove(move);
        check(f.progress(a, "jump") == 1, "later move cancellation discards pending native jump");
        f.ground(a);
        f.tick++;
        f.producer.onJump(jump);
        f.producer.onJump(jump);
        move.setCancelled(false);
        f.producer.onMove(move);
        a.at = to;
        check(
                f.progress(a, "jump") == 2,
                "duplicate jump notifications require one matching movement only");
        f.producer.onJump(jump);
        f.producer.onMove(move);
        check(f.progress(a, "jump") == 2, "replayed jump and movement cannot count twice");
        f.ground(a);
        f.tick++;
        from = a.at.clone();
        to = from.clone().add(0, 0.42, 0);
        f.producer.onJump(new PlayerJumpEvent(a.player, from, to));
        f.tick += 3;
        f.producer.onMove(new PlayerMoveEvent(a.player, from, to));
        a.at = to;
        check(
                f.progress(a, "jump") == 2,
                "expired jump intent is never applied to unrelated later movement");
        f.ground(a);
        f.producer.onVelocity(new PlayerVelocityEvent(a.player, new Vector(0, 0.7, 0)));
        f.jump(a, false);
        check(f.progress(a, "jump") == 2, "external upward impulse cannot generate jump credit");
        f.tick += 21;
        f.ground(a);
        f.jump(a, false);
        check(
                f.progress(a, "jump") == 3,
                "valid jumps resume after external impulse exclusion expires");
        check(
                a.writes == 0,
                "jump producer never changes flight, glide, velocity or event authority");
    }

    private static void invalid() {
        List<Consumer<Actor>> modes =
                List.of(
                        a -> a.vehicle = true,
                        a -> a.fly = true,
                        a -> a.glide = true,
                        a -> a.water = true,
                        a -> a.swimming = true,
                        a -> a.climbing = true,
                        a -> a.riptide = true,
                        a -> a.dead = true,
                        a -> a.online = false,
                        a -> a.mode = GameMode.CREATIVE,
                        a -> a.mode = GameMode.SPECTATOR,
                        a -> a.context = false);
        for (var mode : modes) {
            Fixture f = fixture();
            Actor a = assigned(f);
            mode.accept(a);
            f.step(a, 1);
            f.rotate(a, 90);
            f.jump(a, false);
            check(
                    f.progress(a, "walk") == 0
                            && f.progress(a, "turn") == 0
                            && f.progress(a, "jump") == 0,
                    "invalid movement mode is excluded");
        }
        Fixture f = fixture();
        Actor a = assigned(f);
        f.step(a, 0.6);
        f.tick++;
        var e = new PlayerMoveEvent(a.player, a.at.clone(), a.at.clone().add(0.4, 0, 0));
        e.setCancelled(true);
        f.producer.onMove(e);
        f.step(a, 0.4);
        check(f.progress(a, "walk") == 0, "cancellation resets pending fractions");
        f.step(a, 100);
        f.step(a, 0.6);
        check(
                f.progress(a, "walk") == 0,
                "unchecked distance discontinuity is rejected and rebased");
        f.step(a, 0.4);
        check(
                f.progress(a, "walk") == 1,
                "valid segments after discontinuity start a fresh fraction");
        f.tick++;
        var from = a.at.clone();
        var to = from.clone().add(0.6, 0, 0);
        f.producer.onMove(new PlayerMoveEvent(a.player, from, to));
        a.at = to;
        from = a.at.clone();
        to = from.clone().add(0.6, 0, 0);
        f.producer.onMove(new PlayerMoveEvent(a.player, from, to));
        a.at = to;
        check(
                f.progress(a, "walk") == 1,
                "same-tick movement beyond the shared distance budget is rejected");
        f.tick++;
        to = a.at.clone();
        to.setX(Double.NaN);
        f.producer.onMove(new PlayerMoveEvent(a.player, a.at, to));
        check(f.progress(a, "walk") == 1, "non-finite position cannot credit progress");
        f.step(a, 0.4);
        check(
                f.progress(a, "walk") == 1,
                "non-finite position discards previous fractional measurement");
        f.step(a, 0.6);
        long beforeFlood = f.progress(a, "walk");
        f.tick++;
        for (int i = 0; i < 100; i++) {
            var start = a.at.clone();
            var finish = start.clone().add(1, 0, 0);
            f.producer.onMove(new PlayerMoveEvent(a.player, start, finish));
            a.at = finish;
        }
        check(
                f.progress(a, "walk") == beforeFlood + 1,
                "rejecting excess movement cannot refill the same-tick distance budget");
    }

    private static void transitions() {
        Fixture f = fixture();
        Actor a = assigned(f);
        f.step(a, 0.6);
        f.rotate(a, 90);
        var destination = a.at.clone().add(100, 0, 0);
        destination.setYaw(-170);
        var teleport = new PlayerTeleportEvent(a.player, a.at.clone(), destination);
        f.producer.onMove(teleport);
        f.producer.onTeleport(teleport);
        a.at = destination;
        f.step(a, 0.4);
        check(
                f.progress(a, "walk") == 0 && f.progress(a, "turn") == 0,
                "teleport never contributes distance or yaw and clears fractions");
        f.producer.onWorld(new PlayerChangedWorldEvent(a.player, a.at.getWorld()));
        a.at.setWorld(world("second"));
        f.step(a, 0.6);
        check(f.progress(a, "walk") == 0, "world transition resets measurement");
        f.producer.beforeStateChange(a.player);
        a.context = false;
        f.producer.afterStateChange(a.id);
        a.context = true;
        f.producer.afterStateChange(a.id);
        f.step(a, 0.4);
        check(
                f.progress(a, "walk") == 0,
                "NORMAL/BUILD transition observer clears fractional travel");
        f.producer.beforeJoin(a.player);
        a.context = false;
        f.producer.membershipChanged(a.id);
        a.context = true;
        f.producer.membershipChanged(a.id);
        f.step(a, 0.6);
        check(f.progress(a, "walk") == 0, "Activity enter/leave observer clears fractional travel");
        f.producer.onMount(new EntityMountEvent(a.player, a.player));
        f.producer.onDismount(new EntityDismountEvent(a.player, a.player));
        f.step(a, 0.4);
        check(
                f.progress(a, "walk") == 0,
                "mount/dismount without intervening movement still resets state");
        f.producer.onGlide(new EntityToggleGlideEvent(a.player, true));
        f.step(a, 0.6);
        check(f.progress(a, "walk") == 0, "glide transition resets state");
        f.producer.onQuit(new PlayerQuitEvent(a.player, (net.kyori.adventure.text.Component) null));
        Actor reconnect = f.actor(a.id);
        f.producer.onJoin(
                new PlayerJoinEvent(reconnect.player, net.kyori.adventure.text.Component.empty()));
        f.step(reconnect, 0.4);
        check(
                f.progress(reconnect, "walk") == 0,
                "new player session cannot inherit the old session fraction");
        f.running = false;
        f.producer.clear();
        f.step(reconnect, 1);
        check(
                f.progress(reconnect, "walk") == 0 && f.producer.trackedPlayers() == 0,
                "disabled producer is inert");
        f.running = true;
        f.step(reconnect, 1);
        check(
                f.progress(reconnect, "walk") == 1,
                "re-enable begins fresh and can earn valid progress");
        f.step(reconnect, 0.6);
        f.assign(reconnect, "walk", "sprint", "sneak", "jump", "turn");
        f.step(reconnect, 0.4);
        check(
                f.progress(reconnect, "walk") == 0,
                "same-ID assignment replacement resets fractions, without a progress cache");
        f.domain.registry.replaceAll(f.domain.registry.snapshot());
        f.step(reconnect, 0.6);
        check(
                f.progress(reconnect, "walk") == 0,
                "definition reload resets fractions without changing assigned progress");
        f.producer.onQuit(new PlayerQuitEvent(a.player, (net.kyori.adventure.text.Component) null));
        f.producer.onMove(new PlayerMoveEvent(a.player, a.at.clone(), a.at.clone().add(1, 0, 0)));
        f.step(reconnect, 0.4);
        check(
                f.progress(reconnect, "walk") == 1,
                "stale former-session events cannot replace or erase the reconnect measurement");
    }

    private static Fixture finalFixture() {
        Fixture f = fixture();
        f.define("back", QuestMovementProducer.BACKWARD, 10000, 1);
        f.define("toggle", QuestMovementProducer.SNEAK_TOGGLE, 10000, 1);
        return f;
    }

    private static Actor finalActor(Fixture f) {
        Actor a = f.actor();
        a.input = false;
        a.backward = true;
        f.assign(a, "walk", "sprint", "sneak", "jump", "turn", "back", "toggle");
        return a;
    }

    private static void backwardGeometry() {
        Fixture f = finalFixture();
        Actor a = finalActor(f);
        for (int i = 1; i <= 10; i++) {
            f.move(a, 0, 0, -1, 0);
            check(
                    f.progress(a, "back") == i && f.progress(a, "walk") == i,
                    "each pure backward block credits BACKWARD and WALK exactly once");
        }
        for (int i = 0; i < 3; i++) f.move(a, 0, 0, -.25, 0);
        check(f.progress(a, "back") == 10, "backward fractions do not credit early");
        f.move(a, 0, 0, -.25, 0);
        check(f.progress(a, "back") == 11, "backward has an independent fractional accumulator");
        a.left = true;
        for (int i = 0; i < 5; i++) f.move(a, .6, 0, -.8, 0);
        check(
                f.progress(a, "back") == 15 && f.progress(a, "walk") == 16,
                "five diagonal blocks produce four backward blocks, not five");
        a.left = false;
        a.sneak = true;
        f.move(a, 0, 0, -1, 0);
        check(
                f.progress(a, "back") == 16
                        && f.progress(a, "sneak") == 1
                        && f.progress(a, "walk") == 17
                        && f.progress(a, "toggle") == 0,
                "sneaking backward is a distance subset; it cannot synthesize a toggle");
        a.sneak = false;
        f.rotate(a, 90);
        check(f.progress(a, "back") == 16, "rotation alone is not backward distance");
        f.move(a, 1, 0, 0, 90);
        check(f.progress(a, "back") == 17, "positive yaw uses the correct backward X axis");

        f = finalFixture();
        a = finalActor(f);
        f.move(a, Math.sqrt(.75), 0, -.5, 0);
        f.move(a, Math.sqrt(.75), 0, -.5, 0);
        check(f.progress(a, "back") == 1, "exact cosine .5 direction boundary is eligible");
        for (int i = 0; i < 10; i++) f.move(a, Math.sqrt(1 - .49 * .49), 0, -.49, 0);
        check(
                f.progress(a, "back") == 1,
                "mostly sideways movement below direction threshold is excluded");

        f = finalFixture();
        a = finalActor(f);
        f.move(a, 0, 0, -1, 20);
        check(
                f.progress(a, "back") == 0,
                "turning movement retains only conservative endpoint projection");
        f.move(a, 0, 0, -.1, 20);
        check(
                f.progress(a, "back") == 1,
                "small measured turns accumulate the supported backward fraction");
        long before = f.progress(a, "back");
        f.move(a, 0, 0, -1, 110);
        check(
                f.progress(a, "back") == before,
                "large ambiguous mid-move turn cannot earn backward distance");
        f = finalFixture();
        a = finalActor(f);
        a.at.setYaw(179);
        f.move(a, 0, 0, 1, -179);
        f.move(a, 0, 0, .01, -179);
        check(f.progress(a, "back") == 1, "short yaw wraparound remains measurable");
    }

    private static void backwardRejections() {
        List<Consumer<Actor>> inputs =
                List.of(
                        a -> a.backward = false,
                        a -> a.input = true,
                        a -> {
                            a.left = true;
                            a.right = true;
                        },
                        a -> a.sprint = true,
                        a -> a.sprintInput = true);
        for (var input : inputs) {
            Fixture f = finalFixture();
            Actor a = finalActor(f);
            input.accept(a);
            f.move(a, 0, 0, -1, 0);
            check(
                    f.progress(a, "back") == 0,
                    "missing, opposed or sprint-contradictory backward input is excluded");
        }
        Fixture f = finalFixture();
        Actor a = finalActor(f);
        for (int i = 0; i < 5; i++) f.move(a, 1, 0, 0, 0);
        check(
                f.progress(a, "back") == 0 && f.progress(a, "walk") == 5,
                "pure strafing never becomes backward credit");
        f.move(a, 0, 0, 0, 0);
        check(f.progress(a, "back") == 0, "held backward input without displacement is inert");
        a.sprint = true;
        a.sneak = true;
        f.move(a, 0, 0, -1, 0);
        check(
                f.progress(a, "back") == 0
                        && f.progress(a, "sneak") == 1
                        && f.progress(a, "sprint") == 0,
                "contradictory sprint excludes backward but preserves existing Sneak precedence");
        a.sprint = false;
        a.sneak = false;
        f.move(a, 0, 0, -.6, 0);
        var cancelled = new PlayerMoveEvent(a.player, a.at.clone(), a.at.clone().add(0, 0, -.4));
        cancelled.setCancelled(true);
        f.producer.onMove(cancelled);
        f.move(a, 0, 0, -.4, 0);
        check(f.progress(a, "back") == 0, "cancelled move discards backward remainder");
        var teleport =
                new PlayerTeleportEvent(a.player, a.at.clone(), a.at.clone().add(0, 0, -100));
        f.producer.onMove(teleport);
        f.producer.onTeleport(teleport);
        a.at = teleport.getTo();
        f.move(a, 0, 0, -.6, 0);
        check(f.progress(a, "back") == 0, "teleport cannot join backward fractions");
        f.producer.onVelocity(new PlayerVelocityEvent(a.player, new Vector(0, .4, -.4)));
        for (int i = 0; i < 19; i++) f.move(a, 0, 0, -1, 0);
        check(
                f.progress(a, "back") == 0,
                "nineteen velocity-excluded ticks produce no backward progress");
        f.move(a, 0, 0, -1, 0);
        check(
                f.progress(a, "back") == 1,
                "exact twenty-tick velocity boundary allows a fresh sample");
        var replay = f.move(a, 0, 0, -.5, 0);
        f.producer.onMove(replay);
        f.tick++;
        f.producer.onMove(replay);
        f.move(a, 0, 0, -.5, 0);
        check(f.progress(a, "back") == 2, "replayed endpoints do not duplicate backward fractions");
        f.tick++;
        for (int i = 0; i < 30; i++) {
            var from = a.at.clone();
            a.at = a.at.clone().add(0, 0, -1);
            f.producer.onMove(new PlayerMoveEvent(a.player, from, a.at));
        }
        check(
                f.progress(a, "back") == 3,
                "backward cannot bypass the shared one-block tick budget");
        f.move(a, 0, 0, -100, 0);
        var bad = a.at.clone();
        bad.setZ(Double.NaN);
        f.producer.onMove(new PlayerMoveEvent(a.player, a.at.clone(), bad));
        f.move(a, 0, 0, -.5, 0);
        check(
                f.progress(a, "back") == 3,
                "invalid coordinates and unchecked jumps do not earn distance");
        a.ground = false;
        f.move(a, 0, 0, -1, 0);
        check(f.progress(a, "back") == 3, "airborne backward motion is excluded");
    }

    private static void toggleTransitions() {
        Fixture f = finalFixture();
        Actor a = finalActor(f);
        var start = f.toggle(a, true, false);
        check(
                f.progress(a, "toggle") == 1
                        && f.progress(a, "sneak") == 0
                        && f.progress(a, "walk") == 0,
                "stationary non-sneaking to sneaking activation credits only toggle");
        f.producer.onSneak(start);
        check(f.progress(a, "toggle") == 1, "duplicate already-committed start cannot count");
        f.toggle(a, false, false);
        f.tick += 9;
        f.toggle(a, true, false);
        check(f.progress(a, "toggle") == 1, "activation at nine ticks is below cooldown");
        f.toggle(a, false, false);
        f.tick++;
        f.toggle(a, true, false);
        check(
                f.progress(a, "toggle") == 2,
                "exact ten-tick boundary counts; ignored start added no cooldown");
        f.tick += 10;
        f.toggle(a, false, true);
        f.toggle(a, true, false);
        check(f.progress(a, "toggle") == 2 && a.sneak, "cancelled end does not prove a new start");
        f.toggle(a, false, false);
        f.toggle(a, true, true);
        check(
                f.progress(a, "toggle") == 2 && !a.sneak,
                "cancelled start neither counts nor commits state");
        f.toggle(a, true, false);
        check(f.progress(a, "toggle") == 3, "cancelled events add no cooldown");
        f.toggle(a, false, false);
        f.tick += 10;
        var beforeCommit = new PlayerToggleSneakEvent(a.player, true);
        f.producer.onSneak(beforeCommit);
        f.producer.onSneak(beforeCommit);
        check(
                f.progress(a, "toggle") == 3,
                "duplicate pre-commit callbacks grant no provisional credit");
        a.sneak = true;
        f.confirm();
        check(
                f.progress(a, "toggle") == 4,
                "duplicate delivery confirms only once after native flag commit");
        f.tick += 10;
        f.producer.onSneak(beforeCommit);
        check(
                f.progress(a, "toggle") == 4,
                "duplicate signal cannot be rearmed by elapsed cooldown");
        a.sneak = true;
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        check(f.progress(a, "toggle") == 5, "a real end followed by a later start re-arms once");
        f.move(a, 0, 0, -1, 0);
        check(
                f.progress(a, "sneak") == 1 && f.progress(a, "toggle") == 5,
                "sneak distance and toggle remain independent in the same sample");
        check(a.writes == 0, "new keys never mutate player movement authority");

        f = finalFixture();
        a = finalActor(f);
        a.sneak = true;
        f.toggle(a, true, false);
        check(f.progress(a, "toggle") == 0, "initial already-sneaking state is not an activation");
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        check(f.progress(a, "toggle") == 1, "initial end proves the next transition");
        a.ground = false;
        f.tick += 10;
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        check(
                f.progress(a, "toggle") == 2,
                "normal airborne crouch is a state transition, not ground distance");
    }

    private static void toggleEligibility() {
        List<Consumer<Actor>> modes =
                List.of(
                        a -> a.vehicle = true,
                        a -> a.fly = true,
                        a -> a.glide = true,
                        a -> a.water = true,
                        a -> a.swimming = true,
                        a -> a.climbing = true,
                        a -> a.riptide = true,
                        a -> a.dead = true,
                        a -> a.online = false,
                        a -> a.mode = GameMode.CREATIVE,
                        a -> a.mode = GameMode.SPECTATOR,
                        a -> a.context = false);
        for (var mode : modes) {
            Fixture f = finalFixture();
            Actor a = finalActor(f);
            mode.accept(a);
            f.toggle(a, true, false);
            f.move(a, 0, 0, -1, 0);
            check(
                    f.progress(a, "toggle") == 0
                            && f.progress(a, "back") == 0
                            && f.producer.trackedPlayers() == 0,
                    "both new keys share invalid-mode and lobby/activity eligibility");
        }
        Fixture f = finalFixture();
        Actor a = f.actor();
        f.toggle(a, true, false);
        check(f.producer.trackedPlayers() == 0, "unassigned keys retain no sample");
        f.assign(a, "toggle");
        f.toggle(a, true, false);
        check(
                f.progress(a, "toggle") == 0,
                "assignment while already sneaking cannot claim old start");
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        var loaded = f.domain.players.getPlayer(a.id).orElseThrow();
        f.domain.players.unloadPlayer(a.id);
        f.tick += 10;
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        check(
                loaded.getQuestState().snapshot().get("toggle").progress() == 1
                        && f.producer.trackedPlayers() == 0,
                "unloaded profile receives no toggle");
        f.domain.players.loadPlayer(a.id, "Walker");
        f.assign(a, "toggle");
        Actor reconnect = f.actor(a.id);
        f.producer.onJoin(
                new PlayerJoinEvent(reconnect.player, net.kyori.adventure.text.Component.empty()));
        f.readyBeforeAction(reconnect);
        f.toggle(reconnect, true, false);
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        check(
                f.progress(reconnect, "toggle") == 1,
                "stale former session cannot credit or erase reconnect state");
        f.toggle(reconnect, true, false);
        check(
                f.progress(reconnect, "toggle") == 1,
                "reconnect retains its own duplicate protection");
        f.running = false;
        f.producer.clear();
        f.tick += 10;
        f.toggle(reconnect, false, false);
        f.toggle(reconnect, true, false);
        check(
                f.progress(reconnect, "toggle") == 1 && f.producer.trackedPlayers() == 0,
                "retired owner is inert");
        f.running = true;
        f.toggle(reconnect, true, false);
        check(
                f.progress(reconnect, "toggle") == 1,
                "re-enable does not inherit a fictitious non-sneaking state");
        f.readyBeforeAction(reconnect);
        f.toggle(reconnect, false, false);
        f.toggle(reconnect, true, false);
        check(
                f.progress(reconnect, "toggle") == 2,
                "re-enabled owner accepts a new genuine activation");
    }

    private static void finalMovementLifecycle() {
        Fixture unassignedToggle = fixture();
        Actor walker = unassignedToggle.actor();
        unassignedToggle.assign(walker, "walk");
        unassignedToggle.step(walker, .6);
        unassignedToggle.tick += 20;
        unassignedToggle.toggle(walker, true, false);
        unassignedToggle.tick += 20;
        unassignedToggle.toggle(walker, false, false);
        unassignedToggle.step(walker, .4);
        check(
                unassignedToggle.progress(walker, "walk") == 0,
                "unassigned toggle events cannot keep an idle WALK distance measurement alive");
        List<java.util.function.BiConsumer<Fixture, Actor>> resets =
                List.of(
                        (f, a) -> {
                            var to = a.at.clone().add(100, 0, 0);
                            f.producer.onTeleport(new PlayerTeleportEvent(a.player, a.at, to));
                            a.at = to;
                        },
                        (f, a) ->
                                f.producer.onWorld(
                                        new PlayerChangedWorldEvent(a.player, a.at.getWorld())),
                        (f, a) -> {
                            f.producer.onRespawn(new PlayerRespawnEvent(a.player, a.at, false));
                            f.readyBeforeAction(a);
                        },
                        (f, a) ->
                                f.producer.onDeath(
                                        new org.bukkit.event.entity.PlayerDeathEvent(
                                                a.player,
                                                QuestCompletionFixture.proxy(
                                                        org.bukkit.damage.DamageSource.class,
                                                        (name, args) ->
                                                                QuestCompletionFixture.DEFAULT),
                                                List.of(),
                                                0,
                                                (String) null)),
                        (f, a) ->
                                f.producer.onGameMode(
                                        new PlayerGameModeChangeEvent(
                                                a.player, GameMode.ADVENTURE)),
                        (f, a) -> f.producer.onFlight(new PlayerToggleFlightEvent(a.player, true)),
                        (f, a) -> f.producer.onGlide(new EntityToggleGlideEvent(a.player, true)),
                        (f, a) -> f.producer.onMount(new EntityMountEvent(a.player, a.player)),
                        (f, a) ->
                                f.producer.onDismount(new EntityDismountEvent(a.player, a.player)),
                        (f, a) -> {
                            f.producer.beforeStateChange(a.player);
                            a.context = false;
                            f.producer.afterStateChange(a.id);
                            a.context = true;
                            f.producer.afterStateChange(a.id);
                        },
                        (f, a) -> {
                            f.producer.beforeJoin(a.player);
                            a.context = false;
                            f.producer.membershipChanged(a.id);
                            a.context = true;
                            f.producer.membershipChanged(a.id);
                        });
        for (var reset : resets) {
            Fixture f = finalFixture();
            Actor a = finalActor(f);
            f.move(a, 0, 0, -.6, 0);
            f.toggle(a, true, false);
            reset.accept(f, a);
            check(f.producer.trackedPlayers() == 0, "lifecycle clears all new transient state");
            f.move(a, 0, 0, -.4, 0);
            f.toggle(a, true, false);
            check(
                    f.progress(a, "back") == 0 && f.progress(a, "toggle") == 1,
                    "lifecycle cannot carry backward fraction or recount active sneak");
            f.toggle(a, false, false);
            f.toggle(a, true, false);
            check(
                    f.progress(a, "toggle") == 2,
                    "lifecycle permits a fresh proven activation without old session cooldown");
        }
        Fixture f = finalFixture();
        Actor a = finalActor(f);
        f.move(a, 0, 0, -.6, 0);
        f.toggle(a, true, false);
        f.assign(a, "back", "toggle");
        f.move(a, 0, 0, -.4, 0);
        f.toggle(a, true, false);
        check(
                f.progress(a, "back") == 0 && f.progress(a, "toggle") == 0,
                "assignment replacement discards old fraction and old sneak observation");
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        var catalog = f.domain.registry.snapshot();
        f.domain.registry.replaceAll(catalog);
        f.move(a, 0, 0, -.6, 0);
        f.toggle(a, true, false);
        check(
                f.progress(a, "back") == 0 && f.progress(a, "toggle") == 1,
                "catalog reload invalidates measurements without erasing committed progress");
        f.domain.registry.replaceAll(catalog);
        f.move(a, 0, 0, -.4, 0);
        f.toggle(a, true, false);
        check(
                f.progress(a, "back") == 0 && f.progress(a, "toggle") == 1,
                "rollback generation also starts fresh, never recounting active sneak");
        f.tick += 41;
        f.move(a, 0, 0, -.6, 0);
        check(f.progress(a, "back") == 0, "idle observation gap invalidates backward remainder");
    }

    private enum GuardEvent {
        CANCELLED_MOVE,
        NONFINITE_MOVE,
        WORLD_MISMATCH,
        POSITION_GAP,
        OVERSIZE,
        TICK_BURST,
        CANCELLED_VELOCITY,
        CANCELLED_JUMP,
        INVALID_JUMP,
        INVALID_SNEAK
    }

    private static int guardScenarios, guardFailures;

    private static void velocityGuard() {
        var keys =
                List.of(
                        QuestMovementProducer.WALK,
                        QuestMovementProducer.SPRINT,
                        QuestMovementProducer.SNEAK,
                        QuestMovementProducer.JUMP,
                        QuestMovementProducer.ROTATION,
                        QuestMovementProducer.BACKWARD,
                        QuestMovementProducer.SNEAK_TOGGLE);
        for (int origin : new int[] {100, Integer.MAX_VALUE - 10, Integer.MIN_VALUE + 4})
            for (var key : keys) {
                for (var event : GuardEvent.values())
                    guardCase(
                            key + " / " + event + " / " + origin,
                            () -> guardSequence(key, event, origin));
                guardCase(
                        key + " / effective renewal / " + origin,
                        () -> renewedVelocity(key, origin));
            }
        for (var key : keys)
            for (var event :
                    List.of(
                            GuardEvent.CANCELLED_MOVE,
                            GuardEvent.NONFINITE_MOVE,
                            GuardEvent.CANCELLED_JUMP,
                            GuardEvent.INVALID_JUMP,
                            GuardEvent.INVALID_SNEAK))
                guardCase(
                        key + " / ordinary rejection / " + event,
                        () -> ordinaryRejection(key, event));
        for (var key :
                List.of(
                        QuestMovementProducer.WALK,
                        QuestMovementProducer.SPRINT,
                        QuestMovementProducer.SNEAK,
                        QuestMovementProducer.BACKWARD))
            for (var event : List.of(GuardEvent.CANCELLED_MOVE, GuardEvent.CANCELLED_VELOCITY))
                guardCase(key + " / fraction reset / " + event, () -> guardFractions(key, event));
        for (var key :
                List.of(
                        QuestMovementProducer.WALK,
                        QuestMovementProducer.BACKWARD,
                        QuestMovementProducer.SNEAK_TOGGLE))
            for (var event : List.of(GuardEvent.CANCELLED_MOVE, GuardEvent.CANCELLED_VELOCITY)) {
                guardCase(key + " / reward / " + event, () -> guardReward(key, event, false));
                guardCase(key + " / pending retry / " + event, () -> guardReward(key, event, true));
            }
        guardCase(
                "cancelled velocity without a block",
                QuestMovementHarness::cancelledVelocityWithoutBlock);
        guardCase("stale events and reconnect ownership", QuestMovementHarness::guardSessions);
        guardCase("assignment and catalog changes", QuestMovementHarness::guardGenerations);
        guardCase("legitimate lifecycle cleanup", QuestMovementHarness::guardLifecycle);
        System.out.println(
                "Velocity guard scenarios: " + guardScenarios + ", failed: " + guardFailures);
        if (guardFailures != 0)
            throw new AssertionError("Velocity guard failed scenarios: " + guardFailures);
    }

    private static void guardCase(String name, Runnable body) {
        guardScenarios++;
        try {
            body.run();
        } catch (AssertionError failure) {
            guardFailures++;
            System.out.println("GUARD FAIL " + name + ": " + failure.getMessage());
        }
    }

    private static Fixture guardFixture(QuestProgressKey key, long target) {
        Fixture f = new Fixture();
        f.define("guard", key, target, 7);
        return f;
    }

    private static Actor guardActor(Fixture f) {
        Actor a = f.actor();
        f.assign(a, "guard");
        return a;
    }

    private static void velocity(Fixture f, Actor a, boolean cancelled) {
        var event = new PlayerVelocityEvent(a.player, new Vector(0, .4, -.4));
        event.setCancelled(cancelled);
        f.producer.onVelocity(event);
    }

    private static void moveAtTick(Fixture f, Actor a, double x, double y, double z, float yaw) {
        var from = a.at.clone();
        var to = from.clone().add(x, y, z);
        to.setYaw(yaw);
        f.producer.onMove(new PlayerMoveEvent(a.player, from, to));
        a.at = to;
    }

    private static void observeGuardKey(Fixture f, Actor a, QuestProgressKey key) {
        if (key.equals(QuestMovementProducer.SNEAK_TOGGLE)) {
            f.toggle(a, false, false);
            f.toggle(a, true, false);
        } else if (key.equals(QuestMovementProducer.ROTATION)) {
            moveAtTick(f, a, 0, 0, 0, a.at.getYaw() + 90);
        } else if (key.equals(QuestMovementProducer.JUMP)) {
            var from = a.at.clone();
            var to = from.clone().add(0, .42, 0);
            f.producer.onJump(new PlayerJumpEvent(a.player, from, to));
            a.ground = false;
            f.producer.onMove(new PlayerMoveEvent(a.player, from, to));
            a.at = to;
            a.ground = true;
        } else {
            a.input = !key.equals(QuestMovementProducer.BACKWARD);
            a.backward = !a.input;
            a.sprint = key.equals(QuestMovementProducer.SPRINT);
            a.sneak = key.equals(QuestMovementProducer.SNEAK);
            moveAtTick(f, a, 0, 0, -1, 0);
        }
    }

    private static void guardUnit(Fixture f, Actor a, QuestProgressKey key) {
        int start = f.tick;
        for (int i = 0; i < (key.equals(QuestMovementProducer.ROTATION) ? 4 : 1); i++) {
            f.tick = start + i;
            observeGuardKey(f, a, key);
        }
    }

    private static void guardEvent(Fixture f, Actor a, GuardEvent kind) {
        var from = a.at.clone();
        var to = from.clone().add(0, 0, -.5);
        switch (kind) {
            case CANCELLED_MOVE -> {
                var event = new PlayerMoveEvent(a.player, from, to);
                event.setCancelled(true);
                f.producer.onMove(event);
            }
            case NONFINITE_MOVE -> {
                to.setX(Double.NaN);
                f.producer.onMove(new PlayerMoveEvent(a.player, from, to));
            }
            case WORLD_MISMATCH -> {
                to.setWorld(world("guard-other"));
                f.producer.onMove(new PlayerMoveEvent(a.player, from, to));
            }
            case POSITION_GAP ->
                    f.producer.onMove(
                            new PlayerMoveEvent(
                                    a.player,
                                    from.clone().add(100, 0, 0),
                                    to.clone().add(100, 0, 0)));
            case OVERSIZE ->
                    f.producer.onMove(
                            new PlayerMoveEvent(a.player, from, from.clone().add(0, 0, -20)));
            case TICK_BURST -> {
                for (int i = 0; i < 4; i++) moveAtTick(f, a, 0, 0, -.6, a.at.getYaw());
            }
            case CANCELLED_VELOCITY -> velocity(f, a, true);
            case CANCELLED_JUMP -> {
                var event = new PlayerJumpEvent(a.player, from, to);
                event.setCancelled(true);
                f.producer.onJump(event);
            }
            case INVALID_JUMP -> {
                to.setZ(Double.NaN);
                f.producer.onJump(new PlayerJumpEvent(a.player, from, to));
            }
            case INVALID_SNEAK -> {
                a.at.setX(Double.NaN);
                try {
                    f.producer.onSneak(new PlayerToggleSneakEvent(a.player, true));
                } finally {
                    a.at = from;
                }
            }
        }
    }

    private static void guardSequence(QuestProgressKey key, GuardEvent kind, int origin) {
        Fixture f = guardFixture(key, 100);
        Actor a = guardActor(f);
        f.tick = origin;
        velocity(f, a, false);
        check(
                f.producer.trackedPlayers() == 1,
                "effective velocity owns exactly one blocked sample");
        f.tick = origin + 1;
        guardEvent(f, a, kind);
        f.tick = origin + 2;
        guardUnit(f, a, key);
        check(
                f.progress(a, "guard") == 0,
                "expected progress=0 at t+2 after " + kind + ", actual=" + f.progress(a, "guard"));
        f.tick = origin + 10;
        guardEvent(f, a, kind);
        f.tick = origin + 19;
        guardEvent(f, a, kind);
        observeGuardKey(f, a, key);
        check(
                f.progress(a, "guard") == 0,
                "expected progress=0 at t+19, actual=" + f.progress(a, "guard"));
        f.tick = origin + 20;
        guardUnit(f, a, key);
        check(
                f.progress(a, "guard") == 1,
                "original deadline releases exactly one normal unit; actual="
                        + f.progress(a, "guard"));
        check(a.writes == 0, "guard never mutates movement authority");
    }

    private static void renewedVelocity(QuestProgressKey key, int origin) {
        Fixture f = guardFixture(key, 100);
        Actor a = guardActor(f);
        f.tick = origin;
        velocity(f, a, false);
        f.tick = origin + 1;
        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        f.tick = origin + 10;
        velocity(f, a, false);
        f.tick = origin + 11;
        velocity(f, a, true);
        f.tick = origin + 19;
        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        f.tick = origin + 20;
        guardUnit(f, a, key);
        check(
                f.progress(a, "guard") == 0,
                "new effective velocity requires its own full twenty ticks");
        f.tick = origin + 29;
        observeGuardKey(f, a, key);
        check(f.progress(a, "guard") == 0, "renewed velocity remains blocked at t+29");
        f.tick = origin + 30;
        guardUnit(f, a, key);
        check(
                f.progress(a, "guard") == 1,
                "renewed guard expires at t+30 without cancelled-event extension");
    }

    private static void ordinaryRejection(QuestProgressKey key, GuardEvent kind) {
        Fixture f = guardFixture(key, 100);
        Actor a = guardActor(f);
        f.tick = 100;
        guardEvent(f, a, kind);
        observeGuardKey(f, a, key);
        check(f.progress(a, "guard") == 0, "ordinary rejection blocks only its current tick");
        f.tick = 101;
        guardUnit(f, a, key);
        check(f.progress(a, "guard") == 1, "ordinary rejection is not promoted to twenty ticks");
    }

    private static void guardFractions(QuestProgressKey key, GuardEvent kind) {
        Fixture f = guardFixture(key, 100);
        Actor a = guardActor(f);
        a.input = !key.equals(QuestMovementProducer.BACKWARD);
        a.backward = !a.input;
        a.sprint = key.equals(QuestMovementProducer.SPRINT);
        a.sneak = key.equals(QuestMovementProducer.SNEAK);
        f.tick = 99;
        moveAtTick(f, a, 0, 0, -.6, 0);
        f.tick = 100;
        velocity(f, a, false);
        f.tick = 101;
        guardEvent(f, a, kind);
        f.tick = 102;
        moveAtTick(f, a, 0, 0, -1, 0);
        check(f.progress(a, "guard") == 0, "velocity rejects fresh distance during the guard");
        f.tick = 120;
        moveAtTick(f, a, 0, 0, -.4, 0);
        check(f.progress(a, "guard") == 0, "pre-impulse fractions are not resurrected at expiry");
        f.tick = 121;
        moveAtTick(f, a, 0, 0, -.6, 0);
        check(f.progress(a, "guard") == 1, "only new post-expiry fractions combine");
    }

    private static void cancelledVelocityWithoutBlock() {
        Fixture f = guardFixture(QuestMovementProducer.WALK, 100);
        Actor a = guardActor(f);
        velocity(f, a, true);
        check(f.producer.trackedPlayers() == 0, "cancelled impulse creates no sample or exclusion");
        f.tick = 101;
        moveAtTick(f, a, 0, 0, -.6, 0);
        f.tick = 102;
        velocity(f, a, true);
        moveAtTick(f, a, 0, 0, -.4, 0);
        check(f.progress(a, "guard") == 1, "cancelled impulse preserves valid existing fractions");
        f = guardFixture(QuestMovementProducer.SNEAK_TOGGLE, 100);
        a = guardActor(f);
        f.tick = 100;
        guardUnit(f, a, QuestMovementProducer.SNEAK_TOGGLE);
        f.toggle(a, false, false);
        f.tick = 101;
        velocity(f, a, true);
        f.tick = 102;
        guardUnit(f, a, QuestMovementProducer.SNEAK_TOGGLE);
        check(
                f.progress(a, "guard") == 1,
                "cancelled impulse cannot reset the independent ten-tick toggle cooldown");
        f.tick = 110;
        guardUnit(f, a, QuestMovementProducer.SNEAK_TOGGLE);
        check(
                f.progress(a, "guard") == 2,
                "cancelled impulse does not extend toggle cooldown either");
    }

    private static void guardReward(QuestProgressKey key, GuardEvent kind, boolean pending) {
        Fixture f = guardFixture(key, 1);
        Actor a = guardActor(f);
        int[] hudSignals = {0};
        f.domain.quests.addProgressListener(
                (id, assignments, catalog, increased, result) -> hudSignals[0]++);
        f.tick = 100;
        velocity(f, a, false);
        f.tick = 101;
        guardEvent(f, a, kind);
        f.tick = 102;
        guardUnit(f, a, key);
        check(
                f.progress(a, "guard") == 0
                        && f.status(a, "guard") == QuestStatus.ACTIVE
                        && f.coins(a) == 0
                        && hudSignals[0] == 0
                        && f.feedback.isEmpty(),
                "no premature progress, completion, reward, HUD or chat");
        if (pending) f.domain.economy.setCoins(a.id, Long.MAX_VALUE);
        f.tick = 120;
        guardUnit(f, a, key);
        if (pending) {
            check(
                    f.status(a, "guard") == QuestStatus.REWARD_PENDING && hudSignals[0] == 1,
                    "normal failed grant retains pending contract");
            f.tick = 131;
            guardUnit(f, a, key);
            check(
                    f.status(a, "guard") == QuestStatus.REWARD_PENDING
                            && f.coins(a) == Long.MAX_VALUE,
                    "legitimate later failed reward retry remains pending");
            f.tick = 140;
            velocity(f, a, false);
            f.tick = 141;
            guardEvent(f, a, kind);
            f.domain.economy.setCoins(a.id, 0);
            f.tick = 151;
            guardUnit(f, a, key);
            check(
                    f.status(a, "guard") == QuestStatus.REWARD_PENDING
                            && f.coins(a) == 0
                            && hudSignals[0] == 1,
                    "reached target cannot bypass guard to retry reward early");
            f.tick = 160;
            guardUnit(f, a, key);
        }
        check(
                f.status(a, "guard") == QuestStatus.COMPLETED
                        && f.coins(a) == 7
                        && hudSignals[0] == 1,
                "post-expiry ordinary completion/retry grants one reward and one numeric HUD"
                        + " signal");
        f.tick += 10;
        guardUnit(f, a, key);
        f.domain.quests.retryPendingRewards(a.id);
        check(f.coins(a) == 7 && hudSignals[0] == 1, "completed quest remains idempotent");
    }

    private static void guardSessions() {
        Fixture f = guardFixture(QuestMovementProducer.BACKWARD, 100);
        Actor old = guardActor(f);
        f.tick = 100;
        velocity(f, old, false);
        Actor current = f.actor(old.id);
        f.tick = 101;
        velocity(f, current, false);
        f.tick = 102;
        guardEvent(f, old, GuardEvent.CANCELLED_MOVE);
        velocity(f, old, true);
        guardEvent(f, old, GuardEvent.CANCELLED_JUMP);
        f.toggle(old, true, false);
        f.producer.onQuit(
                new PlayerQuitEvent(old.player, (net.kyori.adventure.text.Component) null));
        check(
                f.producer.trackedPlayers() == 1,
                "stale callbacks cannot release the current session's blocked sample");
        guardUnit(f, current, QuestMovementProducer.BACKWARD);
        check(
                f.progress(current, "guard") == 0,
                "stale player cannot grant current-session progress");
        f.tick = 120;
        guardUnit(f, current, QuestMovementProducer.BACKWARD);
        check(f.progress(current, "guard") == 0, "reconnect owns its newer deadline");
        f.tick = 121;
        guardUnit(f, current, QuestMovementProducer.BACKWARD);
        check(f.progress(current, "guard") == 1, "current session resumes at its own expiry");
        f.tick = 130;
        velocity(f, current, false);
        f.producer.onQuit(
                new PlayerQuitEvent(current.player, (net.kyori.adventure.text.Component) null));
        Actor rejoin = f.actor(old.id);
        f.producer.onJoin(
                new PlayerJoinEvent(rejoin.player, net.kyori.adventure.text.Component.empty()));
        f.tick = 131;
        guardUnit(f, rejoin, QuestMovementProducer.BACKWARD);
        check(
                f.progress(rejoin, "guard") == 2,
                "legitimate logout/rejoin does not inherit prior-session velocity history");
    }

    private static void guardGenerations() {
        Fixture f = guardFixture(QuestMovementProducer.BACKWARD, 100);
        Actor a = guardActor(f);
        f.tick = 100;
        velocity(f, a, false);
        f.tick = 101;
        f.assign(a, "guard");
        guardUnit(f, a, QuestMovementProducer.BACKWARD);
        check(
                f.progress(a, "guard") == 0,
                "assignment change cannot bypass same-session impulse exclusion");
        var catalog = f.domain.registry.snapshot();
        f.domain.registry.replaceAll(catalog);
        f.tick = 110;
        guardUnit(f, a, QuestMovementProducer.BACKWARD);
        f.domain.registry.replaceAll(catalog);
        f.tick = 119;
        guardUnit(f, a, QuestMovementProducer.BACKWARD);
        check(
                f.progress(a, "guard") == 0,
                "reload and rollback keep live physical exclusion without old quest observations");
        f.tick = 120;
        guardUnit(f, a, QuestMovementProducer.BACKWARD);
        check(
                f.progress(a, "guard") == 1,
                "new generation begins valid measurement after deadline");
    }

    private static void guardLifecycle() {
        List<java.util.function.BiConsumer<Fixture, Actor>> transitions =
                List.of(
                        (f, a) ->
                                f.producer.onTeleport(
                                        new PlayerTeleportEvent(
                                                a.player, a.at, a.at.clone().add(100, 0, 0))),
                        (f, a) -> {
                            f.producer.onWorld(
                                    new PlayerChangedWorldEvent(a.player, a.at.getWorld()));
                            a.at.setWorld(world("guard-next-world"));
                        },
                        (f, a) -> {
                            f.producer.beforeStateChange(a.player);
                            a.context = false;
                            f.producer.afterStateChange(a.id);
                            a.context = true;
                            f.producer.afterStateChange(a.id);
                        },
                        (f, a) -> {
                            f.producer.beforeJoin(a.player);
                            a.context = false;
                            f.producer.membershipChanged(a.id);
                            a.context = true;
                            f.producer.membershipChanged(a.id);
                        },
                        (f, a) -> {
                            f.running = false;
                            f.producer.clear();
                            velocity(f, a, false);
                            f.running = true;
                        });
        for (var transition : transitions) {
            Fixture f = guardFixture(QuestMovementProducer.BACKWARD, 100);
            Actor a = guardActor(f);
            f.tick = 100;
            velocity(f, a, false);
            f.tick = 101;
            transition.accept(f, a);
            check(
                    f.producer.trackedPlayers() == 0,
                    "legitimate lifecycle release removes transient exclusion");
            f.tick = 102;
            guardUnit(f, a, QuestMovementProducer.BACKWARD);
            check(f.progress(a, "guard") == 1, "new eligible context/runtime starts fresh");
        }
        Fixture f = guardFixture(QuestMovementProducer.BACKWARD, 100);
        Actor a = guardActor(f);
        f.tick = 100;
        velocity(f, a, false);
        var unloaded = f.domain.players.getPlayer(a.id).orElseThrow();
        f.domain.players.unloadPlayer(a.id);
        f.tick = 101;
        guardUnit(f, a, QuestMovementProducer.BACKWARD);
        check(
                f.producer.trackedPlayers() == 0
                        && unloaded.getQuestState().snapshot().get("guard").progress() == 0,
                "unloaded profile clears sample without credit");
        f.domain.players.loadPlayer(a.id, "Walker");
        f.assign(a, "guard");
        f.tick = 102;
        guardUnit(f, a, QuestMovementProducer.BACKWARD);
        check(f.progress(a, "guard") == 1, "reloaded profile has no stale blocked sample");
    }

    private enum LifecycleEvent {
        FLIGHT_START,
        FLIGHT_END,
        TELEPORT_MOVE,
        TELEPORT_DIRECT,
        TELEPORT_BOTH,
        TELEPORT_WORLD,
        GAMEMODE_CREATIVE,
        GAMEMODE_SPECTATOR,
        GLIDE_START,
        GLIDE_END,
        MOUNT,
        DISMOUNT,
        DEATH,
        LOBBY_BEFORE,
        LOBBY_FINALLY,
        ACTIVITY_BEFORE,
        ACTIVITY_ROLLBACK,
        FLIGHT_NO_CHANGE,
        MODE_NO_CHANGE,
        GLIDE_NO_CHANGE,
        TELEPORT_NO_CHANGE,
        LOCATION_FAILURE
    }

    private static void lifecycleGuard() {
        int beforeCases = guardScenarios, beforeFailures = guardFailures;
        var keys =
                List.of(
                        QuestMovementProducer.WALK,
                        QuestMovementProducer.SPRINT,
                        QuestMovementProducer.SNEAK,
                        QuestMovementProducer.JUMP,
                        QuestMovementProducer.ROTATION,
                        QuestMovementProducer.BACKWARD,
                        QuestMovementProducer.SNEAK_TOGGLE);
        for (int origin : new int[] {100, Integer.MAX_VALUE - 10, Integer.MIN_VALUE + 4})
            for (var key : keys)
                for (var event : LifecycleEvent.values())
                    guardCase(
                            "lifecycle " + key + " / " + event + " / " + origin,
                            () -> cancelledLifecycle(key, event, origin));
        for (var key :
                List.of(
                        QuestMovementProducer.WALK,
                        QuestMovementProducer.BACKWARD,
                        QuestMovementProducer.SNEAK_TOGGLE))
            for (var event : LifecycleEvent.values()) {
                guardCase(
                        "lifecycle reward " + key + " / " + event,
                        () -> lifecycleReward(key, event, false));
                guardCase(
                        "lifecycle pending " + key + " / " + event,
                        () -> lifecycleReward(key, event, true));
            }
        for (var event : LifecycleEvent.values())
            guardCase("toggle cooldown / " + event, () -> lifecycleCooldown(event));
        guardCase(
                "cancelled teleport actual endpoint",
                QuestMovementHarness::cancelledTeleportEndpoint);
        guardCase("cancelled flight then launch", QuestMovementHarness::flightThenVelocity);
        guardCase("successful transitions", QuestMovementHarness::successfulLifecycle);
        guardCase("stale lifecycle", QuestMovementHarness::staleLifecycle);
        guardCase("ineligible impulse context", QuestMovementHarness::ineligibleVelocity);
        int cases = guardScenarios - beforeCases, failed = guardFailures - beforeFailures;
        System.out.println("Lifecycle cancellation scenarios: " + cases + ", failed: " + failed);
        if (failed != 0)
            throw new AssertionError("Lifecycle cancellation failed scenarios: " + failed);
    }

    private static org.bukkit.event.entity.PlayerDeathEvent deathEvent(Actor a) {
        return new org.bukkit.event.entity.PlayerDeathEvent(
                a.player,
                QuestCompletionFixture.proxy(
                        org.bukkit.damage.DamageSource.class,
                        (name, args) -> QuestCompletionFixture.DEFAULT),
                List.of(),
                0,
                (String) null);
    }

    private static void lifecycleEvent(Fixture f, Actor a, LifecycleEvent kind) {
        switch (kind) {
            case FLIGHT_START, FLIGHT_END -> {
                var e = new PlayerToggleFlightEvent(a.player, kind == LifecycleEvent.FLIGHT_START);
                e.setCancelled(true);
                f.producer.onFlight(e);
            }
            case TELEPORT_MOVE, TELEPORT_DIRECT, TELEPORT_BOTH, TELEPORT_WORLD -> {
                var to = a.at.clone().add(100, 0, 0);
                if (kind == LifecycleEvent.TELEPORT_WORLD) to.setWorld(world("cancelled-target"));
                var e = new PlayerTeleportEvent(a.player, a.at.clone(), to);
                e.setCancelled(true);
                if (kind != LifecycleEvent.TELEPORT_DIRECT) f.producer.onMove(e);
                if (kind != LifecycleEvent.TELEPORT_MOVE) f.producer.onTeleport(e);
            }
            case GAMEMODE_CREATIVE, GAMEMODE_SPECTATOR -> {
                var e =
                        new PlayerGameModeChangeEvent(
                                a.player,
                                kind == LifecycleEvent.GAMEMODE_CREATIVE
                                        ? GameMode.CREATIVE
                                        : GameMode.SPECTATOR);
                e.setCancelled(true);
                f.producer.onGameMode(e);
            }
            case GLIDE_START, GLIDE_END -> {
                var e = new EntityToggleGlideEvent(a.player, kind == LifecycleEvent.GLIDE_START);
                e.setCancelled(true);
                f.producer.onGlide(e);
            }
            case MOUNT -> {
                var e = new EntityMountEvent(a.player, a.player);
                e.setCancelled(true);
                f.producer.onMount(e);
            }
            case DISMOUNT -> {
                var e = new EntityDismountEvent(a.player, a.player);
                e.setCancelled(true);
                f.producer.onDismount(e);
            }
            case DEATH -> {
                var e = deathEvent(a);
                e.setCancelled(true);
                f.producer.onDeath(e);
            }
            case LOBBY_BEFORE -> f.producer.beforeStateChange(a.player);
            case LOBBY_FINALLY -> {
                f.producer.beforeStateChange(a.player);
                f.producer.afterStateChange(a.id);
            }
            case ACTIVITY_BEFORE -> f.producer.beforeJoin(a.player);
            case ACTIVITY_ROLLBACK -> {
                f.producer.beforeJoin(a.player);
                f.producer.membershipChanged(a.id);
            }
            case FLIGHT_NO_CHANGE ->
                    f.producer.onFlight(new PlayerToggleFlightEvent(a.player, a.fly));
            case MODE_NO_CHANGE ->
                    f.producer.onGameMode(new PlayerGameModeChangeEvent(a.player, a.mode));
            case GLIDE_NO_CHANGE ->
                    f.producer.onGlide(new EntityToggleGlideEvent(a.player, a.glide));
            case TELEPORT_NO_CHANGE -> {
                var e = new PlayerTeleportEvent(a.player, a.at.clone(), a.at.clone());
                f.producer.onMove(e);
                f.producer.onTeleport(e);
            }
            case LOCATION_FAILURE -> {
                a.locationFailure = true;
                try {
                    f.producer.onSneak(new PlayerToggleSneakEvent(a.player, true));
                } finally {
                    a.locationFailure = false;
                }
            }
        }
    }

    private static void cancelledLifecycle(QuestProgressKey key, LifecycleEvent kind, int origin) {
        Fixture f = guardFixture(key, 100);
        Actor a = guardActor(f);
        a.allowFlight = true;
        f.tick = origin;
        velocity(f, a, false);
        var position = a.at.clone();
        f.tick = origin + 1;
        lifecycleEvent(f, a, kind);
        check(
                a.at.equals(position)
                        && !a.fly
                        && !a.glide
                        && !a.vehicle
                        && a.mode == GameMode.SURVIVAL,
                "cancelled attempt leaves context unchanged");
        f.tick = origin + 2;
        guardUnit(f, a, key);
        check(
                f.progress(a, "guard") == 0,
                "expected lifecycle-blocked progress=0 at t+2, actual=" + f.progress(a, "guard"));
        f.tick = origin + 10;
        lifecycleEvent(f, a, kind);
        f.tick = origin + 19;
        lifecycleEvent(f, a, kind);
        observeGuardKey(f, a, key);
        check(f.progress(a, "guard") == 0, "repeated cancellation cannot release before t+20");
        f.tick = origin + 20;
        guardUnit(f, a, key);
        check(f.progress(a, "guard") == 1, "cancellation cannot postpone original deadline");
    }

    private static void lifecycleReward(
            QuestProgressKey key, LifecycleEvent kind, boolean pending) {
        Fixture f = guardFixture(key, 1);
        Actor a = guardActor(f);
        int[] hud = {0};
        f.domain.quests.addProgressListener((id, assignment, catalog, ids, result) -> hud[0]++);
        f.tick = 100;
        velocity(f, a, false);
        f.tick = 101;
        lifecycleEvent(f, a, kind);
        f.tick = 102;
        guardUnit(f, a, key);
        check(
                f.progress(a, "guard") == 0
                        && f.status(a, "guard") == QuestStatus.ACTIVE
                        && f.coins(a) == 0
                        && hud[0] == 0
                        && f.feedback.isEmpty(),
                "no early progress, completion, coins, HUD or chat");
        if (pending) f.domain.economy.setCoins(a.id, Long.MAX_VALUE);
        f.tick = 120;
        guardUnit(f, a, key);
        if (pending) {
            check(f.status(a, "guard") == QuestStatus.REWARD_PENDING, "overflow remains pending");
            f.tick = 130;
            velocity(f, a, false);
            f.tick = 131;
            lifecycleEvent(f, a, kind);
            f.domain.economy.setCoins(a.id, 0);
            f.tick = 142;
            guardUnit(f, a, key);
            check(
                    f.status(a, "guard") == QuestStatus.REWARD_PENDING
                            && f.coins(a) == 0
                            && hud[0] == 1,
                    "no early retry of reached pending target");
            f.tick = 150;
            guardUnit(f, a, key);
        }
        check(
                f.status(a, "guard") == QuestStatus.COMPLETED && f.coins(a) == 7 && hud[0] == 1,
                "normal once-only completion and numeric HUD signal");
        f.tick += 10;
        guardUnit(f, a, key);
        f.domain.quests.retryPendingRewards(a.id);
        check(f.coins(a) == 7 && hud[0] == 1, "later events remain idempotent");
    }

    private static void lifecycleCooldown(LifecycleEvent kind) {
        Fixture f = guardFixture(QuestMovementProducer.SNEAK_TOGGLE, 100);
        Actor a = guardActor(f);
        f.tick = 100;
        guardUnit(f, a, QuestMovementProducer.SNEAK_TOGGLE);
        f.toggle(a, false, false);
        f.tick = 101;
        lifecycleEvent(f, a, kind);
        f.tick = 102;
        guardUnit(f, a, QuestMovementProducer.SNEAK_TOGGLE);
        check(f.progress(a, "guard") == 1, "cancelled attempt cannot reset toggle cooldown");
        f.tick = 110;
        guardUnit(f, a, QuestMovementProducer.SNEAK_TOGGLE);
        check(f.progress(a, "guard") == 2, "cancelled attempt cannot extend toggle cooldown");
    }

    private static void cancelledTeleportEndpoint() {
        Fixture f = guardFixture(QuestMovementProducer.BACKWARD, 100);
        Actor a = guardActor(f);
        a.input = false;
        a.backward = true;
        f.tick = 100;
        moveAtTick(f, a, 0, 0, -.6, 0);
        var original = a.at.clone();
        f.tick = 101;
        lifecycleEvent(f, a, LifecycleEvent.TELEPORT_BOTH);
        check(
                a.at.equals(original) && f.progress(a, "guard") == 0,
                "cancelled target is not actual travel");
        f.tick = 102;
        moveAtTick(f, a, 0, 0, -.4, 0);
        check(f.progress(a, "guard") == 0, "cancelled teleport discards old partial observation");
        f.tick = 103;
        moveAtTick(f, a, 0, 0, -.6, 0);
        check(f.progress(a, "guard") == 1, "new distance starts from actual source");
    }

    private static void flightThenVelocity() {
        Fixture f = guardFixture(QuestMovementProducer.SNEAK_TOGGLE, 100);
        Actor a = guardActor(f);
        f.tick = 100;
        velocity(f, a, false);
        f.tick = 101;
        lifecycleEvent(f, a, LifecycleEvent.FLIGHT_START);
        f.tick = 110;
        velocity(f, a, false);
        f.tick = 111;
        lifecycleEvent(f, a, LifecycleEvent.FLIGHT_START);
        f.tick = 120;
        guardUnit(f, a, QuestMovementProducer.SNEAK_TOGGLE);
        check(f.progress(a, "guard") == 0, "cancelled flight preserves real launch guard");
        f.tick = 129;
        guardUnit(f, a, QuestMovementProducer.SNEAK_TOGGLE);
        check(f.progress(a, "guard") == 0, "renewed launch remains blocked at t+29");
        f.tick = 130;
        guardUnit(f, a, QuestMovementProducer.SNEAK_TOGGLE);
        check(f.progress(a, "guard") == 1, "real launch expires at t+30");
    }

    private static void successfulLifecycle() {
        List<java.util.function.BiConsumer<Fixture, Actor>> transitions =
                List.of(
                        (f, a) -> {
                            f.producer.onFlight(new PlayerToggleFlightEvent(a.player, true));
                            a.fly = true;
                        },
                        (f, a) -> {
                            a.fly = true;
                            f.producer.onFlight(new PlayerToggleFlightEvent(a.player, false));
                            a.fly = false;
                        },
                        (f, a) -> {
                            f.producer.onGlide(new EntityToggleGlideEvent(a.player, true));
                            a.glide = true;
                        },
                        (f, a) -> {
                            a.glide = true;
                            f.producer.onGlide(new EntityToggleGlideEvent(a.player, false));
                            a.glide = false;
                        },
                        (f, a) -> {
                            f.producer.onGameMode(
                                    new PlayerGameModeChangeEvent(a.player, GameMode.CREATIVE));
                            a.mode = GameMode.CREATIVE;
                        },
                        (f, a) -> {
                            f.producer.onMount(new EntityMountEvent(a.player, a.player));
                            a.vehicle = true;
                        },
                        (f, a) -> {
                            a.vehicle = true;
                            f.producer.onDismount(new EntityDismountEvent(a.player, a.player));
                            a.vehicle = false;
                        },
                        (f, a) -> {
                            f.producer.onDeath(deathEvent(a));
                            a.dead = true;
                        },
                        (f, a) -> {
                            var to = a.at.clone().add(100, 0, 0);
                            var e = new PlayerTeleportEvent(a.player, a.at.clone(), to);
                            f.producer.onMove(e);
                            f.producer.onTeleport(e);
                            a.at = to;
                        },
                        (f, a) -> {
                            var to = a.at.clone();
                            to.setWorld(world("successful-target"));
                            f.producer.onTeleport(
                                    new PlayerTeleportEvent(a.player, a.at.clone(), to));
                            a.at = to;
                        },
                        (f, a) -> {
                            f.producer.beforeStateChange(a.player);
                            a.context = false;
                            f.producer.afterStateChange(a.id);
                        },
                        (f, a) -> {
                            f.producer.beforeJoin(a.player);
                            a.context = false;
                            f.producer.membershipChanged(a.id);
                        });
        for (var transition : transitions) {
            Fixture f = guardFixture(QuestMovementProducer.BACKWARD, 100);
            Actor a = guardActor(f);
            f.tick = 100;
            velocity(f, a, false);
            f.tick = 101;
            transition.accept(f, a);
            check(f.producer.trackedPlayers() == 0, "confirmed transition releases old sample");
            a.fly = a.glide = a.vehicle = a.dead = false;
            a.mode = GameMode.SURVIVAL;
            a.context = true;
            f.tick = 102;
            guardUnit(f, a, QuestMovementProducer.BACKWARD);
            check(f.progress(a, "guard") == 1, "new eligible context has no old guard");
        }
    }

    private static void staleLifecycle() {
        for (var kind : LifecycleEvent.values()) {
            Fixture f = guardFixture(QuestMovementProducer.SNEAK_TOGGLE, 100);
            Actor old = guardActor(f);
            Actor current = f.actor(old.id);
            f.tick = 100;
            velocity(f, current, false);
            f.tick = 101;
            lifecycleEvent(f, old, kind);
            f.tick = 102;
            guardUnit(f, current, QuestMovementProducer.SNEAK_TOGGLE);
            check(
                    f.progress(current, "guard") == 0,
                    "old attempt cannot release current session guard");
        }
    }

    private static void ineligibleVelocity() {
        for (var mode :
                List.<Consumer<Actor>>of(
                        a -> a.fly = true,
                        a -> a.glide = true,
                        a -> a.vehicle = true,
                        a -> a.mode = GameMode.CREATIVE,
                        a -> a.context = false)) {
            Fixture f = guardFixture(QuestMovementProducer.BACKWARD, 100);
            Actor a = guardActor(f);
            mode.accept(a);
            f.tick = 100;
            velocity(f, a, false);
            check(
                    f.producer.trackedPlayers() == 0,
                    "ineligible context creates no physical history");
            a.fly = a.glide = a.vehicle = false;
            a.mode = GameMode.SURVIVAL;
            a.context = true;
            f.producer.afterStateChange(a.id);
            f.tick = 101;
            guardUnit(f, a, QuestMovementProducer.BACKWARD);
            check(f.progress(a, "guard") == 1, "later valid context starts normally");
        }
    }

    private static void sneakReconciliation() {
        int beforeCases = guardScenarios, beforeFailures = guardFailures;
        for (int origin : new int[] {100, Integer.MAX_VALUE - 4, Integer.MIN_VALUE + 4}) {
            for (boolean pending : new boolean[] {false, true})
                guardCase(
                        "reconcile target-two / " + origin + " / " + pending,
                        () -> reconcileTargetTwo(origin, pending));
            for (int elapsed : new int[] {9, 10, 11})
                for (boolean cancelledEnd : new boolean[] {false, true})
                    guardCase(
                            "reconcile cooldown / "
                                    + origin
                                    + " / "
                                    + elapsed
                                    + " / "
                                    + cancelledEnd,
                            () -> reconcileCooldown(origin, elapsed, cancelledEnd));
            for (var rejection :
                    List.of(
                            GuardEvent.CANCELLED_MOVE,
                            GuardEvent.NONFINITE_MOVE,
                            GuardEvent.WORLD_MISMATCH,
                            GuardEvent.OVERSIZE,
                            GuardEvent.CANCELLED_JUMP,
                            GuardEvent.INVALID_JUMP,
                            GuardEvent.INVALID_SNEAK))
                guardCase(
                        "reconcile accepted end / " + origin + " / " + rejection,
                        () -> reconcileRejectedEnd(origin, rejection));
            guardCase("reconcile velocity / " + origin, () -> reconcileVelocity(origin));
        }
        for (var key :
                List.of(
                        QuestMovementProducer.WALK,
                        QuestMovementProducer.SPRINT,
                        QuestMovementProducer.SNEAK,
                        QuestMovementProducer.JUMP,
                        QuestMovementProducer.ROTATION,
                        QuestMovementProducer.BACKWARD,
                        QuestMovementProducer.SNEAK_TOGGLE))
            guardCase("reconcile shared gate / " + key, () -> reconcileOtherKey(key));
        for (String reset :
                List.of(
                        "assignment",
                        "catalog",
                        "rollback",
                        "daily",
                        "rejoin",
                        "logout",
                        "teleport",
                        "build",
                        "activity",
                        "flight",
                        "glide",
                        "runtime"))
            guardCase("reconcile ownership / " + reset, () -> reconcileReset(reset));
        guardCase("reconcile duplicates and ordering", QuestMovementHarness::reconcileDuplicates);
        guardCase(
                "reconcile reentrant progress gate", QuestMovementHarness::reconcileReentrantGate);
        guardCase("reconcile accepted end with read failure", () -> reconcileInvalidEnd(true));
        guardCase("reconcile accepted end with invalid position", () -> reconcileInvalidEnd(false));
        int cases = guardScenarios - beforeCases, failed = guardFailures - beforeFailures;
        System.out.println("Sneak reconciliation scenarios: " + cases + ", failed: " + failed);
        if (failed != 0)
            throw new AssertionError("Sneak reconciliation failed scenarios: " + failed);
    }

    private static Fixture reconcileFixture(long target) {
        Fixture f = guardFixture(QuestMovementProducer.SNEAK_TOGGLE, target);
        f.define("walk", QuestMovementProducer.WALK, 10000, 1);
        return f;
    }

    private static Actor reconcileActor(Fixture f) {
        Actor a = f.actor();
        f.assign(a, "guard", "walk");
        return a;
    }

    private static void reconcileTargetTwo(int origin, boolean pending) {
        Fixture f = reconcileFixture(2);
        Actor a = reconcileActor(f);
        int[] hud = {0};
        f.domain.quests.addProgressListener((id, assignment, catalog, ids, result) -> hud[0]++);
        f.tick = origin;
        f.toggle(a, true, false);
        check(
                f.progress(a, "guard") == 1 && hud[0] == 1,
                "first actual start reports progress one");
        f.tick = origin + 10;
        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        f.toggle(a, false, false);
        check(
                !a.sneak && f.progress(a, "guard") == 1 && hud[0] == 1 && f.coins(a) == 0,
                "accepted end during rejection grants nothing");
        if (pending) f.domain.economy.setCoins(a.id, Long.MAX_VALUE);
        f.tick = origin + 11;
        var credited = f.toggle(a, true, false);
        check(
                f.progress(a, "guard") == 2,
                "expected target-two progress=2 after accepted blocked end, actual="
                        + f.progress(a, "guard"));
        check(
                hud[0] == 2 && f.feedback.size() == 1,
                "exactly two numeric HUD signals and one completion/pending message");
        check(
                f.status(a, "guard")
                        == (pending ? QuestStatus.REWARD_PENDING : QuestStatus.COMPLETED),
                "real reward boundary chooses completed or pending");
        f.tick += 10;
        f.producer.onSneak(credited);
        check(hud[0] == 2 && f.feedback.size() == 1, "replay never retries or credits");
        if (pending) {
            f.domain.quests.retryPendingRewards(a.id);
            check(
                    f.status(a, "guard") == QuestStatus.REWARD_PENDING
                            && f.coins(a) == Long.MAX_VALUE,
                    "failed grant retry preserves pending");
            f.domain.economy.setCoins(a.id, 0);
            f.domain.quests.retryPendingRewards(a.id);
        }
        check(
                f.status(a, "guard") == QuestStatus.COMPLETED && f.coins(a) == 7 && hud[0] == 2,
                "one configured payout; retry has no numeric HUD signal");
        f.domain.quests.retryPendingRewards(a.id);
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        check(f.coins(a) == 7 && hud[0] == 2, "completed assignment stays idempotent");
    }

    private static void reconcileCooldown(int origin, int elapsed, boolean cancelledEnd) {
        Fixture f = reconcileFixture(100);
        Actor a = reconcileActor(f);
        f.tick = origin;
        f.toggle(a, true, false);
        f.tick = origin + elapsed - 1;
        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        f.toggle(a, false, cancelledEnd);
        f.tick = origin + elapsed;
        f.toggle(a, true, false);
        long expected = !cancelledEnd && elapsed >= 10 ? 2 : 1;
        check(
                f.progress(a, "guard") == expected,
                "cooldown/end cancellation expected="
                        + expected
                        + ", actual="
                        + f.progress(a, "guard"));
        f.toggle(a, false, false);
        f.tick = origin + (expected == 2 ? elapsed + 10 : 10);
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == expected + 1, "only credited starts advance the cooldown");
    }

    private static void reconcileRejectedEnd(int origin, GuardEvent rejection) {
        Fixture f = reconcileFixture(100);
        Actor a = reconcileActor(f);
        f.tick = origin;
        f.toggle(a, true, false);
        f.tick = origin + 10;
        guardEvent(f, a, rejection);
        f.toggle(a, false, false);
        f.tick = origin + 11;
        f.toggle(a, true, false);
        check(
                f.progress(a, "guard") == 2,
                "rejected observation cannot hide an accepted end: "
                        + rejection
                        + ", actual="
                        + f.progress(a, "guard"));
    }

    private static void reconcileVelocity(int origin) {
        Fixture f = reconcileFixture(100);
        Actor a = reconcileActor(f);
        f.tick = origin;
        f.toggle(a, true, false);
        f.tick = origin + 5;
        velocity(f, a, false);
        f.tick = origin + 10;
        f.toggle(a, false, false);
        f.tick = origin + 15;
        var blockedStart = f.toggle(a, true, false);
        f.tick = origin + 24;
        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        f.toggle(a, false, true);
        check(
                f.progress(a, "guard") == 1 && f.coins(a) == 0,
                "no progress or reward during twenty-tick guard");
        f.tick = origin + 25;
        f.producer.onSneak(blockedStart);
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 1, "expiry never retroactively counts a blocked start");
        f.tick = origin + 26;
        f.toggle(a, false, false);
        f.tick = origin + 27;
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 2, "fresh accepted start after expiry is eligible");
        f.tick = origin + 36;
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        check(
                f.progress(a, "guard") == 2,
                "nine ticks after credited post-guard start is too early");
        f.tick = origin + 37;
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 3, "exact ten ticks after credited start is eligible");
    }

    private static void reconcileOtherKey(QuestProgressKey key) {
        Fixture f = guardFixture(key, 100);
        Actor a = guardActor(f);
        if (!key.equals(QuestMovementProducer.SNEAK_TOGGLE)) {
            f.define("observation", QuestMovementProducer.SNEAK_TOGGLE, 10000, 1);
            f.assign(a, "guard", "observation");
        }
        f.tick = 100;
        velocity(f, a, false);
        f.tick = 101;
        f.toggle(a, true, false);
        f.tick = 102;
        f.toggle(a, false, false);
        f.tick = 119;
        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        f.toggle(a, true, false);
        observeGuardKey(f, a, key);
        check(f.progress(a, "guard") == 0, "state observation never opens shared gate: " + key);
        f.tick = 124;
        f.toggle(a, false, false);
        f.tick = 125;
        guardUnit(f, a, key);
        check(f.progress(a, "guard") == 1, "only fresh post-guard observations count: " + key);
    }

    private static void reconcileReset(String kind) {
        Fixture f = reconcileFixture(100);
        Actor a = reconcileActor(f);
        f.tick = 100;
        f.toggle(a, true, false);
        f.tick = 110;
        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        f.toggle(a, false, false);
        switch (kind) {
            case "assignment" -> f.assign(a, "guard", "walk");
            case "catalog" -> f.domain.registry.replaceAll(f.domain.registry.snapshot());
            case "rollback" -> {
                var catalog = f.domain.registry.snapshot();
                f.domain.registry.replaceAll(catalog);
                f.domain.registry.replaceAll(catalog);
            }
            case "daily" -> {
                var day = java.time.Instant.parse("2026-11-01T12:00:00Z");
                var zone = java.time.ZoneId.of("UTC");
                var time = java.time.LocalTime.MIDNIGHT;
                f.domain
                        .players
                        .getPlayer(a.id)
                        .orElseThrow()
                        .getDailyQuestState()
                        .setCycleId(
                                new dev.vapee.core.quest.daily.DailyQuestCycleResolver()
                                        .resolve(day, zone, time));
                var daily =
                        new dev.vapee.core.quest.daily.DailyQuestService(
                                f.domain.players,
                                f.domain.quests,
                                f.domain.registry,
                                () ->
                                        new dev.vapee.core.quest.daily.DailyQuestConfig.State(
                                                true,
                                                2,
                                                time,
                                                "UTC",
                                                zone,
                                                f.domain.registry.snapshot()),
                                f.domain.logger);
                check(
                        daily.syncPlayer(a.id, day.plusSeconds(86400))
                                == dev.vapee.core.quest.daily.DailyQuestSyncResult.ROTATED,
                        "real daily reset replaces assignments");
            }
            case "rejoin", "logout" -> {
                Actor old = a;
                if (kind.equals("logout")) {
                    f.producer.onQuit(
                            new PlayerQuitEvent(
                                    old.player, net.kyori.adventure.text.Component.empty()));
                    old.online = false;
                    f.online.remove(old.id);
                }
                a = f.actor(old.id);
                f.producer.onJoin(
                        new PlayerJoinEvent(a.player, net.kyori.adventure.text.Component.empty()));
                f.readyBeforeAction(a);
                f.toggle(old, true, false);
                f.toggle(old, false, false);
            }
            case "teleport" ->
                    f.producer.onTeleport(
                            new PlayerTeleportEvent(a.player, a.at, a.at.clone().add(10, 0, 0)));
            case "build", "activity" -> {
                if (kind.equals("build")) f.producer.beforeStateChange(a.player);
                else f.producer.beforeJoin(a.player);
                a.context = false;
                if (kind.equals("build")) f.producer.afterStateChange(a.id);
                else f.producer.membershipChanged(a.id);
                a.context = true;
                if (kind.equals("build")) f.producer.afterStateChange(a.id);
                else f.producer.membershipChanged(a.id);
            }
            case "flight" -> {
                f.producer.onFlight(new PlayerToggleFlightEvent(a.player, true));
                a.fly = true;
                f.producer.onFlight(new PlayerToggleFlightEvent(a.player, false));
                a.fly = false;
            }
            case "glide" -> {
                f.producer.onGlide(new EntityToggleGlideEvent(a.player, true));
                a.glide = true;
                f.producer.onGlide(new EntityToggleGlideEvent(a.player, false));
                a.glide = false;
            }
            case "runtime" -> {
                f.running = false;
                f.producer.clear();
                f.running = true;
                f.readyBeforeAction(a);
            }
            default -> throw new AssertionError(kind);
        }
        f.tick = 111;
        f.toggle(a, true, false);
        check(
                f.progress(a, "guard")
                        == (kind.equals("assignment") || kind.equals("daily") ? 1 : 2),
                "no stale toggle observation after " + kind);
    }

    private static void reconcileDuplicates() {
        Fixture f = reconcileFixture(100);
        Actor a = reconcileActor(f);
        var start = f.toggle(a, true, false);
        f.toggle(a, true, false);
        f.toggle(a, false, false);
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        check(
                f.progress(a, "guard") == 1,
                "same-tick start/end/start and duplicates earn only once");
        f.tick = 110;
        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        f.toggle(a, false, true);
        f.tick = 111;
        f.producer.onSneak(start);
        check(f.progress(a, "guard") == 1, "cancelled end cannot authorize another start");
        f.toggle(a, false, false);
        f.toggle(a, true, true);
        check(f.progress(a, "guard") == 1 && !a.sneak, "cancelled start cannot commit state");
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 2, "accepted start after actual end is counted once");
    }

    private static void reconcileInvalidEnd(boolean failure) {
        Fixture f = reconcileFixture(100);
        Actor a = reconcileActor(f);
        f.toggle(a, true, false);
        f.tick = 110;
        var position = a.at.clone();
        if (failure) a.locationFailure = true;
        else a.at.setX(Double.NaN);
        try {
            f.toggle(a, false, false);
        } finally {
            a.locationFailure = false;
            a.at = position;
        }
        f.tick = 111;
        f.toggle(a, true, false);
        check(
                f.progress(a, "guard") == 2,
                "accepted boolean end is retained despite unusable positional observation; actual="
                        + f.progress(a, "guard"));
    }

    private static void reconcileReentrantGate() {
        Fixture f = fixture();
        Actor a = f.actor();
        f.assign(a, "walk", "sprint");
        a.sprint = true;
        boolean[] once = {true};
        f.domain.quests.addProgressListener(
                (id, assignment, catalog, ids, result) -> {
                    if (once[0] && ids.contains("walk")) {
                        once[0] = false;
                        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
                    }
                });
        moveAtTick(f, a, 0, 0, -1, 0);
        check(
                f.progress(a, "walk") == 1 && f.progress(a, "sprint") == 0,
                "a synchronous observer rejection closes remaining reports in the same move");
        f.tick++;
        moveAtTick(f, a, 0, 0, -1, 0);
        check(
                f.progress(a, "walk") == 2 && f.progress(a, "sprint") == 1,
                "later fresh move resumes after the original one-tick exclusion");
    }

    private static void confirmedSneak() {
        int beforeCases = guardScenarios, beforeFailures = guardFailures;
        for (int origin : new int[] {100, Integer.MAX_VALUE - 4, Integer.MIN_VALUE + 4}) {
            for (boolean respawn : new boolean[] {false, true})
                guardCase(
                        "confirmed loading / " + origin + " / " + respawn,
                        () -> confirmationLoading(origin, respawn));
            for (int elapsed : new int[] {9, 10, 11})
                guardCase(
                        "confirmed cooldown / " + origin + " / " + elapsed,
                        () -> confirmationCooldown(origin, elapsed));
            for (String order :
                    List.of(
                            "end",
                            "restart",
                            "duplicate",
                            "cancel-start",
                            "cancel-end",
                            "supersede"))
                guardCase(
                        "confirmed ordering / " + origin + " / " + order,
                        () -> confirmationOrder(origin, order));
            guardCase("confirmed guarded state / " + origin, () -> confirmationGuard(origin));
            guardCase(
                    "confirmed guard expiry boundary / " + origin,
                    () -> confirmationGuardBoundary(origin));
            guardCase("confirmed target-two / " + origin, () -> confirmationTargetTwo(origin));
        }
        for (String invalidation :
                List.of(
                        "assignment",
                        "catalog",
                        "rollback",
                        "logout",
                        "respawn",
                        "teleport",
                        "flight",
                        "glide",
                        "world",
                        "death",
                        "build",
                        "activity",
                        "velocity",
                        "reject",
                        "runtime",
                        "unloaded"))
            guardCase(
                    "confirmed invalidation / " + invalidation,
                    () -> confirmationInvalidation(invalidation));
        for (String failure :
                List.of("before", "after", "cancelled", "expired", "clear-retry", "unknown"))
            guardCase("confirmed scheduling / " + failure, () -> confirmationFailure(failure));
        guardCase("confirmed bounded shared batch", QuestMovementHarness::confirmationBatch);
        guardCase(
                "confirmed old callback after new batch",
                QuestMovementHarness::confirmationOldCallback);
        guardCase("confirmed lost task recovery", QuestMovementHarness::confirmationLostTask);
        int cases = guardScenarios - beforeCases, failed = guardFailures - beforeFailures;
        System.out.println("Confirmed sneak scenarios: " + cases + ", failed: " + failed);
        if (failed != 0) throw new AssertionError("Confirmed sneak failed scenarios: " + failed);
    }

    private static Fixture confirmationFixture(long target) {
        Fixture f = reconcileFixture(target);
        f.autoConfirm = false;
        return f;
    }

    private static void confirmationLoading(int origin, boolean respawn) {
        Fixture f = confirmationFixture(1);
        Actor a = reconcileActor(f);
        int[] hud = {0};
        f.domain.quests.addProgressListener((id, assignments, catalog, ids, result) -> hud[0]++);
        if (respawn) f.producer.onRespawn(new PlayerRespawnEvent(a.player, a.at, false));
        else
            f.producer.onJoin(
                    new PlayerJoinEvent(a.player, net.kyori.adventure.text.Component.empty()));
        f.tick = origin;
        f.requestToggle(a, true, false, false);
        check(
                !a.sneak
                        && f.progress(a, "guard") == 0
                        && f.coins(a) == 0
                        && hud[0] == 0
                        && f.feedback.isEmpty(),
                "unapplied uncancelled loading start expected progress=0, actual="
                        + f.progress(a, "guard"));
        f.tick = origin + 1;
        f.confirm();
        check(
                f.progress(a, "guard") == 0 && f.status(a, "guard") == QuestStatus.ACTIVE,
                "false physical flag rejects original intent");
        f.tick = origin + 2;
        f.confirm();
        check(
                f.progress(a, "guard") == 0 && hud[0] == 0,
                "later tick/loading alone never recounts");
        f.loaded(a, false);
        f.tick = origin + 3;
        f.toggle(a, true, false);
        check(
                a.sneak && f.progress(a, "guard") == 0,
                "real start remains provisional before confirmation");
        f.tick = origin + 4;
        f.confirm();
        check(
                f.progress(a, "guard") == 1
                        && f.status(a, "guard") == QuestStatus.COMPLETED
                        && f.coins(a) == 7
                        && hud[0] == 1
                        && f.feedback.size() == 1,
                "new real action confirms once through services/HUD boundary");
        for (var task : List.copyOf(f.confirmations)) task.action.run();
        f.domain.quests.retryPendingRewards(a.id);
        check(f.coins(a) == 7 && hud[0] == 1, "old callback/retry cannot duplicate reward");
    }

    private static void confirmationCooldown(int origin, int elapsed) {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.tick = origin;
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 0, "no pre-commit progress");
        f.tick = origin + 1;
        f.confirm();
        check(f.progress(a, "guard") == 1, "physical start confirms at next tick");
        f.tick = origin + elapsed - 1;
        f.toggle(a, false, false);
        f.confirm();
        f.tick = origin + elapsed;
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(
                f.progress(a, "guard") == (elapsed >= 10 ? 2 : 1),
                "credited cooldown uses original action tick, not confirmation tick: " + elapsed);
    }

    private static void confirmationOrder(int origin, String order) {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.tick = origin;
        var start =
                order.equals("supersede")
                        ? f.requestToggle(a, true, false, false)
                        : f.toggle(a, true, false);
        check(f.progress(a, "guard") == 0, "start pending before competing action");
        switch (order) {
            case "end" -> f.toggle(a, false, false);
            case "restart" -> {
                f.toggle(a, false, false);
                f.toggle(a, true, false);
            }
            case "duplicate" -> {
                f.producer.onSneak(start);
                f.producer.onSneak(start);
            }
            case "cancel-start" -> f.toggle(a, true, true);
            case "cancel-end" -> f.toggle(a, false, true);
            case "supersede" -> {
                // The original request was never applied; a newer request owns the final flag.
                f.tick++;
                f.toggle(a, true, false);
            }
            default -> throw new AssertionError(order);
        }
        f.tick = origin + 1;
        f.confirm();
        check(
                f.progress(a, "guard") == (order.equals("end") ? 0 : 1),
                "only the current unambiguous action may confirm: " + order);
        f.confirm();
        check(f.progress(a, "guard") <= 1, "batch is once-only");
        if (order.equals("supersede")) {
            f.tick = origin + 10;
            f.toggle(a, false, false);
            f.confirm();
            f.toggle(a, true, false);
            f.confirm();
            check(
                    f.progress(a, "guard") == 1,
                    "new action owns cooldown; older request cannot backdate it");
        }
    }

    private static void confirmationGuard(int origin) {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.tick = origin;
        velocity(f, a, false);
        f.tick = origin + 10;
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(
                a.sneak && f.progress(a, "guard") == 0,
                "blocked committed start observes state without progress");
        f.tick = origin + 19;
        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        f.tick = origin + 20;
        f.producer.onSneak(new PlayerToggleSneakEvent(a.player, true));
        f.confirm();
        check(f.progress(a, "guard") == 0, "guard expiry cannot confirm an old blocked start");
        f.tick = origin + 21;
        f.toggle(a, false, false);
        f.confirm();
        f.tick = origin + 22;
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 1, "new post-guard end/start is normally eligible");
    }

    private static void confirmationTargetTwo(int origin) {
        Fixture f = confirmationFixture(2);
        Actor a = reconcileActor(f);
        f.tick = origin;
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        f.tick = origin + 10;
        guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        f.toggle(a, false, false);
        f.confirm();
        f.tick = origin + 11;
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 1, "second real start awaits confirmation");
        f.tick++;
        f.confirm();
        check(
                f.progress(a, "guard") == 2 && f.coins(a) == 7,
                "08E.3 target-two contract survives deferred confirmation");
    }

    private static void confirmationGuardBoundary(int origin) {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.tick = origin;
        velocity(f, a, false);
        f.tick = origin + 19;
        f.toggle(a, true, false);
        f.tick = origin + 20;
        f.confirm();
        check(
                f.progress(a, "guard") == 0,
                "excluded origin cannot earn when guard expires at confirmation");
        f.tick++;
        f.toggle(a, false, false);
        f.confirm();
        f.tick++;
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 1, "fresh post-boundary start remains eligible");
    }

    private static void confirmationInvalidation(String kind) {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 0, "pending before invalidation");
        switch (kind) {
            case "assignment" -> f.assign(a, "guard", "walk");
            case "catalog" -> f.domain.registry.replaceAll(f.domain.registry.snapshot());
            case "rollback" -> {
                var c = f.domain.registry.snapshot();
                f.domain.registry.replaceAll(c);
                f.domain.registry.replaceAll(c);
            }
            case "logout" -> {
                f.producer.onQuit(
                        new PlayerQuitEvent(a.player, net.kyori.adventure.text.Component.empty()));
                f.actor(a.id);
            }
            case "respawn" -> f.producer.onRespawn(new PlayerRespawnEvent(a.player, a.at, false));
            case "teleport" ->
                    f.producer.onTeleport(
                            new PlayerTeleportEvent(a.player, a.at, a.at.clone().add(10, 0, 0)));
            case "flight" -> f.producer.onFlight(new PlayerToggleFlightEvent(a.player, true));
            case "glide" -> f.producer.onGlide(new EntityToggleGlideEvent(a.player, true));
            case "world" ->
                    f.producer.onWorld(new PlayerChangedWorldEvent(a.player, a.at.getWorld()));
            case "death" -> f.producer.onDeath(deathEvent(a));
            case "build", "activity" -> {
                a.context = false;
                f.producer.afterStateChange(a.id);
            }
            case "velocity" -> velocity(f, a, false);
            case "reject" -> guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
            case "runtime" -> {
                f.running = false;
                f.producer.clear();
            }
            case "unloaded" -> f.domain.players.unloadPlayer(a.id);
            default -> throw new AssertionError(kind);
        }
        f.tick++;
        f.confirm();
        check(
                f.domain.players.getPlayer(a.id).isEmpty() || f.progress(a, "guard") == 0,
                "old intent cannot cross " + kind);
    }

    private static void confirmationFailure(String kind) {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.refuseConfirmation = kind.equals("before");
        f.failConfirmationAfterEffect = kind.equals("after");
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 0, "scheduler cannot grant synchronously");
        if (kind.equals("cancelled")) f.confirmations.getFirst().cancel();
        if (kind.equals("unknown")) f.confirmations.getFirst().unknownStatus = true;
        if (kind.equals("clear-retry")) {
            f.confirmations.getFirst().refuseCancel = true;
            try {
                f.producer.clear();
                throw new AssertionError("clear accepted refused cancellation");
            } catch (IllegalStateException expected) {
                check(f.progress(a, "guard") == 0, "failed clear revokes progress");
            }
            f.confirmations.getFirst().refuseCancel = false;
            f.producer.clear();
        }
        f.tick += kind.equals("expired") ? 2 : 1;
        f.confirm();
        if (kind.equals("unknown")) {
            f.confirmations.getFirst().unknownStatus = false;
            f.producer.clear();
        }
        for (var task : List.copyOf(f.confirmations)) task.action.run();
        check(
                f.progress(a, "guard") == 0,
                "missing/failed/cancelled/expired callback cannot grant: " + kind);
    }

    private static void confirmationBatch() {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f), b = reconcileActor(f);
        for (int i = 0; i < 1000; i++) {
            f.producer.onSneak(new PlayerToggleSneakEvent(a.player, true));
            f.producer.onSneak(new PlayerToggleSneakEvent(b.player, true));
        }
        check(
                f.confirmationSchedules == 1
                        && f.progress(a, "guard") == 0
                        && f.progress(b, "guard") == 0,
                "burst/session candidates share exactly one bounded one-shot");
        a.sneak = b.sneak = true;
        f.tick++;
        f.confirm();
        check(
                f.progress(a, "guard") == 1 && f.progress(b, "guard") == 1,
                "each session receives at most one confirmed credit");
    }

    private static void confirmationOldCallback() {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.failConfirmationAfterEffect = true;
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 0, "failed scheduling cannot grant before a callback");
        var old = f.confirmations.getFirst();
        f.failConfirmationAfterEffect = false;
        f.tick++;
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        old.action.run();
        check(
                f.progress(a, "guard") == 0,
                "failed old batch cannot drain a new provisional action");
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 1, "new batch alone confirms current action once");
    }

    private static void confirmationLostTask() {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 0, "lost-task candidate starts without provisional credit");
        var lost = f.confirmations.getFirst();
        f.tick += 2;
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        check(
                lost.cancelled && f.progress(a, "guard") == 0,
                "missed deadline cancels old task without late credit");
        f.tick++;
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        lost.action.run();
        check(f.progress(a, "guard") == 0, "lost callback cannot drain recovery batch");
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 1, "fresh action recovers after missed confirmation");
    }

    private static void clientReadiness() {
        int cases = guardScenarios, failures = guardFailures;
        for (int origin : new int[] {100, Integer.MAX_VALUE - 4, Integer.MIN_VALUE + 4}) {
            for (boolean respawn : List.of(false, true))
                for (boolean timeout : List.of(false, true))
                    guardCase(
                            "readiness loading / " + origin + "/" + respawn + "/" + timeout,
                            () -> readinessLoading(origin, respawn, timeout));
            guardCase("readiness publication / " + origin, () -> readinessPublication(origin));
            guardCase("readiness cooldown / " + origin, () -> readinessCooldown(origin));
            guardCase("readiness guard / " + origin, () -> readinessGuard(origin));
        }
        guardCase("readiness unknown re-enable", QuestMovementHarness::readinessUnknown);
        guardCase("readiness sessions and players", QuestMovementHarness::readinessSessions);
        guardCase("readiness generations", QuestMovementHarness::readinessGeneration);
        System.out.println(
                "Client readiness scenarios: "
                        + (guardScenarios - cases)
                        + ", failed: "
                        + (guardFailures - failures));
        if (guardFailures != failures)
            throw new AssertionError(
                    "Client readiness failed scenarios: " + (guardFailures - failures));
    }

    private static void readinessLoading(int origin, boolean respawn, boolean timeout) {
        Fixture f = confirmationFixture(1);
        f.autoReady = false;
        Actor a = reconcileActor(f);
        int[] hud = {0};
        f.domain.quests.addProgressListener((id, assignments, catalog, ids, result) -> hud[0]++);
        f.tick = origin;
        if (respawn) f.producer.onRespawn(new PlayerRespawnEvent(a.player, a.at, false));
        else
            f.producer.onJoin(
                    new PlayerJoinEvent(a.player, net.kyori.adventure.text.Component.empty()));
        f.requestToggle(a, true, false, false);
        check(
                !a.sneak && f.progress(a, "guard") == 0 && f.coins(a) == 0 && hud[0] == 0,
                "unready event does not apply the flag or grant at the boundary");
        f.loaded(a, timeout);
        a.sneak = true; // Native handleInteract's independent write, without a Toggle event.
        f.tick++;
        f.confirm();
        check(
                f.progress(a, "guard") == 0
                        && f.status(a, "guard") == QuestStatus.ACTIVE
                        && f.coins(a) == 0
                        && hud[0] == 0
                        && f.feedback.isEmpty(),
                "independent post-load flag must not confirm old intent: expected=0 actual="
                        + f.progress(a, "guard"));
        f.tick++;
        f.toggle(a, false, false);
        f.confirm();
        f.tick++;
        f.toggle(a, true, false);
        check(f.progress(a, "guard") == 0, "new ready action remains provisional");
        f.tick++;
        f.confirm();
        check(
                f.progress(a, "guard") == 1
                        && f.status(a, "guard") == QuestStatus.COMPLETED
                        && f.coins(a) == 7
                        && hud[0] == 1,
                "new physically applied ready action pays exactly once");
        for (var task : List.copyOf(f.confirmations)) task.action.run();
        f.domain.quests.retryPendingRewards(a.id);
        check(f.coins(a) == 7 && hud[0] == 1, "old callbacks cannot duplicate ready payout");
    }

    private static void readinessPublication(int origin) {
        Fixture f = confirmationFixture(100);
        f.autoReady = false;
        Actor a = reconcileActor(f);
        f.tick = origin;
        f.loaded(a, false); // Paper has not yet published its loaded flag inside this event.
        f.requestToggle(a, true, false, false);
        a.sneak = true;
        f.tick++;
        f.confirm();
        check(
                f.progress(a, "guard") == 0,
                "loaded-event tick is not yet a confirmed native ready boundary");
        f.toggle(a, false, false);
        f.confirm();
        f.tick++;
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 1, "a later ready event/action confirms normally");
    }

    private static void readinessUnknown() {
        Fixture f = confirmationFixture(100);
        f.autoReady = false;
        Actor a = reconcileActor(f);
        for (int elapsed : new int[] {0, 59, 60, 61}) {
            f.tick = 100 + elapsed;
            f.toggle(a, false, false);
            f.toggle(a, true, false);
            f.tick++;
            f.confirm();
            check(
                    f.progress(a, "guard") == 0,
                    "unknown already-online client cannot be guessed ready after " + elapsed);
        }
        f.loaded(a, true); // An actual Paper timeout notification, not an elapsed-time guess.
        f.tick++;
        f.toggle(a, false, false);
        f.confirm();
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 1, "actual timeout notification permits fresh action");
        f.producer.clear();
        f.tick += 10;
        f.toggle(a, false, false);
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 1, "clear discards all readiness proof");
        f.loaded(a, false);
        f.tick++;
        f.toggle(a, false, false);
        f.confirm();
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 2, "fresh runtime requires fresh readiness proof");
    }

    private static void readinessSessions() {
        Fixture f = confirmationFixture(100);
        f.autoReady = false;
        Actor old = reconcileActor(f);
        f.loaded(old, false);
        f.tick++;
        f.toggle(old, true, false);
        f.producer.onQuit(
                new PlayerQuitEvent(old.player, net.kyori.adventure.text.Component.empty()));
        Actor a = f.actor(old.id), b = reconcileActor(f);
        f.loaded(old, false); // Old-session notification cannot bless the replacement.
        f.tick++;
        f.toggle(a, true, false);
        f.toggle(b, true, false);
        f.tick++;
        f.confirm();
        check(
                f.progress(a, "guard") == 0 && f.progress(b, "guard") == 0,
                "old session and another unready player have no authority");
        f.loaded(a, false);
        f.tick++;
        f.toggle(a, false, false);
        f.confirm();
        f.toggle(a, true, false);
        f.toggle(b, false, false);
        f.toggle(b, true, false);
        check(
                f.confirmations.stream().filter(task -> !task.ran && !task.cancelled).count() == 1,
                "only ready sessions create one outstanding shared confirmation");
        f.tick++;
        f.confirm();
        check(
                f.progress(a, "guard") == 1 && f.progress(b, "guard") == 0,
                "readiness is session-local, never shared between players");
        f.loaded(b, true);
        f.tick++;
        f.toggle(b, false, false);
        f.confirm();
        f.toggle(b, true, false);
        f.tick++;
        f.confirm();
        check(
                f.progress(a, "guard") == 1 && f.progress(b, "guard") == 1,
                "second player can independently become ready");
    }

    private static void readinessGeneration() {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.toggle(a, true, false);
        f.loaded(a, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 0, "new ready generation revokes pending old intent");
        f.assign(a, "guard", "walk");
        f.domain.registry.replaceAll(f.domain.registry.snapshot());
        f.toggle(a, false, false);
        f.confirm();
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(
                f.progress(a, "guard") == 1,
                "assignment/catalog changes do not lose current session readiness");
    }

    private static void readinessCooldown(int origin) {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.tick = origin;
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        f.tick = origin + 2;
        f.loaded(a, false);
        f.tick++;
        f.toggle(a, false, false);
        f.confirm();
        f.tick = origin + 9;
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 1, "readiness does not reset ten-tick credited cooldown");
        f.toggle(a, false, false);
        f.confirm();
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 2, "original exact ten-tick action remains eligible");
    }

    private static void readinessGuard(int origin) {
        Fixture f = confirmationFixture(100);
        Actor a = reconcileActor(f);
        f.tick = origin;
        velocity(f, a, false);
        f.tick = origin + 1;
        f.loaded(a, true);
        f.tick = origin + 19;
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 0, "readiness cannot release guard or grant at its expiry");
        f.toggle(a, false, false);
        f.confirm();
        f.toggle(a, true, false);
        f.tick++;
        f.confirm();
        check(f.progress(a, "guard") == 1, "readiness preserves exact original velocity deadline");
    }

    private static void gameplay() {
        int before = guardScenarios, failures = guardFailures;
        for (int origin : new int[] {100, Integer.MAX_VALUE - 4, Integer.MIN_VALUE + 4}) {
            for (String mode : List.of("walk", "sprint", "both", "all"))
                guardCase("jump distance " + mode + "/" + origin, () -> jumpDistance(origin, mode));
            for (int sign : new int[] {-1, 1}) {
                guardCase(
                        "fast rotation " + sign + "/" + origin,
                        () -> fastRotation(origin, sign, false));
                guardCase(
                        "same-tick rotation " + sign + "/" + origin,
                        () -> fastRotation(origin, sign, true));
            }
        }
        for (String invalid :
                List.of(
                        "no-event",
                        "no-input",
                        "not-grounded",
                        "cancel-jump",
                        "cancel-move",
                        "mismatch",
                        "expired",
                        "oversize",
                        "timeout",
                        "height",
                        "drop",
                        "second-rise",
                        "land-fall",
                        "velocity",
                        "burst"))
            guardCase("jump exclusion " + invalid, () -> jumpExclusion(invalid));
        for (String reset :
                List.of(
                        "assignment",
                        "catalog",
                        "rollback",
                        "teleport",
                        "world",
                        "respawn",
                        "flight",
                        "glide",
                        "mount",
                        "build",
                        "activity",
                        "quit",
                        "clear",
                        "reject"))
            guardCase("jump phase ownership " + reset, () -> jumpPhaseReset(reset));
        guardCase("jump distance replay and subsets", QuestMovementHarness::jumpReplay);
        guardCase("jump and rotation domain services", QuestMovementHarness::gameplayRewards);
        guardCase(
                "rotation ambiguous endpoints and replay",
                QuestMovementHarness::fastRotationSafety);
        guardCase("rotation tick flood", QuestMovementHarness::fastRotationFlood);
        for (String invalid : List.of("cancel", "nonfinite", "oversize", "teleport"))
            guardCase("fast rotation invalidation " + invalid, () -> rotationInvalidation(invalid));
        int cases = guardScenarios - before, failed = guardFailures - failures;
        System.out.println("Movement gameplay scenarios: " + cases + ", failed: " + failed);
        if (failed != 0) throw new AssertionError("Movement gameplay failed scenarios: " + failed);
    }

    private static Actor gameplayActor(Fixture f, String... ids) {
        Actor a = f.actor();
        a.sprint = a.jumpInput = true;
        f.assign(a, ids);
        return a;
    }

    private static PlayerMoveEvent beginJump(
            Fixture f, Actor a, boolean cancelJump, boolean cancelMove) {
        var from = a.at.clone();
        var to = from.clone().add(.4, .42, 0);
        var event = new PlayerJumpEvent(a.player, from, to);
        event.setCancelled(cancelJump);
        f.producer.onJump(event); // Actual Paper jump callback still observes the grounded player.
        a.ground = false; // Paper updates ground before its subsequent Move notification.
        var move = new PlayerMoveEvent(a.player, from, to);
        move.setCancelled(cancelJump || cancelMove);
        f.producer.onMove(move);
        if (!move.isCancelled()) a.at = to;
        return move;
    }

    private static void jumpDistance(int origin, String mode) {
        Fixture f = finalFixture();
        String[] ids =
                switch (mode) {
                    case "walk" -> new String[] {"walk"};
                    case "sprint" -> new String[] {"sprint"};
                    case "all" ->
                            new String[] {
                                "walk", "sprint", "sneak", "jump", "turn", "back", "toggle"
                            };
                    default -> new String[] {"walk", "sprint"};
                };
        Actor a = gameplayActor(f, ids);
        f.tick = origin;
        beginJump(f, a, false, false);
        for (double dy : new double[] {.33, .25, .2, -.15, -.25, -.35, -.45})
            f.move(a, .4, dy, 0, 0);
        if (!mode.equals("sprint"))
            check(
                    f.progress(a, "walk") == 3,
                    "confirmed ordinary jump must include horizontal WALK: actual="
                            + f.progress(a, "walk"));
        if (!mode.equals("walk"))
            check(
                    f.progress(a, "sprint") == 3,
                    "sprint jump must include horizontal SPRINT: actual="
                            + f.progress(a, "sprint"));
        if (mode.equals("all"))
            check(
                    f.progress(a, "jump") == 1
                            && f.progress(a, "sneak") == 0
                            && f.progress(a, "back") == 0
                            && f.progress(a, "turn") == 0,
                    "jump is discrete; other air subsets and rotation do not gain authority");
        check(a.writes == 0, "jump measurement never mutates native movement");
    }

    private static void jumpExclusion(String invalid) {
        Fixture f = finalFixture();
        Actor a = gameplayActor(f, "walk", "sprint");
        if (invalid.equals("no-event")) {
            a.ground = false;
            f.move(a, .4, .42, 0, 0);
        } else if (invalid.equals("mismatch")
                || invalid.equals("expired")
                || invalid.equals("oversize")) {
            var from = a.at.clone();
            var to = from.clone().add(.4, .42, 0);
            f.producer.onJump(new PlayerJumpEvent(a.player, from, to));
            a.ground = false;
            if (invalid.equals("expired")) f.tick += 3;
            if (invalid.equals("mismatch")) to.add(.1, 0, 0);
            if (invalid.equals("oversize")) to.add(2, 0, 0);
            f.producer.onMove(new PlayerMoveEvent(a.player, from, to));
            a.at = to;
        } else {
            if (invalid.equals("no-input")) a.jumpInput = false;
            if (invalid.equals("not-grounded")) a.ground = false;
            beginJump(f, a, invalid.equals("cancel-jump"), invalid.equals("cancel-move"));
        }
        switch (invalid) {
            case "timeout" -> f.tick += 21;
            case "height" -> {
                f.move(a, .4, .7, 0, 0);
                f.move(a, .4, .7, 0, 0);
            }
            case "drop" -> {
                f.move(a, .4, -.8, 0, 0);
                f.move(a, .4, -.8, 0, 0);
            }
            case "second-rise" -> {
                f.move(a, .4, -.1, 0, 0);
                f.move(a, .4, .2, 0, 0);
            }
            case "land-fall" -> {
                a.ground = true;
                f.move(a, .4, 0, 0, 0);
                a.ground = false;
            }
            case "velocity" -> {
                velocity(f, a, false);
                f.tick += 20;
            }
            case "burst" -> {
                moveAtTick(f, a, .4, .1, 0, 0);
                moveAtTick(f, a, .4, .1, 0, 0);
            }
            default -> {}
        }
        for (int i = 0; i < 3; i++) f.move(a, .4, -.05, 0, 0);
        check(
                f.progress(a, "walk") == 0 && f.progress(a, "sprint") == 0,
                "unproven/retired jump phase cannot provide distance: " + invalid);
    }

    private static void jumpPhaseReset(String reset) {
        Fixture f = finalFixture();
        Actor a = gameplayActor(f, "walk", "sprint");
        beginJump(f, a, false, false);
        switch (reset) {
            case "assignment" -> f.assign(a, "walk", "sprint");
            case "catalog" -> f.domain.registry.replaceAll(f.domain.registry.snapshot());
            case "rollback" -> {
                var catalog = f.domain.registry.snapshot();
                f.domain.registry.replaceAll(catalog);
                f.domain.registry.replaceAll(catalog);
            }
            case "teleport" ->
                    f.producer.onTeleport(
                            new PlayerTeleportEvent(a.player, a.at, a.at.clone().add(5, 0, 0)));
            case "world" ->
                    f.producer.onWorld(new PlayerChangedWorldEvent(a.player, a.at.getWorld()));
            case "respawn" -> f.producer.onRespawn(new PlayerRespawnEvent(a.player, a.at, false));
            case "flight" -> f.producer.onFlight(new PlayerToggleFlightEvent(a.player, true));
            case "glide" -> f.producer.onGlide(new EntityToggleGlideEvent(a.player, true));
            case "mount" -> f.producer.onMount(new EntityMountEvent(a.player, a.player));
            case "build", "activity" -> {
                f.producer.beforeStateChange(a.player);
                a.context = false;
                f.producer.afterStateChange(a.id);
                a.context = true;
                f.producer.afterStateChange(a.id);
            }
            case "quit" -> {
                Actor old = a;
                f.producer.onQuit(
                        new PlayerQuitEvent(a.player, net.kyori.adventure.text.Component.empty()));
                a = f.actor(old.id);
                a.ground = false;
                a.sprint = true;
                f.producer.onMove(
                        new PlayerMoveEvent(old.player, old.at, old.at.clone().add(.4, 0, 0)));
            }
            case "clear" -> f.producer.clear();
            case "reject" -> guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
            default -> throw new AssertionError(reset);
        }
        for (int i = 0; i < 3; i++) f.move(a, .4, 0, 0, a.at.getYaw());
        check(
                f.progress(a, "walk") == 0 && f.progress(a, "sprint") == 0,
                "jump authorization cannot cross " + reset);
    }

    private static void jumpReplay() {
        Fixture f = finalFixture();
        Actor a = gameplayActor(f, "walk", "sprint", "sneak", "back");
        var move = beginJump(f, a, false, false);
        for (int i = 0; i < 50; i++) f.producer.onMove(move);
        f.move(a, .6, .3, 0, 0);
        check(
                f.progress(a, "walk") == 1 && f.progress(a, "sprint") == 1,
                "replayed launch endpoint cannot duplicate distance");
        a.sprint = false;
        a.sneak = true;
        f.move(a, .6, .2, 0, 0);
        f.move(a, .4, -.2, 0, 0);
        check(
                f.progress(a, "walk") == 2
                        && f.progress(a, "sprint") == 1
                        && f.progress(a, "sneak") == 0
                        && f.progress(a, "back") == 0,
                "air authorization extends WALK/SPRINT only and preserves Sneak precedence");
    }

    private static void fastRotation(int origin, int sign, boolean sameTick) {
        Fixture f = finalFixture();
        Actor a = gameplayActor(f, "turn");
        f.tick = origin;
        a.at.setYaw(170);
        for (int i = 0; i < 3; i++) {
            if (!sameTick) f.tick++;
            moveAtTick(f, a, 0, 0, 0, a.at.getYaw() + sign * 120);
        }
        check(
                f.progress(a, "turn") == 1,
                "three observed fast segments form one net turn: actual=" + f.progress(a, "turn"));
        check(a.writes == 0, "rotation observation never changes movement");
    }

    private static void fastRotationSafety() {
        Fixture f = finalFixture();
        Actor a = gameplayActor(f, "turn");
        f.rotate(a, 120);
        f.rotate(a, 180);
        f.rotate(a, 120);
        f.rotate(a, 120);
        check(f.progress(a, "turn") == 0, "exact half-turn has no unambiguous direction");
        f.producer.forget(a.id);
        for (int i = 0; i < 3; i++) f.rotate(a, 270);
        check(f.progress(a, "turn") == 0, "raw over-180 deltas never invent hidden turns");
        f.rotate(a, 270);
        check(f.progress(a, "turn") == 1, "only four shortest observed quarter arcs count");
        long before = f.progress(a, "turn");
        f.rotate(a, 1080);
        check(f.progress(a, "turn") == before, "same normalized orientation reveals no full spins");
        f.producer.forget(a.id);
        var replay = f.rotate(a, 120);
        for (int i = 0; i < 50; i++) f.producer.onMove(replay);
        f.rotate(a, -120);
        check(f.progress(a, "turn") == before, "replays and reversal cannot farm rotation");
    }

    private static void fastRotationFlood() {
        Fixture f = finalFixture();
        Actor a = gameplayActor(f, "turn");
        for (int i = 0; i < 200; i++) moveAtTick(f, a, 0, 0, 0, a.at.getYaw() + 120);
        check(f.progress(a, "turn") == 1, "at most one observed full turn per server tick");
        f.tick++;
        for (int i = 0; i < 3; i++) moveAtTick(f, a, 0, 0, 0, a.at.getYaw() + 120);
        check(f.progress(a, "turn") == 2, "fresh tick resumes a new complete measured arc");
    }

    private static void rotationInvalidation(String invalid) {
        Fixture f = finalFixture();
        Actor a = gameplayActor(f, "turn");
        f.rotate(a, 120);
        if (invalid.equals("cancel")) guardEvent(f, a, GuardEvent.CANCELLED_MOVE);
        if (invalid.equals("nonfinite")) guardEvent(f, a, GuardEvent.NONFINITE_MOVE);
        if (invalid.equals("oversize")) guardEvent(f, a, GuardEvent.OVERSIZE);
        if (invalid.equals("teleport"))
            f.producer.onTeleport(
                    new PlayerTeleportEvent(a.player, a.at, a.at.clone().add(5, 0, 0)));
        f.rotate(a, 120);
        f.rotate(a, 120);
        check(
                f.progress(a, "turn") == 0,
                "invalid observation cannot carry fast-rotation remainder");
        f.rotate(a, 120);
        check(f.progress(a, "turn") == 1, "only a fresh full arc after invalidation counts");
    }

    private static void gameplayRewards() {
        Fixture f = finalFixture();
        f.define("walk", QuestMovementProducer.WALK, 3, 7);
        f.define("sprint", QuestMovementProducer.SPRINT, 3, 7);
        Actor a = gameplayActor(f, "walk", "sprint");
        int[] hud = {0};
        f.domain.quests.addProgressListener((id, assignments, catalog, ids, result) -> hud[0]++);
        beginJump(f, a, false, false);
        for (double dy : new double[] {.33, .25, .2, -.15, -.25, -.35, -.45})
            f.move(a, .4, dy, 0, 0);
        check(
                f.coins(a) == 14
                        && f.status(a, "walk") == QuestStatus.COMPLETED
                        && f.status(a, "sprint") == QuestStatus.COMPLETED
                        && hud[0] == 6,
                "jump distance uses actual Quest/Reward/numeric HUD boundary exactly once");
        f.domain.quests.retryPendingRewards(a.id);
        check(f.coins(a) == 14, "completed jump distance never pays twice");
        f.define("turn", QuestMovementProducer.ROTATION, 1, 7);
        f.assign(a, "turn");
        a.ground = true;
        f.domain.economy.setCoins(a.id, Long.MAX_VALUE);
        for (int i = 0; i < 3; i++) f.rotate(a, 120);
        check(
                f.status(a, "turn") == QuestStatus.REWARD_PENDING && hud[0] == 7,
                "fast rotation reaches existing overflow/pending and HUD semantics");
        f.domain.economy.setCoins(a.id, 0);
        f.domain.quests.retryPendingRewards(a.id);
        f.domain.quests.retryPendingRewards(a.id);
        check(
                f.coins(a) == 7 && hud[0] == 7 && f.status(a, "turn") == QuestStatus.COMPLETED,
                "pending retry pays once without another numeric HUD signal");
    }

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
}
