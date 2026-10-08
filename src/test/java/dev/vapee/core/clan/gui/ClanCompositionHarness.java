package dev.vapee.core.clan.gui;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
public final class ClanCompositionHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        { var f = new ClanMenuFixture(dev.vapee.core.clan.ClanLimits.defaults(), ClanMenu.DEFAULT_LAYOUT); var p = f.player("Owner", true); f.menu.open(p.player);
          check(p.open.getSize() == f.menu.layout().size(), "current production default opens"); }
        for (var action : ClanMenu.Action.values()) {
            var layout = CompositionTestSupport.without(MenuTestLayouts.CLAN.menu(), action);
            var f = new ClanMenuFixture(dev.vapee.core.clan.ClanLimits.defaults(), new ClanMenu.Layout(layout, 20, 22, 24)); var p = f.player("Owner", true); f.menu.open(p.player);
            var top = p.open; int slot = MenuTestLayouts.CLAN.menu().slot(action);
            check(top.getItem(slot) == null && layout.actionAt(slot).isEmpty(), "omitted control " + action);
            f.click(p, top, slot, ClickType.LEFT);
            check(p.open == top && f.menu.isActive(p.player, top, (ClanMenuHolder) top.getHolder()), "absent control cannot act " + action);
        }
        var layout = MenuLayout.builder(27, ClanMenu.Action.class).content(List.of(10, 12)).emptyState(22).build();
        var f = new ClanMenuFixture(dev.vapee.core.clan.ClanLimits.defaults(), new ClanMenu.Layout(layout, 20, 22, 24)); var p = f.player("Owner", true); f.menu.open(p.player);
        var top = p.open;
        check(top.getSize() == 27 && layout.actions().isEmpty(), "minimal paged menu without controls");
        for (int slot = 0; slot < top.getSize(); slot++)
            check(top.getItem(slot) == null || layout.isContent(slot) || slot == layout.emptySlot(), "only declared content or empty marker " + slot);
        var overview = new ClanMenuFixture(dev.vapee.core.clan.ClanLimits.defaults(), new ClanMenu.Layout(layout, 10, -1, -1));
        var member = overview.player("Overview", true);
        check(overview.clans.createClan(member.id, "Optional Clan", "OPT") == dev.vapee.core.clan.ClanResult.SUCCESS, "overview domain exists");
        overview.menu.open(member.player);
        check(member.open.getItem(10) != null && member.open.getItem(20) == null && member.open.getItem(24) == null, "only placed overview feature renders");
        var blank = MenuLayout.builder(27, ClanMenu.Action.class).content(List.of(10)).build();
        var absent = new ClanMenuFixture(dev.vapee.core.clan.ClanLimits.defaults(), new ClanMenu.Layout(blank, -1, -1, -1));
        var viewer = absent.player("Absent", true); absent.menu.open(viewer.player);
        check(java.util.stream.IntStream.range(0, viewer.open.getSize()).allMatch(slot -> viewer.open.getItem(slot) == null), "all overview and empty displays can be omitted");
        System.out.println("ClanCompositionHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
