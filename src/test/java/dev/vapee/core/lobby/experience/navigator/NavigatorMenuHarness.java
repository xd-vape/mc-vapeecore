package dev.vapee.core.lobby.experience.navigator;

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
        check(player.open.getSize() == 54 && plain(f.title).equals("Warp Navigator"), "unchanged size and title");
        check(text(player.open, 22).equals("No Destinations Available"), "friendly empty state");
        check(spec(player.open, 22).lore().isEmpty(), "empty state reveals no administrative details");
        check(spec(player.open, 49).material() == Material.PAPER
                        && spec(player.open, 50).material() == Material.BARRIER,
                "page info and close keep original slots/materials");
        check(text(player.open, 49).equals("Page 1/1") && plain(spec(player.open, 49).lore().getFirst()).equals("0 destinations."),
                "empty registry has one page and zero destinations");
        check(player.open.getItem(45) == null && player.open.getItem(53) == null, "empty has no paging actions");
        f.destinations(91);
        f.warps.setNavigatorVisible("custom_000", false);
        f.warps.setNavigatorOrder("custom_090", 0);
        f.warps.setNavigatorOrder("custom_001", 20);
        f.warps.setDisplayName("custom_090", "<red>Literal Friendly Name</red>");
        f.warps.setIcon("custom_090", Material.GOLD_INGOT);
        f.menu.open(player.player, -99);
        Inventory first = player.open;
        NavigatorInventoryHolder holder = (NavigatorInventoryHolder) first.getHolder();
        check(holder.getPage() == 0 && holder.getInventory() == first && holder.isBoundTo(first)
                        && f.menu.isActive(player.player, first, holder), "page clamps low with exact active binding");
        check(holder.getWarpId(0).orElseThrow().equals("custom_002"), "hidden ID excluded and custom order delays custom_001");
        for (int slot = 0; slot < 45; slot++) {
            check(first.getItem(slot) != null && holder.getWarpId(slot).isPresent(), "45 mapped content slots: " + slot);
            check(spec(first, slot).lore().stream().map(NavigatorFixture::plain).toList().equals(List.of("Click to teleport.")),
                    "destination lore contains no technical ID: " + slot);
        }
        check(holder.getWarpId(45).isEmpty() && first.getItem(45) == null && first.getItem(53) != null,
                "first page shows only next control");
        check(plain(spec(first, 49).lore().getFirst()).equals("90 destinations.") && text(first, 49).equals("Page 1/2"),
                "page count includes only visible destinations");
        f.click(player, first, 53, ClickType.RIGHT);
        Inventory second = player.open;
        NavigatorInventoryHolder secondHolder = (NavigatorInventoryHolder) second.getHolder();
        check(secondHolder.getPage() == 1 && second.getItem(45) != null && second.getItem(53) == null,
                "right click advances to exact last page");
        check(secondHolder.getWarpId(43).orElseThrow().equals("custom_090")
                        && secondHolder.getWarpId(44).orElseThrow().equals("custom_001"),
                "order tie uses ID, higher order is last");
        check(text(second, 43).equals("<red>Literal Friendly Name</red>")
                        && spec(second, 43).material() == Material.GOLD_INGOT,
                "friendly name is literal and configured icon renders");
        check(!f.menu.isActive(player.player, first, holder), "page replacement invalidates old inventory");
        f.listener.onInventoryClose(new InventoryCloseEvent(view(player, first)));
        check(f.menu.isActive(player.player, second, secondHolder), "late old close preserves new page");
        f.menu.open(player.player, Integer.MAX_VALUE);
        check(((NavigatorInventoryHolder) player.open.getHolder()).getPage() == 1, "high page clamps");
        f.warps.setNavigatorVisible("custom_000", true);
        f.menu.open(player.player, 2);
        check(((NavigatorInventoryHolder) player.open.getHolder()).getPage() == 2
                        && ((NavigatorInventoryHolder) player.open.getHolder()).getWarpId(0).orElseThrow().equals("custom_001")
                        && player.open.getItem(1) == null, "91 destinations render one item on third page");
        f.click(player, player.open, 45, ClickType.LEFT);
        check(((NavigatorInventoryHolder) player.open.getHolder()).getPage() == 1, "left previous paging works");
        f.click(player, player.open, 50, ClickType.RIGHT);
        check(player.open == player.bottom, "right close closes without teleport");

        var hidden = new NavigatorFixture();
        var hiddenPlayer = hidden.player();
        hidden.destinations(1);
        hidden.warps.setNavigatorVisible("custom_000", false);
        hidden.menu.open(hiddenPlayer.player);
        check(text(hiddenPlayer.open, 22).equals("No Destinations Available")
                        && plain(spec(hiddenPlayer.open, 49).lore().getFirst()).equals("0 destinations."),
                "hidden-only registry has same empty presentation");
        hidden.warps.setNavigatorVisible("custom_000", true);
        hidden.menu.open(hiddenPlayer.player);
        check(plain(spec(hiddenPlayer.open, 49).lore().getFirst()).equals("1 destination."), "single destination label");
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
