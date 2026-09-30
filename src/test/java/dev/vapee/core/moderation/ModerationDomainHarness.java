package dev.vapee.core.moderation;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class ModerationDomainHarness {
    private static int checks;
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00.123456789Z");
    private static final ModerationActor CONSOLE = ModerationActor.console();

    public static void main(String[] args) {
        actors();
        text();
        records();
        snapshotsAndResults();
        System.out.println("ModerationDomainHarness passed " + checks + " checks.");
    }

    private static void actors() {
        check(CONSOLE.type() == ModerationActorType.CONSOLE && CONSOLE.playerId().isEmpty(), "console identity");
        check(ModerationActor.player(id(4)).playerId().orElseThrow().equals(id(4)), "player UUID identity");
        reject(() -> new ModerationActor(ModerationActorType.PLAYER, Optional.empty()), "player requires UUID");
        reject(() -> new ModerationActor(ModerationActorType.CONSOLE, Optional.of(id(1))), "console excludes UUID");
        reject(() -> new ModerationActor(null, Optional.empty()), "null actor type");
        reject(() -> new ModerationActor(ModerationActorType.CONSOLE, null), "null actor Optional");
        reject(() -> ModerationActor.player(null), "null player UUID");
    }

    private static void text() {
        check(warning("  Café 😀 <red>literal</red>  ").reason().equals("Café 😀 <red>literal</red>"), "trim Unicode literal");
        check(warning("a".repeat(256)).reason().length() == 256, "256 BMP points");
        check(warning("😀".repeat(256)).reason().length() == 512, "256 supplementary code points, not chars");
        check(warning(" \u2003Unicode\u2003 ").reason().equals("Unicode"), "Unicode whitespace trimming");
        for (String reason : List.of("", " ", "\u2003", "a".repeat(257), "😀".repeat(257),
                "\nreason", "reason\r", "tab\t", "a\u0000b", "a\u007Fb", "a\u0085b", "a\u2028b",
                "a\u2029b", "\uD800", "\uDC00")) {
            reject(() -> warning(reason), "invalid reason " + reason.length());
        }
        reject(() -> warning(null), "null reason");
        check(new ModerationRevocation(CONSOLE, NOW, Optional.of("  gelöst 😀  ")).reason()
                .orElseThrow().equals("gelöst 😀"), "revocation shares trimming");
        for (String reason : List.of("", " ", "a\nb", "😀".repeat(257))) {
            reject(() -> new ModerationRevocation(CONSOLE, NOW, Optional.of(reason)), "invalid revocation reason");
        }
        check(new ModerationRevocation(CONSOLE, NOW, Optional.empty()).reason().isEmpty(), "absent revoke reason");
        reject(() -> new ModerationRevocation(null, NOW, Optional.empty()), "null revoke actor");
        reject(() -> new ModerationRevocation(CONSOLE, null, Optional.empty()), "null revoke time");
        reject(() -> new ModerationRevocation(CONSOLE, NOW, null), "null revoke Optional");
    }

    private static void records() {
        for (ModerationAction action : ModerationAction.values()) {
            ModerationRecord permanent = record(action, Optional.empty(), Optional.empty());
            check(permanent.id().equals(id(1)) && permanent.targetId().equals(id(2))
                    && !permanent.id().equals(permanent.targetId()), "independent record and target IDs");
            check(!permanent.isActiveAt(NOW.minusNanos(1)), action + " inactive before creation");
            check(permanent.isActiveAt(NOW) == action.supportsActiveState(), action + " creation boundary");
            check(permanent.isActiveAt(NOW.plusSeconds(1000)) == action.supportsActiveState(), action + " permanent");
            reject(() -> permanent.isActiveAt(null), "null query time");
            if (action.supportsActiveState()) {
                Instant end = NOW.plusNanos(1);
                ModerationRecord temporary = record(action, Optional.of(end), Optional.empty());
                check(temporary.isActiveAt(NOW), action + " temporary active one nanosecond before end");
                check(!temporary.isActiveAt(end), action + " exact expiry inactive");
                check(!temporary.isActiveAt(end.plusNanos(1)), action + " after expiry");
                reject(() -> record(action, Optional.of(NOW), Optional.empty()), "equal expiry rejected");
                reject(() -> record(action, Optional.of(NOW.minusNanos(1)), Optional.empty()), "earlier expiry rejected");
                ModerationRevocation revoke = new ModerationRevocation(CONSOLE, NOW.plusNanos(2), Optional.empty());
                ModerationRecord revoked = temporary.withRevocation(revoke);
                check(revoked.revocation().orElseThrow().equals(revoke), "revocation retained");
                check(temporary.revocation().isEmpty() && temporary.isActiveAt(NOW), "old immutable record unchanged");
                check(!revoked.isActiveAt(NOW) && !revoked.isActiveAt(NOW.plusSeconds(1)), "revoked fact never active");
                check(record(action, Optional.empty(), Optional.of(new ModerationRevocation(CONSOLE, NOW,
                        Optional.empty()))).revocation().isPresent(), "same instant revocation allowed");
                reject(() -> record(action, Optional.empty(), Optional.of(new ModerationRevocation(CONSOLE,
                        NOW.minusNanos(1), Optional.empty()))), "revoke before creation rejected");
                reject(() -> revoked.withRevocation(revoke), "double revocation rejected");
            } else {
                reject(() -> record(action, Optional.of(NOW.plusSeconds(1)), Optional.empty()), "historic expiry rejected");
                reject(() -> permanent.withRevocation(new ModerationRevocation(CONSOLE, NOW, Optional.empty())),
                        "historic revocation rejected");
            }
            reject(() -> permanent.withRevocation(null), "null replacement revocation");
        }
        reject(() -> new ModerationRecord(null, ModerationAction.WARNING, id(2), CONSOLE, "x", NOW,
                Optional.empty(), Optional.empty()), "null record ID");
        reject(() -> new ModerationRecord(id(1), null, id(2), CONSOLE, "x", NOW,
                Optional.empty(), Optional.empty()), "null action");
        reject(() -> new ModerationRecord(id(1), ModerationAction.WARNING, null, CONSOLE, "x", NOW,
                Optional.empty(), Optional.empty()), "null target");
        reject(() -> new ModerationRecord(id(1), ModerationAction.WARNING, id(2), null, "x", NOW,
                Optional.empty(), Optional.empty()), "null actor");
        reject(() -> new ModerationRecord(id(1), ModerationAction.WARNING, id(2), CONSOLE, "x", null,
                Optional.empty(), Optional.empty()), "null creation");
        reject(() -> record(ModerationAction.MUTE, null, Optional.empty()), "null expiry Optional");
        reject(() -> record(ModerationAction.MUTE, Optional.empty(), null), "null revoke Optional");
    }

    private static void snapshotsAndResults() {
        ModerationRecord first = warning("one");
        ModerationRecord tie = new ModerationRecord(id(3), ModerationAction.KICK, id(2), CONSOLE, "two",
                NOW, Optional.empty(), Optional.empty());
        ModerationRecord later = new ModerationRecord(id(4), ModerationAction.BAN, id(2), CONSOLE, "later",
                NOW.plusNanos(1), Optional.empty(), Optional.empty());
        ArrayList<ModerationRecord> input = new ArrayList<>(List.of(later, tie, first));
        ModerationSnapshot snapshot = new ModerationSnapshot(input);
        check(snapshot.records().equals(List.of(first, tie, later)), "creation ASC then UUID ASC storage order");
        input.clear();
        check(snapshot.records().size() == 3, "snapshot defensive copy and multiple records per target");
        reject(() -> snapshot.records().clear(), "immutable snapshot");
        reject(() -> new ModerationSnapshot(List.of(first, first)), "duplicate record ID");
        reject(() -> new ModerationSnapshot(Arrays.asList(first, null)), "null entry");
        reject(() -> new ModerationSnapshot(null), "null collection");
        check(ModerationSnapshot.empty().records().isEmpty(), "empty valid snapshot");
        check(ModerationResult.success(first).record().orElseThrow().equals(first), "success record");
        for (ModerationStatus status : ModerationStatus.values()) {
            if (status != ModerationStatus.SUCCESS) {
                check(ModerationResult.failure(status).record().isEmpty(), "failure carries no record");
                reject(() -> new ModerationResult(status, Optional.of(first)), "failure with record rejected");
            }
        }
        reject(() -> ModerationResult.failure(ModerationStatus.SUCCESS), "success without record");
        reject(() -> new ModerationResult(null, Optional.empty()), "null result status");
        reject(() -> new ModerationResult(ModerationStatus.SUCCESS, null), "null result Optional");
    }

    private static ModerationRecord warning(String reason) {
        return new ModerationRecord(id(1), ModerationAction.WARNING, id(2), CONSOLE, reason, NOW,
                Optional.empty(), Optional.empty());
    }
    private static ModerationRecord record(ModerationAction action, Optional<Instant> expiry,
                                           Optional<ModerationRevocation> revoke) {
        return new ModerationRecord(id(1), action, id(2), CONSOLE, "reason", NOW, expiry, revoke);
    }
    private static UUID id(long value) { return new UUID(0, value); }
    private static void reject(Runnable operation, String message) {
        boolean failed = false;
        try { operation.run(); } catch (IllegalArgumentException | NullPointerException
                | IllegalStateException | UnsupportedOperationException expected) { failed = true; }
        check(failed, message);
    }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
