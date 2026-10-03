package dev.vapee.core.quest;

import net.kyori.adventure.text.Component;
import java.util.*;
import static dev.vapee.core.quest.QuestCompletionFixture.*;

public final class QuestProgressReporterHarness {
    private static int checks;
    public static void main(String[] args) {
        QuestCompletionFixture f = new QuestCompletionFixture();
        f.define(definition("z", "test:key", 2, 20), definition("a", "test:key", 2, 10));
        UUID id = f.load();
        f.quests.replaceAssignments(id, List.of("z", "a"));
        List<Component> messages = new ArrayList<>();
        QuestProgressReporter reporter = new QuestProgressReporter(f.quests, (player, message) -> messages.add(message), f.logger);
        reporter.report(id, QuestProgressKey.of("test:key"), 1);
        check(messages.isEmpty(), "partial progress stays quiet");
        QuestProgressResult result = reporter.report(id, QuestProgressKey.of("test:key"), Long.MAX_VALUE);
        check(result.reachedQuestIds().equals(List.of("a", "z")), "first target reach is identified in deterministic ID order");
        check(messages.size() == 2 && plain(messages.getFirst()).equals("Daily quest complete: "
                + f.registry.findById("a").orElseThrow().name() + "\n+10 Coins"), "first completion contains literal name and reward");
        check(plain(messages.getLast()).endsWith("\n+20 Coins") && messages.stream().noneMatch(QuestCompletionFixture::hasEvent),
                "simultaneous completions are ordered and configured markup cannot inject events");
        check(f.economy.getCoins(id).orElseThrow() == 30, "real RewardService grants both definition rewards");
        reporter.report(id, QuestProgressKey.of("test:key"), 1);
        check(messages.size() == 2 && f.economy.getCoins(id).orElseThrow() == 30, "completed repeat cannot reward or message again");
        check(reporter.report(UUID.randomUUID(), QuestProgressKey.of("test:key"), 1).status()
                == QuestProgressResult.Status.PLAYER_NOT_LOADED && messages.size() == 2, "unloaded players remain untouched");
        check(reporter.report(id, QuestProgressKey.of("future:event"), 1).status()
                == QuestProgressResult.Status.NO_MATCHING_QUEST, "unknown producer key stays inert");
        boolean immutable = false;
        try { result.reachedQuestIds().add("injected"); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "reached IDs are immutable");
        check(new QuestProgressResult(QuestProgressResult.Status.PROCESSED, 1, List.of("a"), List.of())
                .reachedQuestIds().isEmpty(), "existing four-argument result preserves retry interpretation");

        f.define(definition("pending", "test:pending", 1, 5));
        f.quests.replaceAssignments(id, List.of("pending"));
        f.economy.setCoins(id, Long.MAX_VALUE);
        messages.clear();
        var pending = reporter.report(id, QuestProgressKey.of("test:pending"), 1);
        check(pending.reachedQuestIds().equals(List.of("pending")) && pending.rewardPendingQuestIds().equals(List.of("pending")),
                "first failed grant reports reach and pending separately");
        check(messages.size() == 1 && plain(messages.getFirst()).endsWith("Your reward is pending and will be retried."),
                "first pending reach has controlled completion feedback");
        var retry = reporter.report(id, QuestProgressKey.of("test:pending"), 1);
        check(retry.reachedQuestIds().isEmpty() && messages.size() == 1, "failed retries produce no completion spam");
        f.economy.setCoins(id, 0);
        reporter.report(id, QuestProgressKey.of("test:pending"), 1);
        check(messages.size() == 2 && plain(messages.getLast()).startsWith("Quest reward delivered: ")
                && plain(messages.getLast()).endsWith("\n+5 Coins"), "successful later retry has one delivery message");
        reporter.report(id, QuestProgressKey.of("test:pending"), 1);
        check(messages.size() == 2 && f.economy.getCoins(id).orElseThrow() == 5, "delivery cannot be repeated");

        f.define(definition("one", "test:throw", 1, 7), definition("two", "test:throw", 1, 9));
        f.quests.replaceAssignments(id, List.of("one", "two"));
        int[] calls = {0};
        QuestProgressReporter throwing = new QuestProgressReporter(f.quests, (player, message) -> {
            calls[0]++; if (calls[0] == 1) throw new IllegalStateException("synthetic chat failure");
        }, f.logger);
        throwing.report(id, QuestProgressKey.of("test:throw"), 1);
        check(calls[0] == 2 && f.economy.getCoins(id).orElseThrow() == 21, "feedback failure neither rolls back rewards nor suppresses next feedback");
        check(f.quests.getActiveQuests(id).orElseThrow().stream().allMatch(v -> v.status() == QuestStatus.COMPLETED)
                && f.logs.stream().anyMatch(log -> log.getMessage().contains(id.toString()) && log.getThrown() != null),
                "chat failure leaves completed state and logs player plus cause");
        f.quests.replaceAssignments(id, List.of("one"));
        throwing.silence();
        throwing.report(id, QuestProgressKey.of("test:throw"), 1);
        check(calls[0] == 2 && f.economy.getCoins(id).orElseThrow() == 28, "shutdown sampling grants silently");
        System.out.println("QuestProgressReporterHarness passed " + checks + " checks.");
    }
    private static void check(boolean condition, String text) { checks++; if (!condition) throw new AssertionError(text); }
}
