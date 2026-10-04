package dev.vapee.core.quest;

import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.*;
import static dev.vapee.core.quest.QuestCompletionFixture.*;

public final class QuestPlaytimeProducerHarness {
    private static int checks;
    public static void main(String[] args) {
        QuestCompletionFixture f = new QuestCompletionFixture();
        UUID id = f.load();
        List<Long> progress = new ArrayList<>();
        QuestPlaytimeProducer producer = new QuestPlaytimeProducer(f.players::isLoaded,
                (player, minutes) -> progress.add(minutes), f.logger);
        producer.sample(id, 1199);
        check(progress.isEmpty(), "first sample establishes baseline without historical progress");
        producer.sample(id, 1200);
        check(progress.equals(List.of(1L)), "1199 to 1200 crosses exactly one minute boundary");
        producer.sample(id, 2399);
        check(progress.size() == 1, "remaining partial minute does not count");
        producer.sample(id, 7200);
        check(progress.equals(List.of(1L, 5L)), "lagged samples report all crossed boundaries in one call");
        producer.sample(id, 7200);
        check(progress.size() == 2, "identical statistic is inert");
        producer.sample(id, 1200);
        check(progress.size() == 2 && f.logs.getLast().getMessage().contains(id.toString()), "decreased statistic rebases with UUID warning");
        producer.sample(id, 2400);
        check(progress.getLast() == 1L, "new boundary after reset counts from rebase");
        producer.seed(id, -100);
        producer.sample(id, -5);
        check(progress.size() == 3, "negative statistics clamp to zero");
        producer.sample(id, 1200);
        check(progress.getLast() == 1L && progress.size() == 4, "clamped baseline can advance normally");
        producer.forget(id);
        producer.sample(id, 100_000);
        check(progress.size() == 4, "rejoin discards old runtime baseline");
        UUID unloaded = UUID.randomUUID();
        producer.seed(unloaded, 0); producer.sample(unloaded, 12_000);
        check(producer.trackedPlayers() == 1 && progress.size() == 4, "unloaded players are never tracked or progressed");
        producer.clear();
        check(producer.trackedPlayers() == 0, "disable clears all runtime samples");
        UUID second = f.load();
        producer.seed(id, 100_000);
        producer.seed(second, 1199);
        int before = progress.size();
        producer.sample(id, 100_001);
        producer.sample(second, 1200);
        check(progress.size() == before + 1 && progress.getLast() == 1L,
                "enable seed grants no historical minutes and players have independent baselines");
        producer.clear();

        f.define(definition("play", "playtime:minute", 10, 25));
        f.quests.assignQuest(id, "play");
        var onlineReward = f.players.getPlayer(id).orElseThrow().getOnlineRewardProgress();
        onlineReward.setProcessedPlaytimeTicks(123456L);
        QuestProgressReporter reporter = new QuestProgressReporter(f.quests, (player, message) -> { }, f.logger);
        producer = new QuestPlaytimeProducer(f.players, reporter, f.logger);
        QuestListener listener = new QuestListener(f.quests, producer, f.logger);
        long[] ticks = {1199}; boolean[] fail = {false};
        Player player = proxy(Player.class, (method, arguments) -> switch (method) {
            case "getUniqueId" -> id;
            case "isOnline" -> true;
            case "getStatistic" -> { if (fail[0]) throw new IllegalStateException("synthetic statistic failure"); yield (int) ticks[0]; }
            default -> DEFAULT;
        });
        listener.onPlayerJoin(new PlayerJoinEvent(player, (net.kyori.adventure.text.Component) null));
        ticks[0] = 1200;
        int saves = f.repository.saves;
        listener.onPlayerQuit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        check(f.repository.persisted.get(id).get("play").progress() == 1 && f.repository.saves == saves + 1,
                "LOWEST quit final sample precedes flush while profile remains loaded");
        check(producer.trackedPlayers() == 0 && !f.quests.isDirty(id), "quit forgets sample after final persistence");
        check(f.players.getPlayer(id).orElseThrow().getOnlineRewardProgress() == onlineReward
                        && onlineReward.getProcessedPlaytimeTicks().orElseThrow() == 123456L,
                "quest producer does not replace online reward accounting");
        producer.seed(id, 2400);
        f.quests.addProgress(id, QuestProgressKey.of("playtime:minute"), 1);
        fail[0] = true; saves = f.repository.saves;
        listener.onPlayerQuit(new PlayerQuitEvent(player, net.kyori.adventure.text.Component.empty(), PlayerQuitEvent.QuitReason.DISCONNECTED));
        check(f.repository.saves == saves + 1 && producer.trackedPlayers() == 0,
                "statistic failure still flushes existing dirty progress and forgets sample");
        check(f.logs.stream().anyMatch(log -> log.getThrown() != null && log.getMessage().contains(id.toString())),
                "statistic failure logs its UUID and cause");
        System.out.println("QuestPlaytimeProducerHarness passed " + checks + " checks.");
    }
    private static void check(boolean condition, String text) { checks++; if (!condition) throw new AssertionError(text); }
}
