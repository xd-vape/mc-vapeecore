package dev.vapee.core.quest.daily.menu;

import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.quest.QuestCompletionFixture.*;
import static dev.vapee.core.quest.daily.menu.DailyQuestMenu.Action.*;

public final class QuestLayoutHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        for (var content : List.of(LayoutTestSupport.SPARSE, LayoutTestSupport.REORDERED, LayoutTestSupport.HIGH_CONTENT)) {
            var layout = LayoutTestSupport.paged(DailyQuestMenu.DEFAULT_LAYOUT, content == LayoutTestSupport.HIGH_CONTENT ? 54 : 45, content);
            if (content == LayoutTestSupport.HIGH_CONTENT) layout = new MenuLayout<>(layout.size(), layout.contentSlots(), 40, layout.actions());
            var f = new QuestMenuFixture(layout); var owner = f.player();
            var definitions = new ArrayList<dev.vapee.core.quest.QuestDefinition>();
            for (int i = 0; i < layout.pageSize() + 1; i++) definitions.add(definition("q%03d".formatted(i), "future:event", 100, 1));
            f.domain.registry.replaceAll(definitions);
            f.menu.open(owner.player, 0); var first = owner.open;
            var holder = (DailyQuestInventoryHolder) first.getHolder();
            check(first.getSize() == layout.size(), "alternative inventory size");
            var expected = f.domain.quests.getActiveQuests(owner.id).orElseThrow();
            for (int i = 0; i < layout.pageSize(); i++) check(holder.target(layout.contentSlot(i)).orElseThrow().equals(expected.get(i).definition().id()) && first.getItem(layout.contentSlot(i)) != null, "read-only quest ID mapping at actual slot");
            var state = f.domain.players.getPlayer(owner.id).orElseThrow().getQuestState().snapshot();
            int sync = f.syncCalls, saves = f.domain.repository.saves;
            f.click(owner, first, layout.contentSlot(0), ClickType.LEFT);
            check(f.syncCalls == sync && f.domain.repository.saves == saves && f.domain.players.getPlayer(owner.id).orElseThrow().getQuestState().snapshot().equals(state), "entry clicks cannot mutate/save/grant rewards");
            f.click(owner, first, layout.slot(NEXT), ClickType.RIGHT);
            check(((DailyQuestInventoryHolder) owner.open.getHolder()).page() == 1 && ((DailyQuestInventoryHolder) owner.open.getHolder()).target(layout.contentSlot(0)).orElseThrow().equals(expected.getLast().definition().id()), "page capacity follows content list");
            f.click(owner, owner.open, layout.slot(PREVIOUS), ClickType.LEFT);
            var current = owner.open;
            f.click(owner, first, layout.slot(CLOSE), ClickType.LEFT);
            check(owner.open == current, "stale close inert");
            f.click(owner, current, layout.slot(REFRESH), ClickType.LEFT);
            check(owner.open != current && ((DailyQuestInventoryHolder) owner.open.getHolder()).page() == 0, "refresh rebuilds current page");
            f.click(owner, owner.open, layout.slot(CLOSE), ClickType.RIGHT);
            check(owner.open == owner.bottom && f.menu.activeCount() == 0, "close and cleanup follow layout");
        }
        System.out.println("QuestLayoutHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
