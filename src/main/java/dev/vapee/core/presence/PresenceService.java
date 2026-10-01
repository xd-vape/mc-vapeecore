package dev.vapee.core.presence;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Main-thread-owned, runtime-only online presence state. */
public final class PresenceService {
    private final Set<UUID> onlinePlayers = new HashSet<>();

    public boolean markOnline(UUID uniqueId) {
        return onlinePlayers.add(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    public boolean markOffline(UUID uniqueId) {
        return onlinePlayers.remove(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    public boolean isOnline(UUID uniqueId) {
        return onlinePlayers.contains(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    public Set<UUID> getOnlinePlayers() {
        return Set.copyOf(onlinePlayers);
    }

    public void clear() {
        onlinePlayers.clear();
    }
}
