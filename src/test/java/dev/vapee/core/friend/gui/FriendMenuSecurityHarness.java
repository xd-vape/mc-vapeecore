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
        capabilityBoundaries();
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

    private static void capabilityBoundaries() throws Exception {
        var entry = new FriendMenuFixture();
        var viewer = entry.player("Entry", true);
        viewer.permitted = false;
        int initialSaves = entry.repository.saves;
        entry.menu.open(viewer.player);
        check(viewer.open == null && entry.menu.activeCount() == 0
                && entry.repository.saves == initialSaves, "denied friend entry publishes nothing and saves nothing");
        check(viewer.received.size() == 1 && viewer.closeCalls == 0, "entry denial is controlled and does not close foreign view");
        viewer.permitted = true;
        entry.menu.open(viewer.player);
        check(entry.menu.isActive(viewer.player, viewer.open, FriendMenuFixture.holder(viewer.open)), "grant permits fresh entry");

        for (int action = 0; action < 4; action++) for (boolean allowed : new boolean[]{false, true}) {
            var f = new FriendMenuFixture(); var owner = f.player("Actor", true); var target = f.player("Target", true);
            if (action == 2) f.friends.sendRequest(owner.id, target.id);
            else f.friends.sendRequest(target.id, owner.id);
            if (action == 3) f.friends.acceptRequest(owner.id, target.id);
            FriendMenuView view = action == 2 ? FriendMenuView.OUTGOING
                    : action == 3 ? FriendMenuView.FRIENDS : FriendMenuView.INCOMING;
            ClickType click = action == 1 ? ClickType.RIGHT : action == 3 ? ClickType.SHIFT_RIGHT : ClickType.LEFT;
            f.menu.open(owner.player, view, 0);
            Inventory top = owner.open;
            var snapshot = f.repository.snapshot; var relation = f.friends.getRelation(owner.id, target.id);
            int saves = f.repository.saves, notices = owner.received.size();
            owner.permitted = allowed;
            check(f.click(owner, top, 0, click).isCancelled(), "friend capability event cancelled " + action + allowed);
            if (allowed) {
                check(f.repository.saves == saves + 1 && f.friends.getRelation(owner.id, target.id)
                        == (action == 0 ? FriendRelation.FRIENDS : FriendRelation.NONE), "authorized friend action succeeds " + action);
            } else {
                check(f.repository.saves == saves && f.repository.snapshot == snapshot
                        && f.friends.getRelation(owner.id, target.id) == relation, "revoked friend action preserves request/relation/persistence " + action);
                check(f.menu.activeCount() == 0 && owner.open == owner.bottom && owner.closeCalls == 1,
                        "revoked friend view forgotten and closed " + action);
                check(owner.received.size() == notices + 1, "single controlled friend denial " + action);
                f.click(owner, top, 0, click);
                f.listener.onInventoryClose(new InventoryCloseEvent(f.view(owner, top)));
                check(owner.received.size() == notices + 1 && owner.closeCalls == 1 && f.repository.saves == saves,
                        "replayed denied friend view is inert " + action);
            }
        }
        for (int slot : new int[]{45, 46, 47, 48, 49, 50, 52, 53, 54}) {
            var f = new FriendMenuFixture(); var owner = f.player("Navigation", true);
            f.menu.open(owner.player); Inventory top = owner.open; owner.permitted = false;
            int saves = f.repository.saves;
            check(f.click(owner, top, slot, ClickType.LEFT).isCancelled() && f.menu.activeCount() == 0
                    && owner.open == owner.bottom && f.repository.saves == saves, "revocation blocks friend navigation/bottom " + slot);
        }
        var f = new FriendMenuFixture(); var owner = f.player("Owner", true); var stranger = f.player("Stranger", true);
        f.menu.open(owner.player); Inventory top = owner.open;
        stranger.permitted = false;
        f.click(stranger, top, FriendMenu.ADD_SLOT, ClickType.LEFT);
        check(stranger.closeCalls == 0 && stranger.received.isEmpty() && f.menu.activeCount() == 1,
                "foreign viewer denial does not close or forget owner's binding");
        owner.open = owner.bottom; owner.permitted = false;
        f.click(owner, top, FriendMenu.ADD_SLOT, ClickType.LEFT);
        check(owner.closeCalls == 0 && owner.open == owner.bottom && f.menu.activeCount() == 0,
                "friend event-top authority forgets owned binding without closing foreign current view");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
