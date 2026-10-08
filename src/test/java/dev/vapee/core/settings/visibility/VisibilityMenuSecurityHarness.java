package dev.vapee.core.settings.visibility;

import dev.vapee.core.ui.MenuTestLayouts;
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
        capabilityBoundaries();
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
                MenuTestLayouts.VISIBILITY.size(), ClickType.SHIFT_LEFT).isCancelled(),
                "visibility menu cancels bottom transfer");
        check(fixture.visibilityClick(owner, visibility, -999, ClickType.LEFT).isCancelled(),
                "visibility menu cancels outside click");
        InventoryDragEvent visibilityDrag = new InventoryDragEvent(fixture.view(owner, visibility),
                null, null, false, Map.of());
        fixture.visibilityListener.onInventoryDrag(visibilityDrag);
        check(visibilityDrag.isCancelled(), "visibility menu cancels drag");
        int applyBefore = fixture.applyCalls;
        check(fixture.visibilityClick(other, visibility, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER),
                        ClickType.LEFT).isCancelled()
                        && fixture.applyCalls == applyBefore,
                "wrong viewer cannot toggle visibility");

        VisibilitySettingsHolder forgedVisibility = new VisibilitySettingsHolder(owner.id);
        Inventory forgedVisibilityInventory = fixture.visibilityInventory(forgedVisibility,
                MenuTestLayouts.VISIBILITY.size(), net.kyori.adventure.text.Component.text("Forged"));
        forgedVisibility.bindInventory(forgedVisibilityInventory);
        check(fixture.visibilityClick(owner, forgedVisibilityInventory,
                        MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER), ClickType.LEFT).isCancelled()
                        && fixture.applyCalls == applyBefore,
                "forged bound visibility holder cannot mutate");
        VisibilitySettingsHolder unboundVisibility = new VisibilitySettingsHolder(owner.id);
        Inventory unboundVisibilityInventory = fixture.visibilityInventory(unboundVisibility,
                MenuTestLayouts.VISIBILITY.size(), net.kyori.adventure.text.Component.text("Unbound"));
        check(fixture.visibilityClick(owner, unboundVisibilityInventory,
                        MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER), ClickType.LEFT).isCancelled()
                        && fixture.applyCalls == applyBefore,
                "unbound visibility holder cannot mutate");

        fixture.visibilityMenu.open(owner.player);
        check(fixture.visibilityClick(owner, visibility, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER),
                        ClickType.LEFT).isCancelled()
                        && fixture.applyCalls == applyBefore,
                "stale visibility inventory cannot mutate");
        Inventory currentVisibility = owner.open;
        owner.open = owner.bottom;
        check(fixture.visibilityClick(owner, currentVisibility, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER),
                        ClickType.LEFT).isCancelled()
                        && fixture.applyCalls == applyBefore,
                "active-registry entry still requires exact currently open inventory");
        owner.open = currentVisibility;

        fixture.visiblePlayersMenu.open(owner.player);
        Inventory visible = owner.open;
        for (ClickType click : protectedClicks()) {
            check(fixture.visibleClick(owner, visible, MenuTestLayouts.VISIBLE_PLAYERS.emptySlot(), click).isCancelled(),
                    "visible players menu cancels " + click);
        }
        check(fixture.visibleClick(owner, visible,
                MenuTestLayouts.VISIBLE_PLAYERS.size(), ClickType.SHIFT_LEFT).isCancelled(),
                "visible players menu cancels bottom transfer");
        check(fixture.visibleClick(owner, visible, -999, ClickType.NUMBER_KEY).isCancelled(),
                "visible players menu cancels outside hotbar swap");
        InventoryDragEvent visibleDrag = new InventoryDragEvent(fixture.view(owner, visible),
                null, null, false, Map.of());
        fixture.visiblePlayersListener.onInventoryDrag(visibleDrag);
        check(visibleDrag.isCancelled(), "visible players menu cancels drag");
        check(fixture.visibleClick(other, visible, MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), ClickType.RIGHT).isCancelled()
                        && fixture.settings.isLobbyAddedVisiblePlayer(owner.id, target.id).orElseThrow(),
                "wrong viewer cannot remove mapped target");

        VisiblePlayersHolder forged = new VisiblePlayersHolder(owner.id, 0, Map.of(MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), target.id), MenuTestLayouts.VISIBLE_PLAYERS);
        Inventory forgedInventory = fixture.visibleInventory(forged, MenuTestLayouts.VISIBLE_PLAYERS.size(),
                net.kyori.adventure.text.Component.text("Forged"));
        forged.bindInventory(forgedInventory);
        check(fixture.visibleClick(owner, forgedInventory, MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), ClickType.RIGHT).isCancelled()
                        && fixture.settings.isLobbyAddedVisiblePlayer(owner.id, target.id).orElseThrow(),
                "forged bound target mapping cannot mutate");
        VisiblePlayersHolder unbound = new VisiblePlayersHolder(owner.id, 0, Map.of(MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), target.id), MenuTestLayouts.VISIBLE_PLAYERS);
        Inventory unboundInventory = fixture.visibleInventory(unbound, MenuTestLayouts.VISIBLE_PLAYERS.size(),
                net.kyori.adventure.text.Component.text("Unbound"));
        check(fixture.visibleClick(owner, unboundInventory, MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), ClickType.RIGHT).isCancelled()
                        && fixture.settings.isLobbyAddedVisiblePlayer(owner.id, target.id).orElseThrow(),
                "unbound target mapping cannot mutate");

        var second = fixture.player("Second", true);
        check(fixture.settings.addLobbyVisiblePlayer(owner.id, second.id) == AddedVisiblePlayerResult.SUCCESS,
                "fixture adds second target");
        fixture.visiblePlayersMenu.open(owner.player);
        VisiblePlayersHolder mapping = VisibilityMenuFixture.visibleHolder(owner.open);
        UUID slotZero = mapping.getTarget(MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0)).orElseThrow();
        UUID slotOne = mapping.getTarget(MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(1)).orElseThrow();
        var displayedZero = owner.open.getItem(MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0));
        owner.open.setItem(MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), owner.open.getItem(MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(1)));
        owner.open.setItem(MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(1), displayedZero);
        fixture.visibleClick(owner, owner.open, MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), ClickType.RIGHT);
        check(!fixture.settings.isLobbyAddedVisiblePlayer(owner.id, slotZero).orElseThrow()
                        && fixture.settings.isLobbyAddedVisiblePlayer(owner.id, slotOne).orElseThrow(),
                "server-side slot mapping wins over forged display item data");

        fixture.visiblePlayersMenu.open(owner.player);
        Inventory active = owner.open;
        fixture.visiblePlayersMenu.open(owner.player);
        check(fixture.visibleClick(owner, active, MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), ClickType.RIGHT).isCancelled()
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

    private static void capabilityBoundaries() throws Exception {
        for (boolean list : new boolean[]{false, true}) {
            var f = new VisibilityMenuFixture(); var owner = f.player("Entry", true);
            owner.permitted = false; int saves = f.repository.saves;
            if (list) f.visiblePlayersMenu.open(owner.player); else f.visibilityMenu.open(owner.player);
            check(owner.open == null && f.visibilityMenu.activeCount() == 0 && f.visiblePlayersMenu.activeCount() == 0
                    && f.repository.saves == saves, "denied visibility entry publishes nothing " + list);
            check(owner.received.size() == 1 && owner.closeCalls == 0, "controlled visibility entry denial " + list);
            owner.permitted = true;
            if (list) f.visiblePlayersMenu.open(owner.player); else f.visibilityMenu.open(owner.player);
            check(owner.open != null, "grant permits visibility entry " + list);
        }
        for (int slot : new int[]{MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER, 1), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.FRIENDS), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.FRIENDS, 1), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.STAFF), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.STAFF, 1), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.ADDED_USERS), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.ADDED_USERS, 1), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.GAME_PARTICIPANTS), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.GAME_PARTICIPANTS, 1), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MANAGE_PLAYERS), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.BACK), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.CLOSE), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.REFRESH), MenuTestLayouts.VISIBILITY.size()}) {
            for (boolean allowed : new boolean[]{false, true}) {
                var f = new VisibilityMenuFixture(); var owner = f.player("Actor", true);
                f.visibilityMenu.open(owner.player); Inventory top = owner.open;
                var state = f.settings.getSettings(owner.id).orElseThrow().getVisibility();
                var before = java.util.List.of(state.isAllPlayersVisible(), state.isShowFriends(), state.isShowStaff(),
                        state.isShowAddedUsers(), state.isShowGameParticipants(), state.getAddedPlayers());
                int saves = f.repository.saves; owner.permitted = allowed;
                check(f.visibilityClick(owner, top, slot, ClickType.LEFT).isCancelled(), "visibility action cancelled " + slot + allowed);
                var after = java.util.List.of(state.isAllPlayersVisible(), state.isShowFriends(), state.isShowStaff(),
                        state.isShowAddedUsers(), state.isShowGameParticipants(), state.getAddedPlayers());
                if (!allowed) {
                    check(before.equals(after) && f.repository.saves == saves && f.applyCalls == 0 && f.hotbarCalls == 0
                            && f.settingsBackCalls == 0 && owner.soundCalls == 0 && f.visiblePlayersMenu.activeCount() == 0,
                            "revoked visibility action cannot mutate/save/navigate " + slot);
                    check(f.visibilityMenu.activeCount() == 0 && owner.open == owner.bottom && owner.closeCalls == 1
                            && owner.received.size() == 1, "visibility revocation cleans owned view once " + slot);
                    f.visibilityClick(owner, top, slot, ClickType.LEFT);
                    check(owner.closeCalls == 1 && owner.received.size() == 1, "visibility stale replay inert " + slot);
                } else if (java.util.List.of(MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER, 1), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.FRIENDS), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.FRIENDS, 1), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.STAFF), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.STAFF, 1), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.ADDED_USERS), MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.ADDED_USERS, 1)).contains(slot)) {
                    check(!before.equals(after) && f.repository.saves == saves + 1 && f.applyCalls == 1,
                            "authorized visibility mutation " + slot);
                } else check(before.equals(after) && f.repository.saves == saves, "nonmutating/unavailable visibility route " + slot);
            }
        }
        for (int slot : new int[]{MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), MenuTestLayouts.VISIBLE_PLAYERS.slot(VisiblePlayersMenu.Action.PREVIOUS), MenuTestLayouts.VISIBLE_PLAYERS.slot(VisiblePlayersMenu.Action.ADD), MenuTestLayouts.VISIBLE_PLAYERS.slot(VisiblePlayersMenu.Action.BACK), MenuTestLayouts.VISIBLE_PLAYERS.slot(VisiblePlayersMenu.Action.CLOSE), MenuTestLayouts.VISIBLE_PLAYERS.slot(VisiblePlayersMenu.Action.REFRESH), MenuTestLayouts.VISIBLE_PLAYERS.slot(VisiblePlayersMenu.Action.NEXT), MenuTestLayouts.VISIBLE_PLAYERS.size()}) {
            var f = new VisibilityMenuFixture(); var owner = f.player("List", true); var target = f.player("Target", true);
            f.settings.addLobbyVisiblePlayer(owner.id, target.id);
            f.visiblePlayersMenu.open(owner.player); Inventory top = owner.open;
            int saves = f.repository.saves; owner.permitted = false;
            check(f.visibleClick(owner, top, slot, ClickType.RIGHT).isCancelled()
                    && f.settings.isLobbyAddedVisiblePlayer(owner.id, target.id).orElseThrow() && f.repository.saves == saves
                    && f.applyCalls == 0 && owner.soundCalls == 0, "revoked visible-player action cannot remove/save " + slot);
            check(f.visiblePlayersMenu.activeCount() == 0 && owner.closeCalls == 1 && owner.open == owner.bottom
                    && owner.received.size() == 1 && f.visibilityMenu.activeCount() == 0, "visible-player owned denial cleanup " + slot);
            f.visibleClick(owner, top, slot, ClickType.RIGHT);
            check(owner.received.size() == 1 && owner.closeCalls == 1, "visible-player replay does not spam " + slot);
            owner.permitted = true; f.visiblePlayersMenu.open(owner.player);
            f.visibleClick(owner, owner.open, MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), ClickType.RIGHT);
            check(!f.settings.isLobbyAddedVisiblePlayer(owner.id, target.id).orElseThrow() && f.repository.saves == saves + 1,
                    "grant restores legitimate added-user removal " + slot);
        }
        for (boolean list : new boolean[]{false, true}) {
            var f = new VisibilityMenuFixture(); var owner = f.player("Foreign", true);
            if (list) f.visiblePlayersMenu.open(owner.player); else f.visibilityMenu.open(owner.player);
            Inventory top = owner.open; owner.open = owner.bottom; owner.permitted = false;
            if (list) f.visibleClick(owner, top, MenuTestLayouts.VISIBLE_PLAYERS.contentSlot(0), ClickType.RIGHT);
            else f.visibilityClick(owner, top, MenuTestLayouts.VISIBILITY.slot(VisibilitySettingsMenu.Action.MASTER), ClickType.LEFT);
            check(owner.closeCalls == 0 && owner.open == owner.bottom && owner.received.isEmpty(),
                    "revoked stale visibility event preserves foreign current inventory " + list);
            f.visibilityMenu.closeOpenInventories(); f.visiblePlayersMenu.closeOpenInventories();
            check(f.visibilityMenu.activeCount() == 0 && f.visiblePlayersMenu.activeCount() == 0 && owner.closeCalls == 0,
                    "disable forgets revoked stale binding without touching foreign view " + list);
        }
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
