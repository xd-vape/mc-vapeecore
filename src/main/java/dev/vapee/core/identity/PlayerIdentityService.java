package dev.vapee.core.identity;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class PlayerIdentityService {

    private final PlayerService playerService;

    public PlayerIdentityService(PlayerService playerService) {
        this.playerService = Objects.requireNonNull(playerService, "playerService");
    }

    public Optional<PlayerIdentity> findById(UUID uniqueId) {
        return playerService.findKnownPlayer(Objects.requireNonNull(uniqueId, "uniqueId"))
                .map(PlayerIdentityService::snapshot);
    }

    public PlayerLookupResult findByName(String name) {
        Set<UUID> ids = playerService.findKnownIdsByName(Objects.requireNonNull(name, "name"));
        if (ids.isEmpty()) {
            return PlayerLookupResult.notFound();
        }
        if (ids.size() > 1) {
            return PlayerLookupResult.ambiguous();
        }
        return findById(ids.iterator().next()).map(PlayerLookupResult::found)
                .orElseGet(PlayerLookupResult::notFound);
    }

    public PlayerLookupResult resolve(String nameOrUuid) {
        Objects.requireNonNull(nameOrUuid, "nameOrUuid");
        try {
            return findById(UUID.fromString(nameOrUuid))
                    .map(PlayerLookupResult::found)
                    .orElseGet(PlayerLookupResult::notFound);
        } catch (IllegalArgumentException exception) {
            return findByName(nameOrUuid);
        }
    }

    private static PlayerIdentity snapshot(CorePlayer player) {
        return new PlayerIdentity(player.getUniqueId(), player.getName(),
                player.getFirstJoin(), player.getLastJoin());
    }
}
