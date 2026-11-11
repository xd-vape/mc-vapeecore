package dev.vapee.core.quest.daily;

import dev.vapee.core.i18n.LocalizationService;
import dev.vapee.core.quest.QuestService;

import io.papermc.paper.datacomponent.item.ResolvableProfile;

import net.kyori.adventure.text.Component;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.logging.Level;

final class QuestNpc {
    private final JavaPlugin plugin;
    private final Server server;
    private final QuestNpcConfig config;
    private final BooleanSupplier authorized;
    private final Function<String, CompletableFuture<ResolvableProfile>> resolveSkin;
    private QuestNpcConfig.State state;
    private Mannequin entity;
    private BukkitTask spawnTask;
    private Object spawnTicket;
    private SkinLookup skin;
    private QuestNpcHolograms holograms;
    private boolean spawning;
    private boolean retired;

    QuestNpc(JavaPlugin plugin, BooleanSupplier authorized) {
        this(plugin, authorized, name -> playerSkin(plugin.getServer(), name));
    }

    private static CompletableFuture<ResolvableProfile> playerSkin(Server server, String name) {
        var online = server.getPlayerExact(name);
        if (online != null) {
            var profile = online.getPlayerProfile();
            if (profile.getTextures().getSkin() != null)
                return CompletableFuture.completedFuture(
                        ResolvableProfile.resolvableProfile(profile));
        }
        return server.createProfileExact(null, name)
                .update()
                .thenApply(
                        profile ->
                                profile.getTextures().getSkin() == null
                                        ? null
                                        : ResolvableProfile.resolvableProfile(profile));
    }

