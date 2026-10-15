package dev.vapee.core.lobby.item;

import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;

/** Feature-owned click behavior. Return true only for successful immediate UI feedback.
 * Permission and domain checks belong to the existing feature, never to YAML or this contract. */
@FunctionalInterface
public interface LobbyItemAction {
    default boolean supports(org.bukkit.event.block.Action click) {
        return click == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || click == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK;
    }
    boolean handleClick(Player player, Action click);
}
