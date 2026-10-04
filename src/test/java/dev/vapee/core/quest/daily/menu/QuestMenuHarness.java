package dev.vapee.core.quest.daily.menu;

import dev.vapee.core.quest.*;
import dev.vapee.core.quest.daily.DailyQuestSyncResult;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.quest.QuestCompletionFixture.*;
import static dev.vapee.core.quest.daily.menu.QuestMenuFixture.*;

public final class QuestMenuHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        QuestMenuFixture f = new QuestMenuFixture();
        var player = f.player();
        List<QuestDefinition> definitions = new ArrayList<>();
        for (int i = 0; i < 91; i++) definitions.add(definition("q%03d".formatted(i), "future:event", Long.MAX_VALUE, Long.MAX_VALUE));
        f.domain.registry.replaceAll(definitions);
        f.menu.open(player.player, -100);
        check(f.syncCalls == 1 && player.open.getSize() == 54 && text(player.open, 49).equals("Page 1 / 3"),
                "real daily initialization occurs before opening clamped first page");
        check(player.open.getItem(44) != null && player.open.getItem(45) == null && player.open.getItem(53) != null,
                "45 content slots and functional next-only first-page controls");
        check(text(player.open, 0).equals(definitions.getFirst().name()) && lore(player.open, 0).contains(definitions.getFirst().description()),
                "configured name and description remain literal");
        check(!hasEvent(spec(player.open, 0).name()) && spec(player.open, 0).lore().stream().noneMatch(QuestCompletionFixture::hasEvent),
                "quest item cannot inject click or hover events");
        check(lore(player.open, 0).contains("0 / " + Long.MAX_VALUE) && lore(player.open, 0).contains(Long.MAX_VALUE + " Coins")
                && !lore(player.open, 0).contains("future:event") && !lore(player.open, 0).contains("q000"),
                "long values render without overflow; technical key and ID absent from lore");
        var state = f.domain.players.getPlayer(player.id).orElseThrow().getQuestState().snapshot();
        int saves = f.domain.repository.saves;
        f.click(player, player.open, 0, ClickType.LEFT);
        f.click(player, player.open, 49, ClickType.RIGHT);
        check(state.equals(f.domain.players.getPlayer(player.id).orElseThrow().getQuestState().snapshot())
                && f.domain.repository.saves == saves && f.domain.economy.getCoins(player.id).orElseThrow() == 0,
                "quest and page-info clicks cannot mutate state, save or grant coins");
        f.click(player, player.open, 53, ClickType.RIGHT);
        check(text(player.open, 49).equals("Page 2 / 3") && text(player.open, 0).equals(definitions.get(45).name()),
                "next renders deterministic next 45 definitions");
        f.click(player, player.open, 45, ClickType.LEFT);
        check(text(player.open, 49).equals("Page 1 / 3"), "previous control navigates");
        f.menu.open(player.player, Integer.MAX_VALUE);
        check(text(player.open, 49).equals("Page 3 / 3") && player.open.getItem(1) == null && player.open.getItem(53) == null,
                "oversized page clamps to final single-entry page");
        f.domain.registry.replaceAll(List.of(definitions.getFirst()));
        f.click(player, player.open, 52, ClickType.LEFT);
        check(text(player.open, 49).equals("Page 1 / 1"), "refresh reclamps after current definition catalog shrinks");
        check(state.size() == 91 && f.domain.players.getPlayer(player.id).orElseThrow().getQuestState().snapshot().size() == 91,
                "same-cycle refresh and catalog changes do not reroll persisted assignments");
        f.click(player, player.open, 50, ClickType.LEFT);
        check(player.open == player.bottom && f.menu.activeCount() == 0, "close control releases exact active binding");

        f.domain.define(definition("status", "playtime:minute", 1, 5));
        f.domain.quests.replaceAssignments(player.id, List.of("status"));
        f.forcedResult = DailyQuestSyncResult.CURRENT;
        f.domain.economy.setCoins(player.id, Long.MAX_VALUE);
        f.domain.quests.addProgress(player.id, QuestProgressKey.of("playtime:minute"), 1);
        f.menu.open(player.player, 0);
        check(lore(player.open, 0).contains("Reward Pending") && lore(player.open, 0).contains("retried automatically")
                && spec(player.open, 0).name().color().equals(NamedTextColor.YELLOW), "pending displays yellow status and automatic retry notice");
        f.domain.economy.setCoins(player.id, 0);
        f.domain.quests.addProgress(player.id, QuestProgressKey.of("playtime:minute"), 1);
        f.click(player, player.open, 52, ClickType.RIGHT);
        check(lore(player.open, 0).contains("Completed") && lore(player.open, 0).contains("already been delivered")
                && spec(player.open, 0).name().color().equals(NamedTextColor.GREEN), "refresh reads current completion and delivered reward state");
        check(f.domain.economy.getCoins(player.id).orElseThrow() == 5, "display refresh adds no extra reward");

        QuestMenuFixture daily = new QuestMenuFixture();
        var p = daily.player();
        daily.domain.define(definition("daily", "blackjack:win", 1, 5));
        daily.menu.open(p.player, 0);
        var cycle = daily.domain.players.getPlayer(p.id).orElseThrow().getDailyQuestState().cycleId();
        daily.domain.economy.setCoins(p.id, Long.MAX_VALUE);
        daily.domain.quests.addProgress(p.id, QuestProgressKey.of("blackjack:win"), 1);
        daily.now = daily.now.plusSeconds(86400);
        daily.menu.open(p.player, 0);
        check(p.received().contains("A previous quest reward is still pending.")
                && daily.domain.players.getPlayer(p.id).orElseThrow().getDailyQuestState().cycleId().equals(cycle),
                "next-cycle failed pending retry preserves old assignment and shows controlled notice");
        daily.domain.economy.setCoins(p.id, 0);
        daily.click(p, p.open, 52, ClickType.LEFT);
        check(!daily.domain.players.getPlayer(p.id).orElseThrow().getDailyQuestState().cycleId().equals(cycle)
                && lore(p.open, 0).contains("In Progress") && daily.domain.economy.getCoins(p.id).orElseThrow() == 5,
                "existing daily retry clears blocker and rotates once before rendering new cycle");
        daily.enabled = false;
        daily.click(p, p.open, 52, ClickType.LEFT);
        check(p.open == p.bottom && p.received().contains("Daily quests are currently disabled."),
                "current disabled configuration closes owned view on refresh");
        check(f.presentationCalls == 0 && daily.presentationCalls == 0, "menu creates no sound, title or overlay feedback");
        QuestMenuFixture empty = new QuestMenuFixture();
        var emptyPlayer = empty.player();
        empty.forcedResult = DailyQuestSyncResult.CURRENT;
        empty.menu.open(emptyPlayer.player, 99);
        check(text(emptyPlayer.open, 49).equals("Page 1 / 1") && emptyPlayer.open.getItem(0) == null
                        && emptyPlayer.open.getItem(45) == null && emptyPlayer.open.getItem(53) == null
                        && emptyPlayer.open.getItem(50) != null && emptyPlayer.open.getItem(52) != null,
                "empty definition-backed view retains safe single-page controls");
        System.out.println("QuestMenuHarness passed " + checks + " checks.");
    }
    private static void check(boolean condition, String text) { checks++; if (!condition) throw new AssertionError(text); }
}
