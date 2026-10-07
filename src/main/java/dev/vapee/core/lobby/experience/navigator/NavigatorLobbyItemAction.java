package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.lobby.item.LobbyItemAction;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import java.util.Objects;
import java.util.function.Supplier;

public final class NavigatorLobbyItemAction implements LobbyItemAction {
    private final Supplier<NavigatorMenu> menu;
    public NavigatorLobbyItemAction(Supplier<NavigatorMenu> menu) { this.menu = Objects.requireNonNull(menu); }
    @Override public boolean handleClick(Player player, Action click) {
        NavigatorMenu current = menu.get();
        return current != null && current.open(player);
    }
}
