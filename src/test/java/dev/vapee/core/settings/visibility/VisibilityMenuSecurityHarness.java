package dev.vapee.core.settings.visibility;

import dev.vapee.core.player.settings.AddedVisiblePlayerResult;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;

public final class VisibilityMenuSecurityHarness {
    private static int checks;

    public static void main(String[] args) throws Exception {
        VisibilityMenuFixture fixture = new VisibilityMenuFixture();
        var owner = fixture.player("Owner", true);
        var other = fixture.player("Other", true);
        var target = fixture.player("Target", true);
        check(fixture.settings.addLobbyVisiblePlayer(owner.id, target.id) == AddedVisiblePlayerResult.SUCCESS,
                "fixture has one mapped target");

        fixture.visibilityMenu.open(owner.player);
        Inventory visibility = owner.open;
        for (ClickType click : protectedClicks()) {
            check(fixture.visibilityClick(owner, visibility, 0, click).isCancelled(),
                    "visibility menu cancels " + click);
        }
        check(fixture.visibilityClick(owner, visibility,
                VisibilitySettingsMenu.INVENTORY_SIZE, ClickType.SHIFT_LEFT).isCancelled(),
                "visibility menu cancels bottom transfer");
        check(fixture.visibilityClick(owner, visibility, -999, ClickType.LEFT).isCancelled(),
                "visibility menu cancels outside click");
        InventoryDragEvent visibilityDrag = new InventoryDragEvent(fixture.view(owner, visibility),
                null, null, false, Map.of());
        fixture.visibilityListener.onInventoryDrag(visibilityDrag);
        check(visibilityDrag.isCancelled(), "visibility menu cancels drag");
        int applyBefore = fixture.applyCalls;
        check(fixture.visibilityClick(other, visibility, VisibilitySettingsMenu.MASTER_SLOT,
                        ClickType.LEFT).isCancelled()
                        && fixture.applyCalls == applyBefore,
                "wrong viewer cannot toggle visibility");

        VisibilitySettingsHolder forgedVisibility = new VisibilitySettingsHolder(owner.id);
        Inventory forgedVisibilityInventory = fixture.visibilityInventory(forgedVisibility,
                VisibilitySettingsMenu.INVENTORY_SIZE, net.kyori.adventure.text.Component.text("Forged"));
        forgedVisibility.bindInventory(forgedVisibilityInventory);
        check(fixture.visibilityClick(owner, forgedVisibilityInventory,
                        VisibilitySettingsMenu.MASTER_SLOT, ClickType.LEFT).isCancelled()
                        && fixture.applyCalls == applyBefore,
                "forged bound visibility holder cannot mutate");
        VisibilitySettingsHolder unboundVisibility = new VisibilitySettingsHolder(owner.id);
        Inventory unboundVisibilityInventory = fixture.visibilityInventory(unboundVisibility,
                VisibilitySettingsMenu.INVENTORY_SIZE, net.kyori.adventure.text.Component.text("Unbound"));
        check(fixture.visibilityClick(owner, unboundVisibilityInventory,
                        VisibilitySettingsMenu.MASTER_SLOT, ClickType.LEFT).isCancelled()
                        && fixture.applyCalls == applyBefore,
                "unbound visibility holder cannot mutate");

        fixture.visibilityMenu.open(owner.player);
        check(fixture.visibilityClick(owner, visibility, VisibilitySettingsMenu.MASTER_SLOT,
                        ClickType.LEFT).isCancelled()
                        && fixture.applyCalls == applyBefore,
                "stale visibility inventory cannot mutate");
        Inventory currentVisibility = owner.open;
        owner.open = owner.bottom;
        check(fixture.visibilityClick(owner, currentVisibility, VisibilitySettingsMenu.MASTER_SLOT,
                        ClickType.LEFT).isCancelled()
                        && fixture.applyCalls == applyBefore,
                "active-registry entry still requires exact currently open inventory");
        owner.open = currentVisibility;

        fixture.visiblePlayersMenu.open(owner.player);
        Inventory visible = owner.open;
        for (ClickType click : protectedClicks()) {
            check(fixture.visibleClick(owner, visible, 22, click).isCancelled(),
                    "visible players menu cancels " + click);
        }
        check(fixture.visibleClick(owner, visible,
                VisiblePlayersMenu.INVENTORY_SIZE, ClickType.SHIFT_LEFT).isCancelled(),
                "visible players menu cancels bottom transfer");
        check(fixture.visibleClick(owner, visible, -999, ClickType.NUMBER_KEY).isCancelled(),
                "visible players menu cancels outside hotbar swap");
        InventoryDragEvent visibleDrag = new InventoryDragEvent(fixture.view(owner, visible),
                null, null, false, Map.of());
        fixture.visiblePlayersListener.onInventoryDrag(visibleDrag);
        check(visibleDrag.isCancelled(), "visible players menu cancels drag");
        check(fixture.visibleClick(other, visible, 0, ClickType.RIGHT).isCancelled()
                        && fixture.settings.isLobbyAddedVisiblePlayer(owner.id, target.id).orElseThrow(),
                "wrong viewer cannot remove mapped target");

        VisiblePlayersHolder forged = new VisiblePlayersHolder(owner.id, 0, Map.of(0, target.id));
        Inventory forgedInventory = fixture.visibleInventory(forged, VisiblePlayersMenu.INVENTORY_SIZE,
                net.kyori.adventure.text.Component.text("Forged"));
        forged.bindInventory(forgedInventory);
        check(fixture.visibleClick(owner, forgedInventory, 0, ClickType.RIGHT).isCancelled()
                        && fixture.settings.isLobbyAddedVisiblePlayer(owner.id, target.id).orElseThrow(),
                "forged bound target mapping cannot mutate");
        VisiblePlayersHolder unbound = new VisiblePlayersHolder(owner.id, 0, Map.of(0, target.id));
        Inventory unboundInventory = fixture.visibleInventory(unbound, VisiblePlayersMenu.INVENTORY_SIZE,
                net.kyori.adventure.text.Component.text("Unbound"));
        check(fixture.visibleClick(owner, unboundInventory, 0, ClickType.RIGHT).isCancelled()
                        && fixture.settings.isLobbyAddedVisiblePlayer(owner.id, target.id).orElseThrow(),
                "unbound target mapping cannot mutate");

        var second = fixture.player("Second", true);
        check(fixture.settings.addLobbyVisiblePlayer(owner.id, second.id) == AddedVisiblePlayerResult.SUCCESS,
                "fixture adds second target");
        fixture.visiblePlayersMenu.open(owner.player);
        VisiblePlayersHolder mapping = VisibilityMenuFixture.visibleHolder(owner.open);
        UUID slotZero = mapping.getTarget(0).orElseThrow();
        UUID slotOne = mapping.getTarget(1).orElseThrow();
        var displayedZero = owner.open.getItem(0);
        owner.open.setItem(0, owner.open.getItem(1));
        owner.open.setItem(1, displayedZero);
        fixture.visibleClick(owner, owner.open, 0, ClickType.RIGHT);
        check(!fixture.settings.isLobbyAddedVisiblePlayer(owner.id, slotZero).orElseThrow()
                        && fixture.settings.isLobbyAddedVisiblePlayer(owner.id, slotOne).orElseThrow(),
                "server-side slot mapping wins over forged display item data");

        fixture.visiblePlayersMenu.open(owner.player);
        Inventory active = owner.open;
        fixture.visiblePlayersMenu.open(owner.player);
        check(fixture.visibleClick(owner, active, 0, ClickType.RIGHT).isCancelled()
                        && fixture.settings.isLobbyAddedVisiblePlayer(owner.id, slotOne).orElseThrow(),
                "stale visible players inventory cannot mutate");
        fixture.visiblePlayersListener.onInventoryClose(new InventoryCloseEvent(fixture.view(owner, active)));
        check(fixture.visiblePlayersMenu.isActive(owner.player, owner.open,
                        VisibilityMenuFixture.visibleHolder(owner.open)),
                "closing stale inventory does not invalidate replacement");
        Inventory activeReplacement = owner.open;
        fixture.visiblePlayersListener.onInventoryClose(
                new InventoryCloseEvent(fixture.view(owner, activeReplacement)));
        check(!fixture.visiblePlayersMenu.isActive(owner.player, activeReplacement,
                        VisibilityMenuFixture.visibleHolder(activeReplacement)),
                "close cleanup forgets exact active inventory");

        fixture.visibilityMenu.open(owner.player);
        Inventory disableVisibility = owner.open;
        fixture.visiblePlayersMenu.open(owner.player);
        Inventory disableVisible = owner.open;
        fixture.visibilityMenu.closeOpenInventories();
        fixture.visiblePlayersMenu.closeOpenInventories();
        check(!fixture.visibilityMenu.isActive(owner.player, disableVisibility,
                        VisibilityMenuFixture.visibilityHolder(disableVisibility))
                        && !fixture.visiblePlayersMenu.isActive(owner.player, disableVisible,
                        VisibilityMenuFixture.visibleHolder(disableVisible)),
                "module-style cleanup invalidates both menu registries");
        checks += dev.vapee.core.ui.MenuCloseProbe.verify("VisibilitySettingsMenu", probe -> {
            var menu = new VisibilitySettingsMenu(fixture.settings, fixture.messages, probe::lookup,
                    fixture::visibilityInventory, VisibilityMenuFixture.VisibilitySpecItem::new, probe.logger);
            var listener = new VisibilitySettingsListener(menu, fixture.visiblePlayersMenu, ignored -> { },
                    fixture.settings, ignored -> { }, ignored -> { }, ignored -> { }, fixture.messages, probe.logger);
            return new dev.vapee.core.ui.MenuCloseProbe.Subject(player -> {
                fixture.players.loadPlayer(player.getUniqueId(), player.getName());
                menu.open(player);
            }, menu::closeOpenInventories, menu::activeCount, listener::onInventoryClose);
        });
        checks += dev.vapee.core.ui.MenuCloseProbe.verify("VisiblePlayersMenu", probe -> {
            var menu = new VisiblePlayersMenu(fixture.settings, fixture.identities, fixture.messages, probe::lookup,
                    fixture::visibleInventory, VisibilityMenuFixture.VisibleSpecItem::new, probe.logger);
            var listener = new VisiblePlayersListener(menu, fixture.visibilityMenu, fixture.settings,
                    ignored -> { }, ignored -> { }, fixture.messages, probe.logger);
            return new dev.vapee.core.ui.MenuCloseProbe.Subject(player -> {
                fixture.players.loadPlayer(player.getUniqueId(), player.getName());
                menu.open(player);
            }, menu::closeOpenInventories, menu::activeCount, listener::onInventoryClose);
        });
        System.out.println("VisibilityMenuSecurityHarness passed " + checks + " checks.");
    }

    private static ClickType[] protectedClicks() {
        return new ClickType[]{ClickType.LEFT, ClickType.RIGHT, ClickType.SHIFT_LEFT,
                ClickType.NUMBER_KEY, ClickType.DOUBLE_CLICK, ClickType.SWAP_OFFHAND,
                ClickType.DROP, ClickType.CONTROL_DROP, ClickType.CREATIVE, ClickType.MIDDLE};
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
