package dev.vapee.core.lobby;

import dev.vapee.core.lobby.command.SetSpawnCommand;
import dev.vapee.core.lobby.command.SpawnCommand;
import dev.vapee.core.lobby.config.LobbyConfig;
import dev.vapee.core.message.MessageService;
import dev.vapee.core.module.CoreModule;
import dev.vapee.core.reload.ReloadParticipant;
import dev.vapee.core.reload.ReloadPlan;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class LobbyModule implements CoreModule, ReloadParticipant {

    private final JavaPlugin plugin;
    private final MessageService messageService;

    private LobbyConfig lobbyConfig;
    private LobbyService lobbyService;
    private LobbyListener lobbyListener;
    private PluginCommand spawnCommand;
    private PluginCommand setSpawnCommand;

    public LobbyModule(JavaPlugin plugin, MessageService messageService) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
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
        LobbyListener newLobbyListener = new LobbyListener(plugin, newLobbyConfig, newLobbyService);
        PluginCommand newSpawnCommand = requireCommand("spawn");
        PluginCommand newSetSpawnCommand = requireCommand("setspawn");

        try {
            plugin.getServer().getPluginManager().registerEvents(newLobbyListener, plugin);
            newSpawnCommand.setExecutor(new SpawnCommand(newLobbyService, messageService));
            newSetSpawnCommand.setExecutor(new SetSpawnCommand(newLobbyService, messageService));
        } catch (RuntimeException exception) {
            HandlerList.unregisterAll(newLobbyListener);
            newSpawnCommand.setExecutor(null);
            newSetSpawnCommand.setExecutor(null);
            throw exception;
        }

        lobbyConfig = newLobbyConfig;
        lobbyService = newLobbyService;
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

        setSpawnCommand = null;
        spawnCommand = null;
        lobbyListener = null;
        lobbyService = null;
        lobbyConfig = null;
    }

    public LobbyService getLobbyService() {
        return Objects.requireNonNull(lobbyService, "LobbyModule is not enabled");
    }

    public LobbyConfig getLobbyConfig() {
        return Objects.requireNonNull(lobbyConfig, "LobbyModule is not enabled");
    }

    @Override
    public String getReloadName() {
        return "lobby.yml";
    }

    @Override
    public ReloadPlan prepareReload() {
        LobbyConfig activeConfig = Objects.requireNonNull(lobbyConfig, "LobbyModule is not enabled");
        LobbyService activeService = Objects.requireNonNull(lobbyService, "LobbyModule is not enabled");
        LobbyConfig.State previousConfigState = activeConfig.getState();
        LobbyConfig.State preparedConfigState = activeConfig.prepareReloadState();
        var previousSpawn = activeService.getSpawn();

        return ReloadPlan.of(
                () -> {
                    activeConfig.applyState(preparedConfigState);
                    activeService.applySpawn(preparedConfigState.spawn());
                },
                () -> {
                    activeConfig.applyState(previousConfigState);
                    activeService.applySpawn(previousSpawn);
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
