package dev.vapee.core.player.repository;

import dev.vapee.core.player.CorePlayer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PlayerRepository {

    Optional<CorePlayer> findByUniqueId(UUID uniqueId);

    void save(CorePlayer player);

    boolean exists(UUID uniqueId);

    default Set<UUID> findUniqueIdsByName(String name) {
        return Set.of();
    }
}
