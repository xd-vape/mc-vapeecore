package dev.vapee.core.moderation;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.ArrayList;

/** Immutable, atomically replaced committed mute facts. Safe for asynchronous readers; no I/O. */
public final class ModerationMuteProjection {
    private final Clock clock;
    private volatile Map<UUID, List<ModerationRecord>> state = Map.of();

    public ModerationMuteProjection(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    // Only the main-thread service/module publishes validated, committed snapshots.
    void publish(ModerationSnapshot snapshot) {
        Map<UUID, List<ModerationRecord>> next = new HashMap<>();
        snapshot.records().stream().filter(record -> record.action() == ModerationAction.MUTE)
                .forEach(record -> next.computeIfAbsent(record.targetId(), target -> new ArrayList<>()).add(record));
        next.replaceAll((target, records) -> records.stream().sorted(ModerationSnapshot.HISTORY_ORDER).toList());
        state = Map.copyOf(next);
    }

    public Optional<ModerationRecord> getActiveMute(UUID target) {
        Objects.requireNonNull(target, "target");
        Map<UUID, List<ModerationRecord>> current = state;
        Instant now = clock.instant();
        return current.getOrDefault(target, List.of()).stream().filter(record -> record.isActiveAt(now)).findFirst();
    }

    public boolean isMuted(UUID target) { return getActiveMute(target).isPresent(); }

    void clear() { state = Map.of(); }
}
