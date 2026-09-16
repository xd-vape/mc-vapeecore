package dev.vapee.core.player.social;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class PlayerSocial {

    private final Set<UUID> ignoredPlayers;

    private PlayerSocial(Collection<UUID> ignoredPlayers) {
        Objects.requireNonNull(ignoredPlayers, "ignoredPlayers");
        this.ignoredPlayers = new HashSet<>();
        for (UUID ignoredPlayer : ignoredPlayers) {
            this.ignoredPlayers.add(Objects.requireNonNull(ignoredPlayer, "ignoredPlayer"));
        }
    }

    public static PlayerSocial empty() {
        return new PlayerSocial(Set.of());
    }

    public static PlayerSocial of(Collection<UUID> ignoredPlayers) {
        return new PlayerSocial(ignoredPlayers);
    }

    public Set<UUID> getIgnoredPlayers() {
        return Set.copyOf(ignoredPlayers);
    }

    public boolean isIgnoring(UUID uniqueId) {
        return ignoredPlayers.contains(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    public boolean ignore(UUID uniqueId) {
        return ignoredPlayers.add(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    public boolean unignore(UUID uniqueId) {
        return ignoredPlayers.remove(Objects.requireNonNull(uniqueId, "uniqueId"));
    }
}
