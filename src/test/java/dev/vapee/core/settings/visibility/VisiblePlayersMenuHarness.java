package dev.vapee.core.settings.visibility;

import dev.vapee.core.player.settings.AddedVisiblePlayerResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.UUID;

public final class VisiblePlayersMenuHarness {
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    public static void main(String[] args) throws Exception {
        emptyAndNavigation();
        renderingAndPagination();
        removalAndFailures();
        System.out.println("VisiblePlayersMenuHarness passed " + checks + " checks.");
    }

    private static void emptyAndNavigation() throws Exception {
        VisibilityMenuFixture fixture = new VisibilityMenuFixture();
        var owner = fixture.player("Owner", true);
        fixture.visiblePlayersMenu.open(owner.player);
        Inventory inventory = owner.open;
        VisiblePlayersHolder holder = VisibilityMenuFixture.visibleHolder(inventory);
        check(inventory.getSize() == 54 && VisiblePlayersMenu.CONTENT_SIZE == 45,
                "menu has 54 slots and 45 content slots");
        check(holder.getOwnerUniqueId().equals(owner.id) && holder.getPage() == 0
                        && holder.getInventory() == inventory
                        && fixture.visiblePlayersMenu.isActive(owner.player, inventory, holder),
                "holder binds exact owner, page and active inventory");
        check(text(inventory, 22).equals("No added visible players") && holder.getTarget(22).isEmpty(),
                "empty state has no mutation target");
        check(inventory.getItem(VisiblePlayersMenu.PREVIOUS_SLOT) == null
                        && inventory.getItem(VisiblePlayersMenu.NEXT_SLOT) == null,
                "empty state has bounded navigation");
        check(inventory.getItem(VisiblePlayersMenu.ADD_SLOT) != null,
                "add player remains available in empty state");

        fixture.visibleClick(owner, inventory, VisiblePlayersMenu.ADD_SLOT, ClickType.LEFT);
        check(owner.closeCalls == 1 && hasSuggestion(owner.received.getLast(),
                        "/settings visibility add "),
                "add prompt closes menu and suggests the command");
        fixture.visiblePlayersMenu.open(owner.player);
        fixture.visibleClick(owner, owner.open, VisiblePlayersMenu.BACK_SLOT, ClickType.LEFT);
        check(owner.open.getHolder() instanceof VisibilitySettingsHolder,
                "back opens visibility settings menu");
        fixture.visiblePlayersMenu.open(owner.player);
        Inventory beforeRefresh = owner.open;
        fixture.visibleClick(owner, owner.open, VisiblePlayersMenu.REFRESH_SLOT, ClickType.LEFT);
        check(owner.open != beforeRefresh && VisibilityMenuFixture.visibleHolder(owner.open).getPage() == 0,
                "refresh rebuilds current page");
        fixture.visibleClick(owner, owner.open, VisiblePlayersMenu.CLOSE_SLOT, ClickType.LEFT);
        check(owner.closeCalls == 2, "close button closes menu");
    }

