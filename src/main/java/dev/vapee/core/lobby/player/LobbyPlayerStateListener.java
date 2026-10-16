package dev.vapee.core.lobby.player;

import org.bukkit.entity.Player;
import java.util.UUID;

/** Release dependent lobby state before a NORMAL/BUILD/inventory transition. */
public interface LobbyPlayerStateListener {
    void beforeStateChange(Player player);
    void afterStateChange(UUID playerId);
}
