package dev.vapee.core.moderation;

import static dev.vapee.core.moderation.ModerationTestSupport.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class ModerationMuteProjectionHarness {
    private static int checks;
    public static void main(String[] args) throws Exception {
        Time clock = new Time();
        var projection = new ModerationMuteProjection(clock);
        check(!projection.isMuted(TARGET), "empty");
        for (ModerationAction action : List.of(ModerationAction.BAN, ModerationAction.WARNING, ModerationAction.KICK)) {
            projection.publish(new ModerationSnapshot(List.of(record(1, action, 0, Optional.empty()))));
            check(!projection.isMuted(TARGET), "only MUTE: " + action);
        }
        ModerationRecord temporary = record(2, ModerationAction.MUTE, 0, Optional.of(NOW.plusSeconds(10)));
        projection.publish(new ModerationSnapshot(List.of(temporary)));
        check(projection.getActiveMute(TARGET).orElseThrow().equals(temporary), "committed record available");
        clock.now = NOW.plusSeconds(9); check(projection.isMuted(TARGET), "just before expiry");
        clock.now = NOW.plusSeconds(10); check(!projection.isMuted(TARGET), "exact expiry without publish");
        clock.now = NOW.plusSeconds(11); check(!projection.isMuted(TARGET), "after expiry without publish");
        clock.now = NOW;
        ModerationRecord future = record(3, ModerationAction.MUTE, 10, Optional.empty());
        projection.publish(new ModerationSnapshot(List.of(future)));
        check(!projection.isMuted(TARGET), "future inactive");
        clock.now = NOW.plusSeconds(10); check(projection.isMuted(TARGET), "future activates at creation");
        clock.now = NOW;
        var permanent = record(4, ModerationAction.MUTE, -1, Optional.empty());
        var newest = record(6, ModerationAction.MUTE, 0, Optional.empty());
        var tie = record(5, ModerationAction.MUTE, 0, Optional.empty());
        var source = new ArrayList<>(List.of(permanent, newest, tie));
        projection.publish(new ModerationSnapshot(source)); source.clear();
        ModerationRecord oldView = projection.getActiveMute(TARGET).orElseThrow();
        check(oldView.equals(tie), "newest then UUID deterministic, source independent");
        var revoked = tie.withRevocation(new ModerationRevocation(ModerationActor.console(), NOW, Optional.of("revoked")));
        projection.publish(new ModerationSnapshot(List.of(revoked)));
        check(!projection.isMuted(TARGET), "revoked mute alone inactive");
        projection.publish(new ModerationSnapshot(List.of(revoked, newest)));
        check(projection.getActiveMute(TARGET).orElseThrow().equals(newest), "revoked newest falls back");
        check(oldView.revocation().isEmpty(), "old record view immutable");
        projection.publish(ModerationSnapshot.empty()); check(!projection.isMuted(TARGET), "replacement removes stale UUID");
        var failure = new AtomicReference<Throwable>();
        Thread reader = new Thread(() -> {
            try {
                for (int i = 0; i < 20000; i++) {
                    var value = projection.getActiveMute(TARGET);
                    if (value.isPresent() && !value.get().equals(tie)) throw new AssertionError("partial publication");
                }
            } catch (Throwable t) { failure.set(t); }
        }, "mute-projection-reader");
        reader.start();
        for (int i = 0; i < 10000; i++) projection.publish(i % 2 == 0
                ? new ModerationSnapshot(List.of(tie)) : ModerationSnapshot.empty());
        reader.join(10000);
        check(!reader.isAlive() && failure.get() == null, "actual concurrent read/publication, no partial state/CME");
        projection.publish(new ModerationSnapshot(List.of(permanent))); projection.clear();
        check(!projection.isMuted(TARGET), "clear");
        try { projection.getActiveMute(null); throw new AssertionError("null accepted"); }
        catch (NullPointerException expected) { checks++; }
        var state = ModerationMuteProjection.class.getDeclaredField("state"); state.setAccessible(true);
        projection.publish(new ModerationSnapshot(List.of(permanent)));
        var map = (Map<?, ?>) state.get(projection);
        try { map.clear(); throw new AssertionError("mutable map"); }
        catch (UnsupportedOperationException expected) { checks++; }
        try { ((List<?>) map.get(TARGET)).clear(); throw new AssertionError("mutable list"); }
        catch (UnsupportedOperationException expected) { checks++; }
        System.out.println("ModerationMuteProjectionHarness passed " + checks + " checks.");
    }
    private static ModerationRecord record(long id, ModerationAction action, long offset,
                                            Optional<java.time.Instant> expiry) {
        return new ModerationRecord(new UUID(0, id), action, TARGET, ModerationActor.console(), ATTACK,
                NOW.plusSeconds(offset), expiry, Optional.empty());
    }
    private static void check(boolean condition, String label) { if (!condition) throw new AssertionError(label); checks++; }
}
