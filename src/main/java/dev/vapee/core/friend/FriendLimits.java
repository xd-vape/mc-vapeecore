package dev.vapee.core.friend;

/** Explicit runtime limits. Zero and negative values are deliberately unsupported. */
public record FriendLimits(int maxFriends, int maxIncomingRequests, int maxOutgoingRequests) {

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
