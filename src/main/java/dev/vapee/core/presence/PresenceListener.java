package dev.vapee.core.presence;

import dev.vapee.core.player.PlayerService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PresenceListener implements Listener {
    private final PresenceService presenceService;
    private final Predicate<UUID> loadedPlayer;
    private final TransitionNotifier notifier;
    private final Logger logger;

    public PresenceListener(
            PresenceService presenceService,
            PlayerService playerService,
            FriendPresenceNotifier notifier,
            Logger logger
    ) {
        this(presenceService, Objects.requireNonNull(playerService, "playerService")::isLoaded,
                Objects.requireNonNull(notifier, "notifier")::notifyStatus, logger);
    }

    PresenceListener(
            PresenceService presenceService,
            Predicate<UUID> loadedPlayer,
            TransitionNotifier notifier,
            Logger logger
    ) {
        this.presenceService = Objects.requireNonNull(presenceService, "presenceService");
        this.loadedPlayer = Objects.requireNonNull(loadedPlayer, "loadedPlayer");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        handleJoin(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handleQuit(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    void handleJoin(UUID uniqueId, String name) {
        UUID validatedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        if (!loadedPlayer.test(validatedUniqueId) || !presenceService.markOnline(validatedUniqueId)) return;
        notifyTransition(validatedUniqueId, name, PresenceStatus.ONLINE);
    }

    void handleQuit(UUID uniqueId, String name) {
        UUID validatedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        if (!presenceService.markOffline(validatedUniqueId)) return;
        notifyTransition(validatedUniqueId, name, PresenceStatus.OFFLINE);
    }

    private void notifyTransition(UUID uniqueId, String name, PresenceStatus status) {
        try {
            notifier.notify(uniqueId, Objects.requireNonNull(name, "name"), status);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING,
                    "Presence transition " + status + " for " + uniqueId
                            + " was recorded but notification dispatch failed.",
                    exception);
        }
    }

    @FunctionalInterface
    interface TransitionNotifier {
        void notify(UUID uniqueId, String name, PresenceStatus status);
    }
}
