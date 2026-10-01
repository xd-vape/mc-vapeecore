package dev.vapee.core.presence;

import java.util.Set;
import java.util.UUID;

public final class PresenceServiceHarness {
    private static int checks;

    public static void main(String[] args) {
        PresenceService service = new PresenceService();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        check(service.getOnlinePlayers().isEmpty(), "new service starts empty");
        check(service.markOnline(first), "first online transition is published");
        check(!service.markOnline(first), "duplicate online transition is suppressed");
        check(service.isOnline(first) && !service.isOnline(second), "membership is exact");
        check(service.markOnline(second), "second player can transition online");
        Set<UUID> snapshot = service.getOnlinePlayers();
        check(snapshot.equals(Set.of(first, second)), "snapshot contains all online players");
        expectUnsupported(() -> snapshot.remove(first));
        check(service.isOnline(first), "snapshot cannot mutate service state");
        check(service.markOffline(first), "first offline transition is published");
        check(!service.markOffline(first), "duplicate offline transition is suppressed");
        service.clear();
        check(service.getOnlinePlayers().isEmpty(), "clear removes all runtime state");
        check(snapshot.equals(Set.of(first, second)), "earlier snapshot is a defensive copy");
        expectNull(() -> service.markOnline(null));
        expectNull(() -> service.markOffline(null));
        expectNull(() -> service.isOnline(null));
        System.out.println("PresenceServiceHarness passed " + checks + " checks.");
    }

    private static void expectUnsupported(Runnable action) {
        boolean failed = false;
        try { action.run(); } catch (UnsupportedOperationException expected) { failed = true; }
        check(failed, "snapshot is immutable");
    }

    private static void expectNull(Runnable action) {
        boolean failed = false;
        try { action.run(); } catch (NullPointerException expected) { failed = true; }
        check(failed, "null UUID is rejected");
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
