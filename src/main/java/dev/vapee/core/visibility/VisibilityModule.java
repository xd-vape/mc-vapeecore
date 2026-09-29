package dev.vapee.core.visibility;

import dev.vapee.core.friend.FriendModule;
import dev.vapee.core.friend.FriendRelationshipListener;
import dev.vapee.core.friend.FriendService;
import dev.vapee.core.lobby.LobbyModule;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.social.SocialModule;
import dev.vapee.core.social.IgnoreRelationshipListener;
import dev.vapee.core.social.SocialService;
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
    private FriendService subscribedFriends;
    private SocialService subscribedSocial;
    private FriendRelationshipListener friendRelationshipListener;
    private IgnoreRelationshipListener ignoreRelationshipListener;

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
        FriendService friendService = friends.getFriendService();
        SocialService socialService = social.getSocialService();
        VisibilityPolicy policy = new VisibilityPolicy(players.getPlayerSettingsService(),
                socialService, friendService,
                (UUID viewer, UUID target) -> false); // Activity/game integration is deferred.
        VisibilityService newService = new VisibilityService(plugin, lobby.getLobbyService(), policy);
        FriendRelationshipListener newFriendListener = (first, second) -> refreshPairSafely(
                newService, first, second, "friendship");
        IgnoreRelationshipListener newIgnoreListener = (first, second) -> refreshPairSafely(
                newService, first, second, "ignore");
        friendService.addRelationshipListener(newFriendListener);
        socialService.addRelationshipListener(newIgnoreListener);
        service = newService;
        subscribedFriends = friendService;
        subscribedSocial = socialService;
        friendRelationshipListener = newFriendListener;
        ignoreRelationshipListener = newIgnoreListener;
        plugin.getLogger().info("Visibility module enabled (lobby-only).");
    }

    @Override public void disable() {
        try {
            if (subscribedFriends != null && friendRelationshipListener != null) {
                subscribedFriends.removeRelationshipListener(friendRelationshipListener);
            }
            if (subscribedSocial != null && ignoreRelationshipListener != null) {
                subscribedSocial.removeRelationshipListener(ignoreRelationshipListener);
            }
            if (service != null) {
                try {
                    service.restoreAll();
                } catch (RuntimeException exception) {
                    plugin.getLogger().log(Level.WARNING,
                            "Could not fully restore VapeeCore visibility during shutdown.", exception);
                }
            }
        } finally {
            ignoreRelationshipListener = null;
            friendRelationshipListener = null;
            subscribedSocial = null;
            subscribedFriends = null;
            service = null;
        }
    }

    private void refreshPairSafely(VisibilityService activeService, UUID first, UUID second, String relation) {
        try {
            activeService.refreshPair(first, second);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Could not refresh lobby visibility after " + relation + " change for "
                            + first + " and " + second + ".", exception);
        }
    }

    public VisibilityService getVisibilityService() {
        return Objects.requireNonNull(service, "VisibilityModule is not enabled");
    }
}
