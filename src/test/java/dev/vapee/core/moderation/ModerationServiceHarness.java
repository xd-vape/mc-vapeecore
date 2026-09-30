package dev.vapee.core.moderation;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ModerationServiceHarness {
    private static int checks;
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00.123456789Z");
    private static final UUID TARGET = id(90);
    private static final ModerationActor CONSOLE = ModerationActor.console();

    public static void main(String[] args) {
        operations();
        temporaryBan();
        queriesAndValidation();
        for (int mutation = 0; mutation < 6; mutation++) failure(mutation);
        System.out.println("ModerationServiceHarness passed " + checks + " checks.");
    }

    private static void temporaryBan() {
        Fixture f = new Fixture();
        ModerationRecord ban = success(f.service.issueBan(TARGET, CONSOLE, "Temporary ban", Optional.of(NOW.plusNanos(1))));
        check(f.service.isBanned(TARGET), "temporary ban active before expiry");
        f.clock.now = NOW.plusNanos(1);
        noWrite(f, () -> check(!f.service.isBanned(TARGET), "temporary ban expires at exact nanosecond"));
        noWrite(f, () -> status(f.service.revokeBan(TARGET, CONSOLE, Optional.empty()), ModerationStatus.NOT_BANNED));
        ModerationRecord next = success(f.service.issueBan(TARGET, CONSOLE, "Reissued", Optional.empty()));
        check(f.service.getHistory(TARGET).equals(List.of(next, ban)), "expired ban reissue retains history");
        check(f.service.getActiveBan(TARGET).orElseThrow().equals(next), "reissued ban active");
        Fixture restarted = new Fixture();
        restarted.repo.persisted = new ModerationSnapshot(List.of(ban.withRevocation(
                new ModerationRevocation(CONSOLE, NOW, Optional.empty()))));
        ModerationService loaded = new ModerationService(restarted.repo, restarted.clock, () -> id(10));
        noWrite(restarted, () -> check(!loaded.isBanned(TARGET), "loaded revoked ban inactive"));
        success(loaded.issueBan(TARGET, CONSOLE, "After loaded revoke", Optional.empty()));
        check(loaded.getHistory(TARGET).size() == 2, "loaded revoked reissue keeps history");
    }

    private static void operations() {
        Fixture f = new Fixture();
        ModerationService s = f.service;
        check(f.repo.loads == 1 && f.repo.saves == 0 && s.getHistory(TARGET).isEmpty(), "load only once, no initial write");
        ModerationRecord warning = success(s.issueWarning(TARGET, CONSOLE, "  Warning  "));
        ModerationRecord kick = success(s.recordKick(TARGET, ModerationActor.player(id(88)), "Kick"));
        check(warning.id().equals(id(1)) && kick.id().equals(id(2)), "deterministic injected IDs");
        check(warning.createdAt().equals(NOW) && warning.reason().equals("Warning"), "exact injected clock and trim");
        check(!s.isMuted(TARGET) && !s.isBanned(TARGET), "historic actions not active");
        ModerationRecord mute = success(s.issueMute(TARGET, CONSOLE, "Mute", Optional.of(NOW.plusSeconds(2))));
        ModerationRecord ban = success(s.issueBan(TARGET, CONSOLE, "Ban", Optional.empty()));
        check(s.isMuted(TARGET) && s.isBanned(TARGET), "mute and ban coexist");
        check(s.getActiveMute(TARGET).orElseThrow().equals(mute) && s.getActiveBan(TARGET).orElseThrow().equals(ban),
                "active queries return saved immutable records");
        noWrite(f, () -> status(s.issueMute(TARGET, CONSOLE, "Duplicate", Optional.empty()), ModerationStatus.ALREADY_MUTED));
        noWrite(f, () -> status(s.issueBan(TARGET, CONSOLE, "Duplicate", Optional.empty()), ModerationStatus.ALREADY_BANNED));
        check(f.nextId == 4, "duplicates do not consume IDs");
        List<ModerationRecord> savedHistory = s.getHistory(TARGET);
        noWrite(f, () -> {
            check(s.getRecord(mute.id()).orElseThrow().equals(mute), "record lookup");
            check(s.getRecord(id(999)).isEmpty(), "unknown record");
            check(s.getHistory(id(999)).isEmpty(), "unknown target history");
            check(savedHistory.equals(List.of(warning, kick, mute, ban)), "same time UUID ASC history");
        });
        f.clock.now = NOW.plusSeconds(2);
        noWrite(f, () -> check(!s.isMuted(TARGET) && s.isBanned(TARGET), "expiry boundary without write"));
        noWrite(f, () -> status(s.revokeMute(TARGET, CONSOLE, Optional.empty()), ModerationStatus.NOT_MUTED));
        ModerationRecord nextMute = success(s.issueMute(TARGET, CONSOLE, "New mute", Optional.empty()));
        check(s.getHistory(TARGET).getFirst().equals(nextMute) && s.getAllRecords().size() == 5, "newest history, expired retained");
        ModerationRecord revoked = success(s.revokeMute(TARGET, ModerationActor.player(id(87)), Optional.of("  Appeal  ")));
        check(revoked.id().equals(nextMute.id()) && revoked.createdAt().equals(nextMute.createdAt())
                && revoked.reason().equals(nextMute.reason()), "revoke replaces same historical ID preserving original fact");
        check(revoked.revocation().orElseThrow().revokedAt().equals(f.clock.now)
                && revoked.revocation().orElseThrow().reason().orElseThrow().equals("Appeal"), "revoke metadata clock and reason");
        check(!s.isMuted(TARGET) && nextMute.revocation().isEmpty() && savedHistory.size() == 4, "immutable old views");
        noWrite(f, () -> status(s.revokeMute(TARGET, CONSOLE, Optional.empty()), ModerationStatus.NOT_MUTED));
        success(s.issueMute(TARGET, CONSOLE, "Again", Optional.of(f.clock.now.plusNanos(1))));
        ModerationRecord revokedBan = success(s.revokeBan(TARGET, CONSOLE, Optional.empty()));
        check(revokedBan.revocation().orElseThrow().reason().isEmpty() && !s.isBanned(TARGET), "ban revoke no reason");
        noWrite(f, () -> status(s.revokeBan(TARGET, CONSOLE, Optional.empty()), ModerationStatus.NOT_BANNED));
        success(s.issueBan(TARGET, CONSOLE, "Again ban", Optional.empty()));
        check(s.getHistory(TARGET).size() == 7, "warnings, kicks, expired and revoked facts retained");
        check(s.getAllRecords().equals(f.repo.persisted.records()), "single state equals successful persistence");
        int saves = f.repo.saves;
        ModerationService reloaded = new ModerationService(f.repo, f.clock, () -> id(100));
        check(reloaded.getAllRecords().equals(s.getAllRecords()) && f.repo.saves == saves, "restart loads without save");
        reject(() -> s.getHistory(TARGET).clear(), "history immutable");
        reject(() -> s.getAllRecords().clear(), "all records immutable");
    }

    private static void queriesAndValidation() {
        Fixture f = new Fixture();
        ModerationService s = f.service;
        for (ModerationAction action : List.of(ModerationAction.MUTE, ModerationAction.BAN)) {
            noWrite(f, () -> status(action == ModerationAction.MUTE ? s.revokeMute(TARGET, CONSOLE, Optional.empty())
                    : s.revokeBan(TARGET, CONSOLE, Optional.empty()),
                    action == ModerationAction.MUTE ? ModerationStatus.NOT_MUTED : ModerationStatus.NOT_BANNED));
        }
        noWrite(f, () -> {
            reject(() -> s.issueMute(TARGET, CONSOLE, "x", Optional.of(NOW)), "equal expiry");
            reject(() -> s.issueBan(TARGET, CONSOLE, "x", Optional.of(NOW.minusNanos(1))), "past expiry");
            reject(() -> s.issueMute(TARGET, CONSOLE, "x", null), "null expiry");
            reject(() -> s.issueWarning(null, CONSOLE, "x"), "null target");
            reject(() -> s.recordKick(TARGET, null, "x"), "null actor");
            reject(() -> s.issueBan(TARGET, CONSOLE, " ", Optional.empty()), "blank reason");
            reject(() -> s.revokeBan(TARGET, CONSOLE, Optional.of("")), "empty revoke reason even on no-op");
            reject(() -> s.revokeMute(TARGET, null, Optional.empty()), "null revoke actor");
            reject(() -> s.revokeMute(TARGET, CONSOLE, null), "null revoke Optional");
            reject(() -> s.getHistory(null), "null history target");
            reject(() -> s.isMuted(null), "null active target");
            reject(() -> s.getRecord(null), "null ID lookup");
        });
        check(f.nextId == 0, "invalid input consumes no ID");
        ModerationRecord future = new ModerationRecord(id(1), ModerationAction.BAN, TARGET, CONSOLE, "Future",
                NOW.plusSeconds(1), Optional.empty(), Optional.empty());
        f.repo.persisted = new ModerationSnapshot(List.of(future));
        ModerationService loaded = new ModerationService(f.repo, f.clock, () -> id(1));
        check(!loaded.isBanned(TARGET), "future creation inactive");
        noWrite(f, () -> reject(() -> loaded.issueWarning(TARGET, CONSOLE, "Collision"), "ID collision"));
        f.clock.now = NOW.plusSeconds(1);
        check(loaded.isBanned(TARGET), "future becomes active at creation without scheduling");
        reject(() -> new ModerationService(null, f.clock, () -> id(1)), "null repository");
        reject(() -> new ModerationService(f.repo, null, () -> id(1)), "null clock");
        reject(() -> new ModerationService(f.repo, f.clock, null), "null ID supplier");
        noWrite(f, () -> reject(() -> new ModerationService(f.repo, f.clock, () -> null)
                .issueWarning(TARGET, CONSOLE, "x"), "null supplied ID"));
    }

    private static void failure(int mutation) {
        Fixture f = new Fixture();
        if (mutation == 4) success(f.service.issueMute(TARGET, CONSOLE, "Seed", Optional.empty()));
        if (mutation == 5) success(f.service.issueBan(TARGET, CONSOLE, "Seed", Optional.empty()));
        List<ModerationRecord> before = f.service.getAllRecords();
        ModerationSnapshot disk = f.repo.persisted;
        boolean mute = f.service.isMuted(TARGET), ban = f.service.isBanned(TARGET);
        f.repo.onSave = candidate -> check(f.service.getAllRecords() == before
                && f.repo.persisted == disk && !candidate.equals(disk), "save-before-swap " + mutation);
        f.repo.fail = true;
        try {
            switch (mutation) {
                case 0 -> f.service.issueWarning(TARGET, CONSOLE, "Warning");
                case 1 -> f.service.recordKick(TARGET, CONSOLE, "Kick");
                case 2 -> f.service.issueMute(TARGET, CONSOLE, "Mute", Optional.empty());
                case 3 -> f.service.issueBan(TARGET, CONSOLE, "Ban", Optional.empty());
                case 4 -> f.service.revokeMute(TARGET, CONSOLE, Optional.of("Revoke"));
                case 5 -> f.service.revokeBan(TARGET, CONSOLE, Optional.empty());
                default -> throw new AssertionError();
            }
            throw new AssertionError("failure swallowed " + mutation);
        } catch (ModerationRepositoryException expected) {
            check(expected.getMessage().equals("injected save failure"), "repository failure propagates " + mutation);
        }
        check(f.service.getAllRecords() == before && f.service.getHistory(TARGET).size() == before.size()
                && f.repo.persisted == disk, "exact old snapshot/history after failure " + mutation);
        check(f.service.isMuted(TARGET) == mute && f.service.isBanned(TARGET) == ban, "active state unchanged " + mutation);
        f.repo.fail = false;
        f.repo.onSave = candidate -> { };
        if (mutation == 4) success(f.service.revokeMute(TARGET, CONSOLE, Optional.empty()));
        else if (mutation == 5) success(f.service.revokeBan(TARGET, CONSOLE, Optional.empty()));
        else success(f.service.issueWarning(TARGET, CONSOLE, "Retry"));
        check(f.repo.persisted.records().equals(f.service.getAllRecords()), "recovery after failure " + mutation);
    }

    private static final class Fixture {
        final MemoryRepository repo = new MemoryRepository();
        final MutableClock clock = new MutableClock();
        long nextId;
        final ModerationService service = new ModerationService(repo, clock, () -> id(++nextId));
    }
    private static final class MemoryRepository implements ModerationRepository {
        ModerationSnapshot persisted = ModerationSnapshot.empty();
        int loads, saves;
        boolean fail;
        java.util.function.Consumer<ModerationSnapshot> onSave = candidate -> { };
        public ModerationSnapshot initialize() { loads++; return persisted; }
        public void save(ModerationSnapshot value) {
            saves++;
            onSave.accept(value);
            if (fail) throw new ModerationRepositoryException("injected save failure");
            persisted = value;
        }
    }
    private static final class MutableClock extends Clock {
        Instant now = NOW;
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        public Instant instant() { return now; }
    }
    private static void noWrite(Fixture f, Runnable operation) {
        int saves = f.repo.saves;
        operation.run();
        check(f.repo.saves == saves, "operation/query performs no write");
    }
    private static ModerationRecord success(ModerationResult result) {
        check(result.status() == ModerationStatus.SUCCESS && result.record().isPresent(), "successful saved record");
        return result.record().orElseThrow();
    }
    private static void status(ModerationResult result, ModerationStatus status) {
        check(result.status() == status && result.record().isEmpty(), "status " + status);
    }
    private static void reject(Runnable operation, String message) {
        boolean failed = false;
        try { operation.run(); } catch (IllegalArgumentException | NullPointerException
                | IllegalStateException | UnsupportedOperationException expected) { failed = true; }
        check(failed, message);
    }
    private static UUID id(long value) { return new UUID(0, value); }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
