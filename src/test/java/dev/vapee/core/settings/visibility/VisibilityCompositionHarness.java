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
        int modeRow = defaults.visibilityMenu.layout().slot(VisibilitySettingsMenu.Action.MASTER) / 9;
        for (var action : List.of(VisibilitySettingsMenu.Action.MASTER, VisibilitySettingsMenu.Action.FRIENDS,
                VisibilitySettingsMenu.Action.STAFF, VisibilitySettingsMenu.Action.ADDED_USERS,
                VisibilitySettingsMenu.Action.GAME_PARTICIPANTS)) {
            check(defaults.visibilityMenu.layout().slot(action) / 9 == modeRow, "mode and filters share one feature row " + action);
            check(defaults.visibilityMenu.layout().placement(action).size() == 2
                    && VisibilityMenuFixture.visibilitySpec(d.open, defaults.visibilityMenu.layout().slot(action, 1))
                    .material().name().endsWith("STAINED_GLASS_PANE"), "default visible status pane " + action);
        }
        check(defaults.visibilityMenu.layout().slot(VisibilitySettingsMenu.Action.MANAGE_PLAYERS) == d.open.getSize() - 1,
                "Manage Players chest is at the lower right corner");
        var inventory = d.open;
        int master = defaults.visibilityMenu.layout().slot(VisibilitySettingsMenu.Action.MASTER, 1);
        defaults.visibilityClick(d, inventory, master, ClickType.RIGHT);
        check(d.open == inventory && VisibilityMenuFixture.visibilitySpec(inventory, master).material() == org.bukkit.Material.YELLOW_STAINED_GLASS_PANE,
                "mode pane cycles to FILTERED in the same inventory");
        defaults.visibilityClick(d, inventory, master, ClickType.LEFT);
        check(VisibilityMenuFixture.visibilitySpec(inventory, master).material() == org.bukkit.Material.RED_STAINED_GLASS_PANE,
                "HIDDEN has a distinct red status pane");
        int friends = defaults.visibilityMenu.layout().slot(VisibilitySettingsMenu.Action.FRIENDS, 1);
        boolean previous = defaults.settings.getSettings(d.id).orElseThrow().getVisibility().isShowFriends();
        defaults.visibilityClick(d, inventory, friends, ClickType.RIGHT);
        check(defaults.settings.getSettings(d.id).orElseThrow().getVisibility().isShowFriends() != previous
                        && VisibilityMenuFixture.visibilitySpec(inventory, friends).material()
                        == (previous ? org.bukkit.Material.RED_STAINED_GLASS_PANE : org.bukkit.Material.LIME_STAINED_GLASS_PANE),
                "filter status pane changes persisted state and rerenders immediately");
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
