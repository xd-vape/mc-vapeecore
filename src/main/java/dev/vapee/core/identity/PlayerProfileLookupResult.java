package dev.vapee.core.identity;

import java.util.Objects;
import java.util.Optional;

public record PlayerProfileLookupResult(PlayerLookupStatus status, Optional<PlayerProfile> profile) {

    public PlayerProfileLookupResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(profile, "profile");
        if ((status == PlayerLookupStatus.FOUND) != profile.isPresent()) {
            throw new IllegalArgumentException("Only FOUND may contain a profile");
        }
    }
}
