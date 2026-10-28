package dev.vapee.core.presence;

import dev.vapee.core.player.PlayerService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PresenceListener implements Listener {
    private final PresenceService presenceService;
    private final Predicate<UUID> loadedPlayer;
    private final TransitionNotifier notifier;
    private final Logger logger;
    private final Consumer<Player> joinSummary;

    public PresenceListener(
            PresenceService presenceService,
            PlayerService playerService,
            FriendPresenceNotifier notifier,
            Logger logger
    ) {
        this(presenceService, playerService, notifier, logger, player -> { });
    }

    public PresenceListener(
            PresenceService presenceService,
            PlayerService playerService,
            FriendPresenceNotifier notifier,
            Logger logger,
            Consumer<Player> joinSummary
    ) {
        this(presenceService, Objects.requireNonNull(playerService, "playerService")::isLoaded,
                Objects.requireNonNull(notifier, "notifier")::notifyStatus, logger, joinSummary);
    }

    PresenceListener(
            PresenceService presenceService,
            Predicate<UUID> loadedPlayer,
            TransitionNotifier notifier,
            Logger logger
    ) {
        this(presenceService, loadedPlayer, notifier, logger, player -> { });
    }

    PresenceListener(
            PresenceService presenceService,
            Predicate<UUID> loadedPlayer,
            TransitionNotifier notifier,
            Logger logger,
            Consumer<Player> joinSummary
    ) {
        this.presenceService = Objects.requireNonNull(presenceService, "presenceService");
        this.loadedPlayer = Objects.requireNonNull(loadedPlayer, "loadedPlayer");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.joinSummary = Objects.requireNonNull(joinSummary, "joinSummary");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!handleJoin(player.getUniqueId(), player.getName())) return;
        try {
            joinSummary.accept(player);
        } catch (RuntimeException exception) {
            logger.log(Level.WARNING, "Could not deliver friend join summary for "
                    + player.getUniqueId() + ".", exception);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        handleQuit(event.getPlayer().getUniqueId(), event.getPlayer().getName());
    }

    boolean handleJoin(UUID uniqueId, String name) {
        UUID validatedUniqueId = Objects.requireNonNull(uniqueId, "uniqueId");
        if (!loadedPlayer.test(validatedUniqueId) || !presenceService.markOnline(validatedUniqueId)) return false;
        notifyTransition(validatedUniqueId, name, PresenceStatus.ONLINE);
        return true;
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
