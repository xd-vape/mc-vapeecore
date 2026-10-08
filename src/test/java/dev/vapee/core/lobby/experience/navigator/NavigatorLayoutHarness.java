package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.lobby.experience.navigator.NavigatorMenu.Action.*;

public final class NavigatorLayoutHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        for (var content : List.of(LayoutTestSupport.SPARSE, LayoutTestSupport.REORDERED, LayoutTestSupport.HIGH_CONTENT)) {
            var layout = LayoutTestSupport.paged(NavigatorMenu.DEFAULT_LAYOUT, content == LayoutTestSupport.HIGH_CONTENT ? 54 : 45, content);
            if (content == LayoutTestSupport.HIGH_CONTENT) layout = new MenuLayout<>(layout.size(), layout.contentSlots(), 40, layout.actions());
            var f = new NavigatorFixture(layout); var owner = f.player();
            check(f.menu.open(owner.player) && owner.open.getSize() == layout.size() && owner.open.getItem(layout.emptySlot()) != null, "empty alternative layout");
            f.destinations(layout.pageSize() + 1);
            var expected = f.warps.getNavigatorWarps();
            f.menu.open(owner.player); var first = owner.open;
            var holder = (NavigatorInventoryHolder) first.getHolder();
            for (int i = 0; i < layout.pageSize(); i++) check(holder.getWarpId(layout.contentSlot(i)).orElseThrow().equals(expected.get(i).id()) && first.getItem(layout.contentSlot(i)) != null, "negative control 2: ordered explicit warp slots");
            f.click(owner, first, layout.slot(NEXT), ClickType.RIGHT);
            check(((NavigatorInventoryHolder) owner.open.getHolder()).getPage() == 1 && ((NavigatorInventoryHolder) owner.open.getHolder()).getWarpId(layout.contentSlot(0)).orElseThrow().equals(expected.getLast().id()), "capacity determines page boundary");
            f.click(owner, owner.open, layout.slot(PREVIOUS), ClickType.LEFT);
            var current = owner.open;
            f.click(owner, first, layout.contentSlot(0), ClickType.LEFT);
            check(owner.teleports == 0 && owner.open == current, "stale mapped warp cannot teleport");
            f.click(owner, current, layout.contentSlot(1), ClickType.LEFT);
            check(owner.teleports == 1 && owner.open == owner.bottom, "moved content invokes existing teleport");
            f.menu.open(owner.player);
            check(f.drag(owner, owner.open, layout.contentSlot(0)).isCancelled(), "alternative content protected against drag");
            f.click(owner, owner.open, layout.slot(CLOSE), ClickType.LEFT);
            check(owner.open == owner.bottom && !f.menu.isActive(owner.player, holder.getInventory(), holder), "moved close and cleanup");
        }
        System.out.println("NavigatorLayoutHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
