package dev.vapee.core.settings;

import org.bukkit.event.inventory.ClickType;

/** Layout edits must move the displayed icon and the production click action together. */
public final class SettingsLayoutAlignmentHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var fixture = new SettingsMenuFixture();
        var owner = fixture.player("Layout");
        fixture.menu.open(owner.player);
        var inventory = owner.open;
        var icon = inventory.getItem(SettingsMenu.SCOREBOARD_SLOT);
        check(icon != null, "scoreboard icon occupies the named layout slot");
        check(SettingsMenuFixture.spec(inventory, SettingsMenu.SCOREBOARD_SLOT).material() == org.bukkit.Material.MAP, "layout slot renders scoreboard icon");
        int saves = fixture.repository.saves;
        check(fixture.settings.isScoreboardEnabled(owner.id).orElseThrow(), "initial scoreboard preference");
        fixture.click(owner, inventory, SettingsMenu.SCOREBOARD_SLOT, ClickType.LEFT);
        check(!fixture.settings.isScoreboardEnabled(owner.id).orElseThrow() && fixture.repository.saves == saves + 1,
                "rendered icon dispatches the current production action");
        fixture.click(owner, inventory, SettingsMenu.SCOREBOARD_STATUS_SLOT, ClickType.RIGHT);
        check(fixture.settings.isScoreboardEnabled(owner.id).orElseThrow() && fixture.repository.saves == saves + 2,
                "matching status slot dispatches same feature");
        check(owner.open == inventory && owner.opens == 1, "layout actions update the same bound inventory");
        System.out.println("SettingsLayoutAlignmentHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
