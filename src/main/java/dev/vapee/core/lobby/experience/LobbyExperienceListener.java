package dev.vapee.core.lobby.experience;

import dev.vapee.core.lobby.LobbyService;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.player.PlayerService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
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
import java.util.logging.Level;

public final class LobbyExperienceListener implements Listener {

    private final JavaPlugin plugin;
    private final LobbyConfig lobbyConfig;
    private final LobbyService lobbyService;
    private final PlayerService playerService;
    private final LobbyItemService lobbyItemService;
    private final LobbyVisibilityService visibilityService;
    private final MessageService messageService;

    private boolean active = true;

    public LobbyExperienceListener(
            JavaPlugin plugin,
            LobbyConfig lobbyConfig,
            LobbyService lobbyService,
            PlayerService playerService,
            LobbyItemService lobbyItemService,
            LobbyVisibilityService visibilityService,
            MessageService messageService
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.lobbyConfig = Objects.requireNonNull(lobbyConfig, "lobbyConfig");
        this.lobbyService = Objects.requireNonNull(lobbyService, "lobbyService");
        this.playerService = Objects.requireNonNull(playerService, "playerService");
        this.lobbyItemService = Objects.requireNonNull(lobbyItemService, "lobbyItemService");
        this.visibilityService = Objects.requireNonNull(visibilityService, "visibilityService");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.joinMessage(renderMessage(
                event.getPlayer(),
                lobbyConfig.getJoinMessageSettings(),
                true
        ));
        scheduleSynchronization(event.getPlayer(), 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerQuit(PlayerQuitEvent event) {
        event.quitMessage(renderMessage(
                event.getPlayer(),
                lobbyConfig.getQuitMessageSettings(),
                false
        ));
        lobbyItemService.removeManagedItems(event.getPlayer());
        visibilityService.restorePlayer(event.getPlayer());
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        boolean wasInLobby = lobbyService.isLobbyWorld(event.getFrom());
        boolean isInLobby = lobbyService.isLobbyWorld(player.getWorld());
        if (wasInLobby && !isInLobby) {
            lobbyItemService.removeManagedItems(player);
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
        lobbyItemService.applyLobbyItems(player);
        visibilityService.synchronizePlayer(player);
    }

    private Component renderMessage(
            Player player,
            LobbyConfig.MessageSettings settings,
            boolean join
    ) {
        if (!settings.enabled()) {
            return null;
        }

        Component playerName = Component.text(player.getName());
        try {
            return messageService.deserialize(
                    settings.format(),
                    Placeholder.component("name", playerName)
            );
        } catch (RuntimeException exception) {
            plugin.getLogger().log(
                    Level.WARNING,
                    "Could not render the lobby " + (join ? "join" : "quit")
                            + " message for " + player.getUniqueId() + "; using a safe component fallback.",
                    exception
            );
            return Component.text("[", NamedTextColor.DARK_GRAY)
                    .append(Component.text(join ? "+" : "-", join ? NamedTextColor.GREEN : NamedTextColor.RED))
                    .append(Component.text("] ", NamedTextColor.DARK_GRAY))
                    .append(playerName.color(NamedTextColor.WHITE));
        }
    }
}
