package dev.vapee.core.quest.daily;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.quest.QuestProgressResult;
import dev.vapee.core.quest.daily.menu.DailyQuestMenu;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;

import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BooleanSupplier;
import java.util.logging.Level;

/** Presentation only. A completed reward batch reaches this observer exactly once from QuestService. */
final class DailyQuestCompletionFeedback implements BiConsumer<UUID, QuestProgressResult> {
    private final JavaPlugin plugin;
    private final Server server;
    private final PlayerService players;
    private final DailyQuestConfig config;
    private final DailyQuestMenu menu;
    private final BooleanSupplier authorized;
    private final Map<UUID, Opening> pending = new HashMap<>();
    private final Set<BukkitTask> retiredTasks = new LinkedHashSet<>();

    DailyQuestCompletionFeedback(
            JavaPlugin plugin,
            PlayerService players,
            DailyQuestConfig config,
            DailyQuestMenu menu,
            BooleanSupplier authorized) {
        this.plugin = plugin;
        this.server = plugin.getServer();
        this.players = players;
        this.config = config;
        this.menu = menu;
        this.authorized = authorized;
    }

    @Override
    public void accept(UUID owner, QuestProgressResult result) {
        if (!authorized.getAsBoolean() || result.completedQuestIds().isEmpty()) return;
        var state = config.getState();
        if (state.definitions().stream()
                .noneMatch(quest -> result.completedQuestIds().contains(quest.id()))) return;
        var profile = players.getPlayer(owner).orElse(null);
        Player player = server.getPlayer(owner);
        if (profile == null || player == null || !player.isOnline()) return;
        var settings = state.feedback();
        if (settings.soundEnabled()) {
            try {
                player.playSound(Sound.sound(Key.key(settings.sound()), Sound.Source.MASTER, settings.soundVolume(), 1));
            } catch (RuntimeException failure) {
                plugin.getLogger()
                        .log(
                                Level.WARNING,
                                "Could not play daily quest completion sound for " + owner,
                                failure);
            }
        }
        if (!state.enabled()
                || !settings.openMenu()
                || !authorized.getAsBoolean()
                || config.getState() != state) return;
        Opening previous = pending.get(owner);
        if (previous != null && previous.player == player && previous.profile == profile) return;
        discard(owner);
        var opening = new Opening(player, profile);
        opening.task =
                server.getScheduler()
                        .runTaskLater(plugin, () -> open(owner, opening), settings.delayTicks());
        pending.put(owner, opening);
    }

    private void open(UUID owner, Opening opening) {
        if (pending.get(owner) != opening) return;
        opening.task = null;
        try {
            menu.openAutomatically(
                    opening.player,
                    () ->
                            authorized.getAsBoolean()
                                    && pending.get(owner) == opening
                                    && opening.player.isOnline()
                                    && server.getPlayer(owner) == opening.player
                                    && players.getPlayer(owner).orElse(null) == opening.profile);
        } finally {
            pending.remove(owner, opening);
        }
    }

    void forget(Player player) {
        UUID owner = player.getUniqueId();
        Opening opening = pending.get(owner);
        if (opening != null && opening.player == player) discard(owner);
    }

    void joined(Player player) {
        UUID owner = player.getUniqueId();
        if (pending.containsKey(owner) && server.getPlayer(owner) == player) discard(owner);
    }

    private void discard(UUID owner) {
        Opening opening = pending.remove(owner);
        if (opening != null && opening.task != null) {
            retiredTasks.add(opening.task);
            try {
                cancel(opening.task);
            } catch (RuntimeException failure) {
                plugin.getLogger()
                        .log(
                                Level.WARNING,
                                "Could not cancel retired daily quest menu callback",
                                failure);
            }
        }
    }

    void invalidate() {
        for (Opening opening : pending.values())
            if (opening.task != null) retiredTasks.add(opening.task);
        pending.clear();
        RuntimeException failure =
                new IllegalStateException("Daily quest feedback callbacks remain scheduled");
        for (BukkitTask task : List.copyOf(retiredTasks)) {
            try {
                cancel(task);
            } catch (RuntimeException exception) {
                failure.addSuppressed(exception);
            }
        }
        if (failure.getSuppressed().length != 0) throw failure;
    }

    private void cancel(BukkitTask task) {
        task.cancel();
        if (!task.isCancelled())
            throw new IllegalStateException("Daily quest menu cancellation not confirmed");
        retiredTasks.remove(task);
    }

    private static final class Opening {
        private final Player player;
        private final CorePlayer profile;
        private BukkitTask task;

        private Opening(Player player, CorePlayer profile) {
            this.player = player;
            this.profile = profile;
        }
    }
}
