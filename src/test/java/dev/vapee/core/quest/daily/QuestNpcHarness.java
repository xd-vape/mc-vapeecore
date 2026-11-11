package dev.vapee.core.quest.daily;

import static dev.vapee.core.quest.QuestCompletionFixture.DEFAULT;
import static dev.vapee.core.quest.QuestCompletionFixture.definition;
import static dev.vapee.core.quest.QuestCompletionFixture.proxy;

import dev.vapee.core.quest.daily.menu.QuestMenuFixture;
import dev.vapee.core.reload.ReloadResult;
import dev.vapee.core.reload.ReloadService;

import io.papermc.paper.datacomponent.item.ResolvableProfile;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public final class QuestNpcHarness {
    private static final QuestNpcConfig.Position POSITION =
            new QuestNpcConfig.Position("test", -0.25, 64, -16.1, 90, 0);
    private static final QuestNpcConfig.State PLACED =
            new QuestNpcConfig.State("Quest Master", "", POSITION);
    private static int checks;

    public static void main(String[] args) throws Exception {
        lifecycle();
        failures();
        interactionsAndSkin();
        System.out.println("QuestNpcHarness passed " + checks + " checks.");
    }

    private static void lifecycle() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var world = new NpcWorld(f);
            var config = world.config();
            var module = f.module();
            module.enable();
            var command = module.npcCommand();
            var admin = world.sender(true);
            var guest = world.sender(false);
            command.onCommand(guest, null, "core", new String[] {"set"});
            check(
                    config.read().position() == null
                            && command.onTabComplete(guest, null, "core", new String[] {""})
                                    .isEmpty(),
                    "non-admin cannot place or discover admin actions");
            command.onCommand(admin, null, "core", new String[] {"name", "Quest", "Master"});
            command.onCommand(admin, null, "core", new String[] {"set"});
            check(
                    config.read().equals(PLACED) && world.live() == 1,
                    "admin placement persists exact position");
            var initial = world.latest();
            check(
                    initial.invulnerable
                            && initial.immovable
                            && !initial.gravity
                            && !initial.collidable
                            && !initial.persistent
                            && !initial.pickup,
                    "disposable mannequin is protected and cannot enter chunk saves");
            check(module.prepareReload() != null && world.live() == 1, "prepare is read-only");
            module.prepareReload().apply();
            check(
                    world.latest() == initial && world.live() == 1,
                    "same placement reload keeps exactly one");
            var listener = (DailyQuestListener) f.runtime.registered.getFirst();
            initial.valid = false; // Nonpersistent entity discarded by Paper on chunk unload.
            world.entitiesLoaded = false;
            listener.onChunkLoad(new ChunkLoadEvent(world.chunk, false));
            listener.onEntitiesLoad(new EntitiesLoadEvent(world.chunk, List.of()));
            check(world.callbacks.size() == 1, "both chunk events coalesce");
            world.runCallback();
            check(world.live() == 0, "waits for loaded entities");
            world.entitiesLoaded = true;
            listener.onEntitiesLoad(new EntitiesLoadEvent(world.chunk, List.of()));
            world.runCallback();
            check(world.live() == 1 && world.latest() != initial, "chunk reload recreates one NPC");
            listener.onEntitiesLoad(new EntitiesLoadEvent(world.chunk, List.of()));
            module.disable();
            world.runCallback();
            check(world.live() == 0, "disable removes NPC and invalidates queued chunk callback");
            var restarted = f.module();
            restarted.enable();
            check(
                    world.live() == 1 && world.latest().location.getX() == POSITION.x(),
                    "fresh runtime restores stored NPC");
            restarted.npcCommand().onCommand(admin, null, "core", new String[] {"remove"});
            check(
                    world.live() == 0 && config.read().position() == null,
                    "remove clears persisted placement");
            restarted
                    .npcCommand()
                    .onCommand(admin, null, "core", new String[] {"skin", "ExamplePlayer"});
            restarted
                    .npcCommand()
                    .onCommand(admin, null, "core", new String[] {"skin", "bad/name"});
            check(
                    config.read().skin().equals("ExamplePlayer") && world.live() == 0,
                    "skin command persists valid names without spawning and rejects invalid names");
            restarted.disable();
        }
    }

    private static void failures() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var world = new NpcWorld(f);
            var config = world.config();
            config.save(config.read(), PLACED);
            var module = f.module();
            f.failSchedule = true; // Failure after the mannequin was acquired.
            fails(module::enable);
            check(
                    world.spawns.size() == 1 && world.live() == 0 && f.runtime.handlerCount() == 0,
                    "late startup failure releases exact mannequin and listeners");
            f.failSchedule = false;
            module.enable();
            world.failSpawn = true;
            config.save(PLACED, new QuestNpcConfig.State("Replacement", "", POSITION));
            var reload = new ReloadService(f.runtime.logger, List.of(module)).reload();
            check(
                    reload.status() == ReloadResult.Status.APPLY_FAILED
                            && world.live() == 1
                            && world.latest().name.equals("Quest Master"),
                    "failed reload rolls back visible NPC");
            world.latest().refuseRemoval = true;
            fails(module::disable);
            fails(module::enable);
            check(world.live() == 1, "unresolved removal blocks duplicate ownership");
            world.latest().refuseRemoval = false;
            module.disable();
            check(world.live() == 0, "disable retry releases retained mannequin");
        }
    }

    @SuppressWarnings("deprecation")
    private static void interactionsAndSkin() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var world = new NpcWorld(f);
            var lookups = new ArrayList<CompletableFuture<ResolvableProfile>>();
            var npc =
                    new QuestNpc(
                            f.runtime.plugin,
                            () -> true,
                            name -> {
                                var lookup = new CompletableFuture<ResolvableProfile>();
                                lookups.add(lookup);
                                return lookup;
                            });
            npc.apply(new QuestNpcConfig.State("Quest Master", "MissingPlayer", POSITION));
            lookups.getFirst().complete(null);
            world.runCallback();
            check(
                    world.latest().profile == null && world.live() == 1,
                    "missing skin retains default mannequin");
            npc.apply(new QuestNpcConfig.State("Quest Master", "KnownPlayer", POSITION));
            var resolved = proxy(ResolvableProfile.class, (method, args) -> DEFAULT);
            lookups.getLast().complete(resolved);
            world.runCallback();
            check(world.latest().profile == resolved, "resolved skin applied on server callback");
            var menus = new QuestMenuFixture();
            menus.domain.define(definition("daily", "test:walk", 2, 1));
            var player = menus.player();
            var listener = new DailyQuestListener(menus.daily, () -> true, null, npc, menus.menu);
            var entity = world.latest().entity;
            var denied = new PlayerInteractEntityEvent(player.player, entity, EquipmentSlot.HAND);
            player.permission = false;
            listener.onNpcInteract(denied);
            check(
                    denied.isCancelled() && player.opens == 0,
                    "permission is enforced by existing menu");
            player.permission = true;
            var offhand =
                    new PlayerInteractEntityEvent(player.player, entity, EquipmentSlot.OFF_HAND);
            listener.onNpcInteract(offhand);
            check(offhand.isCancelled() && player.opens == 0, "offhand cannot open");
            var click =
                    new PlayerInteractAtEntityEvent(
                            player.player, entity, new Vector(), EquipmentSlot.HAND);
            listener.onNpcInteractAt(click);
            listener.onNpcInteract(
                    new PlayerInteractEntityEvent(player.player, entity, EquipmentSlot.HAND));
            check(
                    click.isCancelled() && player.opens == 1,
                    "paired main-hand events open existing menu once");
            var source = proxy(org.bukkit.damage.DamageSource.class, (method, args) -> DEFAULT);
            var damage =
                    new EntityDamageEvent(
                            entity, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 1);
            var teleport =
                    new EntityTeleportEvent(
                            entity, world.location, world.location.clone().add(1, 0, 0));
            var mount = new EntityMountEvent(player.player, entity);
            listener.onNpcDamage(damage);
            listener.onNpcTeleport(teleport);
            listener.onNpcMount(mount);
            check(
                    damage.isCancelled() && teleport.isCancelled() && mount.isCancelled(),
                    "owned entity rejects damage, teleport and riding");
            npc.apply(new QuestNpcConfig.State("Quest Master", "NextPlayer", POSITION));
            var retired = world.latest();
            lookups.getLast().complete(resolved);
            npc.shutdown();
            world.runCallback();
            check(
                    retired.profile == null && world.live() == 0,
                    "retired skin callback cannot mutate removed NPC");
        }
    }

    private static final class NpcWorld {
        final DailyQuestLifecycleFixture f;
        final World world;
        final Chunk chunk;
        final Location location;
        final List<NpcEntity> spawns = new ArrayList<>();
        final List<Runnable> callbacks = new ArrayList<>();
        boolean entitiesLoaded = true;
        boolean failSpawn;

        NpcWorld(DailyQuestLifecycleFixture f) {
            this.f = f;
            world = proxy(World.class, this::worldCall);
            location =
                    new Location(
                            world,
                            POSITION.x(),
                            POSITION.y(),
                            POSITION.z(),
                            POSITION.yaw(),
                            POSITION.pitch());
            chunk =
                    proxy(
                            Chunk.class,
                            (method, args) ->
                                    switch (method) {
                                        case "getWorld" -> world;
                                        case "getX" -> -1;
                                        case "getZ" -> -2;
                                        case "isEntitiesLoaded" -> entitiesLoaded;
                                        default -> DEFAULT;
                                    });
            var scheduler =
                    (BukkitScheduler) f.runtime.serverCalls.apply("getScheduler", new Object[0]);
            var wrapped =
                    proxy(
                            BukkitScheduler.class,
                            (method, args) ->
                                    switch (method) {
                                        case "getMainThreadExecutor" -> (Executor) callbacks::add;
                                        case "runTask" -> {
                                            Runnable callback = (Runnable) args[1];
                                            callbacks.add(callback);
                                            yield f.new Task(callback, 1, 0);
                                        }
                                        case "runTaskTimer" ->
                                                scheduler.runTaskTimer(
                                                        (Plugin) args[0],
                                                        (Runnable) args[1],
                                                        (long) args[2],
                                                        (long) args[3]);
                                        default -> throw new AssertionError(method);
                                    });
            f.runtime.serverCalls =
                    (method, args) ->
                            switch (method) {
                                case "getWorld" -> world;
                                case "getScheduler" -> wrapped;
                                default -> throw new AssertionError(method);
                            };
        }

        @SuppressWarnings("unchecked")
        private Object worldCall(String method, Object[] args) {
            return switch (method) {
                case "getName" -> "test";
                case "getMinHeight" -> -64;
                case "getMaxHeight" -> 320;
                case "isChunkLoaded" -> args[0].equals(-1) && args[1].equals(-2);
                case "getChunkAt" -> chunk;
                case "spawn" -> {
                    var npc = new NpcEntity((Location) args[0]);
                    spawns.add(npc);
                    ((Consumer<Mannequin>) args[4]).accept(npc.entity);
                    npc.valid = true;
                    if (failSpawn) {
                        failSpawn = false;
                        throw new IllegalStateException("injected spawn failure after acquisition");
                    }
                    yield npc.entity;
                }
                default -> DEFAULT;
            };
        }

        QuestNpcConfig config() {
            return new QuestNpcConfig(f.directory.resolve("quest-npc.yml"), f.runtime.logger);
        }

        long live() {
            return spawns.stream().filter(npc -> npc.valid).count();
        }

        NpcEntity latest() {
            return spawns.getLast();
        }

        void runCallback() {
            callbacks.removeFirst().run();
        }

        Player sender(boolean admin) {
            return proxy(
                    Player.class,
                    (method, args) ->
                            switch (method) {
                                case "hasPermission" -> admin;
                                case "getLocation" -> location.clone();
                                default -> DEFAULT;
                            });
        }
    }

    private static final class NpcEntity {
        final Location location;
        final Mannequin entity;
        boolean valid, invulnerable, immovable, refuseRemoval;
        boolean gravity = true, collidable = true, persistent = true, pickup = true;
        String name;
        ResolvableProfile profile;

        NpcEntity(Location location) {
            this.location = location;
            entity =
                    proxy(
                            Mannequin.class,
                            (method, args) -> {
                                switch (method) {
                                    case "isValid":
                                        return valid;
                                    case "remove":
                                        if (!refuseRemoval) valid = false;
                                        break;
                                    case "setInvulnerable":
                                        invulnerable = (boolean) args[0];
                                        break;
                                    case "setImmovable":
                                        immovable = (boolean) args[0];
                                        break;
                                    case "setGravity":
                                        gravity = (boolean) args[0];
                                        break;
                                    case "setCollidable":
                                        collidable = (boolean) args[0];
                                        break;
                                    case "setPersistent":
                                        persistent = (boolean) args[0];
                                        break;
                                    case "setCanPickupItems":
                                        pickup = (boolean) args[0];
                                        break;
                                    case "customName":
                                        name =
                                                dev.vapee.core.quest.QuestCompletionFixture.plain(
                                                        (net.kyori.adventure.text.Component)
                                                                args[0]);
                                        break;
                                    case "setProfile":
                                        profile = (ResolvableProfile) args[0];
                                        break;
                                    default:
                                        return DEFAULT;
                                }
                                return null;
                            });
        }
    }

    private static void fails(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("Expected lifecycle failure");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
