package dev.vapee.core.social;

import dev.vapee.core.player.CorePlayer;
import dev.vapee.core.player.PlayerService;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class SocialService {

    private final PlayerService playerService;
    private final Logger logger;
    private final ConcurrentMap<UUID, Set<UUID>> ignoreSnapshots = new ConcurrentHashMap<>();

    public SocialService(PlayerService playerService, Logger logger) {
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public IgnoreResult ignore(UUID owner, UUID target) {
        UUID validatedOwner = Objects.requireNonNull(owner, "owner");
        UUID validatedTarget = Objects.requireNonNull(target, "target");
        if (validatedOwner.equals(validatedTarget)) {
            return IgnoreResult.CANNOT_IGNORE_SELF;
        }

        Optional<CorePlayer> optionalOwner = playerService.getPlayer(validatedOwner);
        if (optionalOwner.isEmpty()) {
            return IgnoreResult.OWNER_NOT_LOADED;
        }

        CorePlayer ownerPlayer = optionalOwner.get();
        if (!ownerPlayer.getSocial().ignore(validatedTarget)) {
            return IgnoreResult.ALREADY_IGNORED;
        }

        try {
            playerService.savePlayer(validatedOwner);
        } catch (RuntimeException exception) {
            ownerPlayer.getSocial().unignore(validatedTarget);
            throw exception;
        }

        publishSnapshot(ownerPlayer);
        return IgnoreResult.SUCCESS;
    }

    public IgnoreResult unignore(UUID owner, UUID target) {
        UUID validatedOwner = Objects.requireNonNull(owner, "owner");
        UUID validatedTarget = Objects.requireNonNull(target, "target");
        if (validatedOwner.equals(validatedTarget)) {
            return IgnoreResult.CANNOT_IGNORE_SELF;
        }

        Optional<CorePlayer> optionalOwner = playerService.getPlayer(validatedOwner);
        if (optionalOwner.isEmpty()) {
            return IgnoreResult.OWNER_NOT_LOADED;
        }

        CorePlayer ownerPlayer = optionalOwner.get();
        if (!ownerPlayer.getSocial().unignore(validatedTarget)) {
            return IgnoreResult.NOT_IGNORED;
        }

        try {
            playerService.savePlayer(validatedOwner);
        } catch (RuntimeException exception) {
            ownerPlayer.getSocial().ignore(validatedTarget);
            throw exception;
        }

        publishSnapshot(ownerPlayer);
        return IgnoreResult.SUCCESS;
    }

    public boolean isIgnoring(UUID owner, UUID target) {
        Set<UUID> ignoredPlayers = ignoreSnapshots.get(Objects.requireNonNull(owner, "owner"));
        return ignoredPlayers != null && ignoredPlayers.contains(Objects.requireNonNull(target, "target"));
    }

    public Set<UUID> getIgnoredPlayers(UUID owner) {
        Set<UUID> ignoredPlayers = ignoreSnapshots.get(Objects.requireNonNull(owner, "owner"));
        return ignoredPlayers == null ? Set.of() : ignoredPlayers;
    }

    public Optional<String> findKnownPlayerName(UUID uniqueId) {
        UUID validatedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        try {
            return playerService.findKnownPlayer(validatedUniqueId).map(CorePlayer::getName);
        } catch (RuntimeException exception) {
            logger.log(
                    Level.WARNING,
                    "Could not resolve the persisted name for ignored player " + validatedUniqueId + ".",
                    exception
            );
            return Optional.empty();
        }
    }

    public void activatePlayer(UUID owner) {
        UUID validatedOwner = Objects.requireNonNull(owner, "owner");
        playerService.getPlayer(validatedOwner).ifPresentOrElse(
                this::publishSnapshot,
                () -> ignoreSnapshots.remove(validatedOwner)
        );
    }

    public void deactivatePlayer(UUID owner) {
        ignoreSnapshots.remove(Objects.requireNonNull(owner, "owner"));
    }

    public void clearSnapshots() {
        ignoreSnapshots.clear();
    }

    private void publishSnapshot(CorePlayer player) {
        ignoreSnapshots.put(player.getUniqueId(), Set.copyOf(player.getSocial().getIgnoredPlayers()));
    }
}
