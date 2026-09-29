package dev.vapee.core.friend;

import java.util.UUID;

/** Domain-neutral notification emitted after a persisted friendship relation changes. */
@FunctionalInterface
public interface FriendRelationshipListener {
    void onFriendRelationshipChanged(UUID first, UUID second);
}
