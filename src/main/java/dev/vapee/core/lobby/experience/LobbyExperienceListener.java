package dev.vapee.core.lobby.experience;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.message.LobbyMessageService;
import dev.vapee.core.player.PlayerService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class LobbyExperienceListener implements Listener {

    private final JavaPlugin plugin;
    private final LobbyService lobbyService;
    private final PlayerService playerService;
    private final LobbyVisibilityService visibilityService;
    private final LobbyMessageService lobbyMessageService;

    private boolean active = true;

    public LobbyExperienceListener(
            JavaPlugin plugin,
            LobbyService lobbyService,
            PlayerService playerService,
            LobbyVisibilityService visibilityService,
            LobbyMessageService lobbyMessageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.visibilityService = Objects.requireNonNull(visibilityService, "visibilityService");
        this.lobbyMessageService = Objects.requireNonNull(lobbyMessageService, "lobbyMessageService");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.joinMessage(lobbyMessageService.renderJoinMessage(event.getPlayer()));
        scheduleSynchronization(event.getPlayer(), 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.quitMessage(lobbyMessageService.renderQuitMessage(event.getPlayer()));
        visibilityService.restorePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean wasInLobby = lobbyService.isLobbyWorld(event.getFrom());
        boolean isInLobby = lobbyService.isLobbyWorld(player.getWorld());
        if (wasInLobby && !isInLobby) {
            visibilityService.restorePlayer(player);
            return;
        }
        if (!wasInLobby && isInLobby && playerService.isLoaded(player.getUniqueId())) {
            synchronize(player);
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        scheduleSynchronization(event.getPlayer(), 1L);
    }

    public void deactivate() {
        active = false;
    }

    private void scheduleSynchronization(Player player, long delayTicks) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!active
                    || !plugin.isEnabled()
                    || !player.isOnline()
                    || !playerService.isLoaded(player.getUniqueId())
                    || !lobbyService.isLobbyWorld(player.getWorld())) {
                return;
            }
            synchronize(player);
        }, delayTicks);
    }

    private void synchronize(Player player) {
        visibilityService.synchronizePlayer(player);
    }
}
