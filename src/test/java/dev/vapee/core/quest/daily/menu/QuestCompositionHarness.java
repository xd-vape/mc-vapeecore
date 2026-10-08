package dev.vapee.core.quest.daily.menu;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
public final class QuestCompositionHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        { var f = new QuestMenuFixture(DailyQuestMenu.DEFAULT_LAYOUT); var p = f.player(); f.domain.registry.replaceAll(List.of(dev.vapee.core.quest.QuestCompletionFixture.definition("optional", "future:event", 1, 1))); f.menu.open(p.player, 0);
          check(p.open.getSize() == f.menu.layout().size(), "current production default opens"); }
        for (var action : DailyQuestMenu.Action.values()) {
            var layout = CompositionTestSupport.without(MenuTestLayouts.QUEST, action);
            var f = new QuestMenuFixture(layout); var p = f.player(); f.domain.registry.replaceAll(List.of(dev.vapee.core.quest.QuestCompletionFixture.definition("optional", "future:event", 1, 1))); f.menu.open(p.player, 0);
            var top = p.open; int slot = MenuTestLayouts.QUEST.slot(action);
            check(top.getItem(slot) == null && layout.actionAt(slot).isEmpty(), "omitted control " + action);
            f.click(p, top, slot, ClickType.LEFT);
            check(p.open == top && f.menu.isActive(p.player, top, (DailyQuestInventoryHolder) top.getHolder()), "absent control cannot act " + action);
        }
        var layout = MenuLayout.builder(27, DailyQuestMenu.Action.class).content(List.of(10, 12)).emptyState(22).build();
        var f = new QuestMenuFixture(layout); var p = f.player(); f.domain.registry.replaceAll(List.of(dev.vapee.core.quest.QuestCompletionFixture.definition("optional", "future:event", 1, 1))); f.menu.open(p.player, 0);
        var top = p.open;
        check(top.getSize() == 27 && layout.actions().isEmpty(), "minimal paged menu without controls");
        for (int slot = 0; slot < top.getSize(); slot++)
            check(top.getItem(slot) == null || layout.isContent(slot) || slot == layout.emptySlot(), "only declared content or empty marker " + slot);
        System.out.println("QuestCompositionHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
