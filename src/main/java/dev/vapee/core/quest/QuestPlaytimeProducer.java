package dev.vapee.core.quest;

import dev.vapee.core.player.PlayerService;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.logging.Logger;

/** Main-thread runtime baselines; no persisted or offline playtime accounting. */
public final class QuestPlaytimeProducer {
    public static final long TICKS_PER_MINUTE = 1200L;
    private static final QuestProgressKey KEY = QuestProgressKey.of("playtime:minute");
    private final Predicate<UUID> loaded;
    private final BiConsumer<UUID, Long> progress;
    private final Logger logger;
    private final Map<UUID, Long> samples = new HashMap<>();

    public QuestPlaytimeProducer(PlayerService players, QuestProgressReporter reporter, Logger logger) {
        this(id -> players.getPlayer(id).isPresent(),
                (id, minutes) -> reporter.report(id, KEY, minutes), logger);
        Objects.requireNonNull(players, "players");
        Objects.requireNonNull(reporter, "reporter");
    }

    QuestPlaytimeProducer(Predicate<UUID> loaded, BiConsumer<UUID, Long> progress, Logger logger) {
        this.loaded = Objects.requireNonNull(loaded, "loaded");
        this.progress = Objects.requireNonNull(progress, "progress");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void seed(Player player) {
        UUID id = player.getUniqueId();
        if (!player.isOnline() || !loaded.test(id)) return;
        seed(id, player.getStatistic(Statistic.PLAY_ONE_MINUTE));
    }

    void seed(UUID id, long ticks) {
        if (loaded.test(id)) samples.put(id, Math.max(0L, ticks));
    }

    public void sample(Player player) {
        UUID id = player.getUniqueId();
        if (loaded.test(id)) sample(id, player.getStatistic(Statistic.PLAY_ONE_MINUTE));
    }

    void sample(UUID id, long ticks) {
        Objects.requireNonNull(id, "id");
        if (!loaded.test(id)) return;
        long current = Math.max(0L, ticks);
        Long previous = samples.put(id, current);
        if (previous == null) return;
        if (current < previous) {
            logger.warning("Quest playtime statistic decreased for " + id + "; baseline rebased.");
            return;
        }
        long minutes = current / TICKS_PER_MINUTE - previous / TICKS_PER_MINUTE;
        if (minutes > 0L) progress.accept(id, minutes);
    }

    public void forget(UUID id) { samples.remove(id); }
    void clear() { samples.clear(); }
    int trackedPlayers() { return samples.size(); }
}
