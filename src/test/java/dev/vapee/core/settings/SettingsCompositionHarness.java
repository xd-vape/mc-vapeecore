package dev.vapee.core.settings;

import dev.vapee.core.ui.*;
import org.bukkit.Material;
import org.bukkit.event.inventory.*;
import java.util.*;
import static dev.vapee.core.settings.SettingsMenu.Action.*;
import static dev.vapee.core.settings.SettingsMenuFixture.*;

public final class SettingsCompositionHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var production = new SettingsMenuFixture(SettingsMenu.DEFAULT_LAYOUT);
        var owner = production.player("Production");
        check(production.menu.open(owner.player), "current operator composition opens");
        var actual = owner.open;
        check(actual.getSize() == production.menu.layout().size(), "operator size retained");
        for (int slot = 0; slot < actual.getSize(); slot++) {
            check((actual.getItem(slot) != null) == production.menu.layout().actionAt(slot).isPresent(), "only currently placed items render " + slot);
        }
        if (production.menu.layout().actionSlot(REFRESH).isEmpty()) {
            for (int slot = 0; slot < actual.getSize(); slot++)
                check(actual.getItem(slot) == null || spec(actual, slot).material() != Material.CLOCK, "no phantom Refresh");
        }
        for (var action : List.of(SCOREBOARD, SOUNDS, PRIVATE_MESSAGES, FRIEND_REQUESTS, FRIEND_PRESENCE, VISIBILITY)) {
            for (boolean status : List.of(false, true)) {
                var builder = MenuLayout.builder(27, SettingsMenu.Action.class);
                var layout = status ? builder.setting(action, 10, 24).build() : builder.setting(action, 10).build();
                var f = new SettingsMenuFixture(layout); var p = f.player("Single");
                check(f.menu.open(p.player) && p.open.getItem(10) != null, "single feature opens " + action);
                check((p.open.getItem(24) != null) == status, "optional status " + action);
                check(layout.actionSlot(CLOSE).isEmpty() && layout.actionSlot(REFRESH).isEmpty(), "minimal menu has no mandatory buttons");
                int saves = f.repository.saves;
                f.click(p, p.open, 24, ClickType.LEFT);
                check(f.repository.saves == saves + (status && action != VISIBILITY ? 1 : 0), "absent status cannot toggle");
                saves = f.repository.saves;
                f.click(p, p.open, 10, ClickType.RIGHT);
                check(action == VISIBILITY ? f.visibilityCalls > 0 : f.repository.saves == saves + 1, "icon remains functional " + action);
                p.player.closeInventory();
                check(f.menu.activeCount() == 0, "native close cleans menu without Close button");
            }
        }
        var hidden = new SettingsMenuFixture(MenuLayout.builder(27, SettingsMenu.Action.class).setting(SOUNDS, 4).build());
        var h = hidden.player("Hidden"); hidden.menu.open(h.player); var top = h.open;
        check(hidden.menu.entryAt(10).isEmpty() && hidden.menu.entryAt(19).isEmpty() && top.getItem(10) == null, "entire Scoreboard feature absent");
        int saves = hidden.repository.saves;
        hidden.click(h, top, 10, ClickType.LEFT); hidden.click(h, top, 19, ClickType.RIGHT);
        check(hidden.repository.saves == saves && hidden.settings.isScoreboardEnabled(h.id).orElseThrow(), "no ghost feature action");
        check(hidden.settings.setScoreboardEnabled(h.id, false), "hidden domain feature still works outside GUI");
        for (int refreshSlot : List.of(3, 7)) {
            var f = new SettingsMenuFixture(MenuLayout.builder(54, SettingsMenu.Action.class)
                    .setting(SCOREBOARD, 10, 19).button(REFRESH, refreshSlot).build());
            var p = f.player("Refresh"); f.menu.open(p.player); var inventory = p.open;
            check(spec(inventory, refreshSlot).material() == Material.CLOCK, "Refresh renders wherever placed");
            f.settings.setScoreboardEnabled(p.id, false); saves = f.repository.saves;
            f.click(p, inventory, 52, ClickType.LEFT);
            check(text(inventory, 19).equals("Enabled") && f.repository.saves == saves, "old Refresh slot is inert");
            f.click(p, inventory, refreshSlot, ClickType.RIGHT);
            check(text(inventory, 19).equals("Disabled") && f.repository.saves == saves && p.open == inventory, "fixture Refresh reads state without mutation");
            var other = f.player("Foreign"); f.settings.setScoreboardEnabled(p.id, true);
            f.click(other, inventory, refreshSlot, ClickType.LEFT);
            check(text(inventory, 19).equals("Disabled"), "foreign Refresh denied");
        }
        var empty = new SettingsMenuFixture(MenuLayout.builder(9, SettingsMenu.Action.class).build());
        check(empty.menu.open(empty.player("Empty").player), "empty composition is valid");
        System.out.println("SettingsCompositionHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
