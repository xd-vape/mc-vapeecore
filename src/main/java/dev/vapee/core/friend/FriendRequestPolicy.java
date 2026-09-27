package dev.vapee.core.friend;

import java.util.UUID;

/**
 * Integration boundary for ignore and privacy decisions. The sender/recipient
 * order is preserved and the policy is evaluated on send and accept. A disabled
 * request setting blocks only new sends; ignore also blocks acceptance.
 */
@FunctionalInterface
public interface FriendRequestPolicy {

    FriendRequestDecision evaluate(UUID sender, UUID recipient);

    static FriendRequestPolicy allowAll() {
        return (sender, recipient) -> FriendRequestDecision.ALLOW;
    }
}
