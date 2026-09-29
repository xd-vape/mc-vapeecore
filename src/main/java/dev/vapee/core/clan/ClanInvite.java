package dev.vapee.core.clan;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** An invitation belongs to a clan, not to a mutable owner identity. */
public record ClanInvite(UUID clanId, UUID recipient, Instant createdAt) {
    public ClanInvite {
        Objects.requireNonNull(clanId, "clanId");
        Objects.requireNonNull(recipient, "recipient");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}
