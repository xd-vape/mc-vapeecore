package dev.vapee.core.listener;

import dev.vapee.core.config.ConfigService;
import dev.vapee.core.message.MessageService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Objects;

public final class PlayerJoinListener implements Listener {

    private final ConfigService configService;
    private final MessageService messageService;

    public PlayerJoinListener(ConfigService configService, MessageService messageService) {
        this.configService = Objects.requireNonNull(configService, "configService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (configService.isDebugEnabled()) {
            messageService.send(event.getPlayer(), "<yellow>VapeeCore development build active.</yellow>");
        }
    }
}
