package dev.vapee.core.clan.gui;

import dev.vapee.core.clan.*;
import net.kyori.adventure.text.Component;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.Inventory;

import java.util.Map;

public final class ClanMenuSecurityHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        ClanMenuFixture f = new ClanMenuFixture();
        var owner = f.player("Owner", true);
        var member = f.player("Member", true);
        check(f.clans.createClan(owner.id, "Safe Clan", "SAFE") == ClanResult.SUCCESS, "fixture clan");
        check(f.clans.inviteMember(owner.id, member.id) == ClanResult.SUCCESS
                && f.clans.acceptInvite(member.id, f.clans.getClanOf(owner.id).orElseThrow().id()) == ClanResult.SUCCESS,
                "fixture membership");
        f.menu.open(owner.player, ClanMenuView.MEMBERS, 0);
        Inventory active = owner.open;
        ClanMenuHolder holder = ClanMenuFixture.holder(active);
        check(holder.owner().equals(owner.id) && holder.target(1).orElseThrow().equals(member.id),
                "holder owner and slot mapping");
        check(f.menu.isActive(owner.player, active, holder), "exact active inventory binding");
        for (ClickType click : new ClickType[]{ClickType.LEFT, ClickType.RIGHT, ClickType.NUMBER_KEY,
                ClickType.DOUBLE_CLICK, ClickType.SWAP_OFFHAND, ClickType.DROP,
                ClickType.CONTROL_DROP, ClickType.CREATIVE, ClickType.MIDDLE}) {
            check(f.click(owner, active, 1, click).isCancelled(), click + " cancelled");
            check(f.clans.getClanOf(member.id).isPresent(), click + " does not mutate");
        }
        check(f.click(owner, active, 0, ClickType.SHIFT_RIGHT).isCancelled()
                && f.clans.getClanOf(owner.id).isPresent(), "owner entry cannot kick self");
        check(f.click(owner, active, ClanMenu.INVENTORY_SIZE, ClickType.SHIFT_LEFT).isCancelled(),
                "bottom shift transfer cancelled");
        check(f.click(owner, active, 100, ClickType.NUMBER_KEY).isCancelled(), "bottom hotbar swap cancelled");
        check(f.click(owner, active, -999, ClickType.LEFT).isCancelled(), "outside click cancelled");
        check(f.click(owner, active, 22, ClickType.SHIFT_RIGHT).isCancelled()
                && f.clans.getClanOf(member.id).isPresent(), "unmapped content inert");
        InventoryDragEvent drag = new InventoryDragEvent(f.view(owner, active), null, null, false, Map.of());
        f.listener.onInventoryDrag(drag);
        check(drag.isCancelled(), "drag cancelled");
        check(f.click(member, active, 1, ClickType.SHIFT_RIGHT).isCancelled()
                && f.clans.getClanOf(member.id).isPresent(), "wrong viewer blocked");
        ClanMenuHolder forged = new ClanMenuHolder(owner.id, ClanMenuView.MEMBERS, 0, Map.of(1, member.id));
        Inventory forgedInventory = f.inventory(forged, ClanMenu.INVENTORY_SIZE, Component.text("Clan"));
        check(f.click(owner, forgedInventory, 1, ClickType.SHIFT_RIGHT).isCancelled()
                && !f.menu.isActive(owner.player, forgedInventory, forged), "forged holder blocked");
        ClanMenuHolder unbound = new ClanMenuHolder(owner.id, ClanMenuView.MEMBERS, 0, Map.of(1, member.id));
        Inventory unboundInventory = f.inventory(unbound, ClanMenu.INVENTORY_SIZE, Component.text("Clan"));
        check(f.click(owner, unboundInventory, 1, ClickType.SHIFT_RIGHT).isCancelled()
                && !f.menu.isActive(owner.player, unboundInventory, unbound), "unbound holder blocked");
        f.menu.open(owner.player, ClanMenuView.MEMBERS, 0);
        check(f.click(owner, active, 1, ClickType.SHIFT_RIGHT).isCancelled()
                && f.clans.getClanOf(member.id).isPresent(), "stale prior inventory blocked");
        f.listener.onInventoryClose(new InventoryCloseEvent(f.view(owner, active)));
        check(f.menu.isActive(owner.player, owner.open, ClanMenuFixture.holder(owner.open)),
                "old close does not clear replacement");
        owner.permitted = false;
        f.click(owner, owner.open, 1, ClickType.SHIFT_RIGHT);
        check(f.clans.getClanOf(member.id).isPresent() && owner.closeCalls == 1,
                "permission revocation blocks active menu");
        owner.permitted = true;
        f.menu.open(owner.player, ClanMenuView.MEMBERS, 0);
        Inventory current = owner.open;
        f.menu.closeOpenInventories();
        check(!f.menu.isActive(owner.player, current, ClanMenuFixture.holder(current)),
                "disable invalidates active inventory");
        f.menu.open(owner.player, ClanMenuView.OVERVIEW, 0);
        Inventory oldClanMenu = owner.open;
        check(f.clans.disbandClan(owner.id) == ClanResult.SUCCESS
                && f.clans.createClan(owner.id, "Other Clan", "OTHER") == ClanResult.SUCCESS,
                "external clan replacement fixture");
        f.click(owner, oldClanMenu, ClanMenu.ACTION_SLOT, ClickType.SHIFT_RIGHT);
        check(f.clans.getClanOf(owner.id).isPresent() && owner.open != oldClanMenu,
                "stale clan context refreshes without owner action");
        checks += dev.vapee.core.ui.MenuCloseProbe.verify("ClanMenu", probe -> {
            var menu = new ClanMenu(f.clans, f.identity, f.messages, probe::lookup,
                    f::inventory, ClanMenuFixture.SpecItem::new, probe.logger);
            var listener = new ClanMenuListener(menu, f.clans, new ClanMessages(f.messages, probe::lookup),
                    f.messages, probe.logger);
            return new dev.vapee.core.ui.MenuCloseProbe.Subject(menu::open, menu::closeOpenInventories,
                    menu::activeCount, listener::onInventoryClose);
        });
        System.out.println("ClanMenuSecurityHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
}
