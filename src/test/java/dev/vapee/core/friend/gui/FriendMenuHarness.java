package dev.vapee.core.friend.gui;

import dev.vapee.core.friend.FriendLimits;
import dev.vapee.core.friend.FriendMessages;
import dev.vapee.core.friend.FriendRelation;
import dev.vapee.core.friend.FriendResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class FriendMenuHarness {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static int checks;

    public static void main(String[] args) throws Exception {
        emptyAndControls();
        renderingAndPagination();
        actionsAndStaleState();
        resultFeedback();
        System.out.println("FriendMenuHarness passed " + checks + " checks.");
    }

    private static void emptyAndControls() throws Exception {
        FriendMenuFixture fixture = new FriendMenuFixture();
        var owner = fixture.player("Owner", true);
        fixture.menu.open(owner.player);
        Inventory inventory = owner.open;
        FriendMenuHolder holder = FriendMenuFixture.holder(inventory);
        check(inventory.getSize() == 54 && FriendMenu.CONTENT_SIZE == 45, "54 slots with 45 content slots");
        check(holder.getOwnerUniqueId().equals(owner.id) && holder.getView() == FriendMenuView.FRIENDS
                && holder.getPage() == 0 && holder.getInventory() == inventory,
                "holder binds exact inventory, owner, default view and page zero");
        check(fixture.menu.isActive(owner.player, inventory, holder), "new inventory is active");
        check(text(inventory, 22).contains("No friends yet") && holder.getTarget(22).isEmpty(),
                "empty friends view has informational item without target");
        check(inventory.getItem(FriendMenu.PREVIOUS_SLOT) == null
                && inventory.getItem(FriendMenu.NEXT_SLOT) == null, "empty view has no page buttons");
        check(text(inventory, FriendMenu.FRIENDS_SLOT).contains("Friends")
                && lore(inventory, FriendMenu.FRIENDS_SLOT).contains("0 / 100"), "friends tab shows live count and limit");

        fixture.click(owner, inventory, FriendMenu.INCOMING_SLOT, ClickType.LEFT);
        check(FriendMenuFixture.holder(owner.open).getView() == FriendMenuView.INCOMING
                && text(owner.open, 22).contains("No incoming requests"), "incoming tab and empty state");
        fixture.click(owner, owner.open, FriendMenu.OUTGOING_SLOT, ClickType.LEFT);
        check(FriendMenuFixture.holder(owner.open).getView() == FriendMenuView.OUTGOING
                && text(owner.open, 22).contains("No outgoing requests"), "outgoing tab and empty state");
        fixture.click(owner, owner.open, FriendMenu.FRIENDS_SLOT, ClickType.LEFT);
        check(FriendMenuFixture.holder(owner.open).getView() == FriendMenuView.FRIENDS, "friends tab returns to friends");
        Inventory beforeRefresh = owner.open;
        fixture.click(owner, beforeRefresh, FriendMenu.REFRESH_SLOT, ClickType.LEFT);
        check(owner.open != beforeRefresh && FriendMenuFixture.holder(owner.open).getPage() == 0,
                "refresh renders a new live inventory");
        fixture.click(owner, owner.open, FriendMenu.ADD_SLOT, ClickType.LEFT);
        check(owner.closeCalls == 1 && owner.open == owner.bottom,
                "add friend closes the menu without a text-capture system");
        check(owner.received.stream().anyMatch(FriendMenuHarness::hasSuggestCommand),
                "add prompt suggests /friend add with an Adventure click event");
        fixture.menu.open(owner.player);
        fixture.click(owner, owner.open, FriendMenu.CLOSE_SLOT, ClickType.LEFT);
        check(owner.closeCalls == 2, "close button closes menu");
    }

    private static void renderingAndPagination() throws Exception {
        FriendMenuFixture fixture = new FriendMenuFixture();
        var owner = fixture.player("Owner", true);
        var bob = fixture.player("Bob", true);
        var alice = fixture.player("alice", false);
        UUID missing = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        befriend(fixture, owner.id, bob.id);
        befriend(fixture, owner.id, alice.id);
        befriend(fixture, owner.id, missing);
        fixture.menu.open(owner.player);
        Inventory inventory = owner.open;
        FriendMenuHolder holder = FriendMenuFixture.holder(inventory);
        check(holder.getTarget(0).orElseThrow().equals(alice.id)
                && holder.getTarget(1).orElseThrow().equals(bob.id)
                && holder.getTarget(2).orElseThrow().equals(missing),
                "friends sort by case-insensitive name with deterministic UUID fallback");
        check(FriendMenuFixture.spec(inventory, 0).spec.material() == Material.PLAYER_HEAD
                && FriendMenuFixture.spec(inventory, 1).spec.material() == Material.PLAYER_HEAD,
                "friend entries use generic player heads without skin lookups");
        check(lore(inventory, 0).contains("Offline") && lore(inventory, 1).contains("Online"),
                "status uses actual online player rather than known identity");
        check(text(inventory, 2).equals(missing.toString()), "missing identity renders literal UUID fallback");
        check(lore(inventory, 1).contains("Shift + Right-click to remove"),
                "friend removal gesture is clearly explained");
        fixture.limits.set(new FriendLimits(55, 25, 25));
        bob.online = false;
        fixture.click(owner, inventory, FriendMenu.REFRESH_SLOT, ClickType.LEFT);
        check(lore(owner.open, FriendMenu.FRIENDS_SLOT).contains("3 / 55")
                        && lore(owner.open, 1).contains("Offline"),
                "refresh reads reloaded friend limit, count and actual online status");

        FriendMenuFixture pages = new FriendMenuFixture();
        var pageOwner = pages.player("PageOwner", true);
        for (int index = 0; index < 46; index++) {
            var target = pages.player("Page" + String.format("%02d", index), false);
            befriend(pages, pageOwner.id, target.id);
        }
        pages.menu.open(pageOwner.player);
        check(FriendMenuFixture.holder(pageOwner.open).getTarget(44).isPresent()
                && pageOwner.open.getItem(FriendMenu.NEXT_SLOT) != null,
                "45 entries fit page zero and next is active");
        pages.click(pageOwner, pageOwner.open, FriendMenu.NEXT_SLOT, ClickType.LEFT);
        check(FriendMenuFixture.holder(pageOwner.open).getPage() == 1
                && FriendMenuFixture.holder(pageOwner.open).getTarget(0).isPresent()
                && FriendMenuFixture.holder(pageOwner.open).getTarget(1).isEmpty(),
                "page one contains the forty-sixth entry only");
        check(pageOwner.open.getItem(FriendMenu.PREVIOUS_SLOT) != null
                && pageOwner.open.getItem(FriendMenu.NEXT_SLOT) == null, "page one navigation is bounded");
        pages.click(pageOwner, pageOwner.open, FriendMenu.PREVIOUS_SLOT, ClickType.LEFT);
        check(FriendMenuFixture.holder(pageOwner.open).getPage() == 0,
                "previous button returns to page zero");
        pages.click(pageOwner, pageOwner.open, FriendMenu.NEXT_SLOT, ClickType.LEFT);
        UUID last = FriendMenuFixture.holder(pageOwner.open).getTarget(0).orElseThrow();
        pages.friends.removeFriend(pageOwner.id, last);
        pages.click(pageOwner, pageOwner.open, FriendMenu.REFRESH_SLOT, ClickType.LEFT);
        check(FriendMenuFixture.holder(pageOwner.open).getPage() == 0,
                "refresh clamps a vanished last page to page zero");
        pages.menu.open(pageOwner.player, FriendMenuView.FRIENDS, -100);
        check(FriendMenuFixture.holder(pageOwner.open).getPage() == 0, "negative requested page clamps to zero");

        FriendMenuFixture ties = new FriendMenuFixture();
        var tieOwner = ties.player("TieOwner", true);
        UUID earlier = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID later = UUID.fromString("00000000-0000-0000-0000-000000000002");
        ties.player(later, "same", false, true);
        ties.player(earlier, "Same", false, true);
        befriend(ties, tieOwner.id, later);
        befriend(ties, tieOwner.id, earlier);
        ties.menu.open(tieOwner.player);
        check(FriendMenuFixture.holder(tieOwner.open).getTarget(0).orElseThrow().equals(earlier),
                "case-insensitive name ties are broken by UUID, never hash order");

        FriendMenuFixture literal = new FriendMenuFixture();
        var literalOwner = literal.player("LiteralOwner", true);
        var markup = literal.player("<red>Safe</red>", false);
        befriend(literal, literalOwner.id, markup.id);
        literal.menu.open(literalOwner.player);
        check(text(literalOwner.open, 0).equals("<red>Safe</red>")
                        && net.kyori.adventure.text.format.NamedTextColor.AQUA.equals(
                        FriendMenuFixture.spec(literalOwner.open, 0).spec.name().color()),
                "player-controlled names remain literal Components, not MiniMessage");
    }

    private static void actionsAndStaleState() throws Exception {
        FriendMenuFixture fixture = new FriendMenuFixture();
        var owner = fixture.player("Owner", true);
        var bob = fixture.player("Bob", true);
        var charlie = fixture.player("Charlie", true);
        var dana = fixture.player("Dana", false);
        befriend(fixture, owner.id, bob.id);
        fixture.menu.open(owner.player);
        fixture.click(owner, owner.open, 0, ClickType.LEFT);
        check(fixture.friends.getRelation(owner.id, bob.id) == FriendRelation.FRIENDS,
                "ordinary friend click cannot remove a friendship");
        fixture.click(owner, owner.open, 0, ClickType.SHIFT_RIGHT);
        check(fixture.friends.getRelation(owner.id, bob.id) == FriendRelation.NONE
                && last(owner).contains("Removed friend Bob")
                && text(owner.open, 22).contains("No friends"),
                "shift-right removes and refreshes friends view");

        fixture.friends.sendRequest(charlie.id, owner.id);
        fixture.menu.open(owner.player, FriendMenuView.INCOMING, 0);
        check(FriendMenuFixture.holder(owner.open).getTarget(0).orElseThrow().equals(charlie.id)
                && lore(owner.open, 0).contains("Left-click to accept")
                && lore(owner.open, 0).contains("Right-click to decline"),
                "incoming request maps sender UUID and documents both actions");
        fixture.click(owner, owner.open, 0, ClickType.LEFT);
        check(fixture.friends.getRelation(owner.id, charlie.id) == FriendRelation.FRIENDS
                && last(owner).contains("now friends with Charlie")
                && last(charlie).contains("accepted your friend request")
                && charlie.received.size() == 1
                && text(owner.open, 22).contains("No incoming"),
                "accept confirms, notifies online sender once and refreshes");

        fixture.friends.sendRequest(bob.id, owner.id);
        fixture.menu.open(owner.player, FriendMenuView.INCOMING, 0);
        fixture.click(owner, owner.open, 0, ClickType.RIGHT);
        check(fixture.friends.getRelation(owner.id, bob.id) == FriendRelation.NONE
                && last(owner).contains("declined") && text(owner.open, 22).contains("No incoming"),
                "right click declines and refreshes without notifying sender");

        fixture.friends.sendRequest(owner.id, dana.id);
        fixture.menu.open(owner.player, FriendMenuView.OUTGOING, 0);
        check(FriendMenuFixture.holder(owner.open).getTarget(0).orElseThrow().equals(dana.id)
                && lore(owner.open, 0).contains("Click to cancel request."),
                "outgoing request maps recipient UUID");
        fixture.click(owner, owner.open, 0, ClickType.RIGHT);
        check(fixture.friends.getRelation(owner.id, dana.id) == FriendRelation.NONE
                && text(owner.open, 22).contains("No outgoing"), "outgoing request can be cancelled");

        fixture.friends.sendRequest(bob.id, owner.id);
        fixture.menu.open(owner.player, FriendMenuView.INCOMING, 0);
        fixture.friends.denyRequest(owner.id, bob.id);
        fixture.click(owner, owner.open, 0, ClickType.LEFT);
        check(last(owner).contains("No matching friend request")
                && text(owner.open, 22).contains("No incoming"),
                "stale incoming request is reported and refreshed");
        fixture.friends.sendRequest(owner.id, dana.id);
        fixture.menu.open(owner.player, FriendMenuView.OUTGOING, 0);
        fixture.friends.cancelRequest(owner.id, dana.id);
        fixture.click(owner, owner.open, 0, ClickType.LEFT);
        check(last(owner).contains("No matching friend request")
                && text(owner.open, 22).contains("No outgoing"),
                "stale outgoing request is reported and refreshed");
        befriend(fixture, owner.id, bob.id);
        fixture.menu.open(owner.player);
        fixture.friends.removeFriend(owner.id, bob.id);
        fixture.click(owner, owner.open, 0, ClickType.SHIFT_RIGHT);
        check(last(owner).contains("not friends")
                        && fixture.friends.getRelation(owner.id, charlie.id) == FriendRelation.FRIENDS
                        && FriendMenuFixture.holder(owner.open).getTarget(0).orElseThrow().equals(charlie.id),
                "stale friendship is not falsely reported as removed");

        fixture.friends.sendRequest(bob.id, owner.id);
        fixture.menu.open(owner.player, FriendMenuView.INCOMING, 0);
        fixture.repository.failNext = true;
        fixture.click(owner, owner.open, 0, ClickType.LEFT);
        check(fixture.friends.getRelation(owner.id, bob.id) == FriendRelation.INCOMING_REQUEST
                && owner.open == owner.bottom && last(owner).contains("could not be updated"),
                "persistence failure retains state, closes GUI and gives controlled error");
        check(FriendResult.values().length == 12, "all friend result types remain available to shared feedback");
    }

    private static void befriend(FriendMenuFixture fixture, UUID owner, UUID target) {
        check(fixture.friends.sendRequest(owner, target) == FriendResult.SUCCESS, "fixture request succeeds");
        check(fixture.friends.acceptRequest(target, owner) == FriendResult.SUCCESS, "fixture accept succeeds");
    }

    private static void resultFeedback() throws Exception {
        FriendMenuFixture fixture = new FriendMenuFixture();
        var owner = fixture.player("Owner", true);
        var target = fixture.player("Target", false);
        FriendMessages feedback = new FriendMessages(fixture.messages, id -> null);
        Map<FriendResult, String> expected = Map.of(
                FriendResult.SELF, "cannot add yourself",
                FriendResult.ALREADY_FRIENDS, "already friends",
                FriendResult.REQUEST_ALREADY_SENT, "already pending",
                FriendResult.REQUEST_NOT_FOUND, "No matching friend request",
                FriendResult.NOT_FRIENDS, "not friends",
                FriendResult.BLOCKED, "could not be processed",
                FriendResult.REQUESTS_DISABLED, "not accepting",
                FriendResult.FRIEND_LIMIT_REACHED, "friend limit",
                FriendResult.INCOMING_LIMIT_REACHED, "too many incoming",
                FriendResult.OUTGOING_LIMIT_REACHED, "too many outgoing"
        );
        for (Map.Entry<FriendResult, String> entry : expected.entrySet()) {
            feedback.report(owner.player, "accept", target.id, target.name, entry.getKey());
            check(last(owner).contains(entry.getValue()), entry.getKey() + " has controlled GUI feedback");
        }
        feedback.report(owner.player, "accept", target.id, target.name, FriendResult.SUCCESS);
        check(last(owner).contains("now friends"), "success feedback remains shared with command");
        feedback.report(owner.player, "add", target.id, target.name, FriendResult.AUTO_ACCEPTED);
        check(last(owner).contains("now friends"), "cross-request feedback remains shared with command");
    }

    private static String text(Inventory inventory, int slot) {
        return PLAIN.serialize(FriendMenuFixture.spec(inventory, slot).spec.name());
    }

    private static String lore(Inventory inventory, int slot) {
        return String.join("\n", FriendMenuFixture.spec(inventory, slot).spec.lore().stream()
                .map(PLAIN::serialize).toList());
    }

    private static String last(FriendMenuFixture.TestPlayer player) {
        return PLAIN.serialize(player.received.getLast());
    }

    private static boolean hasSuggestCommand(Component component) {
        if (component.clickEvent() != null
                && component.clickEvent().action() == ClickEvent.Action.SUGGEST_COMMAND
                && component.clickEvent().value().equals("/friend add ")) {
            return true;
        }
        return component.children().stream().anyMatch(FriendMenuHarness::hasSuggestCommand);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
