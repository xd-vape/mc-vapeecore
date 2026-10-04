package dev.vapee.core.friend.gui;

import dev.vapee.core.friend.FriendRelation;
import dev.vapee.core.friend.FriendResult;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public final class FriendMenuSecurityHarness {

    private static int checks;

    public static void main(String[] args) throws Exception {
        FriendMenuFixture fixture = new FriendMenuFixture();
        var owner = fixture.player("Owner", true);
        var other = fixture.player("Other", true);
        check(fixture.friends.sendRequest(owner.id, other.id) == FriendResult.SUCCESS
                && fixture.friends.acceptRequest(other.id, owner.id) == FriendResult.SUCCESS,
                "fixture has one real friendship");
        fixture.menu.open(owner.player);
        Inventory active = owner.open;

        for (ClickType click : new ClickType[]{ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT,
                ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.SWAP_OFFHAND,
                ClickType.DROP, ClickType.CONTROL_DROP,
                ClickType.CREATIVE, ClickType.MIDDLE}) {
            check(fixture.click(owner, active, 0, click).isCancelled(),
                    click + " is cancelled in top inventory");
            check(fixture.friends.getRelation(owner.id, other.id) == FriendRelation.FRIENDS,
                    click + " cannot remove a friend");
        }
        check(fixture.click(owner, active, FriendMenu.INVENTORY_SIZE, ClickType.SHIFT_LEFT).isCancelled(),
                "bottom-inventory shift transfer is cancelled");
        InventoryClickEvent collect = new InventoryClickEvent(fixture.view(owner, active),
                InventoryType.SlotType.CONTAINER, 0, ClickType.DOUBLE_CLICK,
                InventoryAction.COLLECT_TO_CURSOR);
        fixture.listener.onInventoryClick(collect);
        check(collect.isCancelled(), "collect-to-cursor action is cancelled");
        check(fixture.click(owner, active, 100, ClickType.NUMBER_KEY).isCancelled(),
                "bottom hotbar swap is cancelled");
        check(fixture.click(owner, active, -999, ClickType.LEFT).isCancelled(),
                "outside click is cancelled");
        check(fixture.click(owner, active, 22, ClickType.SHIFT_RIGHT).isCancelled()
                && fixture.friends.getRelation(owner.id, other.id) == FriendRelation.FRIENDS,
                "info slot has no target and cannot mutate");
        check(fixture.click(owner, active, 51, ClickType.LEFT).isCancelled(),
                "unused navigation slot is inert");

        InventoryDragEvent drag = new InventoryDragEvent(fixture.view(owner, active),
                null, null, false, Map.of());
        fixture.listener.onInventoryDrag(drag);
        check(drag.isCancelled(), "drag is always cancelled for a friend menu");
        check(fixture.click(other, active, 0, ClickType.SHIFT_RIGHT).isCancelled()
                        && fixture.friends.getRelation(owner.id, other.id) == FriendRelation.FRIENDS,
                "another viewer cannot operate the owner's inventory");

        FriendMenuHolder forged = new FriendMenuHolder(owner.id, FriendMenuView.FRIENDS, 0,
                Map.of(0, other.id));
        Inventory forgedInventory = fixture.inventory(forged, FriendMenu.INVENTORY_SIZE,
                net.kyori.adventure.text.Component.text("Friends"));
        forged.bindInventory(forgedInventory);
        check(fixture.click(owner, forgedInventory, 0, ClickType.SHIFT_RIGHT).isCancelled()
                        && fixture.friends.getRelation(owner.id, other.id) == FriendRelation.FRIENDS,
                "forged but bound holder is cancelled and cannot mutate");
        check(!fixture.menu.isActive(owner.player, forgedInventory, forged),
                "forged inventory is not present in the menu's active registry");
        FriendMenuHolder unbound = new FriendMenuHolder(owner.id, FriendMenuView.FRIENDS, 0,
                Map.of(0, other.id));
        Inventory unboundInventory = fixture.inventory(unbound, FriendMenu.INVENTORY_SIZE,
                net.kyori.adventure.text.Component.text("Friends"));
        check(fixture.click(owner, unboundInventory, 0, ClickType.SHIFT_RIGHT).isCancelled()
                        && !fixture.menu.isActive(owner.player, unboundInventory, unbound),
                "unbound holder cannot mutate");

        fixture.menu.open(owner.player);
        check(fixture.click(owner, active, 0, ClickType.SHIFT_RIGHT).isCancelled()
                        && fixture.friends.getRelation(owner.id, other.id) == FriendRelation.FRIENDS,
                "stale prior inventory cannot mutate after refresh");
        fixture.listener.onInventoryClose(new InventoryCloseEvent(fixture.view(owner, active)));
        check(fixture.menu.isActive(owner.player, owner.open, FriendMenuFixture.holder(owner.open)),
                "close event for an old view does not invalidate the replacement");
        check(fixture.click(owner, owner.open, 53, ClickType.LEFT).isCancelled()
                        && fixture.friends.getRelation(owner.id, other.id) == FriendRelation.FRIENDS,
                "inactive next button cannot change state");
        Inventory current = owner.open;
        fixture.listener.onInventoryClose(new InventoryCloseEvent(fixture.view(owner, current)));
        check(!fixture.menu.isActive(owner.player, current, FriendMenuFixture.holder(current)),
                "close event forgets exactly the current inventory");
        fixture.menu.open(owner.player);
        current = owner.open;
        fixture.menu.closeOpenInventories();
        check(owner.closeCalls == 1 && !fixture.menu.isActive(owner.player, current,
                FriendMenuFixture.holder(current)), "disable closes and invalidates active menu");
        checks += dev.vapee.core.ui.MenuCloseProbe.verify("FriendMenu", probe -> {
            var menu = new FriendMenu(fixture.friends, fixture.identities, fixture.messages,
                    probe::lookup, fixture::inventory, FriendMenuFixture.SpecItem::new, probe.logger);
            var listener = new FriendMenuListener(menu, fixture.friends,
                    new dev.vapee.core.friend.FriendMessages(fixture.messages, probe::lookup),
                    fixture.messages, probe.logger);
            return new dev.vapee.core.ui.MenuCloseProbe.Subject(menu::open, menu::closeOpenInventories,
                    menu::activeCount, listener::onInventoryClose);
        });
        System.out.println("FriendMenuSecurityHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
