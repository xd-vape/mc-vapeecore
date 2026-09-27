package dev.vapee.core.identity;

import java.util.Objects;
import java.util.Optional;

public record PlayerLookupResult(PlayerLookupStatus status, Optional<PlayerIdentity> identity) {

    public PlayerLookupResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(identity, "identity");
        if ((status == PlayerLookupStatus.FOUND) != identity.isPresent()) {
            throw new IllegalArgumentException("Only FOUND may contain an identity");
        }
    }

    public static PlayerLookupResult found(PlayerIdentity identity) {
        return new PlayerLookupResult(PlayerLookupStatus.FOUND, Optional.of(identity));
    }

    public static PlayerLookupResult notFound() {
        return new PlayerLookupResult(PlayerLookupStatus.NOT_FOUND, Optional.empty());
    }

    public static PlayerLookupResult ambiguous() {
        return new PlayerLookupResult(PlayerLookupStatus.AMBIGUOUS, Optional.empty());
    }
}