    QuestNpc(
            JavaPlugin plugin,
            BooleanSupplier authorized,
            Function<String, CompletableFuture<ResolvableProfile>> resolveSkin) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.config =
                new QuestNpcConfig(
                        plugin.getDataFolder().toPath().resolve("quest-npc.yml"),
                        plugin.getLogger());
        this.authorized = authorized;
        this.resolveSkin = resolveSkin;
    }

    QuestNpcConfig.State readConfig() {
        return config.read();
    }

    QuestNpcConfig.State state() {
        return state;
    }

    void attachHolograms(
            QuestService quests, LocalizationService localization, BooleanSupplier dailyEnabled) {
        holograms =
                new QuestNpcHolograms(
                        plugin,
                        quests,
                        localization,
                        authorized,
                        dailyEnabled,
                        () -> entity,
                        () -> state.hologram());
    }

    void refreshHolograms() {
        if (holograms != null) holograms.refreshIfTracked();
    }

    void viewerChanged(UUID id) {
        if (holograms != null) holograms.viewerChanged(id);
    }

    void trackingChanged(Entity candidate) {
        if (holograms != null && owns(candidate)) holograms.request();
    }

    void apply(QuestNpcConfig.State next) {
        if (retired || spawning) throw new IllegalStateException("Quest NPC is changing lifecycle");
        cancelSpawn();
        invalidateSkin();
        if (holograms != null) holograms.reset();
        boolean replace = !next.sameMannequin(state);
        if (replace) removeEntity();
        else if (entity != null
                && entity.isValid()
                && next.hologram().enabled() != state.hologram().enabled())
            configureName(entity, next);
        state = next;
        reconcile();
    }

    void save(QuestNpcConfig.State next) {
        if (!authorized.getAsBoolean() || spawning)
            throw new IllegalStateException("Quest NPC is not available");
        var previous = state;
        try {
            apply(next);
            config.save(previous, next);
        } catch (RuntimeException failure) {
            try {
                apply(previous);
            } catch (RuntimeException restoration) {
                failure.addSuppressed(restoration);
            }
            throw failure;
        }
    }

    boolean owns(Entity candidate) {
        return entity != null && entity == candidate;
    }

    void chunkLoaded(Chunk chunk) {
        if (!authorized.getAsBoolean() || retired || state.position() == null || spawnTask != null)
            return;
        var position = state.position();
        if (!chunk.getWorld().getName().equals(position.world())
                || chunk.getX() != position.chunkX()
                || chunk.getZ() != position.chunkZ()) return;
        Object ticket = new Object();
        spawnTicket = ticket;
        try {
            spawnTask =
                    server.getScheduler()
                            .runTask(
                                    plugin,
                                    () -> {
                                        if (spawnTicket != ticket) return;
                                        spawnTicket = null;
                                        spawnTask = null;
                                        if (!authorized.getAsBoolean() || retired) return;
                                        try {
                                            reconcile();
                                        } catch (RuntimeException failure) {
                                            log(
                                                    "Could not spawn quest NPC in its loaded chunk",
                                                    failure);
                                        }
                                    });
        } catch (RuntimeException failure) {
            spawnTicket = null;
            log("Could not schedule quest NPC chunk reconciliation", failure);
        }
    }

    private void reconcile() {
        var position = state.position();
        if (position == null || spawning) return;
        if (entity != null && entity.isValid()) {
            requestSkin();
            refreshHolograms();
            return;
        }
        var world = server.getWorld(position.world());
        if (world == null) return;
        if (position.y() < world.getMinHeight() || position.y() >= world.getMaxHeight())
            throw new IllegalArgumentException(
                    "Quest NPC location is outside its world's build height");
        if (!world.isChunkLoaded(position.chunkX(), position.chunkZ())
                || !world.getChunkAt(position.chunkX(), position.chunkZ()).isEntitiesLoaded())
            return;
        var location =
                new Location(
                        world,
                        position.x(),
                        position.y(),
                        position.z(),
                        position.yaw(),
                        position.pitch());
        spawning = true;
        try {
            var spawned =
                    world.spawn(
                            location,
                            Mannequin.class,
                            SpawnReason.CUSTOM,
                            false,
                            candidate -> {
                                entity = candidate;
                                candidate.setPersistent(false);
                                candidate.setInvulnerable(true);
                                candidate.setImmovable(true);
                                candidate.setGravity(false);
                                candidate.setCollidable(false);
                                candidate.setCanPickupItems(false);
                                candidate.setSilent(true);
                                configureName(candidate, state);
                                candidate.setDescription(Component.empty());
                                if (skin != null && skin.profile != null)
                                    candidate.setProfile(skin.profile);
                            });
            if (retired || entity != spawned || !spawned.isValid())
                throw new IllegalStateException("Quest NPC spawn was not accepted");
            requestSkin();
            refreshHolograms();
        } catch (RuntimeException failure) {
            try {
                removeEntity();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        } finally {
            spawning = false;
        }
    }

    private static void configureName(Mannequin mannequin, QuestNpcConfig.State state) {
        boolean showName = !state.hologram().enabled();
        mannequin.customName(showName ? Component.text(state.name()) : null);
        mannequin.setCustomNameVisible(showName);
    }

    private void requestSkin() {
        if (state.skin().isEmpty() || skin != null) return;
        var requested = new SkinLookup();
        skin = requested;
        try {
            requested.callback =
                    resolveSkin
                            .apply(state.skin())
                            .whenCompleteAsync(
                                    (profile, failure) -> {
                                        if (skin != requested
                                                || retired
                                                || !authorized.getAsBoolean()) return;
                                        if (failure != null) {
                                            log(
                                                    "Could not resolve quest NPC skin for "
                                                            + state.skin(),
                                                    failure);
                                        } else if (profile != null) {
                                            requested.profile = profile;
                                            if (entity != null && entity.isValid()) {
                                                try {
                                                    entity.setProfile(profile);
                                                } catch (RuntimeException updateFailure) {
                                                    log(
                                                            "Could not apply quest NPC skin",
                                                            updateFailure);
                                                }
                                            }
                                        }
                                    },
                                    server.getScheduler().getMainThreadExecutor(plugin));
        } catch (RuntimeException failure) {
            log("Could not start quest NPC skin lookup for " + state.skin(), failure);
        }
    }

    private void cancelSpawn() {
        spawnTicket = null;
        if (spawnTask == null) return;
        spawnTask.cancel();
        if (!spawnTask.isCancelled())
            throw new IllegalStateException("Quest NPC spawn cancellation not confirmed");
        spawnTask = null;
    }

    private void invalidateSkin() {
        var previous = skin;
        skin = null;
        if (previous != null && previous.callback != null) previous.callback.cancel(false);
    }

    private void removeEntity() {
        if (entity == null) return;
        entity.remove();
        if (entity.isValid())
            throw new IllegalStateException("Quest NPC removal was not confirmed");
        entity = null;
    }

    void shutdown() {
        retired = true;
        invalidateSkin();
        var failure = new IllegalStateException("Quest NPC cleanup incomplete");
        if (holograms != null) {
            try {
                holograms.shutdown();
                holograms = null;
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            }
        }
        try {
            try {
                cancelSpawn();
            } finally {
                if (spawning) throw new IllegalStateException("Quest NPC is still spawning");
                removeEntity();
            }
        } catch (RuntimeException cleanup) {
            failure.addSuppressed(cleanup);
        }
        if (failure.getSuppressed().length > 0) throw failure;
    }

    private void log(String message, Throwable failure) {
        plugin.getLogger().log(Level.WARNING, message, failure);
    }

    private static final class SkinLookup {
        private CompletableFuture<?> callback;
        private ResolvableProfile profile;
    }
}
