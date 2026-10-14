package dev.vapee.core.quest;

import dev.vapee.core.i18n.*;
import dev.vapee.core.message.MessageService;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import java.util.*;

public final class QuestFeedbackLocalizationHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var f = new QuestCompletionFixture();
        var german = f.load(); var english = f.load();
        String literal = "<click:run_command:'/op me'>";
        f.define(new QuestDefinition("shared-id", new LocalizedText(Map.of("en", literal + "English quest", "de", literal + "Deutsche Quest")),
                LocalizedText.literal("Description"), QuestProgressKey.of("test:key"), 1, 10));
        f.quests.replaceAssignments(german, List.of("shared-id")); f.quests.replaceAssignments(english, List.of("shared-id"));
        Map<UUID, List<Component>> output = Map.of(german, new ArrayList<>(), english, new ArrayList<>());
        Map<UUID, Player> players = new HashMap<>();
        for (UUID id : List.of(german, english)) players.put(id, QuestCompletionFixture.proxy(Player.class, (method, arguments) -> switch (method) {
            case "getUniqueId" -> id; case "isOnline" -> true; case "locale" -> id.equals(german) ? Locale.GERMANY : Locale.US;
            case "sendMessage" -> { for (Object argument : arguments) if (argument instanceof Component c) output.get(id).add(c); yield null; }
            default -> QuestCompletionFixture.DEFAULT;
        }));
        var messages = new MessageService(() -> "", LocalizationService.bundled(f.logger));
        var reporter = new QuestProgressReporter(f.quests, messages, players::get, f.logger);
        reporter.report(german, QuestProgressKey.of("test:key"), 1); reporter.report(english, QuestProgressKey.of("test:key"), 1);
        check(QuestCompletionFixture.plain(output.get(german).getFirst()).equals("Tägliche Quest abgeschlossen: " + literal + "Deutsche Quest\n+10 Coins"), "German progress feedback includes localized plain quest name");
        check(QuestCompletionFixture.plain(output.get(english).getFirst()).equals("Daily quest complete: " + literal + "English quest\n+10 Coins"), "English progress feedback on same server");
        check(output.values().stream().flatMap(List::stream).noneMatch(QuestCompletionFixture::hasEvent), "quest display names cannot inject click events");
        check(f.economy.getCoins(german).orElseThrow() == 10 && f.economy.getCoins(english).orElseThrow() == 10, "localized feedback follows unchanged real rewards");
        check(f.quests.getActiveQuests(german).orElseThrow().getFirst().definition().id().equals("shared-id")
                && f.quests.getActiveQuests(english).orElseThrow().getFirst().definition().id().equals("shared-id"), "same persisted identity independent of language");
        System.out.println("QuestFeedbackLocalizationHarness passed " + checks + " checks.");
    }
    private static void check(boolean condition, String label) { checks++; if (!condition) throw new AssertionError(label); }
}
