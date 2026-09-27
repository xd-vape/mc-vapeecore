package dev.vapee.core.friend;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Main-thread-owned friends domain service. It deliberately contains no Bukkit
 * player references, scheduling, or concurrent collections.
 */
public final class FriendService {

    private final FriendRepository repository;
    private final FriendLimits limits;
    private final FriendRequestPolicy requestPolicy;
    private final Clock clock;
    private Map<FriendPair, Friendship> friendships;
    private Map<RequestKey, FriendRequest> requests;

    public FriendService(
            FriendRepository repository,
            FriendLimits limits,
            FriendRequestPolicy requestPolicy,
            Clock clock
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.requestPolicy = Objects.requireNonNull(requestPolicy, "requestPolicy");
        this.clock = Objects.requireNonNull(clock, "clock");

        FriendSnapshot initial = Objects.requireNonNull(repository.initialize(), "repository snapshot");
        HashMap<FriendPair, Friendship> loadedFriendships = new HashMap<>();
        for (Friendship friendship : initial.friendships()) {
            loadedFriendships.put(friendship.pair(), friendship);
        }
        HashMap<RequestKey, FriendRequest> loadedRequests = new HashMap<>();
        for (FriendRequest request : initial.requests()) {
            loadedRequests.put(RequestKey.of(request), request);
        }
        friendships = Map.copyOf(loadedFriendships);
        requests = Map.copyOf(loadedRequests);
    }

    public FriendResult sendRequest(UUID sender, UUID recipient) {
        UUID validatedSender = Objects.requireNonNull(sender, "sender");
        UUID validatedRecipient = Objects.requireNonNull(recipient, "recipient");
        if (validatedSender.equals(validatedRecipient)) {
            return FriendResult.SELF;
        }

        FriendPair pair = FriendPair.of(validatedSender, validatedRecipient);
        if (friendships.containsKey(pair)) {
            return FriendResult.ALREADY_FRIENDS;
        }
        RequestKey outgoing = new RequestKey(validatedSender, validatedRecipient);
        if (requests.containsKey(outgoing)) {
            return FriendResult.REQUEST_ALREADY_SENT;
        }

        FriendResult policyResult = evaluatePolicy(validatedSender, validatedRecipient);
        if (policyResult != null) {
            return policyResult;
        }
        if (atFriendLimit(validatedSender) || atFriendLimit(validatedRecipient)) {
            return FriendResult.FRIEND_LIMIT_REACHED;
        }

        RequestKey incoming = outgoing.reverse();
        if (requests.containsKey(incoming)) {
            HashMap<RequestKey, FriendRequest> nextRequests = new HashMap<>(requests);
            nextRequests.remove(incoming);
            HashMap<FriendPair, Friendship> nextFriendships = new HashMap<>(friendships);
            nextFriendships.put(pair, new Friendship(validatedSender, validatedRecipient, clock.instant()));
            persist(nextFriendships, nextRequests);
            return FriendResult.AUTO_ACCEPTED;
        }

        if (countOutgoing(validatedSender) >= limits.maxOutgoingRequests()) {
            return FriendResult.OUTGOING_LIMIT_REACHED;
        }
        if (countIncoming(validatedRecipient) >= limits.maxIncomingRequests()) {
            return FriendResult.INCOMING_LIMIT_REACHED;
        }

        HashMap<RequestKey, FriendRequest> nextRequests = new HashMap<>(requests);
        nextRequests.put(
                outgoing,
                new FriendRequest(validatedSender, validatedRecipient, clock.instant())
        );
        persist(friendships, nextRequests);
        return FriendResult.SUCCESS;
    }

    public FriendResult acceptRequest(UUID recipient, UUID sender) {
        UUID validatedRecipient = Objects.requireNonNull(recipient, "recipient");
        UUID validatedSender = Objects.requireNonNull(sender, "sender");
        if (validatedRecipient.equals(validatedSender)) {
            return FriendResult.SELF;
        }

        FriendPair pair = FriendPair.of(validatedRecipient, validatedSender);
        if (friendships.containsKey(pair)) {
            return FriendResult.ALREADY_FRIENDS;
        }
        RequestKey key = new RequestKey(validatedSender, validatedRecipient);
        if (!requests.containsKey(key)) {
            return FriendResult.REQUEST_NOT_FOUND;
        }

        FriendResult policyResult = evaluatePolicy(validatedSender, validatedRecipient);
        if (policyResult != null) {
            return policyResult;
        }
        if (atFriendLimit(validatedSender) || atFriendLimit(validatedRecipient)) {
            return FriendResult.FRIEND_LIMIT_REACHED;
        }

        HashMap<RequestKey, FriendRequest> nextRequests = new HashMap<>(requests);
        nextRequests.remove(key);
        HashMap<FriendPair, Friendship> nextFriendships = new HashMap<>(friendships);
        nextFriendships.put(pair, new Friendship(validatedSender, validatedRecipient, clock.instant()));
        persist(nextFriendships, nextRequests);
        return FriendResult.SUCCESS;
    }

    public FriendResult denyRequest(UUID recipient, UUID sender) {
        UUID validatedRecipient = Objects.requireNonNull(recipient, "recipient");
        UUID validatedSender = Objects.requireNonNull(sender, "sender");
        if (validatedRecipient.equals(validatedSender)) {
            return FriendResult.SELF;
        }
        RequestKey key = new RequestKey(validatedSender, validatedRecipient);
        if (!requests.containsKey(key)) {
            return FriendResult.REQUEST_NOT_FOUND;
        }

        HashMap<RequestKey, FriendRequest> nextRequests = new HashMap<>(requests);
        nextRequests.remove(key);
        persist(friendships, nextRequests);
        return FriendResult.SUCCESS;
    }

