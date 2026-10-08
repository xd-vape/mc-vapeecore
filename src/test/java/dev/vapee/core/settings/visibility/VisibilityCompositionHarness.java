package dev.vapee.core.settings.visibility;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;

public final class VisibilityCompositionHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var defaults = new VisibilityMenuFixture(VisibilitySettingsMenu.DEFAULT_LAYOUT, VisiblePlayersMenu.DEFAULT_LAYOUT);
        var d = defaults.player("Default", true);
        defaults.visibilityMenu.open(d.player);
        check(d.open.getSize() == defaults.visibilityMenu.layout().size(), "current visibility composition opens");
        defaults.visiblePlayersMenu.open(d.player);
        check(d.open.getSize() == defaults.visiblePlayersMenu.layout().size(), "current players composition opens");
        for (var action : VisibilitySettingsMenu.Action.values()) {
            var missing = CompositionTestSupport.without(MenuTestLayouts.VISIBILITY, action);
            var f = new VisibilityMenuFixture(missing, MenuTestLayouts.VISIBLE_PLAYERS); var p = f.player("Absent", true);
            f.visibilityMenu.open(p.player); var top = p.open; int slot = MenuTestLayouts.VISIBILITY.slot(action);
            check(top.getItem(slot) == null && missing.actionAt(slot).isEmpty(), "visibility placement omitted " + action);
            int saves = f.repository.saves; f.visibilityClick(p, top, slot, ClickType.LEFT);
            check(p.open == top && f.repository.saves == saves && f.applyCalls == 0, "no phantom visibility action " + action);
        }
        var iconOnly = MenuLayout.builder(27, VisibilitySettingsMenu.Action.class).setting(VisibilitySettingsMenu.Action.MASTER, 10).build();
        var playersOnly = MenuLayout.builder(27, VisiblePlayersMenu.Action.class).content(List.of(10, 12)).emptyState(22).build();
        var f = new VisibilityMenuFixture(iconOnly, playersOnly); var p = f.player("Minimal", true);
        f.visibilityMenu.open(p.player); var top = p.open;
        check(top.getItem(10) != null && top.getItem(19) == null, "visibility icon without status");
        f.visibilityClick(p, top, 10, ClickType.LEFT);
        check(!f.settings.areLobbyPlayersVisible(p.id).orElseThrow(), "icon-only visibility action works");
        f.visiblePlayersMenu.open(p.player); top = p.open;
        check(top.getItem(22) != null, "players menu opens without navigation");
        var target = f.player("Target", false); f.settings.addLobbyVisiblePlayer(p.id, target.id);
        f.visiblePlayersMenu.open(p.player); top = p.open;
        check(VisibilityMenuFixture.visibleHolder(top).getTarget(10).orElseThrow().equals(target.id), "content target retained without controls");
        f.visibleClick(p, top, 10, ClickType.RIGHT);
        check(!f.settings.getLobbyAddedVisiblePlayers(p.id).orElseThrow().contains(target.id), "target removal works without controls");
        for (var action : VisiblePlayersMenu.Action.values()) {
            var layout = CompositionTestSupport.without(MenuTestLayouts.VISIBLE_PLAYERS, action);
            var x = new VisibilityMenuFixture(MenuTestLayouts.VISIBILITY, layout); var who = x.player("Players", true);
            x.visiblePlayersMenu.open(who.player); var view = who.open; int slot = MenuTestLayouts.VISIBLE_PLAYERS.slot(action);
            check(view.getItem(slot) == null && layout.actionAt(slot).isEmpty(), "players control omitted " + action);
            int previousSaves = x.repository.saves; x.visibleClick(who, view, slot, ClickType.LEFT);
            check(who.open == view && x.repository.saves == previousSaves && x.applyCalls == 0, "no ghost players control " + action);
        }
        System.out.println("VisibilityCompositionHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
