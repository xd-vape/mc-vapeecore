package dev.vapee.core.moderation;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** The only complete moderation state; canonical creation/UUID-string order. */
public record ModerationSnapshot(List<ModerationRecord> records) {
    static final Comparator<ModerationRecord> STORAGE_ORDER = Comparator
            .comparing(ModerationRecord::createdAt).thenComparing(record -> record.id().toString());
    static final Comparator<ModerationRecord> HISTORY_ORDER = Comparator
            .comparing(ModerationRecord::createdAt).reversed().thenComparing(record -> record.id().toString());

    public ModerationSnapshot {
        Objects.requireNonNull(records, "records");
        HashSet<UUID> ids = new HashSet<>();
        for (ModerationRecord record : records) {
            Objects.requireNonNull(record, "record");
            if (!ids.add(record.id())) throw new IllegalArgumentException("Duplicate moderation record ID: " + record.id());
        }
        records = records.stream().sorted(STORAGE_ORDER).toList();
    }

    public static ModerationSnapshot empty() {
        return new ModerationSnapshot(List.of());
    }
}
