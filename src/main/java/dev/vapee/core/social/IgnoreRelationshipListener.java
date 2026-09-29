package dev.vapee.core.social;

import java.util.UUID;

/** Domain-neutral notification emitted after a persisted ignore relation changes. */
@FunctionalInterface
public interface IgnoreRelationshipListener {
    void onIgnoreRelationshipChanged(UUID owner, UUID target);
}
