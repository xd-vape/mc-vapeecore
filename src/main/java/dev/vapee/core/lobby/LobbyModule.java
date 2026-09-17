package dev.vapee.core.lobby;

import dev.vapee.core.lobby.command.SetSpawnCommand;
import dev.vapee.core.lobby.command.SpawnCommand;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.lobby.item.LobbyItemService;
import dev.vapee.core.lobby.message.LobbyMessageService;
import dev.vapee.core.lobby.player.LobbyPlayerStateService;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.player.PlayerModule;
import dev.vapee.core.player.PlayerService;
import dev.vapee.core.player.settings.PlayerSettingsService;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class LobbyModule implements CoreModule, ReloadParticipant {

    private final JavaPlugin plugin;
    private final PlayerModule playerModule;
    private final MessageService messageService;

    private LobbyConfig lobbyConfig;
    private LobbyService lobbyService;
    private LobbyItemService lobbyItemService;
    private LobbyMessageService lobbyMessageService;
    private LobbyPlayerStateService lobbyPlayerStateService;
    private LobbyListener lobbyListener;
    private PluginCommand spawnCommand;
    private PluginCommand setSpawnCommand;

    public LobbyModule(JavaPlugin plugin, PlayerModule playerModule, MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.playerModule = Objects.requireNonNull(playerModule, "playerModule");
        this.messageService = Objects.requireNonNull(messageService, "messageService");
    }

    @Override
    public String getName() {
        return "Lobby";
    }

    @Override
    public void enable() {
        LobbyConfig newLobbyConfig = new LobbyConfig(plugin);
        newLobbyConfig.initialize();

        LobbyService newLobbyService = new LobbyService(plugin, newLobbyConfig);
        PlayerService newPlayerService = playerModule.getPlayerService();
        PlayerSettingsService newPlayerSettingsService = playerModule.getPlayerSettingsService();
        LobbyItemService newLobbyItemService = new LobbyItemService(
                plugin,
                newLobbyService,
                newPlayerSettingsService
        );
        LobbyMessageService newLobbyMessageService = new LobbyMessageService(
                plugin,
                newLobbyConfig,
                messageService
        );
        LobbyPlayerStateService newLobbyPlayerStateService = new LobbyPlayerStateService(
                plugin,
                newLobbyConfig,
                newLobbyService,
                newLobbyItemService
        );
        LobbyListener newLobbyListener = new LobbyListener(
                plugin,
                newLobbyConfig,
                newLobbyService,
                newPlayerService,
                newLobbyPlayerStateService
        );
        PluginCommand newSpawnCommand = requireCommand("spawn");
        PluginCommand newSetSpawnCommand = requireCommand("setspawn");

        try {
            plugin.getServer().getPluginManager().registerEvents(newLobbyListener, plugin);
            newSpawnCommand.setExecutor(new SpawnCommand(newLobbyService, messageService));
            newSetSpawnCommand.setExecutor(new SetSpawnCommand(newLobbyService, messageService));
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                if (newPlayerService.isLoaded(player.getUniqueId())
                        && newLobbyService.isLobbyWorld(player.getWorld())) {
                    newLobbyPlayerStateService.synchronizeJoin(player);
                }
            }
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(newLobbyListener);
            newSpawnCommand.setExecutor(null);
            newSetSpawnCommand.setExecutor(null);
            try {
                newLobbyPlayerStateService.cleanup();
            } catch (RuntimeException cleanupException) {
                exception.addSuppressed(cleanupException);
            }
            throw exception;
        }

        lobbyConfig = newLobbyConfig;
        lobbyService = newLobbyService;
        lobbyItemService = newLobbyItemService;
        lobbyMessageService = newLobbyMessageService;
        lobbyPlayerStateService = newLobbyPlayerStateService;
        lobbyListener = newLobbyListener;
        spawnCommand = newSpawnCommand;
        setSpawnCommand = newSetSpawnCommand;
        plugin.getLogger().info("Lobby module enabled.");
    }

    @Override
    public void disable() {
        if (lobbyListener != null) {
            HandlerList.unregisterAll(lobbyListener);
        }
        if (spawnCommand != null) {
            spawnCommand.setExecutor(null);
        }
        if (setSpawnCommand != null) {
            setSpawnCommand.setExecutor(null);
        }
        if (lobbyPlayerStateService != null) {
            lobbyPlayerStateService.cleanup();
        }

        setSpawnCommand = null;
        spawnCommand = null;
        lobbyListener = null;
        lobbyPlayerStateService = null;
        lobbyMessageService = null;
        lobbyItemService = null;
        lobbyService = null;
        lobbyConfig = null;
    }

    public LobbyService getLobbyService() {
        return Objects.requireNonNull(lobbyService, "LobbyModule is not enabled");
    }

    public LobbyConfig getLobbyConfig() {
        return Objects.requireNonNull(lobbyConfig, "LobbyModule is not enabled");
    }

    public LobbyPlayerStateService getLobbyPlayerStateService() {
        return Objects.requireNonNull(lobbyPlayerStateService, "LobbyModule is not enabled");
    }

    public LobbyItemService getLobbyItemService() {
        return Objects.requireNonNull(lobbyItemService, "LobbyModule is not enabled");
    }

    public LobbyMessageService getLobbyMessageService() {
        return Objects.requireNonNull(lobbyMessageService, "LobbyModule is not enabled");
    }

    @Override
    public String getReloadName() {
        return "lobby.yml";
    }

    @Override
    public ReloadPlan prepareReload() {
        LobbyConfig activeConfig = Objects.requireNonNull(lobbyConfig, "LobbyModule is not enabled");
        LobbyService activeService = Objects.requireNonNull(lobbyService, "LobbyModule is not enabled");
        LobbyPlayerStateService activePlayerStateService = Objects.requireNonNull(
                lobbyPlayerStateService,
                "LobbyModule is not enabled"
        );
        LobbyConfig.State previousConfigState = activeConfig.getState();
        LobbyConfig.State preparedConfigState = activeConfig.prepareReloadState();
        var previousSpawn = activeService.getSpawn();

        return ReloadPlan.of(
                () -> {
                    activeConfig.applyState(preparedConfigState);
                    activeService.applySpawn(preparedConfigState.spawn());
                    activePlayerStateService.refreshNormalGameModes();
                },
                () -> {
                    activeConfig.applyState(previousConfigState);
                    activeService.applySpawn(previousSpawn);
                    activePlayerStateService.refreshNormalGameModes();
                }
        );
    }

    private PluginCommand requireCommand(String name) {
        return Objects.requireNonNull(
                plugin.getCommand(name),
                "Command '" + name + "' is missing from plugin.yml"
        );
    }
}
