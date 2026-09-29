package dev.vapee.core.visibility;

import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.social.SocialModule;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Level;

public final class VisibilityModule implements CoreModule {
    private final JavaPlugin plugin;
    private final PlayerModule players;
    private final SocialModule social;
    private final FriendModule friends;
    private final LobbyModule lobby;
    private VisibilityService service;

    public VisibilityModule(JavaPlugin plugin, PlayerModule players, SocialModule social,
                            FriendModule friends, LobbyModule lobby) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.players = Objects.requireNonNull(players, "players");
        this.social = Objects.requireNonNull(social, "social");
        this.friends = Objects.requireNonNull(friends, "friends");
        this.lobby = Objects.requireNonNull(lobby, "lobby");
    }

    @Override public String getName() { return "Visibility"; }

    @Override public void enable() {
        VisibilityPolicy policy = new VisibilityPolicy(players.getPlayerSettingsService(),
                social.getSocialService(), friends.getFriendService(),
                (UUID viewer, UUID target) -> false); // Activity/game integration is deferred.
        service = new VisibilityService(plugin, lobby.getLobbyService(), policy);
        plugin.getLogger().info("Visibility module enabled (lobby-only).");
    }

    @Override public void disable() {
        try {
            if (service != null) {
                try {
                    service.restoreAll();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING,
                            "Could not fully restore VapeeCore visibility during shutdown.", exception);
                }
            }
        } finally {
            service = null;
        }
    }

    public VisibilityService getVisibilityService() {
        return Objects.requireNonNull(service, "VisibilityModule is not enabled");
    }
}
