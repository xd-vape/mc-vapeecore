package dev.vapee.core.lobby.experience.navigator;

import dev.vapee.core.activity.ActivityService;
import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.player.LobbyPlayerMode;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.player.PlayerService;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Predicate;

/** Lobby application policy; the generic warp domain has no player-state dependencies. */
public final class NavigatorAccessPolicy {

    private final Predicate<UUID> loaded;
    private final Predicate<World> lobbyWorld;
    private final Function<UUID, LobbyPlayerMode> mode;
    private final Predicate<UUID> participating;

    public NavigatorAccessPolicy(PlayerService players, LobbyService lobby,
                                 LobbyPlayerStateService states, ActivityService activities) {
        this(Objects.requireNonNull(players, "players")::isLoaded,
                Objects.requireNonNull(lobby, "lobby")::isLobbyWorld,
                Objects.requireNonNull(states, "states")::getMode,
                Objects.requireNonNull(activities, "activities")::isParticipating);
    }

    NavigatorAccessPolicy(Predicate<UUID> loaded, Predicate<World> lobbyWorld,
                          Function<UUID, LobbyPlayerMode> mode, Predicate<UUID> participating) {
        this.loaded = Objects.requireNonNull(loaded, "loaded");
        this.lobbyWorld = Objects.requireNonNull(lobbyWorld, "lobbyWorld");
        this.mode = Objects.requireNonNull(mode, "mode");
        this.participating = Objects.requireNonNull(participating, "participating");
    }

    public boolean canAccess(Player player) {
        return player != null && player.isOnline()
                && loaded.test(player.getUniqueId())
                && isLobbyWorld(player.getWorld())
                && mode.apply(player.getUniqueId()) == LobbyPlayerMode.NORMAL
                && !participating.test(player.getUniqueId());
    }

    public boolean isLobbyWorld(World world) {
        return world != null && lobbyWorld.test(world);
    }
}