    public FriendResult cancelRequest(UUID sender, UUID recipient) {
        UUID validatedSender = Objects.requireNonNull(sender, "sender");
        UUID validatedRecipient = Objects.requireNonNull(recipient, "recipient");
        if (validatedSender.equals(validatedRecipient)) {
            return FriendResult.SELF;
        }
        RequestKey key = new RequestKey(validatedSender, validatedRecipient);
        if (!requests.containsKey(key)) {
            return FriendResult.REQUEST_NOT_FOUND;
        }

        HashMap<RequestKey, FriendRequest> nextRequests = new HashMap<>(requests);
        nextRequests.remove(key);
        persist(friendships, nextRequests);
        return FriendResult.SUCCESS;
    }

    public FriendResult removeFriend(UUID player, UUID friend) {
        UUID validatedPlayer = Objects.requireNonNull(player, "player");
        UUID validatedFriend = Objects.requireNonNull(friend, "friend");
        if (validatedPlayer.equals(validatedFriend)) {
            return FriendResult.SELF;
        }
        FriendPair pair = FriendPair.of(validatedPlayer, validatedFriend);
        if (!friendships.containsKey(pair)) {
            return FriendResult.NOT_FRIENDS;
        }

        HashMap<FriendPair, Friendship> nextFriendships = new HashMap<>(friendships);
        nextFriendships.remove(pair);
        persist(nextFriendships, requests);
        return FriendResult.SUCCESS;
    }

    public FriendRelation getRelation(UUID viewer, UUID other) {
        UUID validatedViewer = Objects.requireNonNull(viewer, "viewer");
        UUID validatedOther = Objects.requireNonNull(other, "other");
        if (validatedViewer.equals(validatedOther)) {
            return FriendRelation.NONE;
        }
        if (friendships.containsKey(FriendPair.of(validatedViewer, validatedOther))) {
            return FriendRelation.FRIENDS;
        }
        if (requests.containsKey(new RequestKey(validatedViewer, validatedOther))) {
            return FriendRelation.OUTGOING_REQUEST;
        }
        if (requests.containsKey(new RequestKey(validatedOther, validatedViewer))) {
            return FriendRelation.INCOMING_REQUEST;
        }
        return FriendRelation.NONE;
    }

    public List<UUID> getFriends(UUID player) {
        UUID validatedPlayer = Objects.requireNonNull(player, "player");
        ArrayList<UUID> result = new ArrayList<>();
        for (FriendPair pair : friendships.keySet()) {
            if (pair.contains(validatedPlayer)) {
                result.add(pair.other(validatedPlayer));
            }
        }
        result.sort(FriendPair::compareUuid);
        return List.copyOf(result);
    }

    public List<FriendRequest> getIncomingRequests(UUID player) {
        UUID validatedPlayer = Objects.requireNonNull(player, "player");
        ArrayList<FriendRequest> result = new ArrayList<>();
        for (FriendRequest request : requests.values()) {
            if (request.recipient().equals(validatedPlayer)) {
                result.add(request);
            }
        }
        result.sort((first, second) -> FriendPair.compareUuid(first.sender(), second.sender()));
        return List.copyOf(result);
    }

    public List<FriendRequest> getOutgoingRequests(UUID player) {
        UUID validatedPlayer = Objects.requireNonNull(player, "player");
        ArrayList<FriendRequest> result = new ArrayList<>();
        for (FriendRequest request : requests.values()) {
            if (request.sender().equals(validatedPlayer)) {
                result.add(request);
            }
        }
        result.sort((first, second) -> FriendPair.compareUuid(first.recipient(), second.recipient()));
        return List.copyOf(result);
    }

    public int countFriends(UUID player) {
        UUID validatedPlayer = Objects.requireNonNull(player, "player");
        int count = 0;
        for (FriendPair pair : friendships.keySet()) {
            if (pair.contains(validatedPlayer)) {
                count++;
            }
        }
        return count;
    }

    private FriendResult evaluatePolicy(UUID sender, UUID recipient) {
        FriendRequestDecision decision = Objects.requireNonNull(
                requestPolicy.evaluate(sender, recipient),
                "requestPolicy decision"
        );
        return switch (decision) {
            case ALLOW -> null;
            case BLOCKED -> FriendResult.BLOCKED;
            case REQUESTS_DISABLED -> FriendResult.REQUESTS_DISABLED;
        };
    }

    private boolean atFriendLimit(UUID player) {
        return countFriends(player) >= limits.maxFriends();
    }

    private int countIncoming(UUID player) {
        int count = 0;
        for (FriendRequest request : requests.values()) {
            if (request.recipient().equals(player)) {
                count++;
            }
        }
        return count;
    }

    private int countOutgoing(UUID player) {
        int count = 0;
        for (FriendRequest request : requests.values()) {
            if (request.sender().equals(player)) {
                count++;
            }
        }
        return count;
    }

    private void persist(
            Map<FriendPair, Friendship> nextFriendships,
            Map<RequestKey, FriendRequest> nextRequests
    ) {
        FriendSnapshot snapshot = new FriendSnapshot(
                new ArrayList<>(nextFriendships.values()),
                new ArrayList<>(nextRequests.values())
        );
        repository.save(snapshot);
        friendships = Map.copyOf(nextFriendships);
        requests = Map.copyOf(nextRequests);
    }

    private record RequestKey(UUID sender, UUID recipient) {

        private static RequestKey of(FriendRequest request) {
            return new RequestKey(request.sender(), request.recipient());
        }

        private RequestKey reverse() {
            return new RequestKey(recipient, sender);
        }
    }
}
