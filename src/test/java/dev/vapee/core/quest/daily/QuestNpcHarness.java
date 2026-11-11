package dev.vapee.core.quest.daily;

import static dev.vapee.core.quest.QuestCompletionFixture.DEFAULT;
import static dev.vapee.core.quest.QuestCompletionFixture.definition;
import static dev.vapee.core.quest.QuestCompletionFixture.proxy;

import dev.vapee.core.economy.EconomyService;
import dev.vapee.core.quest.daily.menu.QuestMenuFixture;
import dev.vapee.core.reload.ReloadResult;
import dev.vapee.core.reload.ReloadService;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import io.papermc.paper.event.player.PlayerTrackEntityEvent;
import io.papermc.paper.event.player.PlayerUntrackEntityEvent;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;

import java.nio.file.Files;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
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
        personalHolograms();
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
            var moved =
                    new QuestNpcConfig.Position(
                            POSITION.world(),
                            POSITION.x() - 1,
                            POSITION.y(),
                            POSITION.z(),
                            POSITION.yaw(),
                            POSITION.pitch());
            config.save(PLACED, new QuestNpcConfig.State("Replacement", "", moved));
            var reload = new ReloadService(f.runtime.logger, List.of(module)).reload();
            check(
                    reload.status() == ReloadResult.Status.APPLY_FAILED
                            && world.live() == 1
                            && world.latest().location.getX() == POSITION.x(),
                    "failed reload restores the original NPC placement");
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

    @SuppressWarnings("deprecation")
    private static void personalHolograms() throws Exception {
        try (var f = new DailyQuestLifecycleFixture()) {
            var world = new NpcWorld(f);
            var config = world.config();
            config.save(config.read(), PLACED);
            var dailyFile = f.directory.resolve("daily-quests.yml");
            String yaml =
                    DailyQuestLifecycleFixture.yaml("a", 1)
                            .replace("test:key", "test:a")
                            .replace("quests-per-day: 1", "quests-per-day: 3");
            for (String id : List.of("b", "c", "d"))
                yaml +=
                        DailyQuestLifecycleFixture.yaml(id, 1)
                                .split("quests:\n", 2)[1]
                                .replace("test:key", "test:" + id);
            Files.writeString(dailyFile, yaml);
            var module = f.module();
            module.enable();
            var npc = world.latest();
            var listener = (DailyQuestListener) f.runtime.registered.getFirst();
            var menuListener =
                    (dev.vapee.core.quest.daily.menu.DailyQuestMenuListener)
                            f.runtime.registered.getLast();
            var a = world.viewer(f.load("ViewerA"));
            var b = world.viewer(f.load("ViewerB"));
            var unloaded = world.viewer(UUID.randomUUID());
            check(
                    npc.name == null && config.read().name().equals("Quest Master"),
                    "hologram hides the native name without losing it");
            for (var viewer : List.of(a, b, unloaded)) {
                listener.onNpcTrack(new PlayerTrackEntityEvent(viewer.player, npc.entity));
                npc.tracked.add(viewer.player);
            }
            check(
                    world.displays.isEmpty() && world.callbacks.size() == 1,
                    "tracking events queue one callback without spawning inline");
            world.runCallback();
            check(world.displays.isEmpty(), "unloaded and unassigned profiles receive no display");
            Instant now = Instant.parse("2026-11-11T12:00:00Z");
            var daily = module.getDailyQuestService();
            daily.syncPlayer(a.id, now);
            daily.syncPlayer(b.id, now);
            world.runCallback();
            var aDisplay = a.onlyDisplay();
            var bDisplay = b.onlyDisplay();
            check(
                    aDisplay != bDisplay && unloaded.shown.isEmpty(),
                    "each assigned viewer receives a separate private display");
            check(
                    aDisplay.line().equals("Daily Quests\n0/3 Completed")
                            && aDisplay.text.color() == NamedTextColor.GOLD
                            && aDisplay.text.children().getLast().color() == NamedTextColor.GRAY,
                    "English defaults are two lines in gold and gray");
            check(
                    !aDisplay.visibleByDefault
                            && !aDisplay.persistent
                            && aDisplay.shadow
                            && aDisplay.center
                            && aDisplay.billboard
                            && aDisplay.transparent
                            && !aDisplay.defaultBackground
                            && aDisplay.location.getY() == POSITION.y() + 2.45,
                    "private centered billboard uses transparent background and configured height");
            var keys =
                    f.quests.getActiveQuests(b.id).orElseThrow().stream()
                            .map(q -> q.definition().progressKey())
                            .toList();
            f.quests.addProgress(b.id, keys.get(0), 1);
            f.quests.addProgress(b.id, keys.get(1), 1);
            world.runCallback();
            check(
                    aDisplay.writes == 1 && bDisplay.line().endsWith("2/3 Completed"),
                    "only the completing player's text changes");
            var economy = new EconomyService(f.players);
            economy.setCoins(b.id, Long.MAX_VALUE);
            f.quests.addProgress(b.id, keys.get(2), 1);
            check(
                    world.callbacks.isEmpty() && bDisplay.line().endsWith("2/3 Completed"),
                    "pending reward does not count");
            economy.setCoins(b.id, 0);
            f.quests.retryPendingRewards(b.id);
            world.runCallback();
            check(
                    bDisplay.line().endsWith("3/3 Completed"),
                    "successful reward retry updates completed count");
            Files.writeString(dailyFile, yaml.replace("quests-per-day: 3", "quests-per-day: 4"));
            module.prepareReload().apply();
            world.runCallback();
            check(
                    world.liveDisplays() == 2 && world.latest() == npc,
                    "reload replaces only the owned displays");
            daily.forcePlayer(b.id, now);
            world.runCallback();
            check(
                    b.onlyDisplay().line().endsWith("3/4 Completed"),
                    "force retains completed statuses and updates denominator");
            daily.syncPlayer(b.id, now.plus(2, ChronoUnit.DAYS));
            world.runCallback();
            check(
                    b.onlyDisplay().line().endsWith("0/4 Completed"),
                    "day rotation displays current assignments");
            a.locale = Locale.GERMAN;
            listener.onViewerLocaleChange(new PlayerLocaleChangeEvent(a.player, "de_de"));
            world.runCallback();
            check(
                    a.onlyDisplay().line().equals("Tagesquests\n0/3 abgeschlossen"),
                    "locale change updates only the personal view");

            var retired = b.onlyDisplay();
            b.online = false;
            npc.tracked.remove(b.player);
            f.runtime.online.remove(b.player);
            menuListener.onPlayerQuit(new PlayerQuitEvent(b.player, Component.empty()));
            var rejoined = world.viewer(b.id);
            npc.tracked.add(rejoined.player);
            listener.onNpcTrack(new PlayerTrackEntityEvent(rejoined.player, npc.entity));
            world.runCallback();
            check(
                    !retired.valid
                            && b.shown.isEmpty()
                            && rejoined.onlyDisplay() != retired
                            && world.liveDisplays() == 2,
                    "same UUID reconnect receives a new session-owned display");
            a.world = proxy(World.class, (method, args) -> DEFAULT);
            listener.onViewerWorldChange(new PlayerChangedWorldEvent(a.player, world.world));
            world.runCallback();
            check(
                    a.shown.isEmpty() && world.liveDisplays() == 1,
                    "world change removes the viewer display");

            var active = rejoined.onlyDisplay();
            active.refuseRemoval = true;
            var profile = f.players.getPlayer(b.id).orElseThrow();
            var progress = profile.getQuestState().snapshot();
            long coins = profile.getWallet().getCoins();
            var playerFile = f.directory.resolve("players").resolve(b.id + ".yml");
            byte[] playerBytes = Files.readAllBytes(playerFile);
            npc.tracked.remove(rejoined.player);
            listener.onNpcUntrack(new PlayerUntrackEntityEvent(rejoined.player, npc.entity));
            world.runCallback();
            check(
                    rejoined.shown.isEmpty() && active.valid,
                    "failed removal still hides the old display");
            npc.tracked.add(rejoined.player);
            listener.onNpcTrack(new PlayerTrackEntityEvent(rejoined.player, npc.entity));
            world.runCallback();
            check(
                    world.liveDisplays() == 1 && rejoined.shown.isEmpty(),
                    "failed cleanup cannot duplicate ownership");
            active.refuseRemoval = false;
            world.failDisplay = true;
            listener.onNpcTrack(new PlayerTrackEntityEvent(rejoined.player, npc.entity));
            world.runCallback();
            check(
                    world.liveDisplays() == 0 && rejoined.shown.isEmpty(),
                    "failed initialization removes the partial display");
            check(
                    profile.getQuestState().snapshot().equals(progress)
                            && profile.getWallet().getCoins() == coins
                            && java.util.Arrays.equals(playerBytes, Files.readAllBytes(playerFile)),
                    "presentation failures leave quest, wallet and player file bytes alone");
            listener.onNpcTrack(new PlayerTrackEntityEvent(rejoined.player, npc.entity));
            world.runCallback();
            check(
                    world.liveDisplays() == 1,
                    "next tracking event can recover display after failure");

            npc.valid = false;
            listener.onNpcRemoved(
                    new EntityRemoveEvent(npc.entity, EntityRemoveEvent.Cause.UNLOAD));
            check(world.liveDisplays() == 1, "entity-removal event defers display removal");
            world.runCallback();
            check(world.liveDisplays() == 0, "unload removes display");
            listener.onEntitiesLoad(new EntitiesLoadEvent(world.chunk, List.of()));
            world.runCallback();
            var loaded = world.latest();
            loaded.tracked.add(rejoined.player);
            listener.onNpcTrack(new PlayerTrackEntityEvent(rejoined.player, loaded.entity));
            world.runCallback();
            check(
                    world.liveDisplays() == 1 && world.live() == 1,
                    "chunk reload recreates one actor and one private display");

            var npcFile = f.directory.resolve("quest-npc.yml");
            String original = Files.readString(npcFile);
            Files.writeString(npcFile, original + "\nhologram:\n  enabled: false\n");
            module.prepareReload().apply();
            check(
                    world.liveDisplays() == 0
                            && world.latest() == loaded
                            && loaded.name.equals("Quest Master"),
                    "turning off hologram restores native name on the same actor");
            Files.writeString(
                    npcFile,
                    original
                            + "\nhologram:\n  height-offset: 3.0\n  title: Personal\n"
                            + "  progress-format: '<completed> of <total>'\n  title-color: '#ff8800'\n");
            module.prepareReload().apply();
            world.runCallback();
            check(
                    rejoined.onlyDisplay().line().equals("Personal\n0 of 4")
                            && rejoined.onlyDisplay().location.getY() == POSITION.y() + 3
                            && loaded.name == null,
                    "reload applies custom text, height and hidden native name");
            var custom = config.read().hologram();
            module.npcCommand()
                    .onCommand(world.sender(true), null, "core", new String[] {"name", "Saved"});
            check(
                    config.read().hologram().equals(custom) && config.read().name().equals("Saved"),
                    "admin save preserves hologram configuration");
            module.npcCommand()
                    .onCommand(world.sender(true), null, "core", new String[] {"remove"});
            check(
                    world.live() == 0 && world.liveDisplays() == 0,
                    "remove clears actor and holograms");
            module.disable();
        }
    }

    private static final class NpcWorld {
        final DailyQuestLifecycleFixture f;
        final World world;
        final Chunk chunk;
        final Location location;
        final List<NpcEntity> spawns = new ArrayList<>();
        final List<FakeDisplay> displays = new ArrayList<>();
        final List<Runnable> callbacks = new ArrayList<>();
        boolean entitiesLoaded = true;
        boolean failSpawn;
        boolean failDisplay;

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
                    if (args[1] == TextDisplay.class) {
                        var display = new FakeDisplay((Location) args[0], failDisplay);
                        failDisplay = false;
                        displays.add(display);
                        ((Consumer<TextDisplay>) args[2]).accept(display.entity);
                        yield display.entity;
                    }
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

        long liveDisplays() {
            return displays.stream().filter(display -> display.valid).count();
        }

        Viewer viewer(UUID id) {
            var viewer = new Viewer(this, id);
            f.runtime.online.add(viewer.player);
            return viewer;
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
        final Set<Player> tracked = new LinkedHashSet<>();
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
                                                args[0] == null
                                                        ? null
                                                        : dev.vapee.core.quest
                                                                .QuestCompletionFixture.plain(
                                                                (net.kyori.adventure.text.Component)
                                                                        args[0]);
                                        break;
                                    case "getTrackedBy":
                                        return Set.copyOf(tracked);
                                    case "getWorld":
                                        return location.getWorld();
                                    case "getLocation":
                                        return location.clone();
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

    private static final class FakeDisplay {
        final TextDisplay entity;
        final Location location;
        boolean valid = true, visibleByDefault = true, persistent = true, defaultBackground = true;
        boolean center, billboard, shadow, transparent, refuseRemoval;
        int writes;
        Component text;

        FakeDisplay(Location location, boolean fail) {
            this.location = location;
            entity =
                    proxy(
                            TextDisplay.class,
                            (method, args) -> {
                                switch (method) {
                                    case "isValid":
                                        return valid;
                                    case "remove":
                                        if (!refuseRemoval) valid = false;
                                        break;
                                    case "setVisibleByDefault":
                                        visibleByDefault = (boolean) args[0];
                                        break;
                                    case "setPersistent":
                                        persistent = (boolean) args[0];
                                        break;
                                    case "setDefaultBackground":
                                        defaultBackground = (boolean) args[0];
                                        break;
                                    case "setAlignment":
                                        center = args[0] == TextDisplay.TextAlignment.CENTER;
                                        break;
                                    case "setBillboard":
                                        billboard =
                                                args[0]
                                                        == org.bukkit.entity.Display.Billboard
                                                                .CENTER;
                                        break;
                                    case "setShadowed":
                                        shadow = (boolean) args[0];
                                        break;
                                    case "setBackgroundColor":
                                        transparent = ((org.bukkit.Color) args[0]).getAlpha() == 0;
                                        break;
                                    case "text":
                                        if (args.length == 0) return text;
                                        if (fail)
                                            throw new IllegalStateException(
                                                    "injected display text failure");
                                        if (visibleByDefault)
                                            throw new AssertionError(
                                                    "private text set before visibility");
                                        text = (Component) args[0];
                                        writes++;
                                        break;
                                    default:
                                        return DEFAULT;
                                }
                                return null;
                            });
        }

        String line() {
            return dev.vapee.core.quest.QuestCompletionFixture.plain(text);
        }
    }

    private static final class Viewer {
        final UUID id;
        final Player player;
        final Set<FakeDisplay> shown = new LinkedHashSet<>();
        boolean online = true;
        Locale locale = Locale.ENGLISH;
        World world;

        Viewer(NpcWorld fixture, UUID id) {
            this.id = id;
            this.world = fixture.world;
            player =
                    proxy(
                            Player.class,
                            (method, args) ->
                                    switch (method) {
                                        case "getUniqueId" -> id;
                                        case "isOnline" -> online;
                                        case "getWorld" -> world;
                                        case "locale" -> locale;
                                        case "showEntity" -> {
                                            var display =
                                                    fixture.displays.stream()
                                                            .filter(
                                                                    candidate ->
                                                                            candidate.entity
                                                                                    == args[1])
                                                            .findFirst()
                                                            .orElseThrow();
                                            if (display.visibleByDefault || !display.valid)
                                                throw new AssertionError(
                                                        "public or invalid display");
                                            shown.add(display);
                                            yield null;
                                        }
                                        case "hideEntity" -> {
                                            shown.removeIf(display -> display.entity == args[1]);
                                            yield null;
                                        }
                                        default -> DEFAULT;
                                    });
        }

        FakeDisplay onlyDisplay() {
            if (shown.size() != 1)
                throw new AssertionError(
                        "Expected exactly one display for " + id + ", got " + shown.size());
            return shown.iterator().next();
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
