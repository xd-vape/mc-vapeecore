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
        if (args.length == 1 && args[0].equals("--velocity-guard")) {
            velocityGuard();
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
                "armed lobby double-jump allowFlight does not disable walking or mutate glide flags");
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
        f.producer.afterStateChange(a.id);
        f.step(a, 0.4);
        check(
                f.progress(a, "walk") == 0,
                "NORMAL/BUILD transition observer clears fractional travel");
        f.producer.beforeJoin(a.player);
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
                f.progress(a, "toggle") == 4,
                "duplicate delivery before native flag commit counts only once");
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
                        (f, a) ->
                                f.producer.onTeleport(
                                        new PlayerTeleportEvent(a.player, a.at, a.at)),
                        (f, a) ->
                                f.producer.onWorld(
                                        new PlayerChangedWorldEvent(a.player, a.at.getWorld())),
                        (f, a) ->
                                f.producer.onRespawn(new PlayerRespawnEvent(a.player, a.at, false)),
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
                            f.producer.afterStateChange(a.id);
                        },
                        (f, a) -> {
                            f.producer.beforeJoin(a.player);
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
                "post-expiry ordinary completion/retry grants one reward and one numeric HUD signal");
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
                            f.producer.afterStateChange(a.id);
                        },
                        (f, a) -> {
                            f.producer.beforeJoin(a.player);
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

    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
}
