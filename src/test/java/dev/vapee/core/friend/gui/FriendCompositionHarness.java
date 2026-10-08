package dev.vapee.core.friend.gui;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
public final class FriendCompositionHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        { var f = new FriendMenuFixture(FriendMenu.DEFAULT_LAYOUT); var p = f.player("Owner", true); f.menu.open(p.player);
          check(p.open.getSize() == f.menu.layout().size(), "current production default opens"); }
        for (var action : FriendMenu.Action.values()) {
            var layout = CompositionTestSupport.without(MenuTestLayouts.FRIEND, action);
            var f = new FriendMenuFixture(layout); var p = f.player("Owner", true); f.menu.open(p.player);
            var top = p.open; int slot = MenuTestLayouts.FRIEND.slot(action);
            check(top.getItem(slot) == null && layout.actionAt(slot).isEmpty(), "omitted control " + action);
            f.click(p, top, slot, ClickType.LEFT);
            check(p.open == top && f.menu.isActive(p.player, top, (FriendMenuHolder) top.getHolder()), "absent control cannot act " + action);
        }
        var layout = MenuLayout.builder(27, FriendMenu.Action.class).content(List.of(10, 12)).emptyState(22).build();
        var f = new FriendMenuFixture(layout); var p = f.player("Owner", true); f.menu.open(p.player);
        var top = p.open;
        check(top.getSize() == 27 && layout.actions().isEmpty(), "minimal paged menu without controls");
        for (int slot = 0; slot < top.getSize(); slot++)
            check(top.getItem(slot) == null || layout.isContent(slot) || slot == layout.emptySlot(), "only declared content or empty marker " + slot);
        System.out.println("FriendCompositionHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
