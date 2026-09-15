package dev.vapee.core.privatemessage;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Objects;

public final class PrivateMessageListener implements Listener {

    private final PrivateMessageService privateMessageService;

    public PrivateMessageListener(PrivateMessageService privateMessageService) {
        this.privateMessageService = Objects.requireNonNull(privateMessageService, "privateMessageService");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        privateMessageService.removePlayer(event.getPlayer().getUniqueId());
    }
}
