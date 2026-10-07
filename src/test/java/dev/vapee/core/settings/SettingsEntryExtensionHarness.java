package dev.vapee.core.settings;

import dev.vapee.core.ui.UiItems;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import java.util.ArrayList;
import java.util.List;

/** An extra fixture descriptor is rendered and clicked using the very same production entry, no listener edit. */
public final class SettingsEntryExtensionHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        var f = new SettingsMenuFixture(); var player = f.player("SyntheticTile");
        int[] calls = {0};
        var entry = new SettingsMenuEntry(28, 37,
                current -> UiItems.literal(Material.DIAMOND, "Synthetic", net.kyori.adventure.text.format.NamedTextColor.AQUA, List.of()),
                current -> UiItems.literal(Material.EMERALD, "Synthetic Status", net.kyori.adventure.text.format.NamedTextColor.GREEN, List.of()),
                (viewer, current, context) -> { calls[0]++; return SettingsMenuEntry.Result.OPENED; });
        var entries = new ArrayList<>(SettingsMenuEntries.defaults()); entries.add(entry);
        var menu = new SettingsMenu(f.settings, f.messages, id -> f.online.get(id).player,
                (holder, size, title) -> SettingsMenuFixture.inventory(holder, size), spec -> new Item(spec), f.logger, entries);
        var listener = SettingsMenuFixture.createListener(menu, f.settings, p -> f.presentationCalls++, p -> f.visibilityCalls++, p -> f.soundCalls++, f.messages, f.logger);
        check(menu.open(player.player), "synthetic fixture opens actual menu"); var inventory = player.open;
        check(inventory.getItem(28) instanceof Item item && item.spec.material() == Material.DIAMOND, "entry rendered at its only declared icon slot");
        check(inventory.getItem(37) instanceof Item item && item.spec.material() == Material.EMERALD, "paired status rendered at descriptor slot");
        check(menu.entryAt(28).orElseThrow() == entry && menu.entryAt(37).orElseThrow() == entry, "both clicks resolve exactly the rendered descriptor");
        int originalSaves = f.repository.saves;
        for (int slot : new int[]{28,37}) {
            var event = new org.bukkit.event.inventory.InventoryClickEvent(SettingsMenuFixture.view(player, inventory),
                    org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, org.bukkit.event.inventory.InventoryAction.PICKUP_ALL);
            listener.onInventoryClick(event); check(event.isCancelled(), "synthetic click cancelled " + slot);
        }
        check(calls[0] == 2 && f.presentationCalls == 0 && f.visibilityCalls == 0 && f.repository.saves == originalSaves, "exact synthetic action only, no unrelated domain mutation");
        player.permitted = false;
        listener.onInventoryClick(new org.bukkit.event.inventory.InventoryClickEvent(SettingsMenuFixture.view(player, inventory),
                org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,28,ClickType.LEFT,org.bukkit.event.inventory.InventoryAction.PICKUP_ALL));
        check(calls[0] == 2 && player.closes == 1, "runtime permission revocation still blocks synthetic action");
        entries.add(entry);
        try { new SettingsMenu(f.settings,f.messages,id -> null,(h,size,t) -> null,spec -> null,f.logger,entries); throw new AssertionError("duplicate accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        check(SettingsMenuEntries.defaults().size() == 6, "no invented production setting");
        System.out.println("SettingsEntryExtensionHarness passed " + checks + " checks.");
    }
    private static final class Item extends org.bukkit.inventory.ItemStack {
        final dev.vapee.core.ui.UiItemSpec spec; Item(dev.vapee.core.ui.UiItemSpec spec) { super(); this.spec=spec; }
    }
    private static void check(boolean value,String label) { checks++; if(!value)throw new AssertionError(label); }
}
