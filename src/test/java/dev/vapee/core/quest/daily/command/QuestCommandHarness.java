package dev.vapee.core.quest.daily.command;

import dev.vapee.core.quest.QuestCompletionFixture;
import dev.vapee.core.quest.daily.DailyQuestSyncResult;
import dev.vapee.core.quest.daily.menu.QuestMenuFixture;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import java.util.*;
import static dev.vapee.core.quest.QuestCompletionFixture.*;

public final class QuestCommandHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var f = new QuestMenuFixture();
        var p = f.player();
        f.domain.define(definition("one", "playtime:minute", 1, 1));
        var command = new DailyQuestCommand(f.menu, f.messages);
        p.permission = false;
        run(command, p.player, "quests", "reload");
        check(p.received().contains("permission") && f.syncCalls == 0, "permission gate precedes argument parsing and sync");
        List<Component> output = new ArrayList<>();
        boolean[] permission = {false};
        CommandSender console = proxy(CommandSender.class, (method, arguments) -> switch (method) {
            case "hasPermission" -> permission[0];
            case "sendMessage" -> { for (Object arg : arguments) if (arg instanceof Component c) output.add(c); yield null; }
            default -> DEFAULT;
        });
        run(command, console, "quests");
        check(plain(output.getLast()).contains("permission"), "console also sees permission first");
        permission[0] = true; run(command, console, "quest", "extra");
        check(plain(output.getLast()).contains("Only players") && f.syncCalls == 0, "authorized console gets controlled player-only response before arity");
        p.permission = true;
        for (String sub : List.of("help", "reload", "claim", "reroll", "abandon", "accept", "page")) {
            p.output.clear(); run(command, p.player, "quest", sub);
            check(p.received().contains("Invalid usage.") && p.received().contains("/quests") && f.syncCalls == 0,
                    "no extra command capability: " + sub);
        }
        for (String alias : List.of("quests", "quest")) {
            int before = f.syncCalls;
            run(command, p.player, alias);
            check(f.syncCalls == before + 1 && p.open != p.bottom, "both aliases reach real daily sync before menu");
            check(command.onTabComplete(p.player, null, alias, new String[]{""}).isEmpty()
                    && command.onTabComplete(console, null, alias, new String[]{"a", ""}).isEmpty(), "no subcommand completions");
        }
        Map<DailyQuestSyncResult, String> errors = Map.of(
                DailyQuestSyncResult.DISABLED, "Daily quests are currently disabled.",
                DailyQuestSyncResult.NO_DEFINITIONS, "No daily quests are currently configured.",
                DailyQuestSyncResult.PLAYER_NOT_LOADED, "Your player profile is not available.",
                DailyQuestSyncResult.ASSIGNMENT_FAILED, "Your daily quests could not be prepared. Please try again later.");
        for (DailyQuestSyncResult result : DailyQuestSyncResult.values()) {
            f.forcedResult = result; p.output.clear(); int opens = p.opens;
            run(command, p.player, "quests");
            if (errors.containsKey(result)) check(p.opens == opens && p.received().contains(errors.get(result)) && p.open == p.bottom,
                    "controlled non-open result: " + result);
            else check(p.opens == opens + 1 && (result != DailyQuestSyncResult.BLOCKED_PENDING_REWARD
                            || p.received().contains("A previous quest reward is still pending.")), "menu result handled: " + result);
        }
        f.syncFailure = new IllegalStateException("synthetic assignment failure");
        p.output.clear(); run(command, p.player, "quests");
        check(p.received().contains("could not be prepared") && f.domain.logs.stream()
                .anyMatch(log -> log.getMessage().contains(p.id.toString()) && log.getThrown() == f.syncFailure),
                "thrown assignment failure logs UUID and exact cause with controlled response");
        check(f.domain.economy.getCoins(p.id).orElseThrow() == 0 && f.presentationCalls == 0,
                "commands never grant rewards or trigger presentation effects");
        System.out.println("QuestCommandHarness passed " + checks + " checks.");
    }
    private static void run(DailyQuestCommand command, CommandSender sender, String label, String... args) {
        check(command.onCommand(sender, null, label, args), "command handled without Bukkit usage fallback");
    }
    private static void check(boolean condition, String text) { checks++; if (!condition) throw new AssertionError(text); }
}
