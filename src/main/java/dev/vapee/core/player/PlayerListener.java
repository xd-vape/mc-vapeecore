package dev.vapee.core.player;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.message.MessageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;
import java.util.logging.Logger;

public final class PlayerListener implements Listener {

    private final PlayerService playerService;
    private final ConfigService configService;
    private final MessageService messageService;
    private final Logger logger;

    public PlayerListener(
            PlayerService playerService,
            ConfigService configService,
            MessageService messageService,
            Logger logger
    ) {
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.configService = Objects.requireNonNull(configService, "configService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        try {
            playerService.loadPlayer(event.getPlayer().getUniqueId(), event.getPlayer().getName());
        } catch (RuntimeException exception) {
            logger.severe("Core player " + event.getPlayer().getUniqueId()
                    + " could not be loaded and will be disconnected: " + exception.getMessage()
            );
            event.getPlayer().kick(messageService.deserialize(
                    "<red>Your player data could not be loaded. Please contact a server administrator.</red>"
            ));
            return;
        }

        if (configService.isDebugEnabled()) {
            messageService.send(event.getPlayer(), "<yellow>VapeeCore development build active.</yellow>");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        try {
            playerService.unloadPlayer(event.getPlayer().getUniqueId());
        } catch (RuntimeException exception) {
            logger.warning("Core player " + event.getPlayer().getUniqueId()
                    + " could not be unloaded and remains cached for a later save attempt: "
                    + exception.getMessage()
            );
        }
    }
}
