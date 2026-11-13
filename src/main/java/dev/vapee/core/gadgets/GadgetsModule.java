package dev.vapee.core.gadgets;

import dev.vapee.core.activity.ActivityModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.lobby.experience.navigator.NavigatorAccessPolicy;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

public final class GadgetsModule implements CoreModule {
    private final JavaPlugin plugin;
    private final LobbyModule lobby;
    private final PlayerModule players;
    private final ActivityModule activities;
    private final MessageService messages;
    private GadgetsMenu menu;
    private final Runnable reload = () -> {
        if (menu != null) menu.closeAll();
    };

    public GadgetsModule(JavaPlugin plugin, LobbyModule lobby, PlayerModule players,
                         ActivityModule activities, MessageService messages) {
        this.plugin = plugin;
        this.lobby = lobby;
        this.players = players;
        this.activities = activities;
        this.messages = messages;
    }

    @Override
    public String getName() {
        return "Gadgets";
    }

    @Override
    public void enable() {
        if (menu != null) throw new IllegalStateException("Gadgets already enabled");
        var items = lobby.getLobbyItemService();
        var access = new NavigatorAccessPolicy(players.getPlayerService(), lobby.getLobbyService(),
                lobby.getLobbyPlayerStateService(), activities.getActivityService());
        var candidate = new GadgetsMenu(plugin, messages,
                player -> lobby.getItemRegistry().isActive() && items.isEnabled("gadgets") && access.canAccess(player));
        try {
            plugin.getServer().getPluginManager().registerEvents(candidate, plugin);
            lobby.addReloadListener(reload);
            menu = candidate;
        } catch (RuntimeException failure) {
            lobby.removeReloadListener(reload);
            HandlerList.unregisterAll(candidate);
            throw failure;
        }
    }

    public boolean openMenu(Player player) {
        return menu != null && menu.open(player);
    }

    @Override
    public void disable() {
        var previous = menu;
        menu = null;
        lobby.removeReloadListener(reload);
        if (previous != null) {
            try {
                previous.closeAll();
            } finally {
                HandlerList.unregisterAll(previous);
            }
        }
    }
}
