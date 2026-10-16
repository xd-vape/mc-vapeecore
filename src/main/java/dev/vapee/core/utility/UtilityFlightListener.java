package dev.vapee.core.utility;

import org.bukkit.entity.Player;
import java.util.UUID;

/** Synchronous handoff around Utility-owned flight mutations; observers do not own /fly. */
public interface UtilityFlightListener {
    void beforeFlightChange(Player player);
    void afterFlightChange(UUID playerId);
}
