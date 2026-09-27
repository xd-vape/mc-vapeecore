package dev.vapee.core.friend;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class FriendDomainHarness {

    private static int checks;

    private FriendDomainHarness() {
    }

    public static void main(String[] args) {
        UUID first = id(1);
        UUID second = id(2);
        UUID third = id(3);
        Instant createdAt = Instant.ofEpochMilli(12_345L);

        FriendPair pair = FriendPair.of(second, first);
        check(pair.playerA().equals(first) && pair.playerB().equals(second),
                "friend pair uses deterministic UUID text order");
        check(pair.equals(FriendPair.of(first, second)), "inverse pairs are equal");
        check(pair.contains(first) && pair.contains(second) && pair.other(first).equals(second),
                "pair membership and counterpart are symmetric");
        rejectIllegal(() -> FriendPair.of(first, first), "self pair is rejected");
        rejectIllegal(() -> pair.other(third), "unknown pair member is rejected");

        Friendship friendship = new Friendship(second, first, createdAt);
        check(friendship.playerA().equals(first) && friendship.playerB().equals(second),
                "friendship canonicalizes its players");
        check(friendship.equals(new Friendship(first, second, createdAt)),
                "inverse construction represents the same friendship");
        check(friendship.createdAt().equals(createdAt), "friendship retains createdAt");
        rejectIllegal(() -> new Friendship(first, first, createdAt), "self friendship is rejected");
        rejectNull(() -> new Friendship(first, second, null), "friendship requires createdAt");

        FriendRequest outgoing = new FriendRequest(first, second, createdAt);
        FriendRequest incoming = new FriendRequest(second, first, createdAt);
        check(!outgoing.equals(incoming), "request direction is significant");
        check(outgoing.createdAt().equals(createdAt), "request retains createdAt");
        rejectIllegal(() -> new FriendRequest(first, first, createdAt), "self request is rejected");
        rejectNull(() -> new FriendRequest(first, second, null), "request requires createdAt");

        FriendLimits limits = new FriendLimits(10, 20, 30);
        check(limits.maxFriends() == 10 && limits.maxIncomingRequests() == 20
                        && limits.maxOutgoingRequests() == 30,
                "limits retain explicit values");
        rejectIllegal(() -> new FriendLimits(0, 1, 1), "zero friend limit is rejected");
        rejectIllegal(() -> new FriendLimits(1, -1, 1), "negative incoming limit is rejected");
        rejectIllegal(() -> new FriendLimits(1, 1, 0), "zero outgoing limit is rejected");

        Friendship later = new Friendship(second, third, Instant.ofEpochMilli(20_000L));
        FriendRequest request = new FriendRequest(third, first, Instant.ofEpochMilli(30_000L));
        FriendSnapshot snapshot = new FriendSnapshot(List.of(later, friendship), List.of(request));
        check(snapshot.friendships().equals(List.of(friendship, later)),
                "snapshot friendships are deterministic");
        check(snapshot.requests().equals(List.of(request)), "snapshot retains requests");
        rejectUnsupported(() -> snapshot.friendships().add(later),
                "snapshot friendships are immutable");
        rejectUnsupported(() -> snapshot.requests().clear(), "snapshot requests are immutable");

        ArrayList<Friendship> mutable = new ArrayList<>();
        mutable.add(friendship);
        FriendSnapshot copied = new FriendSnapshot(mutable, List.of());
        mutable.clear();
        check(copied.friendships().size() == 1, "snapshot defensively copies input");
        rejectIllegal(() -> new FriendSnapshot(List.of(friendship, friendship), List.of()),
                "duplicate friendship is rejected");
        rejectIllegal(() -> new FriendSnapshot(
                List.of(friendship, new Friendship(second, first, Instant.ofEpochMilli(99L))),
                List.of()
        ), "inverse duplicate friendship is rejected");
        rejectIllegal(() -> new FriendSnapshot(List.of(), List.of(outgoing, outgoing)),
                "duplicate request is rejected");
        rejectIllegal(() -> new FriendSnapshot(List.of(), List.of(outgoing, incoming)),
                "opposing requests are rejected");
        rejectIllegal(() -> new FriendSnapshot(List.of(friendship), List.of(outgoing)),
                "request between friends is rejected");
        check(FriendRequestPolicy.allowAll().evaluate(first, second) == FriendRequestDecision.ALLOW,
                "allow-all policy is explicit");

        System.out.println("FriendDomainHarness passed " + checks + " checks.");
    }

    private static UUID id(long value) {
        return new UUID(0L, value);
    }

    private static void rejectIllegal(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        check(rejected, message);
    }

    private static void rejectNull(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (NullPointerException expected) {
            rejected = true;
        }
        check(rejected, message);
    }

    private static void rejectUnsupported(Runnable action, String message) {
        boolean rejected = false;
        try {
            action.run();
        } catch (UnsupportedOperationException expected) {
            rejected = true;
        }
        check(rejected, message);
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
