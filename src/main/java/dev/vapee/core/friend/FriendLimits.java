package dev.vapee.core.friend;

/** Explicit runtime limits. Zero and negative values are deliberately unsupported. */
public record FriendLimits(int maxFriends, int maxIncomingRequests, int maxOutgoingRequests) {

    public static final int DEFAULT_MAX_FRIENDS = 100;
    public static final int DEFAULT_MAX_INCOMING_REQUESTS = 25;
    public static final int DEFAULT_MAX_OUTGOING_REQUESTS = 25;

    public static FriendLimits defaults() {
        return new FriendLimits(DEFAULT_MAX_FRIENDS,
                DEFAULT_MAX_INCOMING_REQUESTS, DEFAULT_MAX_OUTGOING_REQUESTS);
    }

    public FriendLimits {
        requirePositive(maxFriends, "maxFriends");
        requirePositive(maxIncomingRequests, "maxIncomingRequests");
        requirePositive(maxOutgoingRequests, "maxOutgoingRequests");
    }

    private static void requirePositive(int value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
