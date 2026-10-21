package dev.vapee.core.settings;

import dev.vapee.core.ui.MenuLayout;
import dev.vapee.core.ui.UiItemSpec;
import dev.vapee.core.ui.UiItems;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Independent GUI fixture; never a stored PlayerSettings property. */
public final class SettingsEntryExtensionHarness {
    private static final String TEST_EXTENSION_TOGGLE = "TEST_EXTENSION_TOGGLE";
    private static int checks;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            behavior();
            authority();
        }
        staleInventory();
        System.out.println("SettingsEntryExtensionHarness passed " + checks + " checks.");
    }

    private static void behavior() throws Exception {
        var f = new ExtensionFixture(true);
        int saves = f.base.repository.saves;
        check(f.menu.open(f.owner.player), "independent TEST_EXTENSION_TOGGLE opens");
        Inventory top = f.owner.open;
        assertState(f, top, false);
        check(
                f.menu.entryAt(2).orElseThrow() == f.entry
                        && f.menu.entryAt(11).orElseThrow() == f.entry,
                "main/status dispatch exact independently supplied entry");
        check(f.click(top, 2, ClickType.LEFT).isCancelled(), "owned main click cancelled");
        check(
                f.enabled && !f.decoy && f.calls == 1,
                "synthetic callback changes intended value only");
        assertState(f, top, true);
        f.click(top, 11, ClickType.RIGHT);
        check(!f.enabled && !f.decoy && f.calls == 2, "status invokes same controlled mutation");
        assertState(f, top, false);
        check(
                f.owner.open == top && f.owner.opens == 1,
                "fixture commit rerenders existing inventory");
        int sounds = f.base.soundCalls;
        f.failNext = true;
        f.click(top, 2, ClickType.LEFT);
        check(
                !f.enabled && !f.decoy && f.calls == 3,
                "failed fixture mutation keeps committed value");
        assertState(f, top, false);
        check(
                f.base.soundCalls == sounds && f.owner.open == top,
                "failed fixture mutation gives no success feedback");
        check(
                f.base.repository.saves == saves
                        && f.base.presentationCalls == 0
                        && f.base.visibilityCalls == 0,
                "synthetic callback has no persistence or unrelated feature side effects");
        f.menu.startUpdates();
        f.menu.startUpdates();
        f.enabled = true; // Independently committed fixture state; observer must reread it.
        f.base.settings.setSoundsEnabled(f.owner.id, false);
        assertState(f, top, true);
        check(
                f.calls == 3 && f.base.repository.saves == saves + 1,
                "real commit observer refreshes injected entry without executing it");
        ItemStack before = top.getItem(2);
        f.base.repository.failNext = true;
        try {
            f.base.settings.setSoundsEnabled(f.owner.id, true);
            throw new AssertionError("real save should fail");
        } catch (IllegalStateException expected) {
            check(top.getItem(2) == before, "failed real save emits no optimistic refresh");
        }
        f.menu.shutdown();
        check(
                f.menu.activeCount() == 0 && f.owner.open == f.owner.bottom,
                "shutdown closes injected view");
        f.menu.startUpdates();
        f.menu.open(f.owner.player);
        f.enabled = false;
        f.base.settings.setSoundsEnabled(f.owner.id, true);
        assertState(f, f.owner.open, false);
        f.menu.shutdown();

        var iconOnly = new ExtensionFixture(false);
        iconOnly.menu.open(iconOnly.owner.player);
        check(
                iconOnly.owner.open.getItem(11) == null && iconOnly.menu.entryAt(11).isEmpty(),
                "optional status entirely omitted");
        iconOnly.click(iconOnly.owner.open, 11, ClickType.LEFT);
        check(iconOnly.calls == 0, "unplaced optional status inert");
        iconOnly.click(iconOnly.owner.open, 2, ClickType.RIGHT);
        check(iconOnly.enabled && iconOnly.calls == 1, "icon-only independent toggle works");
        iconOnly.menu.shutdown();

        var base = new SettingsMenuFixture();
        var entries = new ArrayList<>(SettingsMenuEntries.defaults(base.menu.layout()));
        var free =
                java.util.stream.IntStream.range(0, base.menu.layout().size())
                        .filter(slot -> base.menu.layout().actionAt(slot).isEmpty())
                        .limit(2)
                        .toArray();
        entries.add(
                new SettingsMenuEntry(
                        free[0], free[1], f.entry.icon(), f.entry.status(), f.entry.action()));
        var composed =
                new SettingsMenu(
                        base.settings,
                        base.messages,
                        id -> null,
                        (holder, size, title) -> SettingsMenuFixture.inventory(holder, size),
                        Item::new,
                        base.logger,
                        entries,
                        base.menu.layout());
        check(
                composed.entryAt(free[0]).isPresent() && entries.size() == 7,
                "independent entry coexists with all six supported features");
        entries.add(entries.getLast());
        try {
            new SettingsMenu(
                    base.settings,
                    base.messages,
                    id -> null,
                    (holder, size, title) -> null,
                    Item::new,
                    base.logger,
                    entries,
                    base.menu.layout());
            throw new AssertionError("duplicate synthetic entry accepted");
        } catch (IllegalArgumentException expected) {
            check(true, "duplicate extension rejected");
        }
    }

    private static void authority() throws Exception {
        var f = new ExtensionFixture(true);
        f.menu.open(f.owner.player);
        Inventory top = f.owner.open;
        var other = f.base.player("Other");
        var foreign = click(other, top, 2, ClickType.LEFT);
        f.listener.onInventoryClick(foreign);
        check(
                foreign.isCancelled() && f.calls == 0,
                "foreign UUID cannot execute independent action");
        var forgedHolder = new SettingsInventoryHolder(f.owner.id);
        Inventory forged = SettingsMenuFixture.inventory(forgedHolder, top.getSize());
        forgedHolder.bindInventory(forged);
        f.owner.open = forged;
        check(
                f.click(forged, 2, ClickType.LEFT).isCancelled() && f.calls == 0,
                "opened clone without active registration is inert");
        Inventory clone = SettingsMenuFixture.inventory(top.getHolder(), top.getSize());
        f.owner.open = clone;
        f.click(clone, 2, ClickType.LEFT);
        check(f.calls == 0, "original holder cannot authorize different inventory");
        f.owner.open = top;
        for (ClickType type :
                List.of(
                        ClickType.SHIFT_LEFT,
                        ClickType.NUMBER_KEY,
                        ClickType.DOUBLE_CLICK,
                        ClickType.SWAP_OFFHAND,
                        ClickType.DROP,
                        ClickType.MIDDLE,
                        ClickType.CREATIVE)) {
            check(
                    f.click(top, 2, type).isCancelled() && f.calls == 0,
                    "disallowed synthetic click " + type);
        }
        check(
                f.click(top, top.getSize(), ClickType.LEFT).isCancelled() && f.calls == 0,
                "bottom inventory inert");
        var drag =
                new InventoryDragEvent(
                        SettingsMenuFixture.view(f.owner, top), null, null, false, Map.of());
        f.listener.onInventoryDrag(drag);
        check(drag.isCancelled() && f.calls == 0, "extension drags cancelled");
        f.owner.permitted = false;
        f.click(top, 2, ClickType.RIGHT);
        check(
                f.calls == 0 && f.owner.open == f.owner.bottom && f.menu.activeCount() == 0,
                "permission revocation closes and invalidates entry");
        check(!f.menu.open(f.owner.player), "denied open publishes no inventory");
        f.owner.permitted = true;
        f.menu.open(f.owner.player);
        f.base.players.unloadPlayer(f.owner.id);
        f.click(f.owner.open, 2, ClickType.LEFT);
        check(
                f.calls == 0 && f.owner.open == f.owner.bottom,
                "unloaded profile cannot execute fixture callback");
        check(!f.menu.open(f.owner.player), "unloaded profile cannot open extension menu");
        f.menu.shutdown();
    }

    private static void staleInventory() throws Exception {
        var f = new ExtensionFixture(true);
        f.menu.startUpdates();
        f.menu.open(f.owner.player);
        Inventory stale = f.owner.open;
        ItemStack oldIcon = stale.getItem(2);
        f.menu.open(f.owner.player);
        Inventory replacement = f.owner.open;
        f.click(stale, 2, ClickType.LEFT);
        check(f.calls == 0 && !f.enabled, "replaced inventory cannot execute synthetic mutation");
        f.enabled = true;
        f.base.settings.setSoundsEnabled(f.owner.id, false);
        check(stale.getItem(2) == oldIcon, "observer does not repaint stale inventory");
        assertState(f, replacement, true);
        f.menu.shutdown();
        f.owner.open = replacement;
        f.click(replacement, 2, ClickType.LEFT);
        check(f.calls == 0 && f.enabled, "shutdown inventory cannot regain authority by reopening");
        ItemStack stoppedIcon = replacement.getItem(2);
        f.base.settings.setSoundsEnabled(f.owner.id, true);
        check(
                replacement.getItem(2) == stoppedIcon,
                "detached observer cannot repaint shutdown inventory");
    }

    private static void assertState(ExtensionFixture f, Inventory inventory, boolean enabled) {
        Item icon = (Item) inventory.getItem(2);
        check(
                icon.spec.material() == Material.DIAMOND
                        && SettingsMenuFixture.plain(icon.spec.name())
                                .equals(TEST_EXTENSION_TOGGLE),
                "fixture-owned main presentation");
        check(
                SettingsMenuFixture.plain(icon.spec.lore().getFirst())
                        .equals(enabled ? "On" : "Off"),
                "main displays committed fixture value");
        if (f.entry.statusSlot() >= 0) {
            Item status = (Item) inventory.getItem(11);
            check(
                    status.spec.material()
                                    == (enabled
                                            ? Material.LIME_STAINED_GLASS_PANE
                                            : Material.RED_STAINED_GLASS_PANE)
                            && SettingsMenuFixture.plain(status.spec.name())
                                    .equals(enabled ? "On" : "Off"),
                    "optional status displays committed fixture value");
        }
    }

    private static InventoryClickEvent click(
            SettingsMenuFixture.TestPlayer player, Inventory top, int slot, ClickType type) {
        return new InventoryClickEvent(
                SettingsMenuFixture.view(player, top),
                InventoryType.SlotType.CONTAINER,
                slot,
                type,
                InventoryAction.PICKUP_ALL);
    }

    private static final class ExtensionFixture {
        final SettingsMenuFixture base = new SettingsMenuFixture();
        final SettingsMenuFixture.TestPlayer owner = base.player("Synthetic");
        final SettingsMenuEntry entry;
        final SettingsMenu menu;
        final SettingsListener listener;
        boolean enabled;
        boolean decoy;
        boolean failNext;
        int calls;

        ExtensionFixture(boolean status) throws Exception {
            entry =
                    new SettingsMenuEntry(
                            2,
                            status ? 11 : -1,
                            current ->
                                    UiItems.literal(
                                            Material.DIAMOND,
                                            Component.text(TEST_EXTENSION_TOGGLE),
                                            NamedTextColor.AQUA,
                                            List.of(Component.text(enabled ? "On" : "Off"))),
                            current ->
                                    UiItems.literal(
                                            enabled
                                                    ? Material.LIME_STAINED_GLASS_PANE
                                                    : Material.RED_STAINED_GLASS_PANE,
                                            enabled ? "On" : "Off",
                                            NamedTextColor.GRAY,
                                            List.of()),
                            (viewer, current, context) -> {
                                calls++;
                                if (failNext) {
                                    failNext = false;
                                    throw new IllegalStateException("fixture commit refused");
                                }
                                enabled = !enabled;
                                return SettingsMenuEntry.Result.SAVED;
                            });
            // Arbitrary test entries use the existing slot seam, no fake production enum identity.
            menu =
                    new SettingsMenu(
                            base.settings,
                            base.messages,
                            id -> base.online.get(id).player,
                            (holder, size, title) -> SettingsMenuFixture.inventory(holder, size),
                            Item::new,
                            base.logger,
                            List.of(entry),
                            MenuLayout.builder(27, SettingsMenu.Action.class).build());
            listener =
                    SettingsMenuFixture.createListener(
                            menu,
                            base.settings,
                            p -> base.presentationCalls++,
                            p -> base.visibilityCalls++,
                            p -> base.soundCalls++,
                            base.messages,
                            base.logger);
        }

        InventoryClickEvent click(Inventory top, int slot, ClickType type) {
            var event = SettingsEntryExtensionHarness.click(owner, top, slot, type);
            listener.onInventoryClick(event);
            return event;
        }
    }

    private static final class Item extends ItemStack {
        final UiItemSpec spec;

        Item(UiItemSpec spec) {
            this.spec = spec;
        }
    }

    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
}
