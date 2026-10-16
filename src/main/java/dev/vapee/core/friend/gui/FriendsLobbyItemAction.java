package dev.vapee.core.friend.gui;

import dev.vapee.core.lobby.item.LobbyItemAction;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import java.util.Objects;
import java.util.function.Supplier;

/** The existing FriendMenu open gate owns current authorization; no parallel lobby permission policy. */
public final class FriendsLobbyItemAction implements LobbyItemAction {
    private final Supplier<FriendMenu> menu;
    public FriendsLobbyItemAction(Supplier<FriendMenu> menu) { this.menu = Objects.requireNonNull(menu); }
    @Override public boolean handleClick(Player player, Action click) {
        if (click != Action.RIGHT_CLICK_AIR && click != Action.RIGHT_CLICK_BLOCK) return false;
        FriendMenu current = menu.get();
        return current != null && current.open(player);
    }
}
