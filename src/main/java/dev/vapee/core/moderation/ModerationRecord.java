package dev.vapee.core.moderation;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable historical fact; active state is derived without writes or scheduling. */
public record ModerationRecord(UUID id, ModerationAction action, UUID targetId,
                               ModerationActor actor, String reason, Instant createdAt,
                               Optional<Instant> expiresAt, Optional<ModerationRevocation> revocation) {
    public ModerationRecord {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(actor, "actor");
        reason = ModerationText.reason(reason);
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(expiresAt, "expiresAt");
        Objects.requireNonNull(revocation, "revocation");
        if (!action.supportsActiveState() && (expiresAt.isPresent() || revocation.isPresent())) {
            throw new IllegalArgumentException("WARNING/KICK cannot expire or be revoked");
        }
        if (expiresAt.isPresent() && !expiresAt.get().isAfter(createdAt)) {
            throw new IllegalArgumentException("Expiry must be strictly after creation");
        }
        if (revocation.isPresent() && revocation.get().revokedAt().isBefore(createdAt)) {
            throw new IllegalArgumentException("Revocation must not precede creation");
        }
    }

    public boolean isActiveAt(Instant instant) {
        Objects.requireNonNull(instant, "instant");
        return action.supportsActiveState() && !instant.isBefore(createdAt) && revocation.isEmpty()
                && (expiresAt.isEmpty() || instant.isBefore(expiresAt.get()));
    }

    public ModerationRecord withRevocation(ModerationRevocation value) {
        if (revocation.isPresent()) throw new IllegalStateException("Record is already revoked");
        return new ModerationRecord(id, action, targetId, actor, reason, createdAt,
                expiresAt, Optional.of(Objects.requireNonNull(value, "revocation")));
    }
}
