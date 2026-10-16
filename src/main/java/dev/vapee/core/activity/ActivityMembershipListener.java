package dev.vapee.core.activity;

import org.bukkit.entity.Player;
import java.util.UUID;

/** Narrow ownership boundary, independent of any game's inventory or UI. */
public interface ActivityMembershipListener {
    void beforeJoin(Player player);
    void membershipChanged(UUID playerId);
}
