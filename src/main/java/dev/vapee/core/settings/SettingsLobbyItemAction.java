package dev.vapee.core.settings;

import dev.vapee.core.lobby.item.LobbyItemAction;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import java.util.Objects;
import java.util.function.Supplier;

public final class SettingsLobbyItemAction implements LobbyItemAction {
    private final Supplier<SettingsMenu> menu;
    public SettingsLobbyItemAction(Supplier<SettingsMenu> menu) { this.menu = Objects.requireNonNull(menu); }
    @Override public boolean handleClick(Player player, Action click) {
        if (click != Action.RIGHT_CLICK_AIR && click != Action.RIGHT_CLICK_BLOCK) return false;
        SettingsMenu current = menu.get();
        return current != null && current.open(player);
    }
}