    private static void renderingAndPagination() throws Exception {
        VisibilityMenuFixture fixture = new VisibilityMenuFixture();
        var owner = fixture.player("Owner", true);
        var bob = fixture.player("Bob", true);
        var alice = fixture.player("alice", false);
        UUID unknown = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        add(fixture, owner.id, bob.id);
        add(fixture, owner.id, unknown);
        add(fixture, owner.id, alice.id);
        fixture.visiblePlayersMenu.open(owner.player);
        VisiblePlayersHolder holder = VisibilityMenuFixture.visibleHolder(owner.open);
        check(holder.getTarget(0).orElseThrow().equals(alice.id)
                        && holder.getTarget(1).orElseThrow().equals(bob.id)
                        && holder.getTarget(2).orElseThrow().equals(unknown),
                "known names sort case-insensitively before deterministic UUID fallback");
        check(text(owner.open, 0).equals("alice") && text(owner.open, 1).equals("Bob")
                        && text(owner.open, 2).equals(unknown.toString()),
                "known identities use names and unknown identity uses UUID");
        check(material(owner.open, 0) == Material.PLAYER_HEAD
                        && lore(owner.open, 0).contains("Offline")
                        && lore(owner.open, 1).contains("Online")
                        && lore(owner.open, 1).contains("Right-click to remove."),
                "entry renders head, actual online status and removal gesture");
        check(VisibilityMenuFixture.visibleSpec(owner.open, 1).lore().getFirst().color()
                        .equals(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                        && VisibilityMenuFixture.visibleSpec(owner.open, 0).lore().getFirst().color()
                        .equals(net.kyori.adventure.text.format.NamedTextColor.GRAY),
                "online and offline entries use clear status colors");

        VisibilityMenuFixture pages = new VisibilityMenuFixture();
        var pageOwner = pages.player("PageOwner", true);
        for (int index = 0; index < 46; index++) {
            var target = pages.player("Page" + String.format("%02d", index), false);
            add(pages, pageOwner.id, target.id);
        }
        pages.visiblePlayersMenu.open(pageOwner.player);
        check(VisibilityMenuFixture.visibleHolder(pageOwner.open).getTarget(44).isPresent()
                        && pageOwner.open.getItem(VisiblePlayersMenu.NEXT_SLOT) != null,
                "45 entries occupy page zero and next is available");
        pages.visibleClick(pageOwner, pageOwner.open, VisiblePlayersMenu.NEXT_SLOT, ClickType.LEFT);
        check(VisibilityMenuFixture.visibleHolder(pageOwner.open).getPage() == 1
                        && VisibilityMenuFixture.visibleHolder(pageOwner.open).getTarget(0).isPresent()
                        && VisibilityMenuFixture.visibleHolder(pageOwner.open).getTarget(1).isEmpty(),
                "page one contains only the forty-sixth entry");
        check(pageOwner.open.getItem(VisiblePlayersMenu.PREVIOUS_SLOT) != null
                        && pageOwner.open.getItem(VisiblePlayersMenu.NEXT_SLOT) == null,
                "page-one navigation is bounded");
        pages.visibleClick(pageOwner, pageOwner.open, VisiblePlayersMenu.PREVIOUS_SLOT, ClickType.LEFT);
        check(VisibilityMenuFixture.visibleHolder(pageOwner.open).getPage() == 0,
                "previous returns to page zero");
        pages.visibleClick(pageOwner, pageOwner.open, VisiblePlayersMenu.NEXT_SLOT, ClickType.LEFT);
        UUID last = VisibilityMenuFixture.visibleHolder(pageOwner.open).getTarget(0).orElseThrow();
        pages.settings.removeLobbyVisiblePlayer(pageOwner.id, last);
        pages.visibleClick(pageOwner, pageOwner.open, VisiblePlayersMenu.REFRESH_SLOT, ClickType.LEFT);
        check(VisibilityMenuFixture.visibleHolder(pageOwner.open).getPage() == 0,
                "refresh clamps a removed last page");
        pages.visiblePlayersMenu.open(pageOwner.player, -100);
        check(VisibilityMenuFixture.visibleHolder(pageOwner.open).getPage() == 0,
                "negative requested page clamps to zero");
    }

    private static void removalAndFailures() throws Exception {
        VisibilityMenuFixture fixture = new VisibilityMenuFixture();
        var owner = fixture.player("Owner", true);
        var first = fixture.player("First", true);
        var second = fixture.player("Second", false);
        add(fixture, owner.id, first.id);
        add(fixture, owner.id, second.id);
        fixture.visiblePlayersMenu.open(owner.player);
        UUID slotZero = VisibilityMenuFixture.visibleHolder(owner.open).getTarget(0).orElseThrow();
        fixture.visibleClick(owner, owner.open, 0, ClickType.LEFT);
        check(fixture.settings.isLobbyAddedVisiblePlayer(owner.id, slotZero).orElseThrow(),
                "left click never removes an entry");
        fixture.visibleClick(owner, owner.open, 0, ClickType.RIGHT);
        check(!fixture.settings.isLobbyAddedVisiblePlayer(owner.id, slotZero).orElseThrow()
                        && fixture.applyCalls == 1
                        && plain(owner.received.getLast()).contains("Removed"),
                "right click removes, applies immediately and confirms");

        UUID remaining = VisibilityMenuFixture.visibleHolder(owner.open).getTarget(0).orElseThrow();
        fixture.repository.failNext = true;
        int appliesBeforeFailure = fixture.applyCalls;
        fixture.visibleClick(owner, owner.open, 0, ClickType.RIGHT);
        check(fixture.settings.isLobbyAddedVisiblePlayer(owner.id, remaining).orElseThrow()
                        && fixture.applyCalls == appliesBeforeFailure
                        && plain(owner.received.getLast()).contains("could not be saved"),
                "remove save failure rolls back without visibility apply");

        fixture.visiblePlayersMenu.open(owner.player);
        remaining = VisibilityMenuFixture.visibleHolder(owner.open).getTarget(0).orElseThrow();
        fixture.settings.removeLobbyVisiblePlayer(owner.id, remaining);
        fixture.visibleClick(owner, owner.open, 0, ClickType.RIGHT);
        check(plain(owner.received.getLast()).contains("no longer")
                        && text(owner.open, 22).equals("No added visible players"),
                "stale target is controlled and rebuilds empty state");
    }

    private static void add(VisibilityMenuFixture fixture, UUID owner, UUID target) {
        check(fixture.settings.addLobbyVisiblePlayer(owner, target) == AddedVisiblePlayerResult.SUCCESS,
                "fixture add succeeds");
    }

    private static Material material(Inventory inventory, int slot) {
        return VisibilityMenuFixture.visibleSpec(inventory, slot).material();
    }

    private static String text(Inventory inventory, int slot) {
        return plain(VisibilityMenuFixture.visibleSpec(inventory, slot).name());
    }

    private static String lore(Inventory inventory, int slot) {
        return String.join("\n", VisibilityMenuFixture.visibleSpec(inventory, slot).lore().stream()
                .map(PLAIN::serialize).toList());
    }

    private static boolean hasSuggestion(Component component, String value) {
        if (component.clickEvent() != null
                && component.clickEvent().action() == ClickEvent.Action.SUGGEST_COMMAND
                && component.clickEvent().value().equals(value)) return true;
        return component.children().stream().anyMatch(child -> hasSuggestion(child, value));
    }

    private static String plain(Component component) {
        return PLAIN.serialize(component);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
