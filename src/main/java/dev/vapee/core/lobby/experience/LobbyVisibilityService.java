package dev.vapee.core.lobby.experience;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class LobbyVisibilityService {

    private final JavaPlugin plugin;
    private final LobbyService lobbyService;
    private final PlayerSettingsService playerSettingsService;

    public LobbyVisibilityService(
            JavaPlugin plugin,
            LobbyService lobbyService,
            PlayerSettingsService playerSettingsService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.playerSettingsService = Objects.requireNonNull(playerSettingsService, "playerSettingsService");
    }

    public void synchronizePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        if (!validatedPlayer.isOnline() || !lobbyService.isLobbyWorld(validatedPlayer.getWorld())) {
            return;
        }

        applyViewerPreference(validatedPlayer);
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            if (viewer.equals(validatedPlayer)
                    || !lobbyService.isLobbyWorld(viewer.getWorld())) {
                continue;
            }
            applyVisibility(viewer, validatedPlayer, lobbyPlayersVisible(viewer));
        }
    }

    public void applyViewerPreference(Player viewer) {
        Player validatedViewer = Objects.requireNonNull(viewer, "viewer");
        if (!validatedViewer.isOnline() || !lobbyService.isLobbyWorld(validatedViewer.getWorld())) {
            return;
        }

        boolean visible = lobbyPlayersVisible(validatedViewer);
        for (Player target : plugin.getServer().getOnlinePlayers()) {
            if (target.equals(validatedViewer)
                    || !lobbyService.isLobbyWorld(target.getWorld())) {
                continue;
            }
            applyVisibility(validatedViewer, target, visible);
        }
    }

    public void restorePlayer(Player player) {
        Player validatedPlayer = Objects.requireNonNull(player, "player");
        for (Player other : plugin.getServer().getOnlinePlayers()) {
            if (other.equals(validatedPlayer)) {
                continue;
            }
            validatedPlayer.showPlayer(plugin, other);
            other.showPlayer(plugin, validatedPlayer);
        }
    }

    public void restoreAll() {
        for (Player viewer : plugin.getServer().getOnlinePlayers()) {
            for (Player target : plugin.getServer().getOnlinePlayers()) {
                if (!viewer.equals(target)) {
                    viewer.showPlayer(plugin, target);
                }
            }
        }
    }

    private boolean lobbyPlayersVisible(Player viewer) {
        return playerSettingsService.areLobbyPlayersVisible(viewer.getUniqueId()).orElse(true);
    }

    private void applyVisibility(Player viewer, Player target, boolean visible) {
        if (viewer.equals(target)) {
            return;
        }
        if (visible) {
            viewer.showPlayer(plugin, target);
        } else {
            viewer.hidePlayer(plugin, target);
        }
    }
}
