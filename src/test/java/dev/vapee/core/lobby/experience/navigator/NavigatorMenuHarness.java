package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.ui.MenuTestLayouts;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;

import java.util.List;

import static dev.vapee.core.lobby.experience.navigator.NavigatorFixture.*;

public final class NavigatorMenuHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        var f = new NavigatorFixture();
        var player = f.player();
        check(f.menu.open(player.player), "valid empty registry opens");
        check(player.open.getSize() == MenuTestLayouts.NAVIGATOR.size() && plain(f.title).equals("Warp Navigator"), "unchanged size and title");
        check(text(player.open, MenuTestLayouts.NAVIGATOR.emptySlot()).equals("No Destinations Available"), "friendly empty state");
        check(spec(player.open, MenuTestLayouts.NAVIGATOR.emptySlot()).lore().isEmpty(), "empty state reveals no administrative details");
        check(player.open.getItem(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PAGE_INFO)) == null
                        && spec(player.open, MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.CLOSE)).material() == Material.BARRIER,
                "single-page menu omits Page Info while preserving Close");
        check(((NavigatorInventoryHolder) player.open.getHolder()).getPage() == 0 && ((NavigatorInventoryHolder) player.open.getHolder()).actionAt(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PAGE_INFO)).isEmpty(),
                "empty registry has one page and zero destinations");
        check(player.open.getItem(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PREVIOUS)) == null && player.open.getItem(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.NEXT)) == null, "empty has no paging actions");
        int capacity = f.menu.layout().pageSize();
        int count = capacity * 2 + 1;
        f.destinations(count);
        f.warps.setNavigatorVisible("custom_000", false);
        f.warps.setNavigatorOrder("custom_%03d".formatted(count - 1), 0);
        f.warps.setNavigatorOrder("custom_001", 20);
        f.warps.setDisplayName("custom_%03d".formatted(count - 1), "<red>Literal Friendly Name</red>");
        f.warps.setIcon("custom_%03d".formatted(count - 1), Material.GOLD_INGOT);
        f.menu.open(player.player, -99);
        Inventory first = player.open;
        NavigatorInventoryHolder holder = (NavigatorInventoryHolder) first.getHolder();
        check(holder.getPage() == 0 && holder.getInventory() == first && holder.isBoundTo(first)
                        && f.menu.isActive(player.player, first, holder), "page clamps low with exact active binding");
        check(holder.getWarpId(MenuTestLayouts.NAVIGATOR.contentSlot(0)).orElseThrow().equals("custom_002"), "hidden ID excluded and custom order delays custom_001");
        for (int slot : MenuTestLayouts.NAVIGATOR.contentSlots()) {
            check(first.getItem(slot) != null && holder.getWarpId(slot).isPresent(), "all explicit mapped content slots: " + slot);
            check(spec(first, slot).lore().stream().map(NavigatorFixture::plain).toList().equals(List.of("Click to teleport.")),
                    "destination lore contains no technical ID: " + slot);
        }
        check(holder.getWarpId(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PREVIOUS)).isEmpty() && first.getItem(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PREVIOUS)) == null && first.getItem(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.NEXT)) != null,
                "first page shows only next control");
        check(plain(spec(first, MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PAGE_INFO)).lore().getFirst()).equals((count - 1) + " destinations.") && text(first, MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PAGE_INFO)).equals("Page 1 / 2"),
                "page count includes only visible destinations");
        f.click(player, first, MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.NEXT), ClickType.RIGHT);
        Inventory second = player.open;
        NavigatorInventoryHolder secondHolder = (NavigatorInventoryHolder) second.getHolder();
        check(secondHolder.getPage() == 1 && second.getItem(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PREVIOUS)) != null && second.getItem(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.NEXT)) == null,
                "right click advances to exact last page");
        check(secondHolder.getWarpId(MenuTestLayouts.NAVIGATOR.contentSlot(capacity - 2)).orElseThrow().equals("custom_%03d".formatted(count - 1))
                        && secondHolder.getWarpId(MenuTestLayouts.NAVIGATOR.contentSlots().getLast()).orElseThrow().equals("custom_001"),
                "order tie uses ID, higher order is last");
        check(text(second, MenuTestLayouts.NAVIGATOR.contentSlot(capacity - 2)).equals("<red>Literal Friendly Name</red>")
                        && spec(second, MenuTestLayouts.NAVIGATOR.contentSlot(capacity - 2)).material() == Material.GOLD_INGOT,
                "friendly name is literal and configured icon renders");
        check(!f.menu.isActive(player.player, first, holder), "page replacement invalidates old inventory");
        f.listener.onInventoryClose(new InventoryCloseEvent(view(player, first)));
        check(f.menu.isActive(player.player, second, secondHolder), "late old close preserves new page");
        f.menu.open(player.player, Integer.MAX_VALUE);
        check(((NavigatorInventoryHolder) player.open.getHolder()).getPage() == 1, "high page clamps");
        f.warps.setNavigatorVisible("custom_000", true);
        f.menu.open(player.player, 2);
        check(((NavigatorInventoryHolder) player.open.getHolder()).getPage() == 2
                        && ((NavigatorInventoryHolder) player.open.getHolder()).getWarpId(MenuTestLayouts.NAVIGATOR.contentSlot(0)).orElseThrow().equals("custom_001")
                        && player.open.getItem(MenuTestLayouts.NAVIGATOR.contentSlot(1)) == null, "91 destinations render one item on third page");
        f.click(player, player.open, MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PREVIOUS), ClickType.LEFT);
        check(((NavigatorInventoryHolder) player.open.getHolder()).getPage() == 1, "left previous paging works");
        f.click(player, player.open, MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.CLOSE), ClickType.RIGHT);
        check(player.open == player.bottom, "right close closes without teleport");

        var hidden = new NavigatorFixture();
        var hiddenPlayer = hidden.player();
        hidden.destinations(1);
        hidden.warps.setNavigatorVisible("custom_000", false);
        hidden.menu.open(hiddenPlayer.player);
        check(text(hiddenPlayer.open, MenuTestLayouts.NAVIGATOR.emptySlot()).equals("No Destinations Available")
                        && hiddenPlayer.open.getItem(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PAGE_INFO)) == null,
                "hidden-only registry has same empty presentation");
        hidden.warps.setNavigatorVisible("custom_000", true);
        hidden.menu.open(hiddenPlayer.player);
        check(hiddenPlayer.open.getItem(MenuTestLayouts.NAVIGATOR.slot(NavigatorMenu.Action.PAGE_INFO)) == null && ((NavigatorInventoryHolder) hiddenPlayer.open.getHolder()).getWarpId(MenuTestLayouts.NAVIGATOR.contentSlot(0)).orElseThrow().equals("custom_000"), "single destination renders without pagination clutter");
        hiddenPlayer.cancelNextOpen = true;
        Inventory preserved = hiddenPlayer.open;
        check(!hidden.menu.open(hiddenPlayer.player)
                        && hidden.menu.isActive(hiddenPlayer.player, preserved, (NavigatorInventoryHolder) preserved.getHolder()),
                "cancelled replacement does not publish a new inventory");
        System.out.println("NavigatorMenuHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
