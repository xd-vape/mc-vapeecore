package dev.vapee.core.settings.visibility;

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
        f.rootClick(p, oldRoot, SettingsMenu.VISIBILITY_SLOT, ClickType.LEFT);
        check(p.open.getHolder() instanceof VisibilitySettingsHolder, "root icon opens visibility");
        int saves = f.repository.saves;
        f.rootClick(p, oldRoot, SettingsMenu.SCOREBOARD_SLOT, ClickType.LEFT);
        check(f.repository.saves == saves, "old root cannot toggle after navigation");
        f.visibilityClick(p, p.open, VisibilitySettingsMenu.BACK_SLOT, ClickType.LEFT);
        check(p.open.getHolder() instanceof SettingsInventoryHolder, "visibility back opens root");
        Inventory root = p.open;
        f.rootClick(p, root, SettingsMenu.REFRESH_SLOT, ClickType.RIGHT);
        check(p.open == root, "root refresh stays in exact inventory");
        f.rootClick(p, root, SettingsMenu.VISIBILITY_STATUS_SLOT, ClickType.RIGHT);
        Inventory visibility = p.open;
        check(visibility.getHolder() instanceof VisibilitySettingsHolder, "root status opens visibility");
        f.visibilityClick(p, visibility, VisibilitySettingsMenu.REFRESH_SLOT, ClickType.LEFT);
        check(p.open != visibility && p.open.getHolder() instanceof VisibilitySettingsHolder,
                "visibility refresh stays in visibility view");
        f.visibilityClick(p, visibility, VisibilitySettingsMenu.MASTER_SLOT, ClickType.LEFT);
        check(f.repository.saves == saves, "stale visibility cannot toggle after refresh");
        visibility = p.open;
        f.visibilityClick(p, visibility, VisibilitySettingsMenu.MANAGE_PLAYERS_SLOT, ClickType.LEFT);
        Inventory manage = p.open;
        check(manage.getHolder() instanceof VisiblePlayersHolder, "visibility opens manage players");
        f.visibilityClick(p, visibility, VisibilitySettingsMenu.BACK_SLOT, ClickType.LEFT);
        check(p.open == manage, "stale visibility cannot navigate after opening manage");
        f.visibleClick(p, manage, VisiblePlayersMenu.REFRESH_SLOT, ClickType.RIGHT);
        check(p.open != manage && p.open.getHolder() instanceof VisiblePlayersHolder,
                "manage refresh stays in correct view");
        f.visibleClick(p, p.open, VisiblePlayersMenu.BACK_SLOT, ClickType.LEFT);
        check(p.open.getHolder() instanceof VisibilitySettingsHolder, "manage back opens visibility");
        visibility = p.open;
        f.visibleClick(p, manage, VisiblePlayersMenu.BACK_SLOT, ClickType.LEFT);
        check(p.open == visibility, "stale manage cannot navigate after back");
        f.visibilityClick(p, visibility, VisibilitySettingsMenu.BACK_SLOT, ClickType.LEFT);
        check(p.open.getHolder() instanceof SettingsInventoryHolder, "second back completes hierarchy");
        f.rootClick(p, p.open, SettingsMenu.CLOSE_SLOT, ClickType.LEFT);
        check(p.open == p.bottom, "root close opens no parent");
        f.visibilityMenu.open(p.player);
        f.visibilityClick(p, p.open, VisibilitySettingsMenu.CLOSE_SLOT, ClickType.RIGHT);
        check(p.open == p.bottom, "visibility close opens no parent");
        f.visiblePlayersMenu.open(p.player);
        f.visibleClick(p, p.open, VisiblePlayersMenu.CLOSE_SLOT, ClickType.LEFT);
        check(p.open == p.bottom, "manage close opens no parent");
        check(f.repository.saves == saves && f.applyCalls == 0 && p.soundCalls == 0,
                "navigation and refresh are nonmutating and silent");
        System.out.println("SettingsNavigationHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
