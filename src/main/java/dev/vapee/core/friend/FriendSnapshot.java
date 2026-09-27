package dev.vapee.core.friend;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable, validated, deterministically ordered complete friends state. */
public record FriendSnapshot(List<Friendship> friendships, List<FriendRequest> requests) {

    public FriendSnapshot {
        Objects.requireNonNull(friendships, "friendships");
        Objects.requireNonNull(requests, "requests");

        ArrayList<Friendship> sortedFriendships = new ArrayList<>(friendships.size());
        Set<FriendPair> pairs = new HashSet<>();
        for (Friendship friendship : friendships) {
            Friendship validated = Objects.requireNonNull(friendship, "friendship");
            if (!pairs.add(validated.pair())) {
                throw new IllegalArgumentException("Duplicate friendship: " + validated.pair());
            }
            sortedFriendships.add(validated);
        }
        sortedFriendships.sort((first, second) -> first.pair().compareTo(second.pair()));

        ArrayList<FriendRequest> sortedRequests = new ArrayList<>(requests.size());
        Set<RequestKey> requestKeys = new HashSet<>();
        for (FriendRequest request : requests) {
            FriendRequest validated = Objects.requireNonNull(request, "request");
            RequestKey key = new RequestKey(validated.sender(), validated.recipient());
            if (!requestKeys.add(key)) {
                throw new IllegalArgumentException("Duplicate friend request: " + key);
            }
            if (requestKeys.contains(key.reverse())) {
                throw new IllegalArgumentException("Opposing friend requests are not allowed: " + key);
            }
            if (pairs.contains(FriendPair.of(validated.sender(), validated.recipient()))) {
                throw new IllegalArgumentException("Friends cannot have a pending request: " + key);
            }
            sortedRequests.add(validated);
        }
        sortedRequests.sort((first, second) -> {
            int sender = FriendPair.compareUuid(first.sender(), second.sender());
            return sender != 0
                    ? sender
                    : FriendPair.compareUuid(first.recipient(), second.recipient());
        });

        friendships = List.copyOf(sortedFriendships);
        requests = List.copyOf(sortedRequests);
    }

    public static FriendSnapshot empty() {
        return new FriendSnapshot(List.of(), List.of());
    }

    private record RequestKey(java.util.UUID sender, java.util.UUID recipient) {

        private RequestKey reverse() {
            return new RequestKey(recipient, sender);
        }
    }
}
