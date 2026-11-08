package dev.vapee.core.quest;

import static dev.vapee.core.quest.QuestCompletionFixture.DEFAULT;
import static dev.vapee.core.quest.QuestCompletionFixture.proxy;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;

import net.kyori.adventure.text.Component;

import org.bukkit.GameMode;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;

import java.util.*;

/** Real quest/reward/economy; only online players, movement input and server ticks are controlled. */
final class QuestMovementTestSupport {
    private static final Map<String, World> WORLDS = new HashMap<>();

    static final class Fixture {
        final QuestCompletionFixture domain = new QuestCompletionFixture();
        final Map<UUID, Actor> online = new HashMap<>();
        final List<Component> feedback = new ArrayList<>();
        int tick = 100, entity;
        boolean running = true;
        final QuestProgressReporter reporter =
                new QuestProgressReporter(
                        domain.quests, (id, text) -> feedback.add(text), domain.logger);
        final QuestMovementProducer producer =
                new QuestMovementProducer(
                        domain.players,
                        domain.registry,
                        reporter,
                        id -> online.containsKey(id) ? online.get(id).player : null,
                        id -> online.containsKey(id) && online.get(id).context,
                        () -> tick,
                        () -> running,
                        domain.logger);

        Actor actor() {
            return actor(domain.load());
        }

        Actor actor(UUID id) {
            Actor actor = new Actor(this, id, ++entity);
            online.put(id, actor);
            return actor;
        }

        void define(String id, QuestProgressKey key, long target, long coins) {
            var definitions = new ArrayList<>(domain.registry.snapshot());
            definitions.removeIf(d -> d.id().equals(id));
            definitions.add(QuestCompletionFixture.definition(id, key.value(), target, coins));
            domain.registry.replaceAll(definitions);
        }

        void assign(Actor actor, String... ids) {
            domain.quests.replaceAssignments(actor.id, List.of(ids));
        }

        long progress(Actor actor, String id) {
            return domain.players
                    .getPlayer(actor.id)
                    .orElseThrow()
                    .getQuestState()
                    .snapshot()
                    .get(id)
                    .progress();
        }

        QuestStatus status(Actor actor, String id) {
            return domain.players
                    .getPlayer(actor.id)
                    .orElseThrow()
                    .getQuestState()
                    .snapshot()
                    .get(id)
                    .status();
        }

        long coins(Actor actor) {
            return domain.economy.getCoins(actor.id).orElseThrow();
        }

        PlayerMoveEvent step(Actor actor, double distance) {
            return move(actor, distance, 0, 0, actor.at.getYaw());
        }

        PlayerMoveEvent rotate(Actor actor, float delta) {
            return move(actor, 0, 0, 0, actor.at.getYaw() + delta);
        }

        PlayerMoveEvent move(Actor actor, double x, double y, double z, float yaw) {
            tick++;
            var from = actor.at.clone();
            var to = from.clone().add(x, y, z);
            to.setYaw(yaw);
            var event = new PlayerMoveEvent(actor.player, from, to);
            producer.onMove(event);
            actor.at = to;
            return event;
        }

        PlayerToggleSneakEvent toggle(Actor actor, boolean next, boolean cancelled) {
            var event = new PlayerToggleSneakEvent(actor.player, next);
            event.setCancelled(cancelled);
            producer.onSneak(event);
            // Paper commits the new flag AFTER the callback, and only for accepted events.
            if (!cancelled) actor.sneak = next;
            return event;
        }

        void jump(Actor actor, boolean cancelled) {
            tick++;
            var from = actor.at.clone();
            var to = from.clone().add(0, 0.42, 0);
            var event = new PlayerJumpEvent(actor.player, from, to);
            event.setCancelled(cancelled);
            producer.onJump(event);
            actor.ground = false;
            var movement = new PlayerMoveEvent(actor.player, from, to);
            movement.setCancelled(cancelled);
            producer.onMove(movement);
            if (!cancelled) actor.at = to;
        }

        void ground(Actor actor) {
            actor.ground = true;
            tick += 10;
        }
    }

    static final class Actor {
        final UUID id;
        final Player player;
        Location at = new Location(world("first"), 0, 64, 0);
        boolean online = true, context = true, ground = true, input = true;
        boolean backward, left, right, sprintInput;
        boolean sprint, sneak, vehicle, fly, glide, water, swimming, climbing, riptide, dead;
        boolean allowFlight;
        GameMode mode = GameMode.SURVIVAL;
        int writes;

        Actor(Fixture fixture, UUID id, int entity) {
            this.id = id;
            Input keys =
                    proxy(
                            Input.class,
                            (name, args) ->
                                    switch (name) {
                                        case "isForward" -> input;
                                        case "isBackward" -> backward;
                                        case "isLeft" -> left;
                                        case "isRight" -> right;
                                        case "isSneak" -> sneak;
                                        case "isSprint" -> sprint || sprintInput;
                                        default -> DEFAULT;
                                    });
            player =
                    proxy(
                            Player.class,
                            (name, args) ->
                                    switch (name) {
                                        case "getUniqueId" -> id;
                                        case "getEntityId" -> entity;
                                        case "getName" -> "Walker";
                                        case "getLocation" -> at.clone();
                                        case "getWorld" -> at.getWorld();
                                        case "getCurrentInput" -> keys;
                                        case "isOnline", "isValid" -> online;
                                        case "isOnGround" -> ground;
                                        case "isSprinting" -> sprint;
                                        case "isSneaking" -> sneak;
                                        case "isInsideVehicle" -> vehicle;
                                        case "isFlying" -> fly;
                                        case "isGliding" -> glide;
                                        case "isInWater" -> water;
                                        case "isSwimming" -> swimming;
                                        case "isClimbing" -> climbing;
                                        case "isRiptiding" -> riptide;
                                        case "isDead" -> dead;
                                        case "getGameMode" -> mode;
                                        case "getAllowFlight" -> allowFlight;
                                        case "setAllowFlight",
                                                "setFlying",
                                                "setGliding",
                                                "setVelocity",
                                                "teleport",
                                                "setGameMode" -> {
                                            writes++;
                                            throw new AssertionError(
                                                    "Producer mutated movement/glide authority");
                                        }
                                        default -> DEFAULT;
                                    });
        }
    }

    static World world(String name) {
        return WORLDS.computeIfAbsent(name, QuestMovementTestSupport::createWorld);
    }

    private static World createWorld(String name) {
        UUID id = UUID.nameUUIDFromBytes(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return proxy(
                World.class,
                (method, args) ->
                        switch (method) {
                            case "getUID" -> id;
                            case "getName" -> name;
                            case "getMinHeight" -> -64;
                            case "getMaxHeight" -> 320;
                            default -> DEFAULT;
                        });
    }
}
