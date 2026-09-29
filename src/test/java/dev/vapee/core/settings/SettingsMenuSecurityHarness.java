package dev.vapee.core.settings;

import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import java.util.Map;

import static dev.vapee.core.settings.SettingsMenuFixture.*;

public final class SettingsMenuSecurityHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        var f = new SettingsMenuFixture();
        var owner = f.player("Owner");
        var other = f.player("Other");
        f.menu.open(owner.player);
        Inventory active = owner.open;
        SettingsInventoryHolder holder = (SettingsInventoryHolder) active.getHolder();
        check(holder.getOwnerUniqueId().equals(owner.id) && holder.isBoundTo(active)
                        && holder.getInventory() == active && f.menu.isActive(owner.player, active, holder),
                "exact owner, holder and active inventory binding");
        int baseline = f.repository.saves;
        for (ClickType click : new ClickType[]{ClickType.SHIFT_LEFT, ClickType.SHIFT_RIGHT,
                ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.SWAP_OFFHAND,
                ClickType.DROP, ClickType.CONTROL_DROP, ClickType.MIDDLE, ClickType.CREATIVE,
                ClickType.WINDOW_BORDER_LEFT, ClickType.UNKNOWN}) {
            check(f.click(owner, active, 10, click).isCancelled() && f.repository.saves == baseline,
                    "non-menu action cannot mutate: " + click);
        }
        for (int slot : new int[]{54, 60, -999, 0, 53}) {
            check(f.click(owner, active, slot, ClickType.LEFT).isCancelled() && f.repository.saves == baseline,
                    "bottom, outside and invalid slots blocked: " + slot);
        }
        check(f.click(owner, active, 54, ClickType.SHIFT_LEFT).isCancelled(), "bottom shift transfer cancelled");
        check(f.click(other, active, 10, ClickType.RIGHT).isCancelled() && f.repository.saves == baseline,
                "wrong viewer cannot mutate");
        var unbound = new SettingsInventoryHolder(owner.id);
        Inventory unboundInventory = inventory(unbound, 54);
        check(f.click(owner, unboundInventory, 10, ClickType.LEFT).isCancelled()
                        && f.repository.saves == baseline, "unbound holder is cancelled without NPE");
        var forged = new SettingsInventoryHolder(owner.id);
        Inventory forgedInventory = inventory(forged, 54);
        forged.bindInventory(forgedInventory);
        owner.open = forgedInventory;
        check(f.click(owner, forgedInventory, 10, ClickType.RIGHT).isCancelled()
                        && f.repository.saves == baseline, "even opened forged holder cannot mutate");
        Inventory mismatched = inventory(holder, 54);
        owner.open = mismatched;
        check(f.click(owner, mismatched, 10, ClickType.LEFT).isCancelled()
                        && f.repository.saves == baseline, "holder must bind exact inventory");
        owner.open = owner.bottom;
        check(f.click(owner, active, 10, ClickType.LEFT).isCancelled() && f.repository.saves == baseline,
                "registry entry requires currently open inventory");
        owner.open = active;
        for (Inventory top : new Inventory[]{active, forgedInventory, unboundInventory}) {
            InventoryDragEvent event = new InventoryDragEvent(view(owner, top), null, null, false, Map.of());
            f.listener.onInventoryDrag(event);
            check(event.isCancelled(), "all root-holder drags cancelled, including forged or unbound");
        }
        f.menu.open(owner.player);
        Inventory replacement = owner.open;
        check(f.click(owner, active, 10, ClickType.LEFT).isCancelled() && f.repository.saves == baseline,
                "replaced inventory is stale");
        f.listener.onInventoryClose(new InventoryCloseEvent(view(owner, active)));
        check(f.menu.isActive(owner.player, replacement, (SettingsInventoryHolder) replacement.getHolder()),
                "late close of stale inventory preserves replacement");
        f.listener.onInventoryClose(new InventoryCloseEvent(view(owner, replacement)));
        check(!f.menu.isActive(owner.player, replacement, (SettingsInventoryHolder) replacement.getHolder()),
                "active close invalidates exactly that instance");
        f.menu.open(owner.player);
        Inventory quit = owner.open;
        owner.open = owner.bottom;
        f.listener.onPlayerQuit(new PlayerQuitEvent(owner.player, net.kyori.adventure.text.Component.empty(),
                PlayerQuitEvent.QuitReason.DISCONNECTED));
        owner.open = quit;
        check(!f.menu.isActive(owner.player, quit, (SettingsInventoryHolder) quit.getHolder()),
                "quit forgets registry even if current view already changed");
        owner.cancelNextOpen = true;
        f.menu.open(owner.player);
        check(!f.menu.isActive(owner.player, quit, (SettingsInventoryHolder) quit.getHolder()),
                "cancelled open publishes no active inventory");
        f.menu.open(owner.player);
        Inventory beforeShutdown = owner.open;
        f.menu.closeOpenInventories();
        check(owner.open == owner.bottom && !f.menu.isActive(owner.player, beforeShutdown,
                        (SettingsInventoryHolder) beforeShutdown.getHolder()), "shutdown closes owned inventory");
        f.menu.open(owner.player);
        owner.open = forgedInventory;
        int closed = owner.closes;
        f.menu.closeOpenInventories();
        check(owner.closes == closed && owner.open == forgedInventory, "shutdown preserves unrelated inventory");
        check(f.soundCalls == 0 && f.presentationCalls == 0, "invalid actions give no runtime feedback");
        System.out.println("SettingsMenuSecurityHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
