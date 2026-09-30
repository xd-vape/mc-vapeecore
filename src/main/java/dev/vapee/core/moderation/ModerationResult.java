package dev.vapee.core.moderation;

import java.util.Objects;
import java.util.Optional;

/** Success carries the saved record; every non-success carries no record. */
public record ModerationResult(ModerationStatus status, Optional<ModerationRecord> record) {
    public ModerationResult {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(record, "record");
        if ((status == ModerationStatus.SUCCESS) != record.isPresent()) {
            throw new IllegalArgumentException("Only SUCCESS must contain a record");
        }
    }

    public static ModerationResult success(ModerationRecord record) {
        return new ModerationResult(ModerationStatus.SUCCESS, Optional.of(record));
    }

    public static ModerationResult failure(ModerationStatus status) {
        return new ModerationResult(status, Optional.empty());
    }
}
