package dev.vapee.core.moderation;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.Consumer;

/** Main-thread-owned API. Async consumers must return to the server thread before any access. */
public final class ModerationService {
    private final ModerationRepository repository;
    private final Clock clock;
    private final Supplier<UUID> idSupplier;
    private final Consumer<ModerationSnapshot> committedPublisher;
    private ModerationSnapshot state;

    public ModerationService(ModerationRepository repository, Clock clock, Supplier<UUID> idSupplier) {
        this(repository, clock, idSupplier, snapshot -> { });
    }

    ModerationService(ModerationRepository repository, Clock clock, Supplier<UUID> idSupplier,
                      Consumer<ModerationSnapshot> committedPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier");
        this.committedPublisher = Objects.requireNonNull(committedPublisher, "committedPublisher");
        state = new ModerationSnapshot(Objects.requireNonNull(repository.initialize(), "initial snapshot").records());
        committedPublisher.accept(state);
    }

    public ModerationResult issueWarning(UUID target, ModerationActor actor, String reason) {
        return issue(ModerationAction.WARNING, target, actor, reason, Optional.empty());
    }

    public ModerationResult recordKick(UUID target, ModerationActor actor, String reason) {
        return issue(ModerationAction.KICK, target, actor, reason, Optional.empty());
    }

    public ModerationResult issueMute(UUID target, ModerationActor actor, String reason, Optional<Instant> expiresAt) {
        return issue(ModerationAction.MUTE, target, actor, reason, expiresAt);
    }

    public ModerationResult issueBan(UUID target, ModerationActor actor, String reason, Optional<Instant> expiresAt) {
        return issue(ModerationAction.BAN, target, actor, reason, expiresAt);
    }

    public ModerationResult revokeMute(UUID target, ModerationActor actor, Optional<String> reason) {
        return revoke(ModerationAction.MUTE, target, actor, reason);
    }

    public ModerationResult revokeBan(UUID target, ModerationActor actor, Optional<String> reason) {
        return revoke(ModerationAction.BAN, target, actor, reason);
    }

    public Optional<ModerationRecord> getActiveMute(UUID target) { return active(target, ModerationAction.MUTE, clock.instant()); }
    public Optional<ModerationRecord> getActiveBan(UUID target) { return active(target, ModerationAction.BAN, clock.instant()); }
    public boolean isMuted(UUID target) { return getActiveMute(target).isPresent(); }
    public boolean isBanned(UUID target) { return getActiveBan(target).isPresent(); }

    public List<ModerationRecord> getHistory(UUID target) {
        Objects.requireNonNull(target, "target");
        return state.records().stream().filter(record -> record.targetId().equals(target))
                .sorted(ModerationSnapshot.HISTORY_ORDER).toList();
    }

    public Optional<ModerationRecord> getRecord(UUID id) {
        Objects.requireNonNull(id, "id");
        return state.records().stream().filter(record -> record.id().equals(id)).findFirst();
    }

    public List<ModerationRecord> getAllRecords() { return state.records(); }

    private ModerationResult issue(ModerationAction action, UUID target, ModerationActor actor,
                                   String reason, Optional<Instant> expiry) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(actor, "actor");
        String normalizedReason = ModerationText.reason(reason);
        Objects.requireNonNull(expiry, "expiresAt");
        Instant now = clock.instant();
        if (expiry.isPresent() && !expiry.get().isAfter(now)) {
            throw new IllegalArgumentException("Expiry must be strictly after creation");
        }
        if (action.supportsActiveState() && active(target, action, now).isPresent()) {
            return ModerationResult.failure(action == ModerationAction.MUTE
                    ? ModerationStatus.ALREADY_MUTED : ModerationStatus.ALREADY_BANNED);
        }
        UUID id = Objects.requireNonNull(idSupplier.get(), "record ID");
        if (getRecord(id).isPresent()) throw new IllegalStateException("Duplicate moderation ID: " + id);
        ModerationRecord record = new ModerationRecord(id, action, target, actor, normalizedReason, now, expiry, Optional.empty());
        ArrayList<ModerationRecord> records = new ArrayList<>(state.records());
        records.add(record);
        persist(new ModerationSnapshot(records));
        return ModerationResult.success(record);
    }

    private ModerationResult revoke(ModerationAction action, UUID target, ModerationActor actor, Optional<String> reason) {
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(actor, "actor");
        Optional<String> normalizedReason = Objects.requireNonNull(reason, "reason").map(ModerationText::reason);
        Instant now = clock.instant();
        Optional<ModerationRecord> active = active(target, action, now);
        if (active.isEmpty()) return ModerationResult.failure(action == ModerationAction.MUTE
                ? ModerationStatus.NOT_MUTED : ModerationStatus.NOT_BANNED);
        ModerationRecord old = active.get();
        ModerationRecord revoked = old.withRevocation(new ModerationRevocation(actor, now, normalizedReason));
        List<ModerationRecord> records = state.records().stream()
                .map(record -> record.id().equals(old.id()) ? revoked : record).toList();
        persist(new ModerationSnapshot(records));
        return ModerationResult.success(revoked);
    }

    private Optional<ModerationRecord> active(UUID target, ModerationAction action, Instant now) {
        Objects.requireNonNull(target, "target");
        // Loaded overlapping facts, if any, have a deterministic newest/UUID priority.
        return getHistory(target).stream().filter(record -> record.action() == action && record.isActiveAt(now)).findFirst();
    }

    private void persist(ModerationSnapshot next) {
        repository.save(next);
        committedPublisher.accept(next);
        state = next;
    }
}
