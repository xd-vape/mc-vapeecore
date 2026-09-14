package dev.vapee.core.player;

import dev.vapee.core.player.repository.PlayerRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PlayerService {

    private final PlayerRepository repository;
    private final Logger logger;
    private final Map<UUID, CorePlayer> loadedPlayers = new HashMap<>();

    public PlayerService(PlayerRepository repository, Logger logger) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public CorePlayer loadPlayer(UUID uniqueId, String name) {
        Objects.requireNonNull(uniqueId, "uniqueId");
        Objects.requireNonNull(name, "name");

        Instant now = Instant.now();
        CorePlayer cachedPlayer = loadedPlayers.get(uniqueId);
        if (cachedPlayer != null) {
            cachedPlayer.updateName(name);
            cachedPlayer.updateLastJoin(now);
            return cachedPlayer;
        }

        Optional<CorePlayer> storedPlayer = repository.findByUniqueId(uniqueId);
        if (storedPlayer.isPresent()) {
            CorePlayer player = storedPlayer.get();
            player.updateName(name);
            player.updateLastJoin(now);
            loadedPlayers.put(uniqueId, player);
            return player;
        }

        CorePlayer player = new CorePlayer(uniqueId, name, now, now);
        repository.save(player);
        loadedPlayers.put(uniqueId, player);
        return player;
    }

    public Optional<CorePlayer> getPlayer(UUID uniqueId) {
        return Optional.ofNullable(loadedPlayers.get(Objects.requireNonNull(uniqueId, "uniqueId")));
    }

    public boolean isLoaded(UUID uniqueId) {
        return loadedPlayers.containsKey(Objects.requireNonNull(uniqueId, "uniqueId"));
    }

    public Collection<CorePlayer> getLoadedPlayers() {
        return List.copyOf(loadedPlayers.values());
    }

    public void savePlayer(UUID uniqueId) {
        CorePlayer player = loadedPlayers.get(Objects.requireNonNull(uniqueId, "uniqueId"));
        if (player != null) {
            repository.save(player);
        }
    }

    public void unloadPlayer(UUID uniqueId) {
        UUID validatedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        CorePlayer player = loadedPlayers.get(validatedUniqueId);
        if (player == null) {
            return;
        }

        repository.save(player);
        loadedPlayers.remove(validatedUniqueId, player);
    }

    public void saveAll() {
        Collection<CorePlayer> playersToSave = List.copyOf(loadedPlayers.values());
        logger.info("Saving " + playersToSave.size() + " loaded player(s).");

        for (CorePlayer player : playersToSave) {
            try {
                repository.save(player);
            } catch (RuntimeException exception) {
                logger.log(
                        Level.SEVERE,
                        "Player " + player.getUniqueId()
                                + " could not be saved during save-all; continuing with remaining players.",
                        exception
                );
            }
        }
    }

    void clearLoadedPlayers() {
        loadedPlayers.clear();
    }
}
