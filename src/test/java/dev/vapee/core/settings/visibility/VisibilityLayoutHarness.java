package dev.vapee.core.settings.visibility;

import dev.vapee.core.ui.MenuTestLayouts;
import dev.vapee.core.ui.*;
import org.bukkit.event.inventory.ClickType;
import java.util.*;
import static dev.vapee.core.settings.visibility.VisibilitySettingsMenu.Action.*;

public final class VisibilityLayoutHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var fixed = LayoutTestSupport.move(MenuTestLayouts.VISIBILITY, 45,
                Map.of(MASTER, List.of(10, 43), BACK, List.of(0), CLOSE, List.of(8), REFRESH, List.of(44)));
        for (var content : List.of(LayoutTestSupport.SPARSE, LayoutTestSupport.REORDERED, LayoutTestSupport.HIGH_CONTENT)) {
            var paged = LayoutTestSupport.paged(MenuTestLayouts.VISIBLE_PLAYERS, content == LayoutTestSupport.HIGH_CONTENT ? 54 : 45, content);
            if (content == LayoutTestSupport.HIGH_CONTENT) paged = new MenuLayout<>(paged.size(), paged.contentSlots(), 40, paged.actions());
            var f = new VisibilityMenuFixture(fixed, paged); var owner = f.player("Owner", true);
            f.visibilityMenu.open(owner.player);
            check(owner.open.getSize() == fixed.size() && owner.open.getItem(fixed.slot(MASTER, 1)) != null, "fixed layout rendered");
            f.visibilityClick(owner, owner.open, fixed.slot(MASTER), ClickType.LEFT);
            check(!f.settings.areLobbyPlayersVisible(owner.id).orElseThrow(), "moved feature toggles");
            f.visibilityClick(owner, owner.open, fixed.slot(MASTER, 1), ClickType.RIGHT);
            check(f.settings.areLobbyPlayersVisible(owner.id).orElseThrow(), "detached status toggles same state");
            f.visibilityClick(owner, owner.open, fixed.slot(MANAGE_PLAYERS), ClickType.LEFT);
            check(owner.open.getHolder() instanceof VisiblePlayersHolder && owner.open.getItem(paged.emptySlot()) != null, "managed list uses alternative layout");
            var expected = new ArrayList<UUID>();
            for (int i = 0; i < paged.pageSize() + 1; i++) {
                var target = f.player("Target%02d".formatted(i), false); expected.add(target.id);
                check(f.settings.addLobbyVisiblePlayer(owner.id, target.id) == dev.vapee.core.player.settings.AddedVisiblePlayerResult.SUCCESS, "add fixture");
            }
            f.visiblePlayersMenu.open(owner.player);
            var first = owner.open; var holder = VisibilityMenuFixture.visibleHolder(first);
            for (int i = 0; i < paged.pageSize(); i++) check(holder.getTarget(paged.contentSlot(i)).orElseThrow().equals(expected.get(i)) && first.getItem(paged.contentSlot(i)) != null, "actual sparse UUID mapping");
            f.visibleClick(owner, first, paged.slot(VisiblePlayersMenu.Action.NEXT), ClickType.LEFT);
            check(VisibilityMenuFixture.visibleHolder(owner.open).getPage() == 1 && VisibilityMenuFixture.visibleHolder(owner.open).getTarget(paged.contentSlot(0)).orElseThrow().equals(expected.getLast()), "capacity controls page boundary");
            f.visibleClick(owner, owner.open, paged.slot(VisiblePlayersMenu.Action.PREVIOUS), ClickType.RIGHT);
            UUID removed = expected.getFirst();
            f.visibleClick(owner, owner.open, paged.contentSlot(0), ClickType.RIGHT);
            check(!f.settings.isLobbyAddedVisiblePlayer(owner.id, removed).orElseThrow() && f.settings.isLobbyAddedVisiblePlayer(owner.id, expected.get(1)).orElseThrow(), "only clicked UUID removed");
            var current = owner.open;
            f.visibleClick(owner, first, paged.slot(VisiblePlayersMenu.Action.CLOSE), ClickType.LEFT);
            check(owner.open == current, "stale alternative inventory inert");
            f.visibleClick(owner, current, paged.slot(VisiblePlayersMenu.Action.BACK), ClickType.LEFT);
            check(owner.open.getHolder() instanceof VisibilitySettingsHolder, "moved back uses existing flow");
            f.visibilityClick(owner, owner.open, fixed.slot(CLOSE), ClickType.LEFT);
            check(owner.open == owner.bottom, "moved close follows fixed geometry");
        }
        System.out.println("VisibilityLayoutHarness passed " + checks + " checks.");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
