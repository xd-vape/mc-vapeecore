package dev.vapee.core.settings.visibility;

import dev.vapee.core.ui.MenuTestLayouts;
import dev.vapee.core.settings.SettingsInventoryHolder;
import dev.vapee.core.settings.SettingsMenu;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

public final class SettingsNavigationHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        var f = new VisibilityMenuFixture();
        var p = f.player("Owner", true);
        f.rootMenu.open(p.player);
        Inventory oldRoot = p.open;
        f.rootClick(p, oldRoot, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY), ClickType.LEFT);
        check(p.open.getHolder() instanceof VisibilitySettingsHolder, "root icon opens visibility");
        int saves = f.repository.saves;
        f.rootClick(p, oldRoot, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.SCOREBOARD), ClickType.LEFT);
        check(f.repository.saves == saves, "old root cannot toggle after navigation");
        f.visibilityClick(p, p.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.BACK), ClickType.LEFT);
        check(p.open.getHolder() instanceof SettingsInventoryHolder, "visibility back opens root");
        Inventory root = p.open;
        f.rootMenu.refreshActive(p.id);
        check(p.open == root, "root refresh stays in exact inventory");
        f.rootClick(p, root, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.VISIBILITY, 1), ClickType.RIGHT);
        Inventory visibility = p.open;
        check(visibility.getHolder() instanceof VisibilitySettingsHolder, "root status opens visibility");
        f.visibilityMenu.refreshActive(p.id);
        check(p.open == visibility && p.open.getHolder() instanceof VisibilitySettingsHolder,
                "visibility refresh stays in visibility view");
        f.visibilityMenu.open(p.player); // Deliberate new binding for the stale-inventory guard.
        f.visibilityClick(p, visibility, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER), ClickType.LEFT);
        check(f.repository.saves == saves, "stale visibility cannot toggle after refresh");
        visibility = p.open;
        f.visibilityClick(p, visibility, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MANAGE_PLAYERS), ClickType.LEFT);
        Inventory manage = p.open;
        check(manage.getHolder() instanceof VisiblePlayersHolder, "visibility opens manage players");
        f.visibilityClick(p, visibility, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.BACK), ClickType.LEFT);
        check(p.open == manage, "stale visibility cannot navigate after opening manage");
        f.visiblePlayersMenu.refreshActive(p.id);
        check(p.open == manage && p.open.getHolder() instanceof VisiblePlayersHolder,
                "manage refresh stays in correct view");
        f.visibleClick(p, p.open, MenuTestLayouts.VISIBLE_PLAYERS.slot(VisiblePlayersMenu.Action.BACK), ClickType.LEFT);
        check(p.open.getHolder() instanceof VisibilitySettingsHolder, "manage back opens visibility");
        visibility = p.open;
        f.visibleClick(p, manage, MenuTestLayouts.VISIBLE_PLAYERS.slot(VisiblePlayersMenu.Action.BACK), ClickType.LEFT);
        check(p.open == visibility, "stale manage cannot navigate after back");
        f.visibilityClick(p, visibility, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.BACK), ClickType.LEFT);
        check(p.open.getHolder() instanceof SettingsInventoryHolder, "second back completes hierarchy");
        f.rootClick(p, p.open, MenuTestLayouts.SETTINGS.slot(SettingsMenu.Action.CLOSE), ClickType.LEFT);
        check(p.open == p.bottom, "root close opens no parent");
        f.visibilityMenu.open(p.player);
        f.visibilityClick(p, p.open, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.CLOSE), ClickType.RIGHT);
        check(p.open == p.bottom, "visibility close opens no parent");
        f.visiblePlayersMenu.open(p.player);
        f.visibleClick(p, p.open, MenuTestLayouts.VISIBLE_PLAYERS.slot(VisiblePlayersMenu.Action.CLOSE), ClickType.LEFT);
        check(p.open == p.bottom, "manage close opens no parent");
        check(f.repository.saves == saves && f.applyCalls == 0 && p.soundCalls == 0,
                "navigation and refresh are nonmutating and silent");
        int[] parentCalls = {0};
        f.visibilityListener.setBackOpener(viewer -> { parentCalls[0]++; viewer.closeInventory(); });
        f.visibilityMenu.open(p.player);
        var previousView = p.open;
        f.visibilityClick(p, previousView, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.BACK), ClickType.RIGHT);
        check(parentCalls[0] == 1 && p.open == p.bottom, "Back uses the experience-supplied parent instead of Settings");
        f.visibilityClick(p, previousView, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.BACK), ClickType.RIGHT);
        check(parentCalls[0] == 1, "stale Back cannot invoke parent navigation");
        System.out.println("SettingsNavigationHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
