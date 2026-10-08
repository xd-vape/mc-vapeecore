package dev.vapee.core.friend.gui;

import dev.vapee.core.ui.MenuTestLayouts;
import dev.vapee.core.ui.*;
import dev.vapee.core.friend.FriendResult;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.friend.gui.FriendMenu.Action.*;

public final class FriendLayoutHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        for (var content : List.of(LayoutTestSupport.SPARSE, LayoutTestSupport.REORDERED, LayoutTestSupport.HIGH_CONTENT)) {
            var layout = LayoutTestSupport.paged(MenuTestLayouts.FRIEND, content == LayoutTestSupport.HIGH_CONTENT ? 54 : 45, content);
            if (content == LayoutTestSupport.HIGH_CONTENT) layout = new MenuLayout<>(layout.size(), layout.contentSlots(), 40, layout.actions());
            var f = new FriendMenuFixture(layout); var owner = f.player("Owner", true);
            f.menu.open(owner.player);
            check(owner.open.getSize() == layout.size() && owner.open.getItem(layout.emptySlot()) != null, "size and empty position follow layout");
            var expected = new ArrayList<UUID>();
            for (int i = 0; i < layout.pageSize() + 1; i++) {
                var target = f.player("Target%02d".formatted(i), false); expected.add(target.id);
                check(f.friends.sendRequest(owner.id, target.id) == FriendResult.SUCCESS && f.friends.acceptRequest(target.id, owner.id) == FriendResult.SUCCESS, "friend fixture");
            }
            f.menu.open(owner.player); var first = owner.open;
            var holder = FriendMenuFixture.holder(first);
            for (int i = 0; i < layout.pageSize(); i++) check(holder.getTarget(layout.contentSlot(i)).orElseThrow().equals(expected.get(i)) && first.getItem(layout.contentSlot(i)) != null, "rendered physical slot has exact UUID");
            f.click(owner, first, layout.slot(NEXT), ClickType.LEFT);
            check(FriendMenuFixture.holder(owner.open).getPage() == 1 && FriendMenuFixture.holder(owner.open).getTarget(layout.contentSlot(0)).orElseThrow().equals(expected.getLast()), "explicit capacity determines second page");
            f.click(owner, owner.open, layout.slot(PREVIOUS), ClickType.RIGHT);
            int saves = f.repository.saves;
            f.click(owner, owner.open, layout.contentSlot(0), ClickType.LEFT);
            check(f.repository.saves == saves, "plain click cannot remove friendship");
            f.click(owner, owner.open, layout.contentSlot(0), ClickType.SHIFT_RIGHT);
            check(!f.friends.getFriends(owner.id).contains(expected.getFirst()) && f.friends.getFriends(owner.id).contains(expected.get(1)), "only mapped UUID removed");
            var current = owner.open;
            f.click(owner, first, layout.slot(CLOSE), ClickType.LEFT);
            check(owner.open == current, "stale close inert");
            f.click(owner, current, layout.slot(INCOMING), ClickType.LEFT);
            check(FriendMenuFixture.holder(owner.open).getView() == FriendMenuView.INCOMING, "moved tab opens correct view");
            var sender = f.player("Sender", true);
            check(f.friends.sendRequest(sender.id, owner.id) == FriendResult.SUCCESS, "incoming fixture");
            f.click(owner, owner.open, layout.slot(REFRESH), ClickType.LEFT);
            check(FriendMenuFixture.holder(owner.open).getTarget(layout.contentSlot(0)).orElseThrow().equals(sender.id), "incoming UUID mapped");
            f.click(owner, owner.open, layout.contentSlot(0), ClickType.LEFT);
            check(f.friends.getFriends(owner.id).contains(sender.id), "existing incoming accept gesture preserved");
            var closing = owner.open;
            f.click(owner, closing, layout.slot(CLOSE), ClickType.LEFT);
            f.listener.onInventoryClose(new org.bukkit.event.inventory.InventoryCloseEvent(f.view(owner, closing)));
            check(owner.open == owner.bottom && f.menu.activeCount() == 0, "close and cleanup follow geometry");
        }
        System.out.println("FriendLayoutHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
