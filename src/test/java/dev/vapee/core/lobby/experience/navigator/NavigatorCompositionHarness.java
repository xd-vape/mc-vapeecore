package dev.vapee.core.lobby.experience.navigator;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
public final class NavigatorCompositionHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        { var f = new NavigatorFixture(NavigatorMenu.DEFAULT_LAYOUT); var p = f.player(); f.menu.open(p.player);
          check(p.open.getSize() == f.menu.layout().size(), "current production default opens"); }
        for (var action : NavigatorMenu.Action.values()) {
            var layout = CompositionTestSupport.without(MenuTestLayouts.NAVIGATOR, action);
            var f = new NavigatorFixture(layout); var p = f.player(); f.menu.open(p.player);
            var top = p.open; int slot = MenuTestLayouts.NAVIGATOR.slot(action);
            check(top.getItem(slot) == null && layout.actionAt(slot).isEmpty(), "omitted control " + action);
            f.click(p, top, slot, ClickType.LEFT);
            check(p.open == top && f.menu.isActive(p.player, top, (NavigatorInventoryHolder) top.getHolder()), "absent control cannot act " + action);
        }
        var layout = MenuLayout.builder(27, NavigatorMenu.Action.class).content(List.of(10, 12)).emptyState(22).build();
        var f = new NavigatorFixture(layout); var p = f.player(); f.menu.open(p.player);
        var top = p.open;
        check(top.getSize() == 27 && layout.actions().isEmpty(), "minimal paged menu without controls");
        for (int slot = 0; slot < top.getSize(); slot++)
            check(top.getItem(slot) == null || layout.isContent(slot) || slot == layout.emptySlot(), "only declared content or empty marker " + slot);
        System.out.println("NavigatorCompositionHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
