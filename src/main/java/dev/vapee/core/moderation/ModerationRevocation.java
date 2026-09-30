package dev.vapee.core.moderation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public record ModerationRevocation(ModerationActor actor, Instant revokedAt, Optional<String> reason) {
    public ModerationRevocation {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(revokedAt, "revokedAt");
        reason = Objects.requireNonNull(reason, "reason").map(ModerationText::reason);
    }
}
