package dev.vapee.core.moderation;

import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Synchronous, read-only check before PlayerJoinEvent and player-data loading. */
public final class ModerationLoginListener implements Listener {
    private final Function<UUID, Optional<ModerationRecord>> activeBan;
    private final Logger logger;

    public ModerationLoginListener(ModerationService service, Logger logger) {
        this(Objects.requireNonNull(service)::getActiveBan, logger);
    }

    ModerationLoginListener(Function<UUID, Optional<ModerationRecord>> activeBan, Logger logger) {
        this.activeBan = Objects.requireNonNull(activeBan);
        this.logger = Objects.requireNonNull(logger);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        rejection(event.getPlayer().getUniqueId()).ifPresent(component ->
                event.disallow(PlayerLoginEvent.Result.KICK_BANNED, component));
    }

    Optional<Component> rejection(UUID playerId) {
        try { return activeBan.apply(playerId).map(ModerationComponents::banScreen); }
        catch (RuntimeException exception) {
            logger.log(Level.SEVERE, "Could not query login ban [target=" + playerId + "]", exception);
            return Optional.empty(); // No fabricated ban; preserves any pre-existing event rejection.
        }
    }
}
