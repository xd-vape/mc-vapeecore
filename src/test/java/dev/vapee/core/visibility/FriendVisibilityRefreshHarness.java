package dev.vapee.core.visibility;

import dev.vapee.core.friend.FriendRelation;
import dev.vapee.core.friend.FriendRepositoryException;
import dev.vapee.core.friend.FriendResult;

public final class FriendVisibilityRefreshHarness {
    private static int checks;

    public static void main(String[] args) {
        VisibilityRelationshipFixture fixture = new VisibilityRelationshipFixture();
        var alice = fixture.player("Alice");
        var bob = fixture.player("Bob");
        check(fixture.settings.setLobbyPlayersVisible(alice.id, false)
                        && fixture.settings.setLobbyFriendsVisible(alice.id, true)
                        && fixture.settings.setLobbyPlayersVisible(bob.id, false),
                "asymmetric filtered preferences are configured");
        fixture.friends.addRelationshipListener(fixture.visibility::refreshPair);

        fixture.visibility.refreshPair(alice.id, bob.id);
        check(alice.hidden(bob.id) && bob.hidden(alice.id),
                "non-friends are initially hidden in both filtered views");
        alice.clear();
        bob.clear();
        check(fixture.friends.sendRequest(alice.id, bob.id) == FriendResult.SUCCESS,
                "pending request is created");
        check(alice.actions.isEmpty() && bob.actions.isEmpty(),
                "request-only mutation does not emit friendship refresh");

        check(fixture.friends.acceptRequest(bob.id, alice.id) == FriendResult.SUCCESS,
                "friendship acceptance succeeds");
        check(alice.shown(bob.id) && bob.hidden(alice.id),
                "accept refreshes both asymmetric viewer directions immediately");
        alice.clear();
        bob.clear();
        check(fixture.friends.removeFriend(alice.id, bob.id) == FriendResult.SUCCESS,
                "friendship removal succeeds");
        check(alice.hidden(bob.id) && bob.hidden(alice.id),
                "remove immediately re-applies both filtered policies");

        check(fixture.friends.sendRequest(alice.id, bob.id) == FriendResult.SUCCESS,
                "failure fixture recreates pending request");
        alice.clear();
        bob.clear();
        fixture.friendRepository.failNext = true;
        boolean failed = false;
        try {
            fixture.friends.acceptRequest(bob.id, alice.id);
        } catch (FriendRepositoryException expected) {
            failed = true;
        }
        check(failed && fixture.friends.getRelation(alice.id, bob.id) == FriendRelation.OUTGOING_REQUEST,
                "failed persistence preserves pending relation");
        check(alice.actions.isEmpty() && bob.actions.isEmpty(),
                "failed friendship mutation emits no visibility callback");
        System.out.println("FriendVisibilityRefreshHarness passed " + checks + " checks.");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
