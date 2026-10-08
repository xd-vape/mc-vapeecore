package dev.vapee.core.clan.gui;

import dev.vapee.core.ui.MenuTestLayouts;
import dev.vapee.core.ui.*;
import dev.vapee.core.clan.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.clan.gui.ClanMenu.Action.*;

public final class ClanLayoutHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        for (var content : List.of(LayoutTestSupport.SPARSE, LayoutTestSupport.REORDERED, LayoutTestSupport.HIGH_CONTENT)) {
            var layout = LayoutTestSupport.paged(MenuTestLayouts.CLAN.menu(), content == LayoutTestSupport.HIGH_CONTENT ? 54 : 45, content);
            if (content == LayoutTestSupport.HIGH_CONTENT) layout = new MenuLayout<>(layout.size(), layout.contentSlots(), 40, layout.actions());
            var definition = new ClanMenu.Layout(layout, 21, 23, 25);
            var f = new ClanMenuFixture(ClanLimits.defaults(), definition); var owner = f.player("Owner", true);
            f.menu.open(owner.player);
            check(owner.open.getSize() == layout.size() && owner.open.getItem(layout.emptySlot()) != null, "empty overview geometry");
            check(f.clans.createClan(owner.id, "Layout Clan", "LAY") == ClanResult.SUCCESS, "clan fixture");
            UUID clan = f.clans.getClanOf(owner.id).orElseThrow().id();
            var expected = new ArrayList<UUID>(); expected.add(owner.id);
            for (int i = 0; i < layout.pageSize(); i++) {
                var member = f.player("Target%02d".formatted(i), false); expected.add(member.id);
                check(f.clans.inviteMember(owner.id, member.id) == ClanResult.SUCCESS && f.clans.acceptInvite(member.id, clan) == ClanResult.SUCCESS, "member fixture");
            }
            f.menu.open(owner.player);
            check(owner.open.getItem(definition.clanNameSlot()) != null && owner.open.getItem(definition.memberCountSlot()) != null && owner.open.getItem(definition.roleSlot()) != null, "exclusive overview positions follow definition");
            f.click(owner, owner.open, layout.slot(MEMBERS), ClickType.LEFT);
            var first = owner.open; var holder = ClanMenuFixture.holder(first);
            for (int i = 0; i < layout.pageSize(); i++) check(holder.target(layout.contentSlot(i)).orElseThrow().equals(expected.get(i)) && first.getItem(layout.contentSlot(i)) != null, "sparse member UUID mapping");
            f.click(owner, first, layout.slot(NEXT), ClickType.LEFT);
            check(ClanMenuFixture.holder(owner.open).page() == 1 && ClanMenuFixture.holder(owner.open).target(layout.contentSlot(0)).orElseThrow().equals(expected.getLast()), "capacity drives member pagination");
            f.click(owner, owner.open, layout.slot(PREVIOUS), ClickType.RIGHT);
            f.click(owner, owner.open, layout.contentSlot(1), ClickType.SHIFT_RIGHT);
            check(f.clans.getClanOf(expected.get(1)).isEmpty() && f.clans.getClanOf(expected.get(2)).isPresent(), "kick affects only mapped member");
            var current = owner.open;
            f.click(owner, first, layout.slot(CLOSE), ClickType.LEFT);
            check(owner.open == current, "stale close inert");
            owner.permitted = false;
            f.click(owner, current, layout.contentSlot(1), ClickType.SHIFT_RIGHT);
            check(f.clans.getClanOf(expected.get(2)).isPresent(), "current permission still required");
            owner.permitted = true; f.menu.open(owner.player);
            f.click(owner, owner.open, layout.slot(CLOSE), ClickType.RIGHT);
            check(owner.open == owner.bottom, "moved close works");
        }
        System.out.println("ClanLayoutHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
