package dev.vapee.core.settings;

import dev.vapee.core.ui.LayoutTestSupport;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.settings.SettingsMenu.Action.*;
import static dev.vapee.core.settings.SettingsMenuFixture.*;

public final class SettingsLayoutHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var layout = LayoutTestSupport.move(SettingsMenu.DEFAULT_LAYOUT, 45,
                Map.of(SCOREBOARD, List.of(10, 43), CLOSE, List.of(8), REFRESH, List.of(44)));
        var f = new SettingsMenuFixture(layout); var owner = f.player("LayoutOwner");
        check(f.menu.open(owner.player) && owner.open.getSize() == layout.size(), "45-slot layout opens");
        var top = owner.open;
        check(spec(top, layout.slot(CLOSE)).material() == org.bukkit.Material.BARRIER, "moved close rendered");
        for (var action : List.of(SCOREBOARD, SOUNDS, PRIVATE_MESSAGES, FRIEND_REQUESTS, FRIEND_PRESENCE)) {
            int feature = layout.slot(action), status = layout.slot(action, 1);
            String initial = text(top, status);
            int saves = f.repository.saves;
            check(f.menu.entryAt(feature).orElseThrow() == f.menu.entryAt(status).orElseThrow(), "pair shares action " + action);
            f.click(owner, top, feature, ClickType.LEFT);
            check(!text(top, status).equals(initial) && f.repository.saves == saves + 1, "icon toggles/persists " + action);
            f.click(owner, top, status, ClickType.RIGHT);
            check(text(top, status).equals(initial) && f.repository.saves == saves + 2, "independent status toggles back " + action);
        }
        int saves = f.repository.saves;
        f.click(owner, top, layout.slot(REFRESH), ClickType.LEFT);
        check(f.repository.saves == saves && owner.open == top, "moved refresh reads current state");
        f.click(owner, top, layout.slot(SCOREBOARD, 1), ClickType.SHIFT_LEFT);
        check(f.repository.saves == saves, "unsupported gesture stays inert");
        var other = f.player("Other");
        saves = f.repository.saves;
        f.click(other, top, layout.slot(SCOREBOARD), ClickType.LEFT);
        check(f.repository.saves == saves, "foreign owner cannot mutate");
        f.menu.open(owner.player); var replacement = owner.open;
        f.click(owner, top, layout.slot(CLOSE), ClickType.LEFT);
        check(owner.open == replacement, "stale moved close cannot close replacement");
        f.click(owner, replacement, layout.slot(CLOSE), ClickType.LEFT);
        check(owner.open == owner.bottom && f.menu.activeCount() == 0, "negative control 1: close follows layout only");
        check(layout.slot(SCOREBOARD) == 10 && layout.slot(SCOREBOARD, 1) == 43, "negative control 3: detached status fixture exercised");
        System.out.println("SettingsLayoutHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
